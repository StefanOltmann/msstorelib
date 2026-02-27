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

import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.nio.charset.StandardCharsets

internal object MsStoreNativeHelpers {

    /**
     * Hard limit when scanning a native C string for its null terminator.
     *
     * This prevents accidental unbounded reads if a native pointer is invalid or
     * not properly null-terminated.
     */
    private const val MAX_C_STRING_BYTES: Long = 16L * 1024L * 1024L

    /**
     * Reads a UTF-8 string from native memory and frees the pointer using the
     * native free function.
     */
    fun readUtf8AndFree(nativeStringSegment: MemorySegment?): String? {

        if (nativeStringSegment == null)
            return null

        val value = readNullTerminatedUtf8(nativeStringSegment)

        /* Always free native allocations to avoid leaking in the JVM process. */
        MsStoreNative.free(nativeStringSegment)

        return value
    }

    /**
     * Reads the last native error string (if any) and frees the pointer.
     */
    fun readLastError(): String? =
        readUtf8AndFree(MsStoreNative.getLastError())

    /**
     * Decodes a null-terminated UTF-8 C string from native memory.
     *
     * The native side allocates these strings with CoTaskMemAlloc and returns a
     * pointer. This method only decodes bytes; releasing memory is handled by
     * [readUtf8AndFree].
     */
    private fun readNullTerminatedUtf8(nativeStringSegment: MemorySegment): String {

        /* Treat pointer as a bounded byte region for safe manual scanning. */
        val cString = nativeStringSegment.reinterpret(MAX_C_STRING_BYTES)

        var length = 0L

        /* Find terminating '\0'. */
        while (length < MAX_C_STRING_BYTES && cString.get(ValueLayout.JAVA_BYTE, length).toInt() != 0) {
            length++
        }

        /* Abort if no terminator was found in the allowed scan window. */
        if (length == MAX_C_STRING_BYTES)
            throw IllegalStateException("Native string exceeds ${MAX_C_STRING_BYTES / 1024 / 1024} MiB.")

        /* Copy bytes into a JVM-owned array before decoding as UTF-8. */
        val bytes = ByteArray(length.toInt())

        var index = 0L

        while (index < length) {
            bytes[index.toInt()] = cString.get(ValueLayout.JAVA_BYTE, index)
            index++
        }

        return String(bytes, StandardCharsets.UTF_8)
    }
}
