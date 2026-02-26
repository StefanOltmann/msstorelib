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

import com.sun.jna.Pointer
import java.nio.charset.StandardCharsets

internal object MsStoreNativeHelpers {

    /**
     * Reads a UTF-8 string from native memory and frees the pointer using the
     * native `msstore_winrt_free` function.
     */
    fun readUtf8AndFree(pointer: Pointer?): String? {

        if (pointer == null)
            return null

        val value = pointer.getString(0, StandardCharsets.UTF_8.name())

        /* Always free native allocations to avoid leaking in the JVM process. */
        MsStoreNativeLoader.instance.msstore_winrt_free(pointer)

        return value
    }

    /**
     * Reads the last native error string (if any) and frees the pointer.
     */
    fun readLastError(native: MsStoreNative): String? =
        readUtf8AndFree(native.msstore_winrt_get_last_error())
}
