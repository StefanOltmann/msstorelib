package de.stefan_oltmann.msstore.model

/**
 * Result status for a Microsoft Store purchase request.
 *
 * Names are aligned with Windows.Services.Store.StorePurchaseStatus for clarity.
 */
public enum class MsStorePurchaseStatus {

    Succeeded,
    AlreadyPurchased,
    NotPurchased,
    NetworkError,
    ServerError,
    Unknown;

    internal companion object {
        fun fromNativeCode(code: Int): MsStorePurchaseStatus = when (code) {
            0 -> Succeeded
            1 -> AlreadyPurchased
            2 -> NotPurchased
            3 -> NetworkError
            4 -> ServerError
            else -> Unknown
        }
    }
}
