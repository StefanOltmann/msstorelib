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

/**
 * Public API entry-point for Microsoft Store license info and purchases.
 *
 * Every method throws [MsStoreLicenseException] exclusively. Underlying
 * errors, including native failures and JVM errors, are always preserved as
 * the exception's cause.
 */
public object MsStore {

    /**
     * Returns the current app license info.
     *
     * This blocks the calling thread until the Store answers; the native
     * layer aborts the query after 30 seconds when the Store service does
     * not respond. Call it from a background thread and keep the UI
     * thread responsive.
     *
     * @throws MsStoreLicenseException when the native call fails.
     */
    public fun getLicenseInfo(): MsStoreLicenseInfo =
        MsStoreLicense.getLicenseInfo()

    /**
     * Requests a purchase for the given Store product ID.
     *
     * This blocks until the Store dialog closes. Keep the app's UI thread
     * responsive while the dialog is open, so do not wait for the result on a
     * background thread while the UI thread is blocked. The native layer
     * picks the owner window automatically: a visible window on the calling
     * thread, the foreground window of this process, or any visible window of
     * this process.
     *
     * The calling thread must run in a single-threaded COM apartment (STA).
     * If other JVM or native code already initialized the calling thread as
     * multi-threaded (MTA), the call fails with a clear error.
     *
     * @throws MsStoreLicenseException when the native call fails.
     */
    public fun requestPurchase(storeId: String): MsStorePurchaseStatus =
        MsStorePurchase.requestPurchase(storeId)
}
