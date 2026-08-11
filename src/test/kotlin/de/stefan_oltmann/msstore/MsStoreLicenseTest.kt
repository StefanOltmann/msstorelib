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
import java.lang.foreign.ValueLayout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/*
 * Mirror of the native layout from msstore_winrt.h. Offsets and sizes are
 * guarded by static_asserts on the C++ side, so both sides cannot drift.
 */
private const val LICENSE_SIZE = 40L
private const val ADDON_SIZE = 24L
private const val OFF_SKU_STORE_ID = 0L
private const val OFF_IS_ACTIVE = 8L
private const val OFF_IS_TRIAL = 9L
private const val OFF_EXPIRATION = 16L
private const val OFF_ADDONS = 24L
private const val OFF_ADDON_COUNT = 32L
private const val OFF_IN_APP_OFFER_TOKEN = 8L
private const val OFF_ADDON_EXPIRATION = 16L

class MsStoreLicenseTest {

    @Test
    fun parsesLicenseStructWithAddOns() {

        Arena.ofConfined().use { arena ->

            val addOns = allocateAddOn(
                arena,
                skuStoreId = "9NBLGGH4R315/0001",
                inAppOfferToken = "offer_one",
                expirationDate = 111L,
                skuStoreId2 = "9NBLGGH4R316/0002",
                inAppOfferToken2 = "offer_two",
                expirationDate2 = 222L
            )

            val license = allocateLicense(
                arena,
                skuStoreId = "9ND96XCDZRGB/0010",
                isActive = true,
                isTrial = false,
                expirationDate = 12_345L,
                addOns = addOns,
                addOnCount = 2
            )

            val info = MsStoreLicense.readLicenseInfo(license)

            assertEquals("9ND96XCDZRGB", info.storeId)
            assertEquals("0010", info.skuId)
            assertTrue(info.isActive)
            assertFalse(info.isTrial)
            assertEquals(12_345L, info.expirationDate)
            assertEquals(2, info.addOnLicenses.size)
            assertEquals("9NBLGGH4R315", info.addOnLicenses[0].storeId)
            assertEquals("0001", info.addOnLicenses[0].skuId)
            assertEquals("offer_one", info.addOnLicenses[0].inAppOfferToken)
            assertEquals(111L, info.addOnLicenses[0].expirationDate)
            assertEquals("9NBLGGH4R316", info.addOnLicenses[1].storeId)
            assertEquals("0002", info.addOnLicenses[1].skuId)
            assertEquals("offer_two", info.addOnLicenses[1].inAppOfferToken)
            assertEquals(222L, info.addOnLicenses[1].expirationDate)
        }
    }

    @Test
    fun parsesNullSkuStoreIdAsEmptyStrings() {

        Arena.ofConfined().use { arena ->

            val license = allocateLicense(
                arena,
                skuStoreId = null,
                isActive = false,
                isTrial = false,
                expirationDate = 0L,
                addOns = null,
                addOnCount = 0
            )

            val info = MsStoreLicense.readLicenseInfo(license)

            assertEquals("", info.storeId)
            assertEquals("", info.skuId)
            assertFalse(info.isActive)
            assertFalse(info.isTrial)
            assertEquals(0L, info.expirationDate)
            assertTrue(info.addOnLicenses.isEmpty())
        }
    }

    @Test
    fun parsesSkuStoreIdWithoutSeparator() {

        Arena.ofConfined().use { arena ->

            val license = allocateLicense(
                arena,
                skuStoreId = "9ND96XCDZRGB",
                isActive = true,
                isTrial = false,
                expirationDate = 12_345L,
                addOns = null,
                addOnCount = 0
            )

            val info = MsStoreLicense.readLicenseInfo(license)

            assertEquals("9ND96XCDZRGB", info.storeId)
            assertEquals("", info.skuId)
        }
    }

    @Test
    fun ignoresAddOnPointerWhenCountIsZero() {

        Arena.ofConfined().use { arena ->

            val license = allocateLicense(
                arena,
                skuStoreId = "9ND96XCDZRGB/0010",
                isActive = true,
                isTrial = false,
                expirationDate = 0L,
                addOns = arena.allocate(ADDON_SIZE * 2, 8),
                addOnCount = 0
            )

            val info = MsStoreLicense.readLicenseInfo(license)

            assertTrue(info.addOnLicenses.isEmpty())
        }
    }

