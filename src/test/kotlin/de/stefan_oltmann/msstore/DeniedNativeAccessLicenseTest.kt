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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/**
 * Regression guard for the JEP 472 error contract of [MsStore.getLicenseInfo].
 *
 * Runs only in the `denyNativeAccessTest` Gradle task, whose JVM starts
 * without `--enable-native-access`, so the restricted FFM calls fail during
 * [MsStoreNative] initialization and the error-wrap path must produce the
 * launch-option help text with the cause chain preserved.
 */
class DeniedNativeAccessLicenseTest {

    @Test
    fun blockedNativeAccessYieldsLaunchHelpText() {

        val exception = assertFailsWith<MsStoreLicenseException> {
            MsStore.getLicenseInfo()
        }

        assertEquals(NATIVE_ACCESS_HELP_MESSAGE, exception.message)

        val initError = assertIs<ExceptionInInitializerError>(exception.cause)
        assertIs<IllegalCallerException>(initError.cause)
    }
}
