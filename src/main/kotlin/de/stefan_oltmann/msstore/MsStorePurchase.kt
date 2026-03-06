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

import de.stefan_oltmann.msstore.model.MsStoreLicenseInfo.Companion.STORE_ID_LENGTH
import de.stefan_oltmann.msstore.model.MsStorePurchaseStatus

/**
 * JVM API entry-point for triggering Microsoft Store purchases.
 *
 * The native layer calls StoreContext.RequestPurchaseAsync(storeId) and maps
 * the resulting StorePurchaseStatus into [de.stefan_oltmann.msstore.model.MsStorePurchaseStatus].
 */
internal object MsStorePurchase {

    /**
     * Requests a purchase for the given Store product ID.
     *
     * @throws MsStoreLicenseException when the native call fails.
     */
    fun requestPurchase(storeId: String): MsStorePurchaseStatus {

        try {

            /* Prevent wrong use */
            if (storeId.length != STORE_ID_LENGTH)
                throw MsStoreLicenseException("Store ID must be 12 characters long.")

            val statusCode = MsStoreNative.requestPurchase(storeId)

            if (statusCode < 0)
                throw MsStoreLicenseException(MsStoreNativeHelpers.readLastError() ?: "Native purchase request failed.")

            return MsStorePurchaseStatus.fromNativeCode(statusCode)

        } catch (ex: MsStoreLicenseException) {
            /* Pass on MsStoreLicenseException as is. */
            throw ex
        } catch (ex: Throwable) {
            /* Wrap everything else in a MsStoreLicenseException */
            throw MsStoreLicenseException(ex.message ?: "Request query failed.")
        }
    }
}
