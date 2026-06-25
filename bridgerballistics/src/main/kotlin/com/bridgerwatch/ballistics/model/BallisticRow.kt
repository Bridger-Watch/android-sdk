package com.bridgerwatch.ballistics.model

/**
 * A single row of the `ballistic_table`.
 *
 * On the wire each row looks like:
 * ```json
 * { "range_yards": 105, "Comeup_distance": -46.0, "comeup_angle": -8.88 }
 * ```
 * The range key (`range_yards` vs `range_meters`) follows the profile's
 * [Units.range]; [BallisticProfile] writes the correct one for you, so you only
 * ever set [range] here in whichever unit the profile uses.
 */
data class BallisticRow(
    /** Range for this row, in the profile's range unit (yards or meters). */
    val range: Double,
    /** Linear come-up / drop at this range (`Comeup_distance`), in the profile's linear unit. */
    val comeupDistance: Double,
    /** Angular come-up at this range (`comeup_angle`), in the profile's angle unit. */
    val comeupAngle: Double,
    /**
     * Optional angular come-up expressed in milliradians (`comeup_mrad_angle`).
     * Include when available; the watch uses it if present.
     */
    val comeupMRADAngle: Double? = null,
    /**
     * Optional wind drift in inches (`drift_in`).
     * Include when available; the watch uses it if present.
     */
    val driftInches: Double? = null,
) {
    /**
     * Encodes this row into an ordered map, using [rangeUnit] to pick the range
     * key (`range_yards` / `range_meters`).
     */
    internal fun encode(rangeUnit: RangeUnit): LinkedHashMap<String, Any?> {
        val map = linkedMapOf<String, Any?>(
            rangeUnit.rowKey to range,
            "Comeup_distance" to comeupDistance,
            "comeup_angle" to comeupAngle,
        )
        if (comeupMRADAngle != null) map["comeup_mrad_angle"] = comeupMRADAngle
        if (driftInches != null) map["drift_in"] = driftInches
        return map
    }
}
