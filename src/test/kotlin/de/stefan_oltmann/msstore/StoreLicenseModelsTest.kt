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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Verifies that the JSON parser accepts known fields and ignores unknown ones.
 */
class StoreLicenseModelsTest {

    @Test
    fun parsesExtendedJsonDataSubset() {

        /* Representative subset of ExtendedJsonData with one add-on. */
        val json = """
            {
              "productId": "9NBLGGH4R315",
              "skuId": "0010",
              "expiration": "2030-01-01T00:00:00Z",
              "isActive": true,
              "isTrial": false,
              "isTrialOwnedByThisUser": false,
              "trialTimeRemaining": "PT0S",
              "inAppOfferToken": "",
              "productAddOns": [
                {
                  "inAppOfferToken": "addon-token",
                  "productId": "9NBLGGH4R316",
                  "productType": "Durable",
                  "skuId": "0001",
                  "skuType": "Full",
                  "expiration": "2030-01-01T00:00:00Z",
                  "isActive": true
                }
              ],
              "unknownField": "ignored"
            }
        """.trimIndent()

        /* Parse and validate that known fields map correctly. */
        val info = MsStoreLicense.parseLicenseJson(json)

        assertEquals("9NBLGGH4R315", info.productId)
        assertEquals("0010", info.skuId)
        assertEquals("2030-01-01T00:00:00Z", info.expiration)
        assertTrue(info.isActive)
        assertFalse(info.isTrial)
        assertFalse(info.isTrialOwnedByThisUser)
        assertEquals("PT0S", info.trialTimeRemaining)
        assertEquals("", info.inAppOfferToken)
        assertEquals(1, info.productAddOns.size)

        val addOn = info.productAddOns[0]
        assertEquals("addon-token", addOn.inAppOfferToken)
        assertEquals("9NBLGGH4R316", addOn.productId)
        assertEquals("Durable", addOn.productType)
        assertEquals("0001", addOn.skuId)
        assertEquals("Full", addOn.skuType)
        assertEquals("2030-01-01T00:00:00Z", addOn.expiration)
        assertTrue(addOn.isActive)
    }

    @Test
    fun usesDefaultValuesWhenMissingFields() {

        /* Minimal payload: missing fields should fall back to defaults. */
        val json = """
            {
              "productId": "9NBLGGH4R317",
              "isActive": false
            }
        """.trimIndent()

        /* Parse and assert defaults for unspecified fields. */
        val info = MsStoreLicense.parseLicenseJson(json)

        assertEquals("9NBLGGH4R317", info.productId)
        assertFalse(info.isActive)
        assertEquals("", info.skuId)
        assertEquals("", info.expiration)
        assertEquals(0, info.productAddOns.size)
    }
}
