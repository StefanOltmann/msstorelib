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

import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class MsStoreNativeHelpersTest {

    @Test
    fun readsAsciiString() {

        Arena.ofConfined().use { arena ->

            val segment = arena.allocateFrom("hello")

            assertEquals("hello", MsStoreNativeHelpers.readStringFromAddress(segment))
        }
    }

    @Test
    fun readsEmptyString() {

        Arena.ofConfined().use { arena ->

            val segment = arena.allocateFrom("")

            assertEquals("", MsStoreNativeHelpers.readStringFromAddress(segment))
        }
    }

    @Test
    fun readsUtf8String() {

        Arena.ofConfined().use { arena ->

            val segment = arena.allocateFrom("grüße")

            assertEquals("grüße", MsStoreNativeHelpers.readStringFromAddress(segment))
        }
    }

    @Test
    fun nullAddressReturnsNull() {
        assertNull(MsStoreNativeHelpers.readStringFromAddress(MemorySegment.NULL))
    }

    @Test
    fun rejectsUnterminatedString() {

        Arena.ofConfined().use { arena ->

            val segment = arena.allocate(MsStoreNativeHelpers.MAX_C_STRING_BYTES + 1L, 1)

            segment.asByteBuffer().put(ByteArray(MsStoreNativeHelpers.MAX_C_STRING_BYTES.toInt()) { 0x41 })

            val exception = assertFailsWith<IllegalStateException> {
                MsStoreNativeHelpers.readNullTerminatedUtf8(segment)
            }

            assertEquals("Native string exceeds 16 MiB.", exception.message)
        }
    }

    @Test
    fun blockedNativeAccessYieldsHelpMessage() {

        val initError = ExceptionInInitializerError(IllegalCallerException("Illegal native access."))

        assertEquals(
            NATIVE_ACCESS_HELP_MESSAGE,
            MsStoreNativeHelpers.initFailureMessage("License query failed.", initError)
        )
    }

    @Test
    fun blockedNativeAccessDeepInChainYieldsHelpMessage() {

        val loadError = UnsatisfiedLinkError("load failed")
        loadError.initCause(IllegalCallerException("blocked"))
        val initError = ExceptionInInitializerError(loadError)

        assertEquals(
            NATIVE_ACCESS_HELP_MESSAGE,
            MsStoreNativeHelpers.initFailureMessage("License query failed.", initError)
        )
    }

    @Test
    fun initFailureSurfacesDeepestCauseMessage() {

        val initError = ExceptionInInitializerError(UnsatisfiedLinkError("Could not load 'msstore_winrt'."))

        assertEquals(
            "Could not load 'msstore_winrt'.",
            MsStoreNativeHelpers.initFailureMessage("License query failed.", initError)
        )
    }

    @Test
    fun initFailureWithoutCauseMessageFallsBack() {

        val initError = ExceptionInInitializerError()

        assertEquals(
            "License query failed.",
            MsStoreNativeHelpers.initFailureMessage("License query failed.", initError)
        )
    }
}
