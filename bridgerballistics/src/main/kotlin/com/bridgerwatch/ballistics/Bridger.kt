package com.bridgerwatch.ballistics

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.bridgerwatch.ballistics.model.BallisticProfile
import com.bridgerwatch.ballistics.support.BridgerError
import com.bridgerwatch.ballistics.transfer.BridgerSlot
import com.bridgerwatch.ballistics.transfer.ImportUrlBuilder
import com.bridgerwatch.ballistics.transfer.PayloadCodec
import com.bridgerwatch.ballistics.transfer.PayloadEncoding
import com.bridgerwatch.ballistics.validation.ProfileValidator
import com.bridgerwatch.ballistics.validation.ValidationResult
import java.io.File

/**
 * The single entry point for sending a ballistic profile to the **Bridger
 * Android app** (and onward to the Bridger Watch).
 *
 * Build a [BallisticProfile] (or bring your own JSON) and call one of these.
 * The package handles the exact wire format, the four payload encodings, base64,
 * strict URL escaping, validation, and the temp-file plumbing for the share sheet.
 *
 * ## The three delivery paths
 * All three are supported; any one is enough to integrate.
 * 1. **Deep link / intent** — [open] (or [importUrl] to build the URL yourself,
 *    then start an `ACTION_VIEW` intent). Smoothest path.
 * 2. **Share sheet** — [shareIntent] returns an `ACTION_SEND` chooser that writes
 *    the profile as a `.json` via the bundled FileProvider.
 * 3. **Manual file** — [exportFile] writes a `.json` the user imports from inside Bridger.
 *
 * ## Package-visibility requirement (Path 1)
 * On Android 11+ your app must be able to *see* Bridger to query/launch its
 * scheme. This library's manifest contributes the required `<queries>` entry for
 * the `bridgerwatch` scheme via manifest merge, so [isInstalled] and [open] work
 * without extra setup. (This is the Android analog of iOS `LSApplicationQueriesSchemes`.)
 */
object Bridger {

    /** The package version. */
    const val VERSION: String = "1.0.0"

    /**
     * Default soft cap on the generated `bridgerwatch://` URL length. Payloads
     * that exceed it throw [BridgerError.PayloadTooLarge] — switch to the
     * share-sheet path for those.
     */
    const val DEFAULT_MAX_URL_LENGTH: Int = 100_000

    // MARK: - JSON

    /** Serializes a profile to the exact JSON bytes Bridger consumes. */
    fun makeJson(profile: BallisticProfile, pretty: Boolean = false): ByteArray =
        profile.toJson(pretty)

    /** Decodes Bridger profile JSON back into a [BallisticProfile]. */
    fun makeProfile(json: ByteArray): BallisticProfile = BallisticProfile.fromJson(json)

    // MARK: - Validation

    /** Validates a profile against the rules Bridger applies on import. */
    fun validate(profile: BallisticProfile): ValidationResult = ProfileValidator.validate(profile)

    /** Validates raw profile JSON the way Bridger does on import. */
    fun validate(json: ByteArray): ValidationResult = ProfileValidator.validate(json)

    // MARK: - Path 1: build the import URL

    /**
     * Builds the `bridgerwatch://import-ballistics` URL for a profile.
     *
     * @param slot which of the four watch slots to target. [BridgerSlot.AUTO] lets Bridger choose.
     * @param encoding how to pack the JSON into the URL. [PayloadEncoding.BASE64_JSON] is recommended.
     * @param filename optional `filename` query value. Defaults to `bdata<slot>.json` for a pinned slot.
     * @param validate when `true` (default), throws [BridgerError.ValidationFailed] on blocking errors.
     */
    fun importUrl(
        profile: BallisticProfile,
        slot: BridgerSlot = BridgerSlot.AUTO,
        encoding: PayloadEncoding = PayloadEncoding.BASE64_JSON,
        filename: String? = null,
        validate: Boolean = true,
        maxUrlLength: Int = DEFAULT_MAX_URL_LENGTH,
    ): String {
        if (validate) {
            val result = ProfileValidator.validate(profile)
            if (!result.isValid) throw BridgerError.ValidationFailed(result)
        }
        return buildImportUrl(profile.toJson(false), slot, encoding, filename, maxUrlLength)
    }

    /** Builds the import URL from JSON bytes you already have (used verbatim). */
    fun importUrl(
        json: ByteArray,
        slot: BridgerSlot = BridgerSlot.AUTO,
        encoding: PayloadEncoding = PayloadEncoding.BASE64_JSON,
        filename: String? = null,
        validate: Boolean = true,
        maxUrlLength: Int = DEFAULT_MAX_URL_LENGTH,
    ): String {
        if (validate) {
            val result = ProfileValidator.validate(json)
            if (!result.isValid) throw BridgerError.ValidationFailed(result)
        }
        return buildImportUrl(json, slot, encoding, filename, maxUrlLength)
    }

    private fun buildImportUrl(
        jsonData: ByteArray,
        slot: BridgerSlot,
        encoding: PayloadEncoding,
        filename: String?,
        maxUrlLength: Int,
    ): String {
        val resolvedFilename = filename ?: ImportUrlBuilder.defaultFilename(slot)
        val zipName = resolvedFilename ?: "bdata0.json"
        val dataValue = PayloadCodec.encode(jsonData, encoding, zipName)
        val url = ImportUrlBuilder.url(dataValue, slot, resolvedFilename)
        if (url.length > maxUrlLength) {
            throw BridgerError.PayloadTooLarge(url.length, maxUrlLength)
        }
        return url
    }

