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

    /** Number of bytes in one mebibyte, used for size limits and messages. */
    private const val BYTES_PER_MEBIBYTE: Long = 1024L * 1024L

    /**
     * Hard limit when scanning a native C string for its null terminator.
     *
     * This prevents accidental unbounded reads if a native pointer is invalid or
     * not properly null-terminated.
     */
    internal const val MAX_C_STRING_BYTES: Long = 16L * BYTES_PER_MEBIBYTE

    /**
     * Reads a UTF-8 string from native memory and frees the pointer using the
     * native free function.
     */
    fun readUtf8AndFree(nativeStringSegment: MemorySegment?): String? {

        if (nativeStringSegment == null)
            return null

        return try {

            readNullTerminatedUtf8(nativeStringSegment)

        } finally {

            /* Always free native allocations to avoid leaking in the JVM process. */
            MsStoreNative.free(nativeStringSegment)
        }
    }

    /**
     * Reads the last native error string (if any) and frees the pointer.
     */
    fun readLastError(): String? =
        readUtf8AndFree(MsStoreNative.getLastError())

    /**
     * Returns the user-facing message for a native initialization failure.
     *
     * A blocked restricted API (JEP 472) yields the launch-option help text;
     * otherwise the most specific message in the cause chain is used, so DLL
     * load errors stay visible in the exception message. Falls back to the
     * given text when no cause carries a message.
     */
    fun initFailureMessage(fallback: String, initError: ExceptionInInitializerError): String {

        var current: Throwable? = initError
        var mostSpecificMessage: String? = null

        while (current != null) {

            if (current is IllegalCallerException)
                return NATIVE_ACCESS_HELP_MESSAGE

            if (!current.message.isNullOrBlank())
                mostSpecificMessage = current.message

            current = current.cause
        }

        return mostSpecificMessage ?: fallback
    }

    /**
     * Reads a null-terminated UTF-8 string from the given native address.
     *
     * This does NOT free the pointer, as the pointer is typically owned by a
     * parent struct (e.g., MsStoreLicenseNative).
     */
    fun readStringFromAddress(addressSegment: MemorySegment): String? {

        if (addressSegment.address() == 0L)
            return null

        return readNullTerminatedUtf8(addressSegment)
    }

    /**
     * Decodes a null-terminated UTF-8 C string from native memory.
     *
     * The native side allocates these strings with CoTaskMemAlloc and returns a
     * pointer. This method only decodes bytes; releasing memory is handled by
     * [readUtf8AndFree].
     */
    internal fun readNullTerminatedUtf8(nativeStringSegment: MemorySegment): String {

        /* Treat pointer as a bounded byte region for safe manual scanning. */
        val cString = nativeStringSegment.reinterpret(MAX_C_STRING_BYTES)

        var length = 0L

        /* Find terminating '\0'. */
        while (length < MAX_C_STRING_BYTES && cString.get(ValueLayout.JAVA_BYTE, length).toInt() != 0)
            length++

        /* Abort if no terminator was found in the allowed scan window. */
        if (length == MAX_C_STRING_BYTES)
            throw IllegalStateException("Native string exceeds ${MAX_C_STRING_BYTES / BYTES_PER_MEBIBYTE} MiB.")

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
