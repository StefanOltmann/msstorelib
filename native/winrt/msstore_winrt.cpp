#include "msstore_winrt.h"

#include <windows.h>
#include <objbase.h>
#include <ShObjIdl_core.h>
#include <chrono>
#include <cstdint>
#include <cstring>
#include <string>
#include <string_view>
#include <winrt/base.h>
#include <winrt/Windows.Foundation.h>
#include <winrt/Windows.Foundation.Collections.h>
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

    if (buffer == nullptr)
        return nullptr;

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

/* Converts a WinRT DateTime to Unix epoch milliseconds */
static int64_t to_unix_epoch_millis(winrt::Windows::Foundation::DateTime dateTime) {

    /* Convert WinRT DateTime (ticks since 1601) to system_clock time_point */
    const auto sysTime = winrt::clock::to_sys(dateTime);

    /* Convert to milliseconds since Unix epoch */
    return std::chrono::duration_cast<std::chrono::milliseconds>(
        sysTime.time_since_epoch()
    ).count();
}

/*
 * Returns the StoreAppLicense information directly, or nullptr on error.
 *
 * The returned pointer and all nested strings/arrays are allocated with
 * CoTaskMemAlloc and must be released via msstore_winrt_free_license().
 */
extern "C" MSSTORE_WINRT_API MsStoreLicenseNative* msstore_winrt_get_license() {

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

        MsStoreLicenseNative* licensePointer =
            static_cast<MsStoreLicenseNative*>(::CoTaskMemAlloc(sizeof(MsStoreLicenseNative)));

        if (licensePointer == nullptr) {
            g_lastError = "Out of memory allocating MsStoreLicenseNative.";
            return nullptr;
        }

        std::memset(licensePointer, 0, sizeof(MsStoreLicenseNative));

        licensePointer->SkuStoreId = dup_string(to_string(license.SkuStoreId()));
        licensePointer->IsActive = license.IsActive();
        licensePointer->IsTrial = license.IsTrial();
        licensePointer->ExpirationDate = to_unix_epoch_millis(license.ExpirationDate());

        auto addOnLicenses = license.AddOnLicenses();
        licensePointer->AddOnLicensesCount = static_cast<int>(addOnLicenses.Size());

        if (licensePointer->AddOnLicensesCount > 0) {

            licensePointer->AddOnLicenses = static_cast<MsStoreAddOnLicenseNative*>(
                ::CoTaskMemAlloc(sizeof(MsStoreAddOnLicenseNative) * licensePointer->AddOnLicensesCount));

            if (licensePointer->AddOnLicenses == nullptr) {

                licensePointer->AddOnLicensesCount = 0;

            } else {

                std::memset(licensePointer->AddOnLicenses, 0,
                            sizeof(MsStoreAddOnLicenseNative) * licensePointer->AddOnLicensesCount);

                int index = 0;
                for (auto const& pair : addOnLicenses) {
                    auto const& addOn = pair.Value();
                    licensePointer->AddOnLicenses[index].SkuStoreId = dup_string(to_string(addOn.SkuStoreId()));
                    licensePointer->AddOnLicenses[index].InAppOfferToken = dup_string(to_string(addOn.InAppOfferToken()));
                    licensePointer->AddOnLicenses[index].ExpirationDate = to_unix_epoch_millis(addOn.ExpirationDate());
                    index++;
                }
            }
        } else {
            licensePointer->AddOnLicenses = nullptr;
        }

        g_lastError.clear();

        return licensePointer;

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

        HWND ownerWindow = ::GetForegroundWindow();

        if (ownerWindow == nullptr) {
            g_lastError = "No foreground window handle available for Store UI.";
            return -1;
        }

        /*
         * Desktop apps must provide an owner HWND for Store modal UI.
         * This avoids ERROR_INVALID_WINDOW_HANDLE and UI-thread errors.
         */
        auto initWindow = context.as<IInitializeWithWindow>();
        initWindow->Initialize(ownerWindow);

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
 * Frees memory allocated by msstore_winrt_get_license().
 */
extern "C" MSSTORE_WINRT_API void msstore_winrt_free_license(MsStoreLicenseNative* pointer) {

    if (pointer == nullptr)
        return;

    msstore_winrt_free(pointer->SkuStoreId);

    if (pointer->AddOnLicenses != nullptr) {

        for (int index = 0; index < pointer->AddOnLicensesCount; ++index) {
            msstore_winrt_free(pointer->AddOnLicenses[index].SkuStoreId);
            msstore_winrt_free(pointer->AddOnLicenses[index].InAppOfferToken);
        }

        ::CoTaskMemFree(pointer->AddOnLicenses);
    }

    ::CoTaskMemFree(pointer);
}

/*
 * Frees a pointer allocated by dup_string (and therefore by CoTaskMemAlloc).
 */
extern "C" MSSTORE_WINRT_API void msstore_winrt_free(const char* pointer) {

    if (pointer != nullptr)
        ::CoTaskMemFree(reinterpret_cast<LPVOID>(const_cast<char*>(pointer)));
}

/*
 * Returns the last error message for the current thread as UTF-8 text.
 */
extern "C" MSSTORE_WINRT_API const char* msstore_winrt_get_last_error() {
    return dup_string(g_lastError);
}