    // MARK: - Path 1: launch the Bridger app

    /**
     * Whether the Bridger app appears to be installed (its `bridgerwatch://`
     * scheme can be resolved). Relies on the `<queries>` entry this library
     * contributes for Android 11+ package visibility.
     */
    fun isInstalled(context: Context): Boolean {
        val probe = Intent(Intent.ACTION_VIEW, Uri.parse("${ImportUrlBuilder.SCHEME}://import-ballistics"))
        return probe.resolveActivity(context.packageManager) != null
    }

    /**
     * Sends a profile to Bridger by launching its deep link (Path 1).
     *
     * @throws BridgerError.BridgerNotInstalled if no activity can handle the scheme.
     * @throws BridgerError.ValidationFailed if the profile has blocking errors and [validate] is true.
     */
    fun open(
        context: Context,
        profile: BallisticProfile,
        slot: BridgerSlot = BridgerSlot.AUTO,
        encoding: PayloadEncoding = PayloadEncoding.BASE64_JSON,
        filename: String? = null,
        validate: Boolean = true,
        maxUrlLength: Int = DEFAULT_MAX_URL_LENGTH,
    ) {
        launch(context, importUrl(profile, slot, encoding, filename, validate, maxUrlLength))
    }

    /** Sends JSON you already have to Bridger by launching its deep link (Path 1). */
    fun open(
        context: Context,
        json: ByteArray,
        slot: BridgerSlot = BridgerSlot.AUTO,
        encoding: PayloadEncoding = PayloadEncoding.BASE64_JSON,
        filename: String? = null,
        validate: Boolean = true,
        maxUrlLength: Int = DEFAULT_MAX_URL_LENGTH,
    ) {
        launch(context, importUrl(json, slot, encoding, filename, validate, maxUrlLength))
    }

    private fun launch(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(context.packageManager) == null) {
            throw BridgerError.BridgerNotInstalled
        }
        context.startActivity(intent)
    }

    // MARK: - Path 2: share sheet

    /**
     * Builds an `ACTION_SEND` chooser that shares the profile as a `.json` file
     * (Path 2). Start it with `context.startActivity(...)`. The file is written
     * to the app cache and exposed through this library's bundled FileProvider.
     */
    fun shareIntent(
        context: Context,
        profile: BallisticProfile,
        filename: String? = null,
        validate: Boolean = true,
    ): Intent {
        if (validate) {
            val result = ProfileValidator.validate(profile)
            if (!result.isValid) throw BridgerError.ValidationFailed(result)
        }
        return shareIntent(context, profile.toJson(true), sanitizedFilename(filename ?: profile.chartID), validate = false)
    }

    /** Builds an `ACTION_SEND` chooser sharing JSON you already have (Path 2). */
    fun shareIntent(
        context: Context,
        json: ByteArray,
        filename: String,
        validate: Boolean = true,
    ): Intent {
        if (validate) {
            val result = ProfileValidator.validate(json)
            if (!result.isValid) throw BridgerError.ValidationFailed(result)
        }
        val file = writeCacheFile(context, sanitizedFilename(filename), json)
        val authority = "${context.packageName}.bridgerballistics.fileprovider"
        val uri: Uri = FileProvider.getUriForFile(context, authority, file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Send ballistic profile to Bridger")
    }

    // MARK: - Path 3: write a file

    /** Writes a profile as pretty-printed `.json` into [directory] (Path 3). */
    fun exportFile(
        profile: BallisticProfile,
        directory: File,
        filename: String? = null,
        validate: Boolean = true,
    ): File {
        if (validate) {
            val result = ProfileValidator.validate(profile)
            if (!result.isValid) throw BridgerError.ValidationFailed(result)
        }
        return exportFile(profile.toJson(true), directory, sanitizedFilename(filename ?: profile.chartID), validate = false)
    }

    /** Writes JSON bytes you already have as `.json` into [directory] (Path 3). */
    fun exportFile(
        json: ByteArray,
        directory: File,
        filename: String,
        validate: Boolean = true,
    ): File {
        if (validate) {
            val result = ProfileValidator.validate(json)
            if (!result.isValid) throw BridgerError.ValidationFailed(result)
        }
        val file = File(directory, sanitizedFilename(filename))
        writeBytes(file, json)
        return file
    }

    // MARK: - File helpers

    /** Normalizes a proposed file name: strips path-hostile characters and ensures a `.json` extension. */
    internal fun sanitizedFilename(proposed: String?): String {
        val fallback = "ballistic_profile.json"
        val trimmed = proposed?.trim().orEmpty()
        if (trimmed.isEmpty()) return fallback
        val illegal = "/\\:?%*|\"<>"
        var name = trimmed.filter { it !in illegal }.trim()
        if (name.isEmpty()) return fallback
        if (!name.lowercase().endsWith(".json")) name += ".json"
        return name
    }

    private fun writeBytes(file: File, data: ByteArray) {
        try {
            file.parentFile?.mkdirs()
            file.writeBytes(data)
        } catch (e: Exception) {
            throw BridgerError.FileWriteFailed(e.message ?: "unknown error")
        }
    }

    private fun writeCacheFile(context: Context, filename: String, data: ByteArray): File {
        val dir = File(context.cacheDir, "BridgerBallistics").apply { mkdirs() }
        val file = File(dir, filename)
        writeBytes(file, data)
        return file
    }
}
