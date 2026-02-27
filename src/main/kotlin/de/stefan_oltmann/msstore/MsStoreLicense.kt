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
import kotlinx.serialization.json.Json

/**
 * Internal entry-point for retrieving Microsoft Store license info.
 *
 * Implementation overview:
 * - FFM (Panama) loads `msstore_winrt.dll` (C++/WinRT).
 * - The C++/WinRT DLL calls `StoreContext.GetAppLicenseAsync()` and returns
 *   the `ExtendedJsonData` string for parsing on the JVM.
 *
 * @see https://learn.microsoft.com/windows/uwp/monetize/get-license-info-for-apps-and-add-ons
 */
internal object MsStoreLicense {

    /*
     * JSON parser configured for forward-compatible schema changes.
     *
     * The Store schema can evolve without notice, so we ignore unknown fields
     * to keep parsing resilient across Windows and Store API updates.
     */
    private val json: Json = Json {
        ignoreUnknownKeys = true
    }

    /**
     * Returns the current app license info parsed from the Store JSON payload.
     *
     * @throws MsStoreLicenseException when the native call fails.
     */
    fun getLicenseInfo(): MsStoreLicenseInfo =
        parseLicenseJson(getLicenseJson())

    /**
     * Parses a license JSON payload into a strongly-typed model.
     *
     * This is intentionally small and deterministic so tests can validate the
     * JSON mapping behavior without requiring a Store call.
     */
    fun parseLicenseJson(jsonText: String): MsStoreLicenseInfo =
        json.decodeFromString(jsonText)

    /**
     * Returns the raw JSON license payload as returned by the Store API.
     *
     * @throws MsStoreLicenseException when the native call fails.
     */
    fun getLicenseJson(): String {

        /* First, request the license payload. A null pointer indicates failure. */
        val jsonPointer = MsStoreNative.getLicenseJson()

        val jsonText = MsStoreNativeHelpers.readUtf8AndFree(jsonPointer)

        if (jsonText != null)
            return jsonText

        /* If the payload is null, ask the native layer for the error string. */
        val errorText = MsStoreNativeHelpers.readLastError()

        throw MsStoreLicenseException(errorText ?: "Native license query failed.")
    }
}
