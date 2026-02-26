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
 * Subset of Store add-on license fields returned by `ExtendedJsonData`.
 *
 * Add-ons share the same overall schema as the main product but typically
 * include fewer fields depending on product type.
 *
 * @property inAppOfferToken Offer token for the add-on.
 * @property productId Add-on product identifier.
 * @property productType Add-on product type.
 * @property skuId Add-on SKU identifier.
 * @property skuType Add-on SKU type.
 * @property expiration Expiration date string.
 * @property isActive Whether the add-on license is active.
 *
 * @see https://learn.microsoft.com/windows/uwp/monetize/data-schemas-for-store-products
 */
@Serializable
public data class MsStoreAddOnLicenseInfo(

    /** Offer token if the add-on was acquired through an in-app offer. */
    val inAppOfferToken: String = "",

    /** Add-on product identifier as defined in the Store catalog. */
    val productId: String = "",

    /** Store product type, e.g. Durable, Consumable, UnmanagedConsumable. */
    val productType: String = "",

    /** SKU identifier for the add-on. */
    val skuId: String = "",

    /** SKU type string for the add-on. */
    val skuType: String = "",

    /** Expiration time if the add-on is time-limited. */
    val expiration: String = "",

    /** Whether the add-on license is currently active. */
    val isActive: Boolean = false
)
