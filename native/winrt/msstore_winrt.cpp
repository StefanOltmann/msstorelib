#include "msstore_winrt.h"

#include <windows.h>
#include <objbase.h>
#include <ShObjIdl_core.h>
#include <cstring>
#include <string>
#include <string_view>
#include <winrt/base.h>
#include <winrt/Windows.Foundation.h>
#include <winrt/Windows.Services.Store.h>

using namespace winrt;
using namespace Windows::Services::Store;

/*
 * Thread-local error storage for the last failure in this DLL.
 *
 * We use thread_local so that concurrent calls from different JVM threads do
 * not overwrite each other's error messages.
 */
static thread_local std::string g_lastError;

/*
 * Allocates a UTF-8 string via CoTaskMemAlloc for cross-module ownership.
 *
 * CoTaskMemAlloc + CoTaskMemFree is the safest cross-DLL contract on Windows
 * when the caller is not compiled with the same CRT.
 */
static const char* dup_string(const std::string& value) {

    const size_t size = value.size() + 1;

    char* buffer = static_cast<char*>(::CoTaskMemAlloc(size));

    if (buffer == nullptr) {
        return nullptr;
    }

    std::memcpy(buffer, value.c_str(), size);

    return buffer;
}

/*
 * Maps StorePurchaseStatus into stable numeric codes exposed to the JVM.
 */
static int map_purchase_status(StorePurchaseStatus status) {

    switch (status) {
        case StorePurchaseStatus::Succeeded:
            return 0;
        case StorePurchaseStatus::AlreadyPurchased:
            return 1;
        case StorePurchaseStatus::NotPurchased:
            return 2;
        case StorePurchaseStatus::NetworkError:
            return 3;
        case StorePurchaseStatus::ServerError:
            return 4;
        default:
            return 5;
    }
}

/*
 * Maps StoreRateAndReviewStatus into stable numeric codes exposed to the JVM.
 */
static int map_rate_and_review_status(StoreRateAndReviewStatus status) {

    switch (status) {
        case StoreRateAndReviewStatus::Succeeded:
            return 0;
        case StoreRateAndReviewStatus::CanceledByUser:
            return 1;
        case StoreRateAndReviewStatus::NetworkError:
            return 2;
        case StoreRateAndReviewStatus::Error:
            return 3;
        default:
            return 4;
    }
}

/*
 * Initializes the Store UI owner window for desktop modal dialogs.
 */
static bool initialize_store_ui_owner(const StoreContext& context) {

    HWND ownerWindow = ::GetForegroundWindow();

    if (ownerWindow == nullptr) {
        g_lastError = "No foreground window handle available for Store UI.";
        return false;
    }

    /*
     * Desktop apps must provide an owner HWND for Store modal UI.
     * This avoids ERROR_INVALID_WINDOW_HANDLE and UI-thread errors.
     */
    auto initWindow = context.as<IInitializeWithWindow>();
    initWindow->Initialize(ownerWindow);

    return true;
}

/*
 * Pumps a nested message loop until the async operation completes.
 *
 * Waiting with .get() can block the calling STA thread hard enough to
 * destabilize the host app. This loop keeps that thread responsive until the
 * async result is ready while preserving a synchronous native API for the JVM.
 */
static StoreRateAndReviewResult wait_for_rate_and_review_result(
    const Windows::Foundation::IAsyncOperation<StoreRateAndReviewResult>& operation
) {

    while (operation.Status() == Windows::Foundation::AsyncStatus::Started) {

        MSG message{};

        while (::PeekMessageW(&message, nullptr, 0, 0, PM_REMOVE)) {
            ::TranslateMessage(&message);
            ::DispatchMessageW(&message);
        }

        ::MsgWaitForMultipleObjectsEx(
            0,
            nullptr,
            50,
            QS_ALLINPUT,
            MWMO_INPUTAVAILABLE
        );
    }

    return operation.GetResults();
}