    @Test
    fun ignoresAddOnCountWhenPointerIsNull() {

        Arena.ofConfined().use { arena ->

            val license = allocateLicense(
                arena,
                skuStoreId = "9ND96XCDZRGB/0010",
                isActive = true,
                isTrial = false,
                expirationDate = 0L,
                addOns = null,
                addOnCount = 3
            )

            val info = MsStoreLicense.readLicenseInfo(license)

            assertTrue(info.addOnLicenses.isEmpty())
        }
    }

    @Test
    fun parsesAddOnWithNullStringsAsEmpty() {

        Arena.ofConfined().use { arena ->

            val addOns = allocateAddOn(
                arena,
                skuStoreId = null,
                inAppOfferToken = null,
                expirationDate = 0L,
                skuStoreId2 = null,
                inAppOfferToken2 = null,
                expirationDate2 = 0L
            )

            val license = allocateLicense(
                arena,
                skuStoreId = "9ND96XCDZRGB/0010",
                isActive = true,
                isTrial = false,
                expirationDate = 0L,
                addOns = addOns,
                addOnCount = 2
            )

            val info = MsStoreLicense.readLicenseInfo(license)

            assertEquals(2, info.addOnLicenses.size)
            assertEquals("", info.addOnLicenses[0].storeId)
            assertEquals("", info.addOnLicenses[0].skuId)
            assertEquals("", info.addOnLicenses[0].inAppOfferToken)
            assertEquals(0L, info.addOnLicenses[0].expirationDate)
            assertEquals("", info.addOnLicenses[1].storeId)
        }
    }

    private fun allocateLicense(
        arena: Arena,
        skuStoreId: String?,
        isActive: Boolean,
        isTrial: Boolean,
        expirationDate: Long,
        addOns: MemorySegment?,
        addOnCount: Int
    ): MemorySegment {

        val license = arena.allocate(LICENSE_SIZE, 8)

        license.set(ValueLayout.ADDRESS, OFF_SKU_STORE_ID, allocateCString(arena, skuStoreId))
        license.set(ValueLayout.JAVA_BOOLEAN, OFF_IS_ACTIVE, isActive)
        license.set(ValueLayout.JAVA_BOOLEAN, OFF_IS_TRIAL, isTrial)
        license.set(ValueLayout.JAVA_LONG, OFF_EXPIRATION, expirationDate)
        license.set(ValueLayout.ADDRESS, OFF_ADDONS, addOns ?: MemorySegment.NULL)
        license.set(ValueLayout.JAVA_INT, OFF_ADDON_COUNT, addOnCount)

        return license
    }

    private fun allocateAddOn(
        arena: Arena,
        skuStoreId: String?,
        inAppOfferToken: String?,
        expirationDate: Long,
        skuStoreId2: String?,
        inAppOfferToken2: String?,
        expirationDate2: Long
    ): MemorySegment {

        val addOns = arena.allocate(ADDON_SIZE * 2, 8)

        addOns.set(ValueLayout.ADDRESS, OFF_SKU_STORE_ID, allocateCString(arena, skuStoreId))
        addOns.set(ValueLayout.ADDRESS, OFF_IN_APP_OFFER_TOKEN, allocateCString(arena, inAppOfferToken))
        addOns.set(ValueLayout.JAVA_LONG, OFF_ADDON_EXPIRATION, expirationDate)
        addOns.set(ValueLayout.ADDRESS, ADDON_SIZE + OFF_SKU_STORE_ID, allocateCString(arena, skuStoreId2))
        addOns.set(ValueLayout.ADDRESS, ADDON_SIZE + OFF_IN_APP_OFFER_TOKEN, allocateCString(arena, inAppOfferToken2))
        addOns.set(ValueLayout.JAVA_LONG, ADDON_SIZE + OFF_ADDON_EXPIRATION, expirationDate2)

        return addOns
    }

    private fun allocateCString(arena: Arena, value: String?): MemorySegment =
        if (value == null) MemorySegment.NULL else arena.allocateFrom(value)
}
