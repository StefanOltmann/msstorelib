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

import kotlinx.serialization.Serializable

/**
 * Subset of Store app license fields returned by `ExtendedJsonData`.
 *
 * All fields are modeled as strings and booleans to keep parsing stable across
 * schema revisions. Dates are returned as ISO-8601 or Store-formatted strings.
 *
 * The defaults allow parsing to succeed even when fields are missing, which
 * is common for trial or legacy licenses.
 *
 * @property productId Store product identifier.
 * @property skuId SKU identifier.
 * @property expiration Expiration date string.
 * @property isActive Whether the license is active.
 * @property isTrial Whether the license is a trial.
 * @property isTrialOwnedByThisUser Whether the trial is owned by this user.
 * @property trialTimeRemaining Trial time remaining string.
 * @property inAppOfferToken Offer token when relevant.
 * @property productAddOns Add-on licenses included in the payload.
 *
 * @see https://learn.microsoft.com/windows/uwp/monetize/data-schemas-for-store-products
 */
@Serializable
public data class MsStoreLicenseInfo(

    /** Product identifier as defined in the Store catalog. */
    val productId: String = "",

    /** SKU identifier for the purchased or installed SKU. */
    val skuId: String = "",

    /** Expiration time if the license is time-limited (trial or subscription). */
    val expiration: String = "",

    /** Whether the license is currently active. */
    val isActive: Boolean = false,

    /** Whether the license is a trial license. */
    val isTrial: Boolean = false,

    /** Whether the current user owns the trial license. */
    val isTrialOwnedByThisUser: Boolean = false,

    /** Remaining trial duration, often in ISO-8601 duration format. */
    val trialTimeRemaining: String = "",

    /** Offer token if the license was acquired through an in-app offer. */
    val inAppOfferToken: String = "",

    /** List of add-on licenses attached to the main product. */
    val productAddOns: List<MsStoreAddOnLicenseInfo> = emptyList()
)
