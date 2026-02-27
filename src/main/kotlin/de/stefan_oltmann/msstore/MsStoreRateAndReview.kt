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

import de.stefan_oltmann.msstore.model.MsStoreRateAndReviewStatus

/**
 * JVM API entry-point for showing the Microsoft Store rating dialog.
 *
 * The native layer calls StoreContext.RequestRateAndReviewAppAsync() and maps
 * the resulting StoreRateAndReviewStatus into
 * [de.stefan_oltmann.msstore.model.MsStoreRateAndReviewStatus].
 */
internal object MsStoreRateAndReview {

    /**
     * Shows the Microsoft Store rating and review dialog for the current app.
     *
     * This call uses the current foreground window as the Store dialog owner.
     *
     * @throws MsStoreLicenseException when the native call fails.
     */
    fun requestRateAndReview(): MsStoreRateAndReviewStatus {

        val statusCode = try {
            MsStoreNative.requestRateAndReview()
        } catch (_: UnsatisfiedLinkError) {
            throw MsStoreLicenseException(
                "Native rate and review support is unavailable. Rebuild or replace msstore_winrt.dll."
            )
        }

        if (statusCode < 0) {

            val errorText = MsStoreNativeHelpers.readLastError()

            throw MsStoreLicenseException(errorText ?: "Native rate and review request failed.")
        }

        return MsStoreRateAndReviewStatus.fromNativeCode(statusCode)
    }
}
