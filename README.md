# msstorelib

![Kotlin](https://img.shields.io/badge/kotlin-2.4.10-blue.svg?logo=kotlin)
![Java 25+](https://img.shields.io/badge/Java-25%2B-gray.svg?style=flat)
[![GitHub Sponsors](https://img.shields.io/badge/Sponsor-gray?&logo=GitHub-Sponsors&logoColor=EA4AAA)](https://github.com/sponsors/StefanOltmann)

Kotlin/JVM library for Microsoft Store license info and in-app purchases.

The JVM calls a small C++/WinRT DLL (`msstore_winrt.dll`) via Java FFM (Panama).

## Features

- Query the Store license fields directly from WinRT.
- A stable subset of license fields in Kotlin data classes.
- Acquisition metadata of the app's own product SKU: acquisition date and promotion campaign ID.
- Trigger the Store purchase UI for add-ons or other in-app products.
- Native DLL loading with override, app-local, system-path, and embedded fallback resolution.

## Install from Maven Central

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("de.stefan-oltmann:msstorelib:0.6.0")
}
```

## Usage

### License info

```kotlin
import de.stefan_oltmann.msstore.MsStore
import de.stefan_oltmann.msstore.MsStoreLicenseException

fun main() {

    try {

        val info = MsStore.getLicenseInfo()
        println("License summary:")
        println("storeId        = ${info.storeId}")
        println("skuId          = ${info.skuId}")
        println("isActive       = ${info.isActive}")
        println("isTrial        = ${info.isTrial}")
        println("expirationDate = ${info.expirationDate}")
        println("addOns         = ${info.addOnLicenses.size}")
        println("acquiredDate   = ${info.acquiredDate}")
        println("campaignId     = ${info.campaignId}")

    } catch (ex: MsStoreLicenseException) {
        System.err.println("Store license query failed: ${ex.message}")
        System.err.println("Make sure the app is Store-packaged and msstore_winrt.dll is built.")
        throw ex
    }
}
```

`getLicenseInfo()` blocks the calling thread until the Store answers; the
native layer aborts the license query after 30 seconds when the Store service
does not respond, so the call never hangs forever. Call it from a background
thread to keep the UI responsive.

Reading the acquisition metadata runs an additional, best-effort Store query that is bounded to
10 seconds and never fails the license query. A slow Store therefore delays
`getLicenseInfo()` by at most the license timeout plus this optional timeout, and a failed or
timed-out acquisition query only leaves `acquiredDate` and `campaignId` as `null`.

### Acquisition metadata

`acquiredDate` and `campaignId` describe the app's own product SKU. They come from
`StoreSku.CollectionData`, which the Store only provides for SKUs the current user has an
entitlement to. Both fields are therefore optional and are `null` when the Store does not
provide collection data, for example:

- the app was not installed or acquired from the Microsoft Store,
- the app is not associated with a product in Partner Center,
- the optional Store query fails or times out,
- no promotion campaign is associated with the app.

A `null` value means "unknown", never "acquired just now" and never "no campaign ever".
Check for `null` before using the values, and never derive license state from them:
`acquiredDate`/`campaignId` are informational only and do not change the license status
returned by `MsStoreLicenseInfo.check(...)`.

These fields always describe the app itself. Add-on licenses have their own acquisition data
and never flow into `acquiredDate` or `campaignId`.

```kotlin
val info = MsStore.getLicenseInfo()

val acquiredDate = info.acquiredDate

if (acquiredDate != null)
    println("Acquired at $acquiredDate")
else
    println("Acquisition date not provided by the Store")

val campaignId = info.campaignId

if (campaignId != null)
    println("Acquired via campaign $campaignId")
```

### In-app purchase

```kotlin
import de.stefan_oltmann.msstore.MsStore
import de.stefan_oltmann.msstore.model.MsStorePurchaseStatus

val status = MsStore.requestPurchase("9ND96XCDZRGB")

when (status) {
    MsStorePurchaseStatus.Succeeded -> {
        /* Purchase was successful */
    }
    MsStorePurchaseStatus.AlreadyPurchased -> {
        /* License already exists */
    }
    else -> {
        /* Show error */
    }
}
```

The native layer picks the owner window for the Store modal dialog automatically: a visible window
on the calling thread, the foreground window of this process, or any visible window of this process.
The call blocks until the dialog closes; keep the app's UI thread responsive while it is open (do
not call from a background thread while the UI thread waits for the result). If no window is
available, the call fails with a clear error.

The purchase call must run on a thread with a single-threaded COM apartment (STA). If other JVM or
native code already initialized the calling thread as multi-threaded (MTA), the call fails with a
clear error instead of an unexplained WinRT failure.

## API model types

- `MsStoreLicenseInfo` (app license summary, including optional `acquiredDate` and `campaignId`)
- `MsStoreAddOnLicenseInfo` (add-on license entries)
- `MsStorePurchaseStatus` (purchase result status)

Note: `isTrialOwnedByThisUser`, `trialUniqueId`, and `trialTimeRemaining` are intentionally not
exposed in the API model to avoid false expectations because they are not used by the MS Store API.

Note: `acquiredDate` and `campaignId` are optional (`null` when the Store does not provide them).
See [Acquisition metadata](#acquisition-metadata) above for when the Store provides the values and
what `null` means.

## Error handling

- `MsStoreLicenseException` is the only exception type thrown by the public API. Underlying errors,
  including native failures and JVM errors, are always preserved as the exception's `cause`, so no
  error information is lost.
- Native error text is included in the exception message, so integrators do not need access to
  internal native state to diagnose failures.

## Requirements

- Windows 10/11
- App packaged with MSIX and a Microsoft Store identity
- Product associated in Partner Center
- Java 25 or higher, launched with `--enable-native-access=ALL-UNNAMED` (see below)

If these requirements are not met, Store APIs can return empty results or errors. The acquisition
metadata (`acquiredDate`, `campaignId`) is only provided for apps that were acquired from the
Microsoft Store, so it is `null` for sideloaded or unpackaged apps.

## Native access (JEP 472)

The library loads and calls the native DLL through restricted JVM APIs (FFM, JEP 454). Since JDK 24
these calls require an explicit opt-in (JEP 472): launch your app with
`--enable-native-access=ALL-UNNAMED`, or `--enable-native-access=msstorelib` when the library is on
the module path. Without the flag the JVM prints a warning today and blocks the calls on a future
JDK; msstorelib then fails with an error that includes this launch option.

## Native DLL loading

Resolution order:

1. `-Dmsstore.winrt.path=...`
2. `msstore_winrt.dll` in hosting app folder (next to app/JAR)
3. System library path (`System.loadLibrary("msstore_winrt")`)
4. Extract embedded resource `windows-x86_64/msstore_winrt.dll` to versioned cache and load it

Extraction is only attempted when steps 1-3 fail.

A DLL found in steps 2-3 that cannot be loaded (for example a corrupt or
wrong-architecture file) is skipped and resolution continues with the remaining
steps. The explicit override in step 1 always fails fast, because it is a
deliberate choice. Cache path format:
`<java.io.tmpdir>/msstorelib-native/<LIB_VERSION>/windows-x86_64/msstore_winrt.dll`

`LIB_VERSION` is generated at build time (from project version / git-versioning). This means
extraction runs once per library version, not on every start.

Override path example:

```
-Dmsstore.winrt.path=C:\path\to\msstore_winrt.dll
```

## Local DLL build

For local builds you need to install these two dependencies.

CMake:

```powershell
winget install --id Kitware.CMake -e --accept-source-agreements --accept-package-agreements
```

Visual Studio 2022 Build Tools (C++ workload):

```powershell
winget install --id Microsoft.VisualStudio.2022.BuildTools -e --accept-source-agreements --accept-package-agreements --override "--quiet --wait --norestart --add Microsoft.VisualStudio.Workload.VCTools --includeRecommended"
```

Visual Studio 2026 Build Tools also work; the build detects the installed VS
version and selects the matching CMake generator automatically.

If `winget` is unavailable, use manual installers:

- CMake: https://cmake.org/download/
- Visual Studio Build Tools: https://aka.ms/vs/17/release/vs_BuildTools.exe

Then run: `.\gradlew buildNativeLib`

This builds the DLL and copies it to:
`src/main/resources/windows-x86_64/msstore_winrt.dll`

## Testing and on-device verification

The unit tests cover the native-struct parsing and the Kotlin mapping only, including the
missing-data cases where `acquiredDate` and `campaignId` must be `null`. They do not call the real
Microsoft Store.

`acquiredDate` and `campaignId` depend on live Store data and on the entitlements of the signed-in
user, so they can only be verified on a real device:

1. Package the test app as MSIX with a Microsoft Store identity and associate a product in Partner
   Center.
2. Install or acquire the app from the Store with a user that has an entitlement.
3. Run `MsStore.getLicenseInfo()` and confirm that `acquiredDate` and `campaignId` carry plausible
   values. Also confirm the `null` case with an app or user without an entitlement.

Windows-only checks (`denyNativeAccessTest` and the native DLL load) run as part of the Gradle
build on Windows; on other platforms they are skipped.

## Official docs

- Get license info for apps and add-ons:
  https://learn.microsoft.com/windows/uwp/monetize/get-license-info-for-apps-and-add-ons
- Get product info for apps and add-ons (app's own product and SKUs):
  https://learn.microsoft.com/windows/uwp/monetize/get-product-info-for-apps-and-add-ons
- StoreContext API:
  https://learn.microsoft.com/uwp/api/windows.services.store.storecontext
- StoreContext.GetStoreProductForCurrentAppAsync:
  https://learn.microsoft.com/uwp/api/windows.services.store.storecontext.getstoreproductforcurrentappasync
- StoreSku.CollectionData:
  https://learn.microsoft.com/uwp/api/windows.services.store.storesku.collectiondata
- StoreCollectionData (AcquiredDate, CampaignId):
  https://learn.microsoft.com/uwp/api/windows.services.store.storecollectiondata
