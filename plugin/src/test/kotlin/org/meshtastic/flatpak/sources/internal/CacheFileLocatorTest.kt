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

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.io.TempDir

class CacheFileLocatorTest {

    @Test
    fun `locate finds the cached file, deriving the group from the URL and stripping the repo prefix`(
        @TempDir root: File,
    ) {
        // Gradle files-2.1 layout: <group-with-dots>/<artifact>/<version>/<content-sha1>/<filename>
        val jar = File(root, "org.example/lib/1.0/deadbeefcafe/lib-1.0.jar")
        jar.parentFile.mkdirs()
        jar.writeText("artifact bytes")

        // The URL carries a `/maven2/` repo prefix that must be stripped; the group is `org.example`.
        val found =
            CacheFileLocator.locate(root, "https://repo1.maven.org/maven2/org/example/lib/1.0/lib-1.0.jar")

        assertEquals(jar.canonicalFile, found?.canonicalFile)
    }

    @Test
    fun `locate returns null when filesRoot is not a directory`() {
        assertNull(
            CacheFileLocator.locate(File("/no/such/cache/dir"), "https://repo1.maven.org/maven2/a/b/1.0/b-1.0.jar"),
        )
    }

    @Test
    fun `locate returns null when the URL has fewer than three path segments`(@TempDir root: File) {
        assertNull(CacheFileLocator.locate(root, "https://repo1.maven.org/a/b"))
    }

    @Test
    fun `relativePath splits a full cache path into group, artifact, version, sha1 and filename`(@TempDir root: File) {
        val cacheFile = File(root, "org.example/lib/1.0/deadbeefcafe/lib-1.0.jar")

        assertEquals(
            listOf("org.example", "lib", "1.0", "deadbeefcafe", "lib-1.0.jar"),
            CacheFileLocator.relativePath(root, cacheFile),
        )
    }

    @Test
    fun `relativePath returns null for a path with fewer than five components`(@TempDir root: File) {
        assertNull(CacheFileLocator.relativePath(root, File(root, "a/b/c")))
    }
}
