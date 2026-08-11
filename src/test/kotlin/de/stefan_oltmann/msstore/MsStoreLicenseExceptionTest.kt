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

import de.stefan_oltmann.msstore.model.MsStoreLicenseInfo
import de.stefan_oltmann.msstore.model.check
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class MsStoreLicenseExceptionTest {

    @Test
    fun preservesMessageAndCause() {

        val cause = RuntimeException("boom")

        val exception = MsStoreLicenseException("wrapped", cause)

        assertEquals("wrapped", exception.message)
        assertSame(cause, exception.cause)
    }

    @Test
    fun purchaseValidationThrowsOnlyContractException() {

        val exception = assertFailsWith<MsStoreLicenseException> {
            MsStore.requestPurchase("123")
        }

        assertEquals("Store ID must be 12 characters long.", exception.message)
    }

    @Test
    fun checkValidationThrowsOnlyContractException() {

        val exception = assertFailsWith<MsStoreLicenseException> {
            MsStoreLicenseInfo().check("123")
        }

        assertEquals("Store ID must be 12 characters long.", exception.message)
    }
}
