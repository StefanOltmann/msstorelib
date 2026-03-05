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
package de.stefan_oltmann.msstore.model

import de.stefan_oltmann.msstore.MsStoreLicenseException

/**
 * Subset of Store app license fields returned by the native WinRT API.
 *
 * Fields are modeled as Kotlin primitives/strings plus a list of add-on entries
 * to keep parsing stable across schema revisions. Date values are returned as
 * Unix timestamps (milliseconds).
 *
 * @see https://learn.microsoft.com/uwp/api/windows.services.store.storeapplicense
 */
public data class MsStoreLicenseInfo(

    /**
     * Store ID of the licensed app SKU from the Microsoft Store catalog.
     *
     * If installed from the MS Store, this is a 12-character alphanumeric string like 9ND96XCDZRGB.
     * This value is empty if it's not installed from the MS Store.
     */
    val storeId: String = "",

    /**
     * The SKU ID is a 4-character alphanumeric number that describes the installed version.
     * Release versions often are "0010", but a trial version could have another number.
     */
    val skuId: String = "",

    /**
     * Value that indicates whether the license is valid and provides
     * the current user with an entitlement to use the app.
     */
    val isActive: Boolean = false,

    /**
     * Value that indicates whether the license is a trial license.
     */
    val isTrial: Boolean = false,

    /**
     * Expiration date and time for the app license as timestamp in milliseconds.
     */
    val expirationDate: Long = 0,

    /**
     * Collection of licenses for durable add-ons for which the user has entitlements to use.
     * This property does not include licenses for consumable add-ons.
     */
    val addOnLicenses: List<MsStoreAddOnLicenseInfo> = emptyList()

) {

    /**
     * Convenience property to indicate if the expiration date is in the past.
     */
    val isExpired: Boolean =
        expirationDate != 0L && System.currentTimeMillis() >= expirationDate

    /**
     * Convenience property to indicate an installation from MS Store.
     */
    val isInstalledFromStore: Boolean =
        skuId.isNotEmpty()

    /**
     * Helper method to quickly check the state.
     */
    public fun check(storeId: String): MsStoreLicenseStatus {

        /* Prevent wrong use */
        if (storeId.length != STORE_ID_LENGTH)
            throw MsStoreLicenseException("Store ID must be 12 characters long.")

        /* Not installed from a store or wrong product */
        if (!isInstalledFromStore || !this.storeId.equals(storeId, ignoreCase = true))
            return MsStoreLicenseStatus.NotInStore

        /* Trial logic */
        if (isTrial) {

            return if (isExpired || !isActive)
                MsStoreLicenseStatus.ExpiredTrial
            else
                MsStoreLicenseStatus.Trial
        }

        /* Full license logic */
        if (isActive && !isExpired)
            return MsStoreLicenseStatus.Licensed

        return MsStoreLicenseStatus.Expired
    }

    internal companion object {

        const val STORE_ID_LENGTH = 12
        const val SKU_ID_LENGTH = 4
    }
}
