package com.bridgerwatch.ballistics.transfer

/**
 * Builds the `bridgerwatch://import-ballistics` URL (delivery Path 1).
 *
 * The URL shape, from the Bridger spec:
 * ```
 * bridgerwatch://import-ballistics?slot=<0-3>&filename=bdata<slot>.json&data=<payload>
 * ```
 * `slot` and `filename` are optional; `data` carries the (already-encoded)
 * profile. Every query value is percent-escaped down to the RFC 3986 *unreserved*
 * set so a base64 payload's `+`, `/`, and `=` survive both percent- and
 * form-decoding intact (they become `%2B`, `%2F`, `%3D`).
 *
 * We build the string by hand rather than via `android.net.Uri.Builder` so the
 * escaping is exact and the builder stays unit-testable without the Android
 * framework.
 */
internal object ImportUrlBuilder {
    const val SCHEME = "bridgerwatch"
    const val HOST = "import-ballistics"

    /** The conventional file name for a pinned slot (`bdata<index>.json`), or `null` for [BridgerSlot.AUTO]. */
    fun defaultFilename(slot: BridgerSlot): String? =
        slot.index?.let { "bdata$it.json" }

    /**
     * Builds the import URL.
     *
     * @param dataValue the encoded payload (raw JSON or base64), *before* percent-escaping.
     * @param slot which slot to target. [BridgerSlot.AUTO] omits the `slot` param.
     * @param filename the `filename` query value, or `null` to omit it.
     */
    fun url(dataValue: String, slot: BridgerSlot, filename: String?): String {
        val pairs = mutableListOf<Pair<String, String>>()
        slot.index?.let { pairs.add("slot" to it.toString()) }
        if (filename != null) pairs.add("filename" to filename)
        pairs.add("data" to dataValue)

        val query = pairs.joinToString("&") { "${escape(it.first)}=${escape(it.second)}" }
        return "$SCHEME://$HOST?$query"
    }

    private val UNRESERVED: Set<Char> =
        (('A'..'Z') + ('a'..'z') + ('0'..'9')).toSet() + setOf('-', '.', '_', '~')

    /** Percent-encode every byte not in the RFC 3986 unreserved set (UTF-8, uppercase hex). */
    fun escape(value: String): String {
        val sb = StringBuilder()
        for (byte in value.toByteArray(Charsets.UTF_8)) {
            val code = byte.toInt() and 0xFF
            val c = code.toChar()
            if (c in UNRESERVED) {
                sb.append(c)
            } else {
                sb.append('%').append("%02X".format(code))
            }
        }
        return sb.toString()
    }
}
