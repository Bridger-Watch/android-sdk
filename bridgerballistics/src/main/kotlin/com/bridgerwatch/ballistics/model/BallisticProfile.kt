package com.bridgerwatch.ballistics.model

import com.bridgerwatch.ballistics.support.Json
import org.json.JSONObject
import org.json.JSONTokener

/**
 * A single-weapon Bridger ballistic profile.
 *
 * This is the type-safe representation of the JSON document the Bridger app
 * (iOS and Android) consumes. Build one of these, hand it to
 * [com.bridgerwatch.ballistics.Bridger], and the package produces the exact
 * JSON, encodes it, and delivers it — you never touch the mixed-case keys,
 * base64, or URL escaping yourself.
 *
 * Mapping to the wire format:
 * - [chartID] → `chart_id`
 * - [units] → `units`
 * - [rangeSettings] → `range_settings`
 * - [rifle] → `weapon_system.rifle` (omitted when `null`)
 * - [bullet] → `ammunition.bullet` (omitted when `null`)
 * - [table] → `ballistic_table`
 */
data class BallisticProfile(
    /** Free-form identifier shown for display (`chart_id`). */
    val chartID: String,
    /** Unit declarations (`units`). Defaults to yards / inches / MOA. */
    val units: Units = Units(),
    /** Table geometry (`range_settings`). */
    val rangeSettings: RangeSettings,
    /** Optional rifle metadata (`weapon_system.rifle`). */
    val rifle: Rifle? = null,
    /** Optional bullet / load metadata (`ammunition.bullet`). */
    val bullet: Bullet? = null,
    /** The trajectory rows (`ballistic_table`). */
    val table: List<BallisticRow>,
) {
    internal fun encode(): LinkedHashMap<String, Any?> {
        val root = linkedMapOf<String, Any?>(
            "chart_id" to chartID,
            "units" to units.encode(),
            "range_settings" to rangeSettings.encode(),
        )
        rifle?.let { root["weapon_system"] = linkedMapOf<String, Any?>("rifle" to it.encode()) }
        bullet?.let { root["ammunition"] = linkedMapOf<String, Any?>("bullet" to it.encode()) }
        root["ballistic_table"] = table.map { it.encode(units.range) }
        return root
    }

    /**
     * Serializes this profile to the exact JSON bytes Bridger consumes.
     *
     * @param pretty `true` for indented, key-sorted, deterministic output (good
     *   for files and debugging); `false` for compact output (used for URLs).
     */
    fun toJson(pretty: Boolean = false): ByteArray =
        Json.serialize(encode(), pretty).toByteArray(Charsets.UTF_8)

    companion object {
        /**
         * Decodes Bridger profile JSON back into a [BallisticProfile].
         *
         * Accepts either range key per row (`range_yards` / `range_meters`) and
         * tolerates integer or floating-point numbers. Throws
         * [IllegalArgumentException] if a required field is missing.
         */
        fun fromJson(json: ByteArray): BallisticProfile {
            val root = JSONTokener(json.toString(Charsets.UTF_8)).nextValue() as? JSONObject
                ?: throw IllegalArgumentException("Top-level JSON value must be an object.")

            val chartID = root.optString("chart_id")
            val unitsObj = root.optJSONObject("units")
            val units = Units(
                range = RangeUnit.fromWire(unitsObj?.optString("range")) ?: RangeUnit.YARDS,
                comeupLinear = LinearUnit.fromWire(unitsObj?.optString("Comeup_linear")) ?: LinearUnit.INCHES,
                comeupAngle = AngleUnit.fromWire(unitsObj?.optString("Comeup_angle_unit")) ?: AngleUnit.MOA,
            )

            val rs = root.optJSONObject("range_settings")
                ?: throw IllegalArgumentException("range_settings is missing.")
            val rangeSettings = RangeSettings(
                start = rs.optInt("start_length"),
                end = rs.optInt("end_length"),
                interval = rs.optInt("interval_length"),
                intervalCount = rs.optInt("interval_count"),
                zeroRange = rs.optInt("zero_range_length"),
                tableDisplayRange = rs.optInt("table_display_range_length"),
            )

            val rifle = root.optJSONObject("weapon_system")?.optJSONObject("rifle")?.let {
                Rifle(caliber = it.optString("caliber"), name = it.optString("Name", it.optString("name")))
            }
            val bullet = root.optJSONObject("ammunition")?.optJSONObject("bullet")?.let {
                Bullet(
                    manufacturer = it.optString("manufacturer"),
                    model = it.optString("model"),
                    weightGrains = it.optDouble("weight_grains", 0.0),
                    muzzleVelocityFPS = it.optDouble("muzzle_velocity_fps", 0.0),
                )
            }

            val rows = mutableListOf<BallisticRow>()
            val table = root.optJSONArray("ballistic_table")
            if (table != null) {
                for (i in 0 until table.length()) {
                    val row = table.optJSONObject(i) ?: continue
                    val range = when {
                        row.has("range_yards") -> row.optDouble("range_yards")
                        row.has("range_meters") -> row.optDouble("range_meters")
                        else -> throw IllegalArgumentException("Row $i is missing both range_yards and range_meters.")
                    }
                    rows.add(
                        BallisticRow(
                            range = range,
                            comeupDistance = row.optDouble("Comeup_distance"),
                            comeupAngle = row.optDouble("comeup_angle"),
                            comeupMRADAngle = if (row.has("comeup_mrad_angle")) row.optDouble("comeup_mrad_angle") else null,
                            driftInches = if (row.has("drift_in")) row.optDouble("drift_in") else null,
                        )
                    )
                }
            }

            return BallisticProfile(
                chartID = chartID,
                units = units,
                rangeSettings = rangeSettings,
                rifle = rifle,
                bullet = bullet,
                table = rows,
            )
        }
    }
}
