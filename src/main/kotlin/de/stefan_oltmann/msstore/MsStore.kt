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
import de.stefan_oltmann.msstore.model.MsStorePurchaseStatus
import de.stefan_oltmann.msstore.model.MsStoreRateAndReviewStatus

/**
 * Public API entry-point for Microsoft Store license info and UI actions.
 */
public object MsStore {

    /**
     * Returns the current app license info parsed from the Store JSON payload.
     *
     * @throws MsStoreLicenseException when the native call fails.
     */
    public fun getLicenseInfo(): MsStoreLicenseInfo =
        MsStoreLicense.getLicenseInfo()

    /**
     * Returns the raw JSON license payload as returned by the Store API.
     *
     * @throws MsStoreLicenseException when the native call fails.
     */
    public fun getLicenseJson(): String =
        MsStoreLicense.getLicenseJson()

    /**
     * Requests a purchase for the given Store product ID.
     *
     * @throws MsStoreLicenseException when the native call fails.
     */
    public fun requestPurchase(storeId: String): MsStorePurchaseStatus =
        MsStorePurchase.requestPurchase(storeId)

    /**
     * Shows the Microsoft Store rate-and-review dialog for the current app.
     *
     * This must be called while the app has a focused UI window so the native
     * layer can attach and service the Store dialog.
     *
     * @throws MsStoreLicenseException when the native call fails.
     */
    public fun requestRateAndReview(): MsStoreRateAndReviewStatus =
        MsStoreRateAndReview.requestRateAndReview()
}
