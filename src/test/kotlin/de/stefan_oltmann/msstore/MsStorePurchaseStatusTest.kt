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

import de.stefan_oltmann.msstore.model.MsStorePurchaseStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class MsStorePurchaseStatusTest {

    @Test
    fun mapsNativeStatusCodes() {
        assertEquals(MsStorePurchaseStatus.Succeeded, MsStorePurchaseStatus.fromNativeCode(0))
        assertEquals(MsStorePurchaseStatus.AlreadyPurchased, MsStorePurchaseStatus.fromNativeCode(1))
        assertEquals(MsStorePurchaseStatus.NotPurchased, MsStorePurchaseStatus.fromNativeCode(2))
        assertEquals(MsStorePurchaseStatus.NetworkError, MsStorePurchaseStatus.fromNativeCode(3))
        assertEquals(MsStorePurchaseStatus.ServerError, MsStorePurchaseStatus.fromNativeCode(4))
        assertEquals(MsStorePurchaseStatus.Unknown, MsStorePurchaseStatus.fromNativeCode(42))
    }
}
