# msstorelib

![Kotlin](https://img.shields.io/badge/kotlin-2.3.10-blue.svg?logo=kotlin)
![Java 25+](https://img.shields.io/badge/Java-25%2B-gray.svg?style=flat)
[![GitHub Sponsors](https://img.shields.io/badge/Sponsor-gray?&logo=GitHub-Sponsors&logoColor=EA4AAA)](https://github.com/sponsors/StefanOltmann)

Kotlin/JVM library for Microsoft Store license info and in-app purchases.

The JVM calls a small C++/WinRT DLL (`msstore_winrt.dll`) via Java FFM (Panama).

## Features

- Query the Store license fields directly from WinRT.
- A stable subset of license fields in Kotlin data classes.
- Trigger the Store purchase UI for add-ons or other in-app products.
- Native DLL loading with override, app-local, system-path, and embedded fallback resolution.

## Install from Maven Central

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("de.stefan-oltmann:msstorelib:0.5.0")
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

    } catch (ex: MsStoreLicenseException) {
        System.err.println("Store license query failed: ${ex.message}")
        System.err.println("Make sure the app is Store-packaged and msstore_winrt.dll is built.")
        throw ex
    }
}
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

The native layer assigns the current foreground window as the owner HWND for
Store modal UI. Ensure your app has a focused window when requesting purchases.

## API model types

- `MsStoreLicenseInfo` (app license summary)
- `MsStoreAddOnLicenseInfo` (add-on license entries)
- `MsStorePurchaseStatus` (purchase result status)

Note: `isTrialOwnedByThisUser`, `trialUniqueId`, and `trialTimeRemaining` are
intentionally not exposed in the API model to avoid false expectations because
they are not used by the MS Store API.

## Error handling

- `MsStoreLicenseException` is thrown when the native call fails.
- The native layer stores the last error string, exposed in Kotlin via `MsStoreNative.getLastError()`.

## Requirements

- Windows 10/11
- App packaged with MSIX and a Microsoft Store identity
- Product associated in Partner Center
- Java 25 or higher

If these requirements are not met, Store APIs can return empty results or errors.

## Native DLL loading

Resolution order:

1. `-Dmsstore.winrt.path=...`
2. `msstore_winrt.dll` in hosting app folder (next to app/JAR)
3. System library path (`System.loadLibrary("msstore_winrt")`)
4. Extract embedded resource `windows-x86_64/msstore_winrt.dll` to versioned cache and load it

Extraction is only attempted when steps 1-3 fail.
Cache path format:
`<java.io.tmpdir>/msstorelib-native/<LIB_VERSION>/windows-x86_64/msstore_winrt.dll`

`LIB_VERSION` is generated at build time (from project version / git-versioning).
This means extraction runs once per library version, not on every start.

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

If `winget` is unavailable, use manual installers:

- CMake: https://cmake.org/download/
- Visual Studio Build Tools: https://aka.ms/vs/17/release/vs_BuildTools.exe

Then run: `.\gradlew buildNativeLib`

This builds the DLL and copies it to:
`src/main/resources/windows-x86_64/msstore_winrt.dll`

## Official docs

- Get license info for apps and add-ons:
  https://learn.microsoft.com/windows/uwp/monetize/get-license-info-for-apps-and-add-ons
- StoreContext API:
  https://learn.microsoft.com/uwp/api/windows.services.store.storecontext
