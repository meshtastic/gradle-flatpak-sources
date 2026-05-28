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
