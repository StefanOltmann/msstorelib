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

import com.sun.jna.Native

/**
 * Loads the C++/WinRT shared library.
 *
 * By default, JNA resolves `msstore_winrt` via the system library path.
 * If the DLL is embedded in the JAR, it is extracted and loaded automatically.
 * If you need to point at a specific DLL, set the system property `msstore.winrt.path` to a full file path.
 */
internal object MsStoreNativeLoader {

    /** System property used to override the native DLL path. */
    private const val PROP_WINRT_PATH = "msstore.winrt.path"

    /** Default library name resolved by JNA on Windows. */
    private const val LIB_NAME = "msstore_winrt"

    /** JAR resource location where the DLL is embedded at build time. */
    private const val EMBEDDED_RESOURCE = "native/msstore_winrt.dll"

    /**
     * Lazily loads the native library so JVM startup remains fast.
     *
     * Resolution order:
     * 1. User override via `-Dmsstore.winrt.path=...`
     * 2. Embedded DLL extracted from the JAR
     * 3. Default library name on the system path
     */
    val instance: MsStoreNative by lazy {

        val overridePath = System.getProperty(PROP_WINRT_PATH)?.takeIf { it.isNotBlank() }

        val embeddedPath = extractEmbeddedDllPath()

        val nameOrPath = overridePath ?: embeddedPath ?: LIB_NAME

        Native.load(nameOrPath, MsStoreNative::class.java)
    }

    /**
     * Extracts the embedded DLL to a temporary location and returns its path.
     *
     * Returns null if the resource is missing or extraction fails.
     */
    private fun extractEmbeddedDllPath(): String? {

        return try {

            val extracted = Native.extractFromResourcePath(
                EMBEDDED_RESOURCE,
                MsStoreNativeLoader::class.java.classLoader
            )

            extracted.absolutePath

        } catch (_: Exception) {
            null
        }
    }
}
