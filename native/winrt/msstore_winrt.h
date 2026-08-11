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
#pragma once

#include <cstddef>
#include <cstdint>

/*
 * C ABI surface for the msstore_winrt.dll.
 *
 * This header intentionally exposes a C-compatible interface so the JVM can
 * call into the DLL via FFM without C++ name mangling issues.
 *
 * Memory ownership contract:
 * - All returned strings are UTF-8 and allocated with CoTaskMemAlloc.
 * - Callers must release them with msstore_winrt_free.
 */

#ifdef _WIN32
  #ifdef MSSTORE_WINRT_EXPORTS
    #define MSSTORE_WINRT_API __declspec(dllexport)
  #else
    #define MSSTORE_WINRT_API __declspec(dllimport)
  #endif
#else
  #define MSSTORE_WINRT_API
#endif

extern "C" {

    /*
     * C-compatible structures for Microsoft Store license data.
     *
     * These structures are used to return license data directly from the
     * WinRT API without the overhead of JSON parsing in the JVM.
     *
     * Note: All strings are UTF-8 and must be freed via msstore_winrt_free().
     * ExpirationDate is Unix epoch milliseconds; 0 means the license does
     * not expire.
     */

    typedef struct {
        const char* SkuStoreId;
        const char* InAppOfferToken;
        int64_t ExpirationDate;
    } MsStoreAddOnLicenseNative;

    typedef struct {
        const char* SkuStoreId;
        bool IsActive;
        bool IsTrial;
        int64_t ExpirationDate;
        MsStoreAddOnLicenseNative* AddOnLicenses;
        int AddOnLicensesCount;
    } MsStoreLicenseNative;

    /*
     * The JVM reads these structs via FFM with hardcoded sizes and offsets
     * (MsStoreLicense.kt). These asserts fail the native build when the
     * layout changes, so the two sides cannot drift silently.
     */
    static_assert(sizeof(MsStoreAddOnLicenseNative) == 24, "JVM expects MsStoreAddOnLicenseNative size 24.");
    static_assert(offsetof(MsStoreAddOnLicenseNative, SkuStoreId) == 0, "JVM expects SkuStoreId at offset 0.");
    static_assert(offsetof(MsStoreAddOnLicenseNative, InAppOfferToken) == 8, "JVM expects InAppOfferToken at offset 8.");
    static_assert(offsetof(MsStoreAddOnLicenseNative, ExpirationDate) == 16, "JVM expects ExpirationDate at offset 16.");

    static_assert(sizeof(MsStoreLicenseNative) == 40, "JVM expects MsStoreLicenseNative size 40.");
    static_assert(offsetof(MsStoreLicenseNative, SkuStoreId) == 0, "JVM expects SkuStoreId at offset 0.");
    static_assert(offsetof(MsStoreLicenseNative, IsActive) == 8, "JVM expects IsActive at offset 8.");
    static_assert(offsetof(MsStoreLicenseNative, IsTrial) == 9, "JVM expects IsTrial at offset 9.");
    static_assert(offsetof(MsStoreLicenseNative, ExpirationDate) == 16, "JVM expects ExpirationDate at offset 16.");
    static_assert(offsetof(MsStoreLicenseNative, AddOnLicenses) == 24, "JVM expects AddOnLicenses at offset 24.");
    static_assert(offsetof(MsStoreLicenseNative, AddOnLicensesCount) == 32, "JVM expects AddOnLicensesCount at offset 32.");

    /*
     * Returns the current app license information.
     *
     * On success: returns a non-null pointer to MsStoreLicenseNative.
     * The caller must release it using msstore_winrt_free_license().
     * On failure: returns nullptr. Use msstore_winrt_get_last_error() to read
     * the error message.
     */
    MSSTORE_WINRT_API MsStoreLicenseNative* msstore_winrt_get_license();

    /*
     * Requests a purchase for the given Store ID.
     *
     * Returns a status code (0..5) that maps to MsStorePurchaseStatus:
     * 0 = Succeeded
     * 1 = AlreadyPurchased
     * 2 = NotPurchased
     * 3 = NetworkError
     * 4 = ServerError
     * 5 = Unknown
     *
     * On failure: returns -1. Use msstore_winrt_get_last_error() to read
     * the error message (allocated with CoTaskMemAlloc).
     */
    MSSTORE_WINRT_API int msstore_winrt_request_purchase(const char* storeId);

    /*
     * Frees memory allocated by msstore_winrt_get_license().
     */
    MSSTORE_WINRT_API void msstore_winrt_free_license(MsStoreLicenseNative* ptr);

    /*
     * Frees memory allocated by msstore_winrt_get_last_error() and individual
     * string fields returned inside MsStoreLicenseNative structures.
     */
    MSSTORE_WINRT_API void msstore_winrt_free(const char* ptr);

    /*
     * Returns the last error message for the current thread as UTF-8 text.
     *
     * Always returns a newly allocated string (which may be empty).
     * Caller must free via msstore_winrt_free().
     */
    MSSTORE_WINRT_API const char* msstore_winrt_get_last_error();
}