/*
 * Returns StoreAppLicense.ExtendedJsonData as UTF-8 JSON, or nullptr on error.
 *
 * The call blocks until the async Store API completes; this keeps the native
 * API surface synchronous for the JVM, which then parses JSON on its side.
 */
extern "C" MSSTORE_WINRT_API const char* msstore_winrt_get_license_json() {

    try {

        /*
         * Initialize the apartment for the current thread as STA. This keeps
         * the thread compatible with Store UI calls later on.
         */
        init_apartment(apartment_type::single_threaded);

        /* StoreContext::GetDefault uses the identity of the current package. */
        StoreContext context = StoreContext::GetDefault();

        /* Bridge the async WinRT call into a synchronous result for the JVM. */
        StoreAppLicense license = context.GetAppLicenseAsync().get();

        if (!license) {
            g_lastError = "StoreAppLicense is null.";
            return nullptr;
        }

        std::string json = to_string(license.ExtendedJsonData());

        g_lastError.clear();

        return dup_string(json);

    } catch (const hresult_error& ex) {
        g_lastError = to_string(ex.message());
    } catch (const std::exception& ex) {
        g_lastError = ex.what();
    } catch (...) {
        g_lastError = "Unknown native error.";
    }

    return nullptr;
}

/*
 * Returns the last error string for the current thread.
 *
 * The return value is always a new allocation so the caller can safely free it.
 */
extern "C" MSSTORE_WINRT_API const char* msstore_winrt_get_last_error() {
    return dup_string(g_lastError);
}

/*
 * Requests a purchase for the given Store ID.
 *
 * Returns a stable status code for the JVM or -1 on error.
 */
extern "C" MSSTORE_WINRT_API int msstore_winrt_request_purchase(const char* storeId) {

    try {

        if (storeId == nullptr || *storeId == '\0') {
            g_lastError = "Store ID is null or empty.";
            return -1;
        }

        init_apartment(apartment_type::single_threaded);

        StoreContext context = StoreContext::GetDefault();

        if (!initialize_store_ui_owner(context)) {
            return -1;
        }

        StorePurchaseResult result =
            context.RequestPurchaseAsync(to_hstring(std::string_view(storeId))).get();

        if (!result) {
            g_lastError = "StorePurchaseResult is null.";
            return -1;
        }

        g_lastError.clear();

        return map_purchase_status(result.Status());

    } catch (const hresult_error& ex) {
        g_lastError = to_string(ex.message());
    } catch (const std::exception& ex) {
        g_lastError = ex.what();
    } catch (...) {
        g_lastError = "Unknown native error.";
    }

    return -1;
}

/*
 * Shows the rating and review dialog for the current app.
 *
 * Returns a stable status code for the JVM or -1 on error.
 */
extern "C" MSSTORE_WINRT_API int msstore_winrt_request_rate_and_review() {

    try {

        init_apartment(apartment_type::single_threaded);

        StoreContext context = StoreContext::GetDefault();

        if (!initialize_store_ui_owner(context)) {
            return -1;
        }

        const auto operation = context.RequestRateAndReviewAppAsync();
        StoreRateAndReviewResult result = wait_for_rate_and_review_result(operation);

        if (!result) {
            g_lastError = "StoreRateAndReviewResult is null.";
            return -1;
        }

        g_lastError.clear();

        return map_rate_and_review_status(result.Status());

    } catch (const hresult_error& ex) {
        g_lastError = to_string(ex.message());
    } catch (const std::exception& ex) {
        g_lastError = ex.what();
    } catch (...) {
        g_lastError = "Unknown native error.";
    }

    return -1;
}

/*
 * Frees a pointer allocated by dup_string (and therefore by CoTaskMemAlloc).
 */
extern "C" MSSTORE_WINRT_API void msstore_winrt_free(const char* ptr) {

    if (ptr != nullptr) {
        ::CoTaskMemFree(reinterpret_cast<LPVOID>(const_cast<char*>(ptr)));
    }
}
