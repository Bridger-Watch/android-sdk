package com.bridgerwatch.ballistics.transfer

/**
 * How the profile bytes are encoded into the `data` query value of the
 * `bridgerwatch://import-ballistics` URL (Path 1).
 *
 * The Bridger app accepts all four and auto-detects which one was used (by
 * content + magic bytes — no format flag is sent). For a single-weapon profile
 * the size differences are marginal, so [BASE64_JSON] is the recommended default.
 */
enum class PayloadEncoding {
    /**
     * Format 1 — the raw JSON text. (The package still percent-escapes it
     * correctly, but base64 is simpler and avoids a very long, punctuation-heavy URL.)
     */
    RAW_JSON,

    /** Format 2 — base64 of the JSON. **Recommended.** */
    BASE64_JSON,

    /** Format 3 — base64 of a gzip (`.gz`) archive of the JSON. Smaller URL. */
    GZIP_BASE64,

    /** Format 4 — base64 of a single-entry zip (`.zip`) archive of the JSON. Smaller URL. */
    ZIP_BASE64,
}
