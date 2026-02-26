# msstorelib

![Kotlin](https://img.shields.io/badge/kotlin-2.3.10-blue.svg?logo=kotlin)
![JVM](https://img.shields.io/badge/-JVM-gray.svg?style=flat)
[![GitHub Sponsors](https://img.shields.io/badge/Sponsor-gray?&logo=GitHub-Sponsors&logoColor=EA4AAA)](https://github.com/sponsors/StefanOltmann)

Kotlin/JVM library for Microsoft Store license info and in-app purchases.

The JVM calls a small C++/WinRT DLL (`msstore_winrt.dll`) via JNA.

## Features

- Query the Store license JSON (`ExtendedJsonData`).
- Parse a stable subset of license fields into Kotlin data classes.
- Trigger the Store purchase UI for add-ons or other in-app products.
- Embedded native DLL with an override path for custom-builds.

## Official docs

- Get license info for apps and add-ons:
  https://learn.microsoft.com/windows/uwp/monetize/get-license-info-for-apps-and-add-ons
- Store JSON schema reference (`ExtendedJsonData`):
  https://learn.microsoft.com/windows/uwp/monetize/data-schemas-for-store-products
- StoreContext API:
  https://learn.microsoft.com/uwp/api/windows.services.store.storecontext

## Requirements (Windows)

- Windows 10/11
- App packaged with MSIX and a Microsoft Store identity
- Product associated in Partner Center

If these requirements are not met, Store APIs can return empty results or errors.

## Install from Maven Central

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("de.stefan-oltmann:msstorelib:0.1.0")
}
```

## Usage

### License info

```kotlin
import de.stefan_oltmann.msstore.MsStore
import de.stefan_oltmann.msstore.MsStoreLicenseException

fun main() {

    try {
        
        val json = MsStore.getLicenseJson()
        println("Raw JSON from Store:")
        println(json)

        val info = MsStore.getLicenseInfo()
        println()
        println("Parsed summary:")
        println("productId  = ${info.productId}")
        println("skuId      = ${info.skuId}")
        println("isActive   = ${info.isActive}")
        println("isTrial    = ${info.isTrial}")
        println("expiration = ${info.expiration}")
        
        if (info.productAddOns.isNotEmpty())
            println("addOns     = ${info.productAddOns.size}")

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
import de.stefan_oltmann.msstore.MsStorePurchaseStatus

val status = MsStore.requestPurchase("9p9hdfltk63l")

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

## Error handling

- `MsStoreLicenseException` is thrown when the native call fails.
- The native layer stores the last error string and exposes it via `msstore_winrt_get_last_error()`.

## Native DLL build

Build the C++/WinRT DLL with CMake:

```powershell
cmake -S native/winrt -B build/winrt -G "Visual Studio 17 2022" -A x64 -DCMAKE_GENERATOR_INSTANCE="C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools"
cmake --build build/winrt --config Release
```

Output: `build/winrt/Release/msstore_winrt.dll`

The Gradle task `buildNative` runs the same steps.

### Optional environment overrides

If `cmake` is not on PATH, set an explicit path:

```
setx MSSTORE_CMAKE "C:\Program Files\CMake\bin\cmake.exe"
```

If CMake cannot locate your Visual Studio instance, set:

```
setx MSSTORE_VS_INSTANCE "C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools"
```

## Embedded DLL

The build embeds `msstore_winrt.dll` into the published JVM artifact under
`native/msstore_winrt.dll`. At runtime JNA extracts and loads it automatically.

If you want to use a different DLL build, override it at runtime:

```
-Dmsstore.winrt.path=C:\path\to\msstore_winrt.dll
```
