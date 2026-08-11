/*
 * Copyright 2026 Stefan Oltmann
 * https://github.com/StefanOltmann/msstorelib
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.stefan_oltmann.msstore

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.Assume

class MsStoreNativeLoaderTest {

    private val embeddedDll: ByteArray = ByteArray(4096) { (it * 31 % 256).toByte() }

    @Test
    fun sanitizesVersionSegments() {
        assertEquals("1.2.3", MsStoreNativeLoader.sanitizeVersion("1.2.3"))
        assertEquals("0.6.0-SNAPSHOT", MsStoreNativeLoader.sanitizeVersion("0.6.0-SNAPSHOT"))
        assertEquals("a_b_c_d__", MsStoreNativeLoader.sanitizeVersion("a/b\\c:d*?"))
        assertEquals("dev", MsStoreNativeLoader.sanitizeVersion(""))
        assertEquals("dev", MsStoreNativeLoader.sanitizeVersion("   "))
    }

    @Test
    fun resolvesJpackageDllPath() {

        val previous = System.getProperty(JPACKAGE_APP_PATH)

        try {

            val tempDir = createTempDirectory("msstorelib-test")

            System.setProperty(JPACKAGE_APP_PATH, tempDir.resolve("MsStoreApp.exe").toString())

            val dllPath = MsStoreNativeLoader.resolveJPackageDllPath()

            assertEquals(tempDir.resolve("msstore_winrt.dll"), dllPath)

        } finally {
            restoreProperty(JPACKAGE_APP_PATH, previous)
        }
    }

    @Test
    fun resolvesAppLocalDllPathFromJpackageFolder() {

        val previous = System.getProperty(JPACKAGE_APP_PATH)

        try {

            val tempDir = createTempDirectory("msstorelib-test")

            Files.write(tempDir.resolve("msstore_winrt.dll"), embeddedDll)

            System.setProperty(JPACKAGE_APP_PATH, tempDir.resolve("MsStoreApp.exe").toString())

            val dllPath = MsStoreNativeLoader.resolveAppLocalDllPath()

            assertEquals(tempDir.resolve("msstore_winrt.dll").toString(), dllPath)

        } finally {
            restoreProperty(JPACKAGE_APP_PATH, previous)
        }
    }

    @Test
    fun resolvesJarFolderDllPath() {

        val dllPath = MsStoreNativeLoader.resolveJarFolderDllPath()

        assertTrue(dllPath != null && dllPath.endsWith("msstore_winrt.dll"))
    }

    @Test
    fun resolvesWorkingDirDllPath() {

        val workingDir = System.getProperty("user.dir")

        val expected: Path? = Path.of(workingDir, "msstore_winrt.dll")

        assertEquals(expected, MsStoreNativeLoader.resolveWorkingDirDllPath())
    }

    @Test
    fun resolvesVersionedCacheDllPath() {

        val previous = System.getProperty(JAVA_IO_TMPDIR)

        try {

            val tempDir = createTempDirectory("msstorelib-test")

            System.setProperty(JAVA_IO_TMPDIR, tempDir.toString())

            val cachePath = MsStoreNativeLoader.resolveVersionedCacheDllPath()

            assertEquals(
                Path.of(
                    tempDir.toString(),
                    "msstorelib-native",
                    MsStoreNativeLoader.sanitizeVersion(LIB_VERSION),
                    "windows-x86_64",
                    "msstore_winrt.dll"
                ),
                cachePath
            )

        } finally {
            restoreProperty(JAVA_IO_TMPDIR, previous)
        }
    }

    @Test
    fun extractsDllToCachePath() {

        withTempCacheDir { cachePath ->

            MsStoreNativeLoader.extractToCache(cachePath, embeddedDll)

            assertTrue(Files.isRegularFile(cachePath))
            assertContentEquals(embeddedDll, Files.readAllBytes(cachePath))
        }
    }

    @Test
    fun reusesMatchingCacheFile() {

        withTempCacheDir { cachePath ->

            MsStoreNativeLoader.extractToCache(cachePath, embeddedDll)
            MsStoreNativeLoader.extractToCache(cachePath, embeddedDll)

            assertContentEquals(embeddedDll, Files.readAllBytes(cachePath))
        }
    }

    @Test
    fun replacesTruncatedCacheFile() {

        withTempCacheDir { cachePath ->

            /* Simulate a cache file left behind by an interrupted extraction. */
            Files.write(cachePath, embeddedDll.copyOf(100))

            MsStoreNativeLoader.extractToCache(cachePath, embeddedDll)

            assertContentEquals(embeddedDll, Files.readAllBytes(cachePath))
        }
    }

    @Test
    fun replacesTamperedCacheFile() {

        withTempCacheDir { cachePath ->

            /* Simulate a tampered cache file that matches the DLL size. */
            val tampered = embeddedDll.copyOf()
            tampered[0] = (tampered[0] + 1).toByte()

            Files.write(cachePath, tampered)

            MsStoreNativeLoader.extractToCache(cachePath, embeddedDll)

            assertContentEquals(embeddedDll, Files.readAllBytes(cachePath))
        }
    }

    @Test
    fun createsCacheDirectoriesBeforeExtraction() {

        val cacheDir = createTempDirectory("msstorelib-test")

        try {

            val cachePath = cacheDir.resolve("deep").resolve("nested").resolve("msstore_winrt.dll")

            MsStoreNativeLoader.extractToCache(cachePath, embeddedDll)

            assertContentEquals(embeddedDll, Files.readAllBytes(cachePath))

        } finally {
            cacheDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun handlesConcurrentExtraction() {

        withTempCacheDir { cachePath ->

            val threads = (1..8).map {
                Thread { MsStoreNativeLoader.extractToCache(cachePath, embeddedDll) }
            }

            threads.forEach { thread -> thread.start() }
            threads.forEach { thread -> thread.join() }

            assertContentEquals(embeddedDll, Files.readAllBytes(cachePath))
        }
    }

    @Test
    fun brokenAppLocalDllFallsBackToEmbeddedDll() {

        Assume.assumeTrue(System.getProperty("os.name").lowercase().contains("windows"))

        val appLocalDll = requireNotNull(MsStoreNativeLoader.resolveJarFolderDllPath()) {
            "Test setup requires a resolvable app folder."
        }

        val previousBytes = if (Files.isRegularFile(appLocalDll))
            Files.readAllBytes(appLocalDll)
        else
            null

        try {

            Files.write(appLocalDll, embeddedDll)

            /* Loading must succeed via the embedded resource fallback. */
            val symbol = MsStoreNativeLoader.lookup.find("msstore_winrt_get_license")

            assertTrue(symbol.isPresent)
        } finally {
            restoreAppLocalDll(appLocalDll, previousBytes)
        }
    }

    @Test
    fun attachesFallbackErrorsAsSuppressed() {

        val firstError = UnsatisfiedLinkError("app local")
        val secondError = UnsatisfiedLinkError("system path")
        val finalError = UnsatisfiedLinkError("final")

        val result = MsStoreNativeLoader.attachFallbackErrors(finalError, listOf(firstError, secondError))

        assertSame(finalError, result)
        assertEquals(listOf(firstError, secondError), result.suppressed.toList())
    }

    @Test
    fun attachesNoFallbackErrorsWhenListIsEmpty() {

        val finalError = UnsatisfiedLinkError("final")

        val result = MsStoreNativeLoader.attachFallbackErrors(finalError, emptyList())

        assertSame(finalError, result)
        assertTrue(result.suppressed.isEmpty())
    }

    private fun restoreAppLocalDll(appLocalDll: Path, previousBytes: ByteArray?) {

        if (previousBytes == null)
            Files.deleteIfExists(appLocalDll)
        else
            Files.write(appLocalDll, previousBytes)
    }

    private fun withTempCacheDir(block: (Path) -> Unit) {

        val cacheDir = createTempDirectory("msstorelib-test")

        try {
            block(cacheDir.resolve("msstore_winrt.dll"))
        } finally {
            cacheDir.toFile().deleteRecursively()
        }
    }

    private fun restoreProperty(name: String, previousValue: String?) {

        if (previousValue == null)
            System.clearProperty(name)
        else
            System.setProperty(name, previousValue)
    }

    private companion object {

        const val JPACKAGE_APP_PATH = "jpackage.app-path"
        const val JAVA_IO_TMPDIR = "java.io.tmpdir"
    }
}
