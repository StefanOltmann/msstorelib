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
package de.stefan_oltmann.msstore

import de.stefan_oltmann.msstore.model.MsStoreAddOnLicenseInfo
import de.stefan_oltmann.msstore.model.MsStoreLicenseInfo
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout

/**
 * Internal entry-point for retrieving Microsoft Store license info.
 *
 * Implementation overview:
 * - FFM (Panama) loads `msstore_winrt.dll` (C++/WinRT).
 * - The C++/WinRT DLL calls `StoreContext.GetAppLicenseAsync()` and returns
 *   the data directly.
 *
 * @see https://learn.microsoft.com/windows/uwp/monetize/get-license-info-for-apps-and-add-ons
 */
internal object MsStoreLicense {

    private const val MSSTORE_LICENSE_NATIVE_SIZE = 40L
    private const val MSSTORE_ADDON_LICENSE_NATIVE_SIZE = 24L

    /**
     * Returns the current app license info.
     *
     * @throws MsStoreLicenseException when the native call fails.
     */
    fun getLicenseInfo(): MsStoreLicenseInfo {

        try {

            val pointer = MsStoreNative.getLicense()
                ?: throw MsStoreLicenseException(MsStoreNativeHelpers.readLastError() ?: "Native license query failed.")

            try {
                return readLicenseInfo(pointer)
            } finally {
                MsStoreNative.freeLicense(pointer)
            }

        } catch (ex: MsStoreLicenseException) {
            /* Pass on MsStoreLicenseException as is. */
            throw ex
        } catch (ex: Throwable) {
            /* Wrap everything else in a MsStoreLicenseException */
            throw MsStoreLicenseException(ex.message ?: "License query failed.")
        }
    }

    private fun readLicenseInfo(pointer: MemorySegment): MsStoreLicenseInfo {

        val licenseStruct = pointer.reinterpret(MSSTORE_LICENSE_NATIVE_SIZE)

        /*
         * Layout must match the C struct MsStoreLicenseNative:
         * 0: SkuStoreId (ADDRESS)
         * 8: IsActive (BYTE/BOOL)
         * 9: IsTrial (BYTE/BOOL)
         * 10-15: (Padding to align int64)
         * 16: ExpirationDate (LONG)
         * 24: AddOnLicenses (ADDRESS)
         * 32: AddOnLicensesCount (INT)
         * (Padding to 40)
         */

        val skuStoreId = readString(licenseStruct, 0) ?: ""

        val isActive = licenseStruct.get(ValueLayout.JAVA_BOOLEAN, 8)
        val isTrial = licenseStruct.get(ValueLayout.JAVA_BOOLEAN, 9)
        val expirationDate = licenseStruct.get(ValueLayout.JAVA_LONG, 16)
        val addOnLicensesPointer = licenseStruct.get(ValueLayout.ADDRESS, 24)
        val addOnLicensesCount = licenseStruct.get(ValueLayout.JAVA_INT, 32)

        val addOns = mutableListOf<MsStoreAddOnLicenseInfo>()

        if (addOnLicensesPointer.address() != 0L && addOnLicensesCount > 0) {

            val addOnLicensesStructArray =
                addOnLicensesPointer.reinterpret(addOnLicensesCount.toLong() * MSSTORE_ADDON_LICENSE_NATIVE_SIZE)

            for (index in 0 until addOnLicensesCount) {

                /* Struct size: 2 pointers (2*8) + int64 expiration (8) = 24. */
                val offset = index * MSSTORE_ADDON_LICENSE_NATIVE_SIZE

                addOns.add(readAddOnLicenseInfo(addOnLicensesStructArray, offset))
            }
        }

        /*
         * SkuStoreId is a combination of Store ID and SKU ID.
         * It looks like this: "9ND96XCDZRGB/0100"
         * It's only set if installed from the MS Store.
         *
         * We split this into two fields for easier handling:
         * The first 12 characters must be the Store ID.
         * The rest after the separator will be the SKU ID.
         */

        val storeId = skuStoreId.take(MsStoreLicenseInfo.STORE_ID_LENGTH)
        val skuId = skuStoreId.drop(MsStoreLicenseInfo.STORE_ID_LENGTH + 1)

        return MsStoreLicenseInfo(
            storeId = storeId,
            skuId = skuId,
            expirationDate = expirationDate,
            isActive = isActive,
            isTrial = isTrial,
            addOnLicenses = addOns
        )
    }

    private fun readAddOnLicenseInfo(pointer: MemorySegment, offset: Long): MsStoreAddOnLicenseInfo {

        /*
         * Layout must match MsStoreAddOnLicenseNative:
         * 0: SkuStoreId (ADDRESS)
         * 8: InAppOfferToken (ADDRESS)
         * 16: ExpirationDate (LONG)
         */
        val skuStoreId = readString(pointer, offset + 0) ?: ""
        val inAppOfferToken = readString(pointer, offset + 8) ?: ""
        val expirationDate = pointer.get(ValueLayout.JAVA_LONG, offset + 16)

        val storeId = skuStoreId.take(MsStoreLicenseInfo.STORE_ID_LENGTH)
        val skuId = skuStoreId.drop(MsStoreLicenseInfo.STORE_ID_LENGTH + 1)

        return MsStoreAddOnLicenseInfo(
            storeId = storeId,
            skuId = skuId,
            inAppOfferToken = inAppOfferToken,
            expirationDate = expirationDate
        )
    }

    private fun readString(pointer: MemorySegment, offset: Long): String? {

        val address = pointer.get(ValueLayout.ADDRESS, offset)

        if (address.address() == 0L)
            return null

        return MsStoreNativeHelpers.readStringFromAddress(address)
    }
}
