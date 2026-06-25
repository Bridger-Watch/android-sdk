package com.bridgerwatch.ballistics.support

import com.bridgerwatch.ballistics.validation.ValidationResult

/**
 * Errors thrown by [com.bridgerwatch.ballistics.Bridger].
 *
 * Each subclass carries a partner-friendly `message` suitable for logging or
 * display.
 */
sealed class BridgerError(message: String) : Exception(message) {

    /**
     * Pre-flight validation found one or more blocking errors. The attached
     * [result] lists every issue (errors and warnings).
     */
    data class ValidationFailed(val result: ValidationResult) : BridgerError(
        "The ballistic profile failed validation:\n" +
            result.errors.joinToString("\n") { "• " + it.message }
    )

    /**
     * The generated `bridgerwatch://` URL exceeded the configured length limit.
     * For payloads this large, use the share-sheet path (Path 2) instead.
     */
    data class PayloadTooLarge(val urlLength: Int, val limit: Int) : BridgerError(
        "The import URL is $urlLength characters, which exceeds the limit of $limit. " +
            "Use the share-sheet path (Bridger.shareIntent) for payloads this large."
    )

    /** The profile could not be turned into the requested wire bytes. */
    data class EncodingFailed(val reason: String) :
        BridgerError("Failed to encode the ballistic profile: $reason")

    /** A valid URL could not be constructed from the encoded components. */
    data object UrlConstructionFailed :
        BridgerError("Could not construct a valid bridgerwatch:// URL from the encoded data.")

    /**
     * No activity could handle the `bridgerwatch://` intent — most commonly
     * because the Bridger Android app is not installed. Confirm install with
     * [com.bridgerwatch.ballistics.Bridger.isInstalled].
     */
    data object BridgerNotInstalled :
        BridgerError("Could not open Bridger. It may not be installed on this device.")

    /** Writing the profile to disk failed (Path 2 temp file / Path 3 export). */
    data class FileWriteFailed(val reason: String) :
        BridgerError("Could not write the ballistic profile to disk: $reason")
}
