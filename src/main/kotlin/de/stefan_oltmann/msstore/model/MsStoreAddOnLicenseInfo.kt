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
 * Subset of Store add-on license fields.
 *
 * @see https://learn.microsoft.com/uwp/api/windows.services.store.storelicense
 */
public data class MsStoreAddOnLicenseInfo(

    /**
     * Store ID of the licensed add-on SKU from the Microsoft Store catalog
     */
    val skuStoreId: String = "",

    /**
     * Product ID for the add-on.
     */
    val inAppOfferToken: String = "",

    /**
     * Expiration date and time for the add-on license.
     */
    val expirationDate: Long = 0
)
