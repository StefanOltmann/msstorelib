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
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

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
     * Extraction from the classpath is only attempted as the final fallback.
     * A DLL that exists but fails to load (for example a corrupt or
     * wrong-architecture file) does not stop the chain; only the explicit
     * override fails fast, because it is a deliberate user choice.
     */
    private fun loadNativeLibrary() {

        val overridePath = System.getProperty(PROP_WINRT_PATH)?.takeIf { it.isNotBlank() }

        /* 1) Explicit override always wins. */
        if (overridePath != null) {
            System.load(overridePath)
            return
        }

        /* Errors of the automatic steps, attached to the final failure. */
        val fallbackLoadErrors = mutableListOf<UnsatisfiedLinkError>()

        /* 2) Try DLL next to the host app; a load failure continues with the fallbacks. */
        val localPath = resolveAppLocalDllPath()

        if (localPath != null) {
            try {
                System.load(localPath)
                return
            } catch (error: UnsatisfiedLinkError) {
                fallbackLoadErrors.add(error)
            }
        }

        /* 3) Try standard java.library.path lookup. */
        val systemLoadError = tryLoadFromSystemLibraryPath() ?: return

        fallbackLoadErrors.add(systemLoadError)

        /*
         * 4) Final fallback: extract bundled DLL to a versioned cache path and
         *    load from there.
         */
        val extractedPath = extractEmbeddedDllToVersionedCache()
            ?: throw attachFallbackErrors(
                UnsatisfiedLinkError(
                    "Could not load '$LIB_NAME'. " +
                        "Checked override path, local app folder, java.library.path, " +
                        "and embedded resource '$EMBEDDED_RESOURCE'."
                ),
                fallbackLoadErrors
            )

        try {
            System.load(extractedPath)
        } catch (extractLoadError: UnsatisfiedLinkError) {
            throw attachFallbackErrors(extractLoadError, fallbackLoadErrors)
        }
    }

    /**
     * Returns the given error with all fallback load errors attached as
     * suppressed exceptions, so every step of the chain stays visible.
     */
    internal fun attachFallbackErrors(
        error: UnsatisfiedLinkError,
        fallbackLoadErrors: List<UnsatisfiedLinkError>
    ): UnsatisfiedLinkError {

        for (fallbackError in fallbackLoadErrors)
            error.addSuppressed(fallbackError)

        return error
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
    internal fun resolveAppLocalDllPath(): String? {

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
    internal fun resolveJPackageDllPath(): Path? {

        val appPath = System.getProperty("jpackage.app-path")?.takeIf { it.isNotBlank() }
            ?: return null

        return Path.of(appPath).parent?.resolve(DLL_FILE_NAME)
    }

    /** Returns `<jar-or-classes folder>/msstore_winrt.dll`. */
    internal fun resolveJarFolderDllPath(): Path? {

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
    internal fun resolveWorkingDirDllPath(): Path? {

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
     * An existing cache file is reused only when its content matches the
     * embedded resource, so truncated or tampered files are re-extracted.
     * Extraction writes to a temporary file first and then moves it into
     * place, so concurrent callers never observe partial content.
     *
     * Returns null when the resource is missing or extraction fails.
     */
    private fun extractEmbeddedDllToVersionedCache(): String? =
        runCatching {

            val embeddedDll = readEmbeddedDll() ?: return@runCatching null

            val cacheDllPath = resolveVersionedCacheDllPath()

            extractToCache(cacheDllPath, embeddedDll)

            cacheDllPath.toAbsolutePath().toString()

        }.getOrNull()

    /**
     * Ensures the cache path holds the given DLL content.
     *
     * An existing file is reused only when it is byte-identical to the given
     * bytes; otherwise the file is replaced atomically.
     */
    internal fun extractToCache(cacheDllPath: Path, embeddedDll: ByteArray) {

        if (isSameContent(cacheDllPath, embeddedDll))
            return

        writeDllAtomically(cacheDllPath, embeddedDll)
    }

    /**
     * Returns the embedded DLL bytes, or null when the resource is missing.
     */
    private fun readEmbeddedDll(): ByteArray? =
        MsStoreNativeLoader::class.java.classLoader
            .getResourceAsStream(EMBEDDED_RESOURCE)
            ?.use { stream -> stream.readBytes() }

    /**
     * Returns true when the file exists and is byte-identical to the embedded DLL.
     */
    private fun isSameContent(cacheDllPath: Path, embeddedDll: ByteArray): Boolean {

        if (!Files.isRegularFile(cacheDllPath))
            return false

        if (Files.size(cacheDllPath) != embeddedDll.size.toLong())
            return false

        return sha256(Files.readAllBytes(cacheDllPath)) contentEquals sha256(embeddedDll)
    }

    /**
     * Writes the DLL to a temporary file and moves it into place.
     *
     * The cache path only ever receives fully written content, even when
     * multiple processes or threads extract concurrently.
     */
    private fun writeDllAtomically(cacheDllPath: Path, embeddedDll: ByteArray) {

        /* Ensure versioned cache directories exist before extraction. */
        Files.createDirectories(cacheDllPath.parent)

        val tempFile = Files.createTempFile(cacheDllPath.parent, DLL_FILE_NAME, ".tmp")

        try {

            Files.write(tempFile, embeddedDll)

            try {
                Files.move(tempFile, cacheDllPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: FileSystemException) {
                /* Fall back to a plain replace when the platform rejects
                   atomic moves onto existing targets. */
                Files.move(tempFile, cacheDllPath, StandardCopyOption.REPLACE_EXISTING)
            }

        } finally {
            Files.deleteIfExists(tempFile)
        }
    }

    /**
     * SHA-256 digest of the given bytes, used to verify extracted cache files.
     */
    private fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)

    /**
     * Computes the cache file path for the current library version.
     */
    internal fun resolveVersionedCacheDllPath(): Path {

        val tempRoot = System.getProperty("java.io.tmpdir")
            ?.takeIf { it.isNotBlank() }
            ?: System.getProperty("user.home")
            ?: "."

        return Path.of(
            tempRoot,
            CACHE_ROOT_DIR,
            sanitizeVersion(LIB_VERSION),
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
    internal fun sanitizeVersion(version: String): String =
        version
            .ifBlank { "dev" }
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
}

/**
 * Launch instructions for users whose JVM blocks the restricted FFM calls.
 *
 * Since JDK 24 (JEP 472) restricted methods require explicit opt-in; a
 * future JDK will block them by default.
 */
internal const val NATIVE_ACCESS_HELP_MESSAGE: String =
    "Native access is not enabled for msstorelib. Start the JVM with " +
        "'--enable-native-access=ALL-UNNAMED' (or '--enable-native-access=msstorelib' when the " +
        "library is on the module path) to allow the required FFM calls."
