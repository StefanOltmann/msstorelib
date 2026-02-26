package de.stefan_oltmann.msstore

import com.sun.jna.Library
import com.sun.jna.Pointer

/**
 * JNA interface to the C++/WinRT DLL (msstore_winrt.dll).
 */
internal interface MsStoreNative : Library {

    /**
     * Calls into msstore_winrt_get_license_json.
     *
     * Returns a pointer to UTF-8 JSON allocated with CoTaskMemAlloc on success.
     * The caller must free it by calling [msstore_winrt_free].
     */
    fun msstore_winrt_get_license_json(): Pointer?

    /**
     * Calls into msstore_winrt_get_last_error.
     *
     * Returns a pointer to UTF-8 error text allocated with CoTaskMemAlloc.
     * The caller must free it by calling [msstore_winrt_free].
     */
    fun msstore_winrt_get_last_error(): Pointer?

    /**
     * Calls into msstore_winrt_request_purchase.
     *
     * Returns a status code (see [de.stefan_oltmann.msstore.model.MsStorePurchaseStatus]) or -1 on failure.
     */
    fun msstore_winrt_request_purchase(storeId: String): Int

    /**
     * Frees a pointer returned by the native layer.
     *
     * This must be used for both JSON payloads and error messages to avoid
     * leaking native memory in the JVM process.
     */
    fun msstore_winrt_free(ptr: Pointer?)

}
