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

import java.lang.foreign.SymbolLookup
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Loads the C++/WinRT shared library.
 *
 * Resolution order:
 * 1. Explicit override path (`msstore.winrt.path`)
 * 2. DLL in the hosting app folder (`msstore_winrt.dll`)
 * 3. System library path (`msstore_winrt`)
 * 4. Embedded classpath resource extracted to a versioned cache folder
 *
 * If you need to point at a specific DLL, set the system property `msstore.winrt.path` to a full file path.
 */
internal object MsStoreNativeLoader {

    /** System property used to override the native DLL path. */
    private const val PROP_WINRT_PATH = "msstore.winrt.path"

    /** Base library name used by `System.loadLibrary(...)` (without extension). */
    private const val LIB_NAME = "msstore_winrt"

    /** Native Windows DLL file name used for file-based loads. */
    private const val DLL_FILE_NAME = "$LIB_NAME.dll"

    /** Platform subfolder used both in resources and extraction cache layout. */
    private const val PLATFORM_RESOURCE_DIR = "windows-x86_64"

    /** Embedded resource path inside the JAR. */
    private const val EMBEDDED_RESOURCE = "$PLATFORM_RESOURCE_DIR/$DLL_FILE_NAME"

    /** Root folder under the temp directory where extracted natives are cached. */
    private const val CACHE_ROOT_DIR = "msstorelib-native"

    /** FFM symbol lookup for the loaded native library. */
    val lookup: SymbolLookup by lazy {

        /*
         * Load the native DLL once before any FFM symbol lookup.
         * Symbol lookup depends on the library already being loaded.
         */
        loadNativeLibrary()

        SymbolLookup.loaderLookup()
    }

    /**
     * Loads the native DLL using a strict fallback chain.
     *
     * Extraction from classpath is only attempted as the final fallback.
     */
    private fun loadNativeLibrary() {

        val overridePath = System.getProperty(PROP_WINRT_PATH)?.takeIf { it.isNotBlank() }

        /* 1) Explicit override always wins. */
        if (overridePath != null) {
            System.load(overridePath)
            return
        }

        /* 2) Try DLL next to the host app. */
        val localPath = resolveAppLocalDllPath()

        if (localPath != null) {
            System.load(localPath)
            return
        }

        /* 3) Try standard java.library.path lookup. */
        val systemLoadError = tryLoadFromSystemLibraryPath() ?: return

        /*
         * 4) Final fallback: extract bundled DLL to a versioned cache path and
         *    load from there.
         */
        val extractedPath = extractEmbeddedDllToVersionedCache()
            ?: throw UnsatisfiedLinkError(
                "Could not load '$LIB_NAME'. " +
                    "Checked override path, local app folder, java.library.path, " +
                    "and embedded resource '$EMBEDDED_RESOURCE'."
            ).also { it.addSuppressed(systemLoadError) }

        try {
            System.load(extractedPath)
        } catch (extractLoadError: UnsatisfiedLinkError) {
            extractLoadError.addSuppressed(systemLoadError)
            throw extractLoadError
        }
    }

    /**
     * Attempts `System.loadLibrary(msstore_winrt)`.
     *
     * Returns null on success, otherwise the thrown load error.
     */
    private fun tryLoadFromSystemLibraryPath(): UnsatisfiedLinkError? =
        try {
            System.loadLibrary(LIB_NAME)
            null
        } catch (error: UnsatisfiedLinkError) {
            error
        }

    /**
     * Resolves app-local DLL path candidates and returns the first existing file.
     *
     * Candidate order:
     * - jpackage app folder
     * - folder containing the running jar/classes
     * - current working directory
     */
    private fun resolveAppLocalDllPath(): String? {

        val candidates = sequenceOf(
            resolveJPackageDllPath(),
            resolveJarFolderDllPath(),
            resolveWorkingDirDllPath()
        )

        return candidates
            .filterNotNull()
            .firstOrNull { Files.isRegularFile(it) }
            ?.toAbsolutePath()
            ?.toString()
    }

    /** Returns `<jpackage app folder>/msstore_winrt.dll`, if jpackage is used. */
    private fun resolveJPackageDllPath(): Path? {

        val appPath = System.getProperty("jpackage.app-path")?.takeIf { it.isNotBlank() }
            ?: return null

        return Path.of(appPath).parent?.resolve(DLL_FILE_NAME)
    }

    /** Returns `<jar-or-classes folder>/msstore_winrt.dll`. */
    private fun resolveJarFolderDllPath(): Path? {

        val codeSource = MsStoreNativeLoader::class.java.protectionDomain?.codeSource?.location
            ?: return null

        val locationPath = try {
            Path.of(codeSource.toURI())
        } catch (_: Exception) {
            return null
        }

        val baseDir = if (Files.isDirectory(locationPath))
            locationPath
        else
            locationPath.parent ?: return null

        return baseDir.resolve(DLL_FILE_NAME)
    }

    /** Returns `<working directory>/msstore_winrt.dll`. */
    private fun resolveWorkingDirDllPath(): Path? {

        val workingDir = System.getProperty("user.dir")
            ?.takeIf { it.isNotBlank() }
            ?: return null

        return Path.of(workingDir, DLL_FILE_NAME)
    }

    /**
     * Extracts the embedded DLL into a stable versioned cache location and
     * returns its absolute path.
     *
     * Cache path format:
     * `<tmp>/msstorelib-native/<LIB_VERSION>/windows-x86_64/msstore_winrt.dll`
     *
     * If the file already exists for the same LIB_VERSION, it is reused and no
     * extraction is performed.
     *
     * Returns null when the resource is missing or extraction fails.
     */
    private fun extractEmbeddedDllToVersionedCache(): String? =
        runCatching {

            val cacheDllPath = resolveVersionedCacheDllPath()

            /* Reuse an existing extracted binary for this library version. */
            if (Files.isRegularFile(cacheDllPath) && Files.size(cacheDllPath) > 0L)
                return@runCatching cacheDllPath.toAbsolutePath().toString()

            val input = MsStoreNativeLoader::class.java.classLoader
                .getResourceAsStream(EMBEDDED_RESOURCE)
                ?: return@runCatching null

            input.use { stream ->
                /* Ensure versioned cache directories exist before copy. */
                Files.createDirectories(cacheDllPath.parent)
                Files.copy(stream, cacheDllPath, StandardCopyOption.REPLACE_EXISTING)
            }

            cacheDllPath.toAbsolutePath().toString()

        }.getOrNull()

    /**
     * Computes the cache file path for the current library version.
     */
    private fun resolveVersionedCacheDllPath(): Path {

        val tempRoot = System.getProperty("java.io.tmpdir")
            ?.takeIf { it.isNotBlank() }
            ?: System.getProperty("user.home")
            ?: "."

        return Path.of(
            tempRoot,
            CACHE_ROOT_DIR,
            sanitizePathSegment(LIB_VERSION),
            PLATFORM_RESOURCE_DIR,
            DLL_FILE_NAME
        )
    }

    /**
     * Normalizes values used as path segments.
     *
     * This keeps the cache path stable even if version strings contain
     * characters that are problematic in directory names.
     */
    private fun sanitizePathSegment(value: String): String =
        value
            .ifBlank { "dev" }
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
}
