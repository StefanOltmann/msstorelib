#pragma once

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
        bool IsTrialOwnedByThisUser;
        int64_t TrialTimeRemaining;
        int64_t ExpirationDate;
        const char* TrialUniqueId;
        MsStoreAddOnLicenseNative* AddOnLicenses;
        int AddOnLicensesCount;
    } MsStoreLicenseNative;

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
