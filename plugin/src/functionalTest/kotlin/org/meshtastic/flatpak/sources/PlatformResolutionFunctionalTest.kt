/*
 * Copyright (c) 2026 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.meshtastic.flatpak.sources

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Covers `targetPlatforms` + `platformDependencies` against a hand-built local Maven repository.
 *
 * A local repo rather than Maven Central: these assertions are about which modules get resolved, and a fake
 * repo makes that deterministic and offline instead of depending on a real POM's contents (and downloading
 * tens of megabytes of natives). URLs are not asserted, because `file:` repositories are deliberately
 * excluded from the reconstructed URL list — the log line naming each force-resolved module is the
 * observable behaviour here.
 */
class PlatformResolutionFunctionalTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `platform dependencies resolve transitively`() {
        val projectDir = projectUsing(
            platformDependencies = listOf("com.example:root-{platform}:1.0"),
            platforms = listOf("linux-arm64"),
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("captureFlatpakSources", "--stacktrace")
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":captureFlatpakSources")?.outcome)
        assertTrue(
            result.output.contains("force-resolved com.example:root-linux-arm64:1.0"),
            "the declared coordinate itself should be force-resolved:\n${result.output}",
        )
        // The point of the test: nothing declared these, the root's POM did. A consumer used to have to copy
        // them (with their versions and classifiers) out of a POM it does not own. Both real-world shapes are
        // covered — a classified artifact (LWJGL's and maplibre-native-ffi's natives) and an artifactId that
        // ends in the architecture (skiko's, and compose-desktop's own).
        assertTrue(
            result.output.contains("force-resolved com.example:native:1.0"),
            "a classified platform transitive should come along:\n${result.output}",
        )
        assertTrue(
            result.output.contains("force-resolved com.example:sibling-linux-arm64:1.0"),
            "an arch-suffixed platform transitive should come along:\n${result.output}",
        )
    }

    @Test
    fun `an unresolvable coordinate does not cost the others`() {
        val projectDir = projectUsing(
            platformDependencies = listOf("com.example:root-{platform}:1.0", "com.example:absent-{platform}:9.9"),
            platforms = listOf("linux-arm64"),
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("captureFlatpakSources", "--stacktrace")
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":captureFlatpakSources")?.outcome)
        assertTrue(
            result.output.contains("force-resolved com.example:root-linux-arm64:1.0"),
            "a resolvable coordinate should survive an unresolvable sibling:\n${result.output}",
        )
        assertTrue(
            result.output.contains("platform resolution failed for com.example:absent-linux-arm64:9.9"),
            "the warning should name the coordinate that failed:\n${result.output}",
        )
    }

    /**
     * A project whose only repository is a local Maven repo holding `com.example:root-linux-arm64:1.0`, which
     * depends on a platform artifact in each of the two shapes that occur in practice: a classifier
     * (`native:1.0:natives-linux-arm64`, as LWJGL and maplibre-native-ffi publish theirs) and an artifactId
     * ending in the architecture (`sibling-linux-arm64`, as skiko and compose-desktop publish theirs).
     */
    private fun projectUsing(platformDependencies: List<String>, platforms: List<String>): File {
        val projectDir = tempDir.resolve("platform-resolution").toFile().apply { mkdirs() }
        val repoDir = tempDir.resolve("maven").toFile().apply { mkdirs() }

        publishModule(
            repoDir = repoDir,
            artifactId = "root-linux-arm64",
            pomDependencies = """
                <dependencies>
                  <dependency>
                    <groupId>com.example</groupId>
                    <artifactId>native</artifactId>
                    <version>1.0</version>
                    <classifier>natives-linux-arm64</classifier>
                  </dependency>
                  <dependency>
                    <groupId>com.example</groupId>
                    <artifactId>sibling-linux-arm64</artifactId>
                    <version>1.0</version>
                  </dependency>
                </dependencies>
            """.trimIndent(),
        )
        publishModule(repoDir = repoDir, artifactId = "native", classifier = "natives-linux-arm64")
        publishModule(repoDir = repoDir, artifactId = "sibling-linux-arm64")

        File(projectDir, "settings.gradle.kts").writeText(
            """
            plugins {
                id("org.meshtastic.flatpak.sources.settings")
            }
            rootProject.name = "platform-resolution-test"
            """.trimIndent(),
        )
        File(projectDir, "build.gradle.kts").writeText(
            """
            repositories {
                maven { url = uri("${repoDir.toURI()}") }
            }
            flatpakSources {
                targetPlatforms.set(setOf(${platforms.joinToString { "\"$it\"" }}))
                platformDependencies.set(setOf(${platformDependencies.joinToString { "\"$it\"" }}))
            }
            """.trimIndent(),
        )
        return projectDir
    }

    /** Writes a POM and a (empty but valid) jar for one module into [repoDir]. */
    private fun publishModule(
        repoDir: File,
        artifactId: String,
        version: String = "1.0",
        classifier: String? = null,
        pomDependencies: String = "",
    ) {
        val moduleDir = File(repoDir, "com/example/$artifactId/$version").apply { mkdirs() }
        File(moduleDir, "$artifactId-$version.pom").writeText(
            """
            <project>
              <modelVersion>4.0.0</modelVersion>
              <groupId>com.example</groupId>
              <artifactId>$artifactId</artifactId>
              <version>$version</version>
              $pomDependencies
            </project>
            """.trimIndent(),
        )
        val jarName = if (classifier == null) "$artifactId-$version.jar" else "$artifactId-$version-$classifier.jar"
        ZipOutputStream(File(moduleDir, jarName).outputStream()).use { }
    }
}
