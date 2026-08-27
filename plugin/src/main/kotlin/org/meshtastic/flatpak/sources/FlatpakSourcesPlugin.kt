/*
 * Copyright (c) 2026 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.meshtastic.flatpak.sources

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.provider.Provider
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.BuildOperationListener
import org.gradle.internal.operations.BuildOperationListenerManager
import org.gradle.internal.operations.OperationFinishEvent
import org.gradle.internal.operations.OperationIdentifier
import org.gradle.internal.operations.OperationProgressEvent
import org.gradle.internal.operations.OperationStartEvent
import org.gradle.internal.resource.ExternalResourceReadBuildOperationType
import org.meshtastic.flatpak.sources.internal.SourcesWriter
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Captures every external resource URL Gradle reads via the internal BuildOperationListener API and emits a
 * Flathub-compliant flatpak-sources.json at build finish.
 *
 * Apply the companion settings plugin in `settings.gradle.kts` for complete capture from build start:
 * ```kotlin
 * plugins { id("org.meshtastic.flatpak.sources.settings") version "..." }
 * ```
 * The settings plugin auto-applies this plugin to the root project; there is no need to apply both.
 */
class FlatpakSourcesPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        check(target == target.rootProject) { "org.meshtastic.flatpak.sources must be applied to the root project" }

        // No-op in offline mode — nothing to capture.
        if (target.gradle.startParameter.isOffline) return

        val extension = target.extensions.create("flatpakSources", FlatpakSourcesExtension::class.java)
        extension.outputFile.convention(target.layout.buildDirectory.file("flatpak-sources.json"))

        val urlCaptureService = registerUrlCaptureService(target)
        // Called for its side effect only — the returned set must not be captured below, see doLast.
        resolveCapturedUrls(target, urlCaptureService)

        target.tasks.register("captureFlatpakSources") {
            group = "flatpak"
            description = "Emit flatpak-sources.json from URLs captured via BuildOperationListener."
            outputs.upToDateWhen { false }
            outputs.file(extension.outputFile)

            val afterTasks = extension.mustRunAfterTasks.get()
            if (afterTasks.isNotEmpty()) mustRunAfter(afterTasks)

            val outFile = extension.outputFile
            val destPrefix = extension.destPrefix
            val excludeSuffixes = extension.excludeSuffixes
            val generateMirrors = extension.generateMirrors
            val filesRoot = File(target.gradle.gradleUserHomeDir, "caches/modules-2/files-2.1")

            // Config-cache/Isolated-Projects forbid capturing a live Project (or anything holding one, like a
            // detached Configuration) inside doLast. tasks.register {} is already lazy — this block only runs
            // when the task is actually needed — so resolving here instead of in doLast still only happens
            // on-demand; it just does so at configuration time rather than execution time. Only the plain
            // List<String>/Logger results below cross into the doLast closure.
            val platformUrls =
                resolvePlatformArtifacts(
                    target,
                    extension.targetPlatforms.getOrElse(emptySet()),
                    extension.platformDependencies.getOrElse(emptySet()),
                )
            val logger = target.logger

            doLast {
                val service = urlCaptureService.get()
                val repoUrls = service.repoUrls
                // Snapshot at execution: the listener keeps writing to this set, and capturing it live puts a
                // mutating ConcurrentHashMap keyset in the CC entry, which Gradle fails to serialize.
                val capturedUrls = service.capturedUrls?.toSet().orEmpty()
                val cacheUrls =
                    if (repoUrls.isNotEmpty()) {
                        scanCacheForMissingArtifacts(filesRoot, capturedUrls, repoUrls, logger)
                    } else {
                        emptyList()
                    }

                val allUrls = capturedUrls.toList() + platformUrls + cacheUrls
                val writer =
                    SourcesWriter(
                        filesRoot = filesRoot,
                        destPrefix = destPrefix.get(),
                        excludeSuffixes = excludeSuffixes.get(),
                        generateMirrors = generateMirrors.get(),
                        repoUrls = repoUrls,
                        logger = logger,
                    )
                writer.write(allUrls, outFile.get().asFile)
            }
        }
    }

    /**
     * Looks up the [UrlCaptureBuildService] the settings plugin registered (by name — `sharedServices` is a
     * singleton registry, so this returns the existing instance rather than creating a new one). Isolated-Projects-
     * safe: unlike `gradle.extensions`, a `BuildService` is a sanctioned cross-project channel, not a read through
     * another project's entry on the shared `Gradle` object.
     */
    private fun registerUrlCaptureService(target: Project): Provider<UrlCaptureBuildService> =
        target.gradle.sharedServices.registerIfAbsent(
            URL_CAPTURE_SERVICE_NAME,
            UrlCaptureBuildService::class.java,
        ) {}

    /**
     * If the settings plugin isn't applied, [urlCaptureService] holds a fresh (empty) instance — fall back to a
     * locally-attached listener. It won't capture bootstrap downloads but gets everything from here on.
     */
    private fun resolveCapturedUrls(
        target: Project,
        urlCaptureService: Provider<UrlCaptureBuildService>,
    ): MutableSet<String> =
        urlCaptureService.get().capturedUrls
            ?: ConcurrentHashMap.newKeySet<String>().also { fallback ->
                urlCaptureService.get().capturedUrls = fallback
                val manager = (target as ProjectInternal).services.get(BuildOperationListenerManager::class.java)
                manager.addListener(OpListener(fallback))
                target.logger.warn(
                    "flatpak-sources: settings plugin not applied — bootstrap downloads will be missing. " +
                        "Add id(\"org.meshtastic.flatpak.sources.settings\") to settings.gradle.kts.",
                )
            }

    private class OpListener(private val urls: MutableSet<String>) : BuildOperationListener {
        override fun started(op: BuildOperationDescriptor, e: OperationStartEvent) = Unit
        override fun progress(id: OperationIdentifier, e: OperationProgressEvent) = Unit
        override fun finished(op: BuildOperationDescriptor, e: OperationFinishEvent) {
            val details = op.details as? ExternalResourceReadBuildOperationType.Details ?: return
            if (e.failure != null) return
            urls.add(details.location)
        }
    }

    companion object {
        internal const val URL_CAPTURE_SERVICE_NAME = "flatpakSourcesUrlCapture"

        /**
         * Force-resolves platform-specific dependencies that wouldn't normally resolve on the current host OS.
         * Downloads them into the standard Gradle cache so [SourcesWriter] can find them via the cache locator.
         *
         * Each coordinate is resolved *transitively*, and in its own detached configuration — see
         * [resolvePlatformCoordinate] for why both matter.
         *
         * @return URLs reconstructed from the resolved artifact coordinates and repositories.
         */
        fun resolvePlatformArtifacts(
            project: Project,
            platforms: Set<String>,
            dependencyTemplates: Set<String>,
        ): List<String> {
            if (platforms.isEmpty() || dependencyTemplates.isEmpty()) return emptyList()

            val repoUrls =
                project.repositories
                    .filterIsInstance<org.gradle.api.artifacts.repositories.MavenArtifactRepository>()
                    .map { it.url.toString().trimEnd('/') }
                    .filter { !it.startsWith("file:") }

            return platforms.flatMap { platform ->
                dependencyTemplates.flatMap { template ->
                    resolvePlatformCoordinate(project, template.replace("{platform}", platform), repoUrls)
                }
            }
        }

        /**
         * Force-resolves one platform coordinate and everything it depends on.
         *
         * Resolution is **transitive** because a platform artifact's own natives are themselves
         * platform-specific, and so are just as absent from the generation host's resolution as the artifact
         * that pulls them: `desktop-jvm-<platform>` needs `skiko-awt-runtime-<platform>`, and maplibre's
         * desktop runtime needs both the maplibre-native-ffi natives and LWJGL's. Non-transitive resolution
         * meant every consumer had to enumerate those by hand, version and classifier included, from POMs it
         * does not own — which went stale silently and surfaced as `Could not find <jar>` minutes into the
         * offline build on the other architecture.
         *
         * One configuration per coordinate, so an entry that cannot resolve costs only itself. A single shared
         * configuration failed as a unit: one bad coordinate dropped every platform URL, and the warning named
         * none of them.
         */
        private fun resolvePlatformCoordinate(
            project: Project,
            notation: String,
            repoUrls: List<String>,
        ): List<String> {
            val config = project.configurations.detachedConfiguration(project.dependencies.create(notation))
            config.isTransitive = true

            return try {
                config.incoming.artifacts.artifacts.flatMap { artifact ->
                    val componentId = artifact.id.componentIdentifier
                    if (componentId !is ModuleComponentIdentifier) return@flatMap emptyList()
                    project.logger.lifecycle(
                        "flatpak-sources: force-resolved {} (platform artifact)",
                        "${componentId.group}:${componentId.module}:${componentId.version}",
                    )
                    artifactToUrls(componentId, artifact.file.name, repoUrls)
                }
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                project.logger.warn("flatpak-sources: platform resolution failed for {} — {}", notation, e.message)
                emptyList()
            }
        }

        private fun artifactToUrls(
            mid: ModuleComponentIdentifier,
            filename: String,
            repoUrls: List<String>,
        ): List<String> {
            val groupPath = mid.group.replace('.', '/')
            val pomFile = "${mid.module}-${mid.version}.pom"
            return repoUrls.flatMap { repoUrl ->
                val base = "$repoUrl/$groupPath/${mid.module}/${mid.version}"
                buildList {
                    add("$base/$filename")
                    if (filename != pomFile) add("$base/$pomFile")
                }
            }
        }

        /**
         * Scans the Gradle file cache for artifacts not already in [capturedUrls]. Picks up artifacts resolved
         * during included build configuration (e.g. build-logic's kotlin-dsl plugin) that the listener missed.
         *
         * Uses HEAD checks to validate URLs before emitting.
         */
        fun scanCacheForMissingArtifacts(
            filesRoot: File,
            capturedUrls: Set<String>,
            repoUrls: List<String>,
            logger: org.gradle.api.logging.Logger,
        ): List<String> {
            if (!filesRoot.isDirectory) return emptyList()
            val urls = mutableListOf<String>()

            filesRoot.listFiles { f -> f.isDirectory }?.forEach { groupDir ->
                val groupPath = groupDir.name.replace('.', '/')
                groupDir.listFiles { f -> f.isDirectory }?.forEach { artifactDir ->
                    artifactDir.listFiles { f -> f.isDirectory }?.forEach { versionDir ->
                        urls.addAll(scanVersionDir(versionDir, groupPath, artifactDir.name, capturedUrls, repoUrls))
                    }
                }
            }

            if (urls.isNotEmpty()) {
                logger.lifecycle("flatpak-sources: cache scan found {} additional URLs", urls.size)
            }
            return urls
        }

        private fun scanVersionDir(
            versionDir: File,
            groupPath: String,
            artifact: String,
            capturedUrls: Set<String>,
            repoUrls: List<String>,
        ): List<String> {
            val version = versionDir.name
            val files =
                versionDir.listFiles { f -> f.isDirectory }
                    ?.flatMap { hashDir -> hashDir.listFiles()?.filter { it.isFile }?.toList() ?: emptyList() }
                    ?: emptyList()

            val result = mutableListOf<String>()
            for (file in files) {
                val filename = file.name
                val alreadyCaptured =
                    repoUrls.any { repo -> capturedUrls.contains("$repo/$groupPath/$artifact/$version/$filename") }
                if (alreadyCaptured) continue
                for (repoUrl in repoUrls) {
                    val fileUrl = "$repoUrl/$groupPath/$artifact/$version/$filename"
                    if (headCheck(fileUrl)) {
                        result.add(fileUrl)
                        break
                    }
                }
            }
            return result
        }

        private fun headCheck(url: String): Boolean =
            try {
                val conn = java.net.URI(url).toURL().openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "HEAD"
                conn.connectTimeout = HEAD_TIMEOUT_MS
                conn.readTimeout = HEAD_TIMEOUT_MS
                conn.instanceFollowRedirects = true
                val code = conn.responseCode
                conn.disconnect()
                code in HTTP_OK_RANGE
            } catch (_: Exception) {
                false
            }

        private const val HEAD_TIMEOUT_MS = 5_000
        private val HTTP_OK_RANGE = 200..299
    }
}
