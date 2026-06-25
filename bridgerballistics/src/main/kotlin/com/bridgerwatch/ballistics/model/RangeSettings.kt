package com.bridgerwatch.ballistics.model

/**
 * The `range_settings` block of a Bridger ballistic profile.
 *
 * Values are whole units in the profile's range unit (e.g. yards). Maps to the
 * Bridger keys `start_length`, `end_length`, `interval_length`,
 * `interval_count`, `zero_range_length`, `table_display_range_length`.
 *
 * Note the `_length` suffix: this is the single-weapon canonical shape the
 * Bridger Android app reads (see its bundled `bdata3.json` reference). It is the
 * unit-agnostic suffix — the per-row range key still reflects yards/meters.
 */
data class RangeSettings(
    /** First range in the table (`start_length`). */
    val start: Int,
    /** Last range in the table (`end_length`). */
    val end: Int,
    /** Step between rows (`interval_length`). */
    val interval: Int,
    /**
     * Number of rows in the table (`interval_count`). Bridger expects this to
     * equal the row count; a mismatch is a warning, not a hard rejection.
     */
    val intervalCount: Int,
    /** Zero range (`zero_range_length`). */
    val zeroRange: Int,
    /** Range to highlight in the watch's table view (`table_display_range_length`). */
    val tableDisplayRange: Int,
) {
    companion object {
        /**
         * Convenience that derives [intervalCount] from a row count and defaults
         * [zeroRange] / [tableDisplayRange] to [start] when omitted.
         *
         * @param rowCount the number of rows in your `ballistic_table` (used for `interval_count`).
         */
        fun fromRowCount(
            start: Int,
            end: Int,
            interval: Int,
            rowCount: Int,
            zeroRange: Int? = null,
            tableDisplayRange: Int? = null,
        ): RangeSettings = RangeSettings(
            start = start,
            end = end,
            interval = interval,
            intervalCount = rowCount,
            zeroRange = zeroRange ?: start,
            tableDisplayRange = tableDisplayRange ?: start,
        )
    }

    internal fun encode(): LinkedHashMap<String, Any?> = linkedMapOf(
        "start_length" to start,
        "end_length" to end,
        "interval_length" to interval,
        "interval_count" to intervalCount,
        "zero_range_length" to zeroRange,
        "table_display_range_length" to tableDisplayRange,
    )
}
