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

import de.stefan_oltmann.msstore.model.MsStoreRateAndReviewStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class MsStoreRateAndReviewStatusTest {

    @Test
    fun mapsNativeStatusCodes() {
        assertEquals(MsStoreRateAndReviewStatus.Succeeded, MsStoreRateAndReviewStatus.fromNativeCode(0))
        assertEquals(MsStoreRateAndReviewStatus.CanceledByUser, MsStoreRateAndReviewStatus.fromNativeCode(1))
        assertEquals(MsStoreRateAndReviewStatus.NetworkError, MsStoreRateAndReviewStatus.fromNativeCode(2))
        assertEquals(MsStoreRateAndReviewStatus.Error, MsStoreRateAndReviewStatus.fromNativeCode(3))
        assertEquals(MsStoreRateAndReviewStatus.Unknown, MsStoreRateAndReviewStatus.fromNativeCode(42))
    }
}
