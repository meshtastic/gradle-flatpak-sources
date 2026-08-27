/*
 * Copyright (c) 2026 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.meshtastic.flatpak.sources

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FlatpakSourcesPluginFunctionalTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `plugin applies successfully and task exists`() {
        val projectDir = createTempProject()

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("captureFlatpakSources", "--stacktrace")
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":captureFlatpakSources")?.outcome)
        assertTrue(File(projectDir, "build/flatpak-sources.json").exists())
    }

    @Test
    fun `custom output file path is respected`() {
        val projectDir = createTempProject(
            extraConfig = """
                flatpakSources {
                    outputFile.set(layout.buildDirectory.file("custom-output.json"))
                }
            """.trimIndent(),
        )

        GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("captureFlatpakSources")
            .build()

        assertTrue(File(projectDir, "build/custom-output.json").exists())
    }

    @Test
    fun `output JSON is valid array`() {
        val projectDir = createTempProject()

        GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("captureFlatpakSources")
            .build()

        val content = File(projectDir, "build/flatpak-sources.json").readText().trim()
        assertTrue(content.startsWith("["))
        assertTrue(content.endsWith("]"))
    }

    @Test
    fun `multi-module project works`() {
        val projectDir = tempDir.resolve("multimodule").toFile().apply { mkdirs() }
        File(projectDir, "settings.gradle.kts").writeText(
            """
            rootProject.name = "multi-module-test"
            include(":lib")
            """.trimIndent(),
        )
        File(projectDir, "build.gradle.kts").writeText(
            """
            plugins {
                id("org.meshtastic.flatpak.sources")
            }
            flatpakSources {
                mustRunAfterTasks.set(listOf(":lib:compileJava"))
            }
            """.trimIndent(),
        )
        val libDir = File(projectDir, "lib").apply { mkdirs() }
        File(libDir, "build.gradle.kts").writeText(
            """
            plugins {
                java
            }
            """.trimIndent(),
        )
        File(libDir, "src/main/java").mkdirs()

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("captureFlatpakSources", "--stacktrace")
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":captureFlatpakSources")?.outcome)
    }

    @Test
    fun `settings plugin applies project plugin and task exists`() {
        val projectDir = tempDir.resolve("settings").toFile().apply { mkdirs() }
        File(projectDir, "settings.gradle.kts").writeText(
            """
            plugins {
                id("org.meshtastic.flatpak.sources.settings")
            }
            rootProject.name = "settings-test"
            """.trimIndent(),
        )
        File(projectDir, "build.gradle.kts").writeText("")

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("captureFlatpakSources", "--stacktrace")
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":captureFlatpakSources")?.outcome)
        assertTrue(File(projectDir, "build/flatpak-sources.json").exists())
    }

    @Test
    fun `settings plugin captures URLs without init script warning`() {
        val projectDir = tempDir.resolve("settings-capture").toFile().apply { mkdirs() }
        File(projectDir, "settings.gradle.kts").writeText(
            """
            plugins {
                id("org.meshtastic.flatpak.sources.settings")
            }
            rootProject.name = "settings-capture-test"
            """.trimIndent(),
        )
        File(projectDir, "build.gradle.kts").writeText(
            """
            plugins {
                java
            }
            repositories {
                mavenCentral()
            }
            """.trimIndent(),
        )
        File(projectDir, "src/main/java").mkdirs()

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("captureFlatpakSources", "--stacktrace")
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":captureFlatpakSources")?.outcome)
        // Settings plugin pre-registers the listener, so the project plugin should NOT warn
        assertTrue(
            !result.output.contains("settings plugin not applied"),
            "Settings plugin should suppress the missing-settings-plugin warning",
        )
    }

    @Test
    fun `settings plugin works with included build reuse pattern`() {
        val projectDir = tempDir.resolve("reuse").toFile().apply { mkdirs() }
        File(projectDir, "settings.gradle.kts").writeText(
            """
            plugins {
                id("org.meshtastic.flatpak.sources.settings")
            }
            rootProject.name = "reuse-test"
            """.trimIndent(),
        )
        File(projectDir, "build.gradle.kts").writeText("")

        // Apply settings plugin a second time to simulate included-build reuse
        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("captureFlatpakSources", "--stacktrace")
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":captureFlatpakSources")?.outcome)
    }

    @Test
    fun `settings plugin works under Isolated Projects in a multi-module build`() {
        // Regression test: the settings plugin used to publish capturedUrls/repoUrls onto
        // gradle.extensions, and FlatpakSourcesPlugin (applied to the root project) read them back
        // via project.gradle.extensions.findByName(...) — a cross-project read through the shared
        // Gradle object, which Isolated Projects forbids. Both now flow through a BuildService
        // instead (Gradle's sanctioned cross-project sharing primitive), looked up by name from
        // either project. This test only proves anything with a real second project + IP enabled.
        val projectDir = tempDir.resolve("isolated-projects").toFile().apply { mkdirs() }
        File(projectDir, "settings.gradle.kts").writeText(
            """
            plugins {
                id("org.meshtastic.flatpak.sources.settings")
            }
            rootProject.name = "isolated-projects-test"
            include(":lib")
            """.trimIndent(),
        )
        File(projectDir, "build.gradle.kts").writeText(
            """
            flatpakSources {
                mustRunAfterTasks.set(listOf(":lib:compileJava"))
            }
            """.trimIndent(),
        )
        val libDir = File(projectDir, "lib").apply { mkdirs() }
        File(libDir, "build.gradle.kts").writeText(
            """
            plugins {
                java
            }
            repositories {
                mavenCentral()
            }
            """.trimIndent(),
        )
        File(libDir, "src/main/java").mkdirs()

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(
                "captureFlatpakSources",
                "--configuration-cache",
                "-Dorg.gradle.unsafe.isolated-projects=true",
                "--stacktrace",
            )
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":captureFlatpakSources")?.outcome)
        val content = File(projectDir, "build/flatpak-sources.json").readText().trim()
        assertTrue(content.startsWith("["))
        assertTrue(content.endsWith("]"))
    }

    @Test
    fun `reused configuration cache entry reads the live captured set, not a serialized copy`() {
        // Regression guard for the live-set capture. The entry is stored at the end of configuration,
        // so a task action closing over the service's MutableSet serialized it while still empty; on
        // reuse the action got that empty copy instead of what the listener had since collected.
        // Markers injected at execution time (which still runs on a reused entry) expose the
        // difference: buggy code reports 0 captured URLs on run 2, fixed code reports 2.
        val projectDir = tempDir.resolve("cc-reuse").toFile().apply { mkdirs() }
        File(projectDir, "settings.gradle.kts").writeText(
            """
            plugins {
                id("org.meshtastic.flatpak.sources.settings")
            }
            rootProject.name = "cc-reuse-test"
            """.trimIndent(),
        )
        File(projectDir, "build.gradle.kts").writeText(
            """
            tasks.register("injectMarker") {
                // Declared inside register{} so doLast captures these and not the enclosing script.
                // The service Provider resolves at configuration time; capturedUrls is `internal`,
                // so reach its mangled getter reflectively.
                val capture = gradle.sharedServices.registrations.getByName("flatpakSourcesUrlCapture").service
                val markerFile = File(projectDir, "marker.txt")
                doLast {
                    val service = capture.get()
                    val getter = service.javaClass.methods.first { it.name.startsWith("getCapturedUrls") }
                    @Suppress("UNCHECKED_CAST")
                    val urls = getter.invoke(service) as MutableSet<String>
                    urls.addAll(markerFile.readLines().filter { it.isNotBlank() })
                }
            }

            flatpakSources {
                mustRunAfterTasks.set(listOf(":injectMarker"))
            }
            """.trimIndent(),
        )

        val marker = File(projectDir, "marker.txt")

        fun run(): BuildResult =
            GradleRunner.create()
                .withProjectDir(projectDir)
                .withPluginClasspath()
                .withArguments("injectMarker", "captureFlatpakSources", "--configuration-cache", "--stacktrace")
                .build()

        marker.writeText("https://example.invalid/run-one.jar")
        val first = run()
        assertEquals(TaskOutcome.SUCCESS, first.task(":captureFlatpakSources")?.outcome)
        assertTrue(first.output.contains("captured 1 URLs"), "first run should see its own marker")

        marker.writeText("https://example.invalid/run-two-a.jar\nhttps://example.invalid/run-two-b.jar")
        val second = run()
        assertEquals(TaskOutcome.SUCCESS, second.task(":captureFlatpakSources")?.outcome)
        assertTrue(second.output.contains("Configuration cache entry reused"), "second run must reuse the entry")
        assertTrue(second.output.contains("captured 2 URLs"), "reused entry must read the live set, not a copy")
    }

    private fun createTempProject(extraConfig: String = ""): File {
        val projectDir = tempDir.resolve("project").toFile().apply { mkdirs() }
        File(projectDir, "settings.gradle.kts").writeText(
            """
            rootProject.name = "test-project"
            """.trimIndent(),
        )
        File(projectDir, "build.gradle.kts").writeText(
            """
            plugins {
                id("org.meshtastic.flatpak.sources")
            }
            $extraConfig
            """.trimIndent(),
        )
        return projectDir
    }
}
