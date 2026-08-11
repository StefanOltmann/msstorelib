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
import de.stefan_oltmann.msstore.model.MsStoreLicenseStatus
import de.stefan_oltmann.msstore.model.check
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MsStoreLicenseInfoTest {

    private val storeId = "9ND96XCDZRGB"

    @Test
    fun emptyLicenseIsNotInStore() {
        val info = MsStoreLicenseInfo()
        assertEquals(MsStoreLicenseStatus.NotInStore, info.check(storeId))
    }

    @Test
    fun licenseWithoutSkuIsNotInStore() {
        val info = MsStoreLicenseInfo(storeId = storeId, isActive = true, expirationDate = Long.MAX_VALUE)
        assertEquals(MsStoreLicenseStatus.NotInStore, info.check(storeId))
    }

    @Test
    fun storeIdMismatchIsNotInStore() {
        val info = MsStoreLicenseInfo(
            storeId = "AAAAAAAAAAAA",
            skuId = "0010",
            isActive = true,
            expirationDate = Long.MAX_VALUE
        )
        assertEquals(MsStoreLicenseStatus.NotInStore, info.check(storeId))
    }

    @Test
    fun activeLicenseWithFutureExpirationIsLicensed() {
        val info = MsStoreLicenseInfo(
            storeId = storeId,
            skuId = "0010",
            isActive = true,
            expirationDate = Long.MAX_VALUE
        )
        assertEquals(MsStoreLicenseStatus.Licensed, info.check(storeId))
    }

    @Test
    fun activeLicenseWithoutExpirationIsLicensed() {
        val info = MsStoreLicenseInfo(storeId = storeId, skuId = "0010", isActive = true, expirationDate = 0L)
        assertEquals(MsStoreLicenseStatus.Licensed, info.check(storeId))
    }

    /*
     * The Store returns far-future sentinel dates for licenses that do not
     * expire: 2099-12-30T20:00Z and 9999-12-31T23:59:59Z in Unix millis.
     * These must never be classified as expired.
     */
    @Test
    fun activeLicenseWithStorePerpetualSentinelIsLicensed() {
        val info = MsStoreLicenseInfo(
            storeId = storeId,
            skuId = "0010",
            isActive = true,
            expirationDate = 4_102_344_000_000L
        )
        assertEquals(MsStoreLicenseStatus.Licensed, info.check(storeId))
    }

    @Test
    fun storePerpetualSentinelIsNotExpired() {
        assertFalse(MsStoreLicenseInfo(expirationDate = 4_102_344_000_000L).isExpired)
    }

    @Test
    fun activeLicenseWithStoreMaxSentinelIsLicensed() {
        val info = MsStoreLicenseInfo(
            storeId = storeId,
            skuId = "0010",
            isActive = true,
            expirationDate = 253_402_300_799_000L
        )
        assertEquals(MsStoreLicenseStatus.Licensed, info.check(storeId))
    }

    @Test
    fun storeMaxSentinelIsNotExpired() {
        assertFalse(MsStoreLicenseInfo(expirationDate = 253_402_300_799_000L).isExpired)
    }

    /*
     * The WinRT "no date" sentinel (1601-01-01) converts to a negative Unix
     * epoch millis value. Any non-positive expiration means "no expiration".
     */
    @Test
    fun winrtNoDateSentinelIsNotExpired() {
        assertFalse(MsStoreLicenseInfo(expirationDate = -11_644_473_600_000L).isExpired)
    }

    @Test
    fun activeLicenseWithWinrtNoDateSentinelIsLicensed() {
        val info = MsStoreLicenseInfo(
            storeId = storeId,
            skuId = "0010",
            isActive = true,
            expirationDate = -11_644_473_600_000L
        )
        assertEquals(MsStoreLicenseStatus.Licensed, info.check(storeId))
    }

    @Test
    fun expiredLicenseIsExpired() {
        val info = MsStoreLicenseInfo(storeId = storeId, skuId = "0010", isActive = true, expirationDate = 1L)
        assertEquals(MsStoreLicenseStatus.Expired, info.check(storeId))
    }

    @Test
    fun inactiveLicenseIsExpired() {
        val info = MsStoreLicenseInfo(
            storeId = storeId,
            skuId = "0010",
            isActive = false,
            expirationDate = Long.MAX_VALUE
        )
        assertEquals(MsStoreLicenseStatus.Expired, info.check(storeId))
    }

    @Test
    fun activeTrialIsTrial() {
        val info = MsStoreLicenseInfo(
            storeId = storeId,
            skuId = "0010",
            isActive = true,
            isTrial = true,
            expirationDate = Long.MAX_VALUE
        )
        assertEquals(MsStoreLicenseStatus.Trial, info.check(storeId))
    }

    @Test
    fun expiredTrialIsExpiredTrial() {
        val info = MsStoreLicenseInfo(
            storeId = storeId,
            skuId = "0010",
            isActive = true,
            isTrial = true,
            expirationDate = 1L
        )
        assertEquals(MsStoreLicenseStatus.ExpiredTrial, info.check(storeId))
    }

    @Test
    fun inactiveTrialIsExpiredTrial() {
        val info = MsStoreLicenseInfo(
            storeId = storeId,
            skuId = "0010",
            isActive = false,
            isTrial = true,
            expirationDate = Long.MAX_VALUE
        )
        assertEquals(MsStoreLicenseStatus.ExpiredTrial, info.check(storeId))
    }

    @Test
    fun storeIdComparisonIsCaseInsensitive() {
        val info = MsStoreLicenseInfo(
            storeId = storeId.lowercase(),
            skuId = "0010",
            isActive = true,
            expirationDate = Long.MAX_VALUE
        )
        assertEquals(MsStoreLicenseStatus.Licensed, info.check(storeId))
    }

    @Test
    fun invalidStoreIdThrowsContractException() {

        val info = MsStoreLicenseInfo(storeId = storeId, skuId = "0010")

        val exception = assertFailsWith<MsStoreLicenseException> { info.check("123") }

        assertEquals("Store ID must be 12 characters long.", exception.message)
    }

    @Test
    fun isExpiredIsFalseWithoutExpiration() {
        assertFalse(MsStoreLicenseInfo(expirationDate = 0L).isExpired)
    }

    @Test
    fun isExpiredIsFalseInTheFuture() {
        assertFalse(MsStoreLicenseInfo(expirationDate = Long.MAX_VALUE).isExpired)
    }

    @Test
    fun isExpiredIsTrueInThePast() {
        assertTrue(MsStoreLicenseInfo(expirationDate = 1L).isExpired)
    }

    @Test
    fun isInstalledFromStoreIsFalseWithoutSkuId() {
        assertFalse(MsStoreLicenseInfo(skuId = "").isInstalledFromStore)
    }

    @Test
    fun isInstalledFromStoreIsTrueWithSkuId() {
        assertTrue(MsStoreLicenseInfo(skuId = "0010").isInstalledFromStore)
    }
}
