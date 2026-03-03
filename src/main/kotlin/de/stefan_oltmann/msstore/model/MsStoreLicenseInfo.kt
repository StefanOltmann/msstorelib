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
     */
    val skuStoreId: String = "",

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
     * Value that indicates whether the current user has an entitlement
     * for the usage-limited trial that is associated with this app license.
     */
    val isTrialOwnedByThisUser: Boolean = false,

    /**
     * Remaining time for the usage-limited trial that
     * is associated with this app license in milliseconds.
     */
    val trialTimeRemaining: Long = 0,

    /**
     * Expiration date and time for the app license as timestamp in milliseconds.
     */
    val expirationDate: Long = 0,

    /**
     * Unique ID that identifies the combination of the current user and
     * the usage-limited trial that is associated with this app license.
     */
    val trialUniqueId: String = "",

    /**
     * Collection of licenses for durable add-ons for which the user has entitlements to use.
     * This property does not include licenses for consumable add-ons.
     */
    val addOnLicenses: List<MsStoreAddOnLicenseInfo> = emptyList()
)
