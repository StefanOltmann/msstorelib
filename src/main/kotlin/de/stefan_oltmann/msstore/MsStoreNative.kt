package de.stefan_oltmann.msstore

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle
import java.nio.charset.StandardCharsets

/**
 * FFM bindings to the C++/WinRT DLL (msstore_winrt.dll).
 */
internal object MsStoreNative {

    /** Entrypoint for creating downcall handles to native symbols. */
    private val linker: Linker = Linker.nativeLinker()

    /** Handle for `const char* msstore_winrt_get_license_json()`. */
    private val getLicenseJsonHandle: MethodHandle = downcall(
        symbolName = "msstore_winrt_get_license_json",
        descriptor = FunctionDescriptor.of(ValueLayout.ADDRESS)
    )

    /** Handle for `const char* msstore_winrt_get_last_error()`. */
    private val getLastErrorHandle: MethodHandle = downcall(
        symbolName = "msstore_winrt_get_last_error",
        descriptor = FunctionDescriptor.of(ValueLayout.ADDRESS)
    )

    /** Handle for `int msstore_winrt_request_purchase(const char*)`. */
    private val requestPurchaseHandle: MethodHandle = downcall(
        symbolName = "msstore_winrt_request_purchase",
        descriptor = FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
    )

    /** Handle for `void msstore_winrt_free(const char*)`. */
    private val freeHandle: MethodHandle = downcall(
        symbolName = "msstore_winrt_free",
        descriptor = FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
    )

    /**
     * Calls into msstore_winrt_get_license_json.
     *
     * Returns a pointer to UTF-8 JSON allocated with CoTaskMemAlloc on success.
     * The caller must free it by calling [free].
     */
    fun getLicenseJson(): MemorySegment? =
        nullIfNullAddress(getLicenseJsonHandle.invoke() as MemorySegment)

    /**
     * Calls into msstore_winrt_get_last_error.
     *
     * Returns a pointer to UTF-8 error text allocated with CoTaskMemAlloc.
     * The caller must free it by calling [free].
     */
    fun getLastError(): MemorySegment? =
        nullIfNullAddress(getLastErrorHandle.invoke() as MemorySegment)

    /**
     * Calls into msstore_winrt_request_purchase.
     *
     * Returns a status code (see [de.stefan_oltmann.msstore.model.MsStorePurchaseStatus]) or -1 on failure.
     */
    fun requestPurchase(storeId: String): Int =
        Arena.ofConfined().use { arena ->

            val nativeStoreId = arena.allocateUtf8String(storeId)

            requestPurchaseHandle.invoke(nativeStoreId) as Int
        }

    /**
     * Frees a pointer returned by the native layer.
     *
     * This must be used for both JSON payloads and error messages to avoid
     * leaking native memory in the JVM process.
     */
    fun free(nativeMemorySegment: MemorySegment?) {

        if (nativeMemorySegment == null)
            return

        freeHandle.invoke(nativeMemorySegment)
    }

    /** Resolves one native symbol and creates a strongly-typed downcall handle. */
    private fun downcall(symbolName: String, descriptor: FunctionDescriptor): MethodHandle {

        val symbol = MsStoreNativeLoader.lookup.find(symbolName).orElseThrow {
            UnsatisfiedLinkError("Native symbol '$symbolName' not found in msstore_winrt.dll.")
        }

        return linker.downcallHandle(symbol, descriptor)
    }

    /**
     * Converts a native return address into nullable Kotlin form.
     *
     * FFM models native addresses as MemorySegment values; a null pointer is
     * represented as address `0`.
     */
    private fun nullIfNullAddress(addressSegment: MemorySegment): MemorySegment? =
        if (addressSegment.address() == 0L) null else addressSegment

    /** Allocates a null-terminated UTF-8 string for C ABI consumption. */
    private fun Arena.allocateUtf8String(value: String): MemorySegment {

        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        val nativeString = allocate((bytes.size + 1).toLong(), 1)
        val buffer = nativeString.asByteBuffer()

        buffer.put(bytes)
        buffer.put(0)

        return nativeString
    }
}
