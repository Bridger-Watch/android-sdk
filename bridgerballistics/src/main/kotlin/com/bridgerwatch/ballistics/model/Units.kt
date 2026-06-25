package com.bridgerwatch.ballistics.model

/**
 * Distance unit used for ranges throughout a [BallisticProfile].
 *
 * This also determines the per-row range key in the emitted JSON
 * (`range_yards` for [YARDS], `range_meters` for [METERS]) — the package
 * handles that for you.
 */
enum class RangeUnit(val wire: String) {
    YARDS("yards"),
    METERS("meters");

    /** The JSON key that carries the range value for a table row in this unit. */
    val rowKey: String
        get() = when (this) {
            YARDS -> "range_yards"
            METERS -> "range_meters"
        }

    companion object {
        fun fromWire(value: String?): RangeUnit? =
            entries.firstOrNull { it.wire.equals(value, ignoreCase = true) }
    }
}

/** Linear unit for the come-up / drop column (`Comeup_distance`). */
enum class LinearUnit(val wire: String) {
    INCHES("inches"),

    /** Encoded as `"cm"` on the wire. */
    CENTIMETERS("cm");

    companion object {
        fun fromWire(value: String?): LinearUnit? =
            entries.firstOrNull { it.wire.equals(value, ignoreCase = true) }
    }
}

/** Angular unit for the come-up column (`comeup_angle`). */
enum class AngleUnit(val wire: String) {
    /** Minutes of angle. Encoded as `"MOA"`. */
    MOA("MOA"),

    /** Milliradians. Encoded as `"MRAD"`. */
    MRAD("MRAD");

    companion object {
        fun fromWire(value: String?): AngleUnit? =
            entries.firstOrNull { it.wire.equals(value, ignoreCase = true) }
    }
}

/**
 * The `units` block of a Bridger ballistic profile.
 *
 * Maps to the (intentionally mixed-case) Bridger keys:
 * `range`, `Comeup_linear`, `Comeup_angle_unit`.
 */
data class Units(
    val range: RangeUnit = RangeUnit.YARDS,
    val comeupLinear: LinearUnit = LinearUnit.INCHES,
    val comeupAngle: AngleUnit = AngleUnit.MOA,
) {
    internal fun encode(): LinkedHashMap<String, Any?> = linkedMapOf(
        "range" to range.wire,
        "Comeup_linear" to comeupLinear.wire,
        "Comeup_angle_unit" to comeupAngle.wire,
    )
}
