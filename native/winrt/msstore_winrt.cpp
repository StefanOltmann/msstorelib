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
#include "msstore_winrt.h"

#include <windows.h>
#include <objbase.h>
#include <ShObjIdl_core.h>
#include <chrono>
#include <cstdint>
#include <cstring>
#include <memory>
#include <string>
#include <string_view>
#include <thread>
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
 * Bound for Store license queries.
 *
 * The Store service can hang (for example on unreachable networks), so
 * the license query is aborted after this time instead of blocking the
 * calling thread forever. The purchase dialog is exempt: it must stay
 * open until the user closes it.
 */
static constexpr std::chrono::seconds LICENSE_QUERY_TIMEOUT{30};

/*
 * Interval for polling the async license query status.
 */
static constexpr std::chrono::milliseconds ASYNC_POLL_INTERVAL{50};

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
    const int64_t millis = std::chrono::duration_cast<std::chrono::milliseconds>(
        sysTime.time_since_epoch()
    ).count();

    /*
     * The Store reports perpetual (non-expiring) licenses with a WinRT
     * DateTime of 0 (1601-01-01), which converts to a negative epoch
     * value. Normalize non-positive values to 0, the documented contract
     * of the JVM model for "no expiration".
     */
    return millis <= 0 ? 0 : millis;
}

/*
 * Initializes the Windows Runtime on the calling thread as STA and returns
 * whether the calling thread is now in a single-threaded apartment.
 *
 * Threads that already have an apartment (for example an MTA used by other
 * JVM code) keep it, so license queries do not fail with apartment-mismatch
 * errors. Only the purchase UI requires STA, so the purchase path must check
 * the result and fail with a clear error when the thread is MTA.
 */
static bool init_store_apartment() {

    try {
        init_apartment(apartment_type::single_threaded);
        return true;
    } catch (const hresult_error& ex) {
        if (ex.code() != RPC_E_CHANGED_MODE)
            throw;
    }

    return false;
}

/*
 * Returns a visible window of this process that can own the Store dialog.
 *
 * Resolution order: a visible top-level window on the calling thread, the
 * foreground window when it belongs to this process, then any visible
 * top-level window of this process. Using a window of another process as
 * owner is unreliable, so it is never chosen.
 */
static HWND resolve_owner_window() {

    HWND owner = nullptr;

    /* 1) A visible top-level window on the calling thread. */
    EnumThreadWindows(GetCurrentThreadId(), [](HWND hwnd, LPARAM lParam) -> BOOL {
        if (IsWindowVisible(hwnd) && GetParent(hwnd) == nullptr) {
            *reinterpret_cast<HWND*>(lParam) = hwnd;
            return FALSE;
        }
        return TRUE;
    }, reinterpret_cast<LPARAM>(&owner));

    if (owner != nullptr)
        return owner;

    /* 2) The foreground window, but only when it belongs to this process. */
    HWND foreground = GetForegroundWindow();

    if (foreground != nullptr) {

        DWORD windowProcessId = 0;
        GetWindowThreadProcessId(foreground, &windowProcessId);

        if (windowProcessId == GetCurrentProcessId())
            return foreground;
    }

    /* 3) Any visible top-level window of this process. */
    EnumWindows([](HWND hwnd, LPARAM lParam) -> BOOL {

        DWORD windowProcessId = 0;
        GetWindowThreadProcessId(hwnd, &windowProcessId);

        if (windowProcessId == GetCurrentProcessId() && IsWindowVisible(hwnd) && GetParent(hwnd) == nullptr) {
            *reinterpret_cast<HWND*>(lParam) = hwnd;
            return FALSE;
        }
        return TRUE;
    }, reinterpret_cast<LPARAM>(&owner));

    return owner;
}

/*
 * Frees a MsStoreLicenseNative allocation via the exported free function.
 */
struct MsStoreLicenseDeleter {
    void operator()(MsStoreLicenseNative* pointer) const noexcept {
        msstore_winrt_free_license(pointer);
    }
};

/* Owns a MsStoreLicenseNative allocation including all nested strings and arrays. */
using MsStoreLicensePtr = std::unique_ptr<MsStoreLicenseNative, MsStoreLicenseDeleter>;

/*
 * Waits up to the given timeout for an async operation to finish.
 *
 * Returns false and cancels the operation when the deadline passes, so
 * a hung Store service cannot block the calling thread forever.
 */
static bool wait_for_completion(
    winrt::Windows::Foundation::IAsyncInfo asyncInfo,
    std::chrono::milliseconds timeout
) {

    const auto deadline = std::chrono::steady_clock::now() + timeout;

    while (asyncInfo.Status() == winrt::Windows::Foundation::AsyncStatus::Started) {

        if (std::chrono::steady_clock::now() >= deadline) {
            asyncInfo.Cancel();
            return false;
        }

        std::this_thread::sleep_for(ASYNC_POLL_INTERVAL);
    }

    return true;
}

/*
 * Returns the StoreAppLicense information directly, or nullptr on error.
 *
 * The returned pointer and all nested strings/arrays are allocated with
 * CoTaskMemAlloc and must be released via msstore_winrt_free_license().
 */
extern "C" MSSTORE_WINRT_API MsStoreLicenseNative* msstore_winrt_get_license() {

    try {

        /* Prepare the calling thread for Store calls, tolerating existing apartments. */
        init_store_apartment();

        /* StoreContext::GetDefault uses the identity of the current package. */
        StoreContext context = StoreContext::GetDefault();

        /* Bridge the async WinRT call into a synchronous, bounded result for the JVM. */
        auto licenseOperation = context.GetAppLicenseAsync();

        if (!wait_for_completion(
                licenseOperation.as<winrt::Windows::Foundation::IAsyncInfo>(),
                LICENSE_QUERY_TIMEOUT
            )) {
            g_lastError = "License query timed out after " +
                std::to_string(LICENSE_QUERY_TIMEOUT.count()) +
                " seconds; the Microsoft Store service may be unreachable.";
            return nullptr;
        }

        StoreAppLicense license = licenseOperation.GetResults();

        if (!license) {
            g_lastError = "StoreAppLicense is null.";
            return nullptr;
        }

        MsStoreLicensePtr licensePointer(
            static_cast<MsStoreLicenseNative*>(::CoTaskMemAlloc(sizeof(MsStoreLicenseNative))));

        if (!licensePointer) {
            g_lastError = "Out of memory allocating MsStoreLicenseNative.";
            return nullptr;
        }

        /*
         * Zero the struct first so that the guard can safely free any field
         * that is still unset when a WinRT call below throws.
         */
        std::memset(licensePointer.get(), 0, sizeof(MsStoreLicenseNative));

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

        return licensePointer.release();

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

        /*
         * The purchase dialog must run on a single-threaded apartment.
         * Fail with a clear error when another component already initialized
         * COM as MTA on this thread, because the Store UI would otherwise
         * fail with an unexplained WinRT error.
         */
        if (!init_store_apartment()) {
            g_lastError = "The purchase dialog requires a single-threaded apartment (STA), but COM was already initialized as multi-threaded (MTA) on the calling thread. Call requestPurchase from a UI thread or a thread without COM initialization.";
            return -1;
        }

        StoreContext context = StoreContext::GetDefault();

        HWND ownerWindow = resolve_owner_window();

        if (ownerWindow == nullptr) {
            g_lastError = "No window available for Store UI. Ensure the app has a visible window or call from the UI thread.";
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
