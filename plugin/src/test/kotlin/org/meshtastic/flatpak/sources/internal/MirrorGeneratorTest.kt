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
package org.meshtastic.flatpak.sources.internal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MirrorGeneratorTest {

    @Test
    fun `rewrites the repo1 central host to the apache host plus the GCS mirror, preserving the path`() {
        val mirrors =
            MirrorGenerator.mirrorsFor("https://repo1.maven.org/maven2/org/example/lib/1.0/lib-1.0.jar")

        assertEquals(
            listOf(
                "https://repo.maven.apache.org/maven2/org/example/lib/1.0/lib-1.0.jar",
                "https://maven-central.storage-download.googleapis.com/maven2/org/example/lib/1.0/lib-1.0.jar",
            ),
            mirrors,
        )
    }

    @Test
    fun `rewrites the apache central host to repo1 plus the GCS mirror`() {
        val mirrors =
            MirrorGenerator.mirrorsFor("https://repo.maven.apache.org/maven2/a/b/1.0/b-1.0.pom")

        assertEquals(
            listOf(
                "https://repo1.maven.org/maven2/a/b/1.0/b-1.0.pom",
                "https://maven-central.storage-download.googleapis.com/maven2/a/b/1.0/b-1.0.pom",
            ),
            mirrors,
        )
    }

    @Test
    fun `returns no mirrors for non-central hosts`() {
        // Google, JitPack and the Gradle plugin portal have no well-known public mirrors.
        assertTrue(MirrorGenerator.mirrorsFor("https://jitpack.io/com/github/x/y/1.0/y-1.0.jar").isEmpty())
        assertTrue(MirrorGenerator.mirrorsFor("https://dl.google.com/dl/android/maven2/a/b/1.0/b.aar").isEmpty())
        assertTrue(MirrorGenerator.mirrorsFor("https://plugins.gradle.org/m2/p/p.jar").isEmpty())
    }

    @Test
    fun `returns no mirrors for a malformed or hostless URL`() {
        assertTrue(MirrorGenerator.mirrorsFor("not a valid url").isEmpty())
        assertTrue(MirrorGenerator.mirrorsFor("").isEmpty())
    }
}
