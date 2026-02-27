#pragma once

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
     * Returns the StoreAppLicense.ExtendedJsonData payload as UTF-8 JSON.
     *
     * On success: returns a non-null pointer that must be freed via msstore_winrt_free().
     * On failure: returns nullptr. Use msstore_winrt_get_last_error() to read
     * the error message (also allocated with CoTaskMemAlloc).
     */
    MSSTORE_WINRT_API const char* msstore_winrt_get_license_json();

    /*
     * Returns the last error message for the current thread as UTF-8 text.
     *
     * Always returns a newly allocated string (which may be empty).
     * Caller must free via msstore_winrt_free().
     */
    MSSTORE_WINRT_API const char* msstore_winrt_get_last_error();

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
     * Shows the rating and review dialog for the current app.
     *
     * Returns a status code (0..4) that maps to MsStoreRateAndReviewStatus:
     * 0 = Succeeded
     * 1 = CanceledByUser
     * 2 = NetworkError
     * 3 = Error
     * 4 = Unknown
     *
     * On failure: returns -1. Use msstore_winrt_get_last_error() to read
     * the error message (allocated with CoTaskMemAlloc).
     */
    MSSTORE_WINRT_API int msstore_winrt_request_rate_and_review();

    /*
     * Frees memory allocated by msstore_winrt_get_license_json() or
     * msstore_winrt_get_last_error().
     */
    MSSTORE_WINRT_API void msstore_winrt_free(const char* ptr);
}
