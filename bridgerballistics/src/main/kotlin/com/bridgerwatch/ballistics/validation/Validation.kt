package com.bridgerwatch.ballistics.validation

import com.bridgerwatch.ballistics.model.BallisticProfile
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener

/**
 * How serious a [ValidationIssue] is.
 *
 * Mirrors Bridger's own behaviour: an [ERROR] is a hard rejection on the watch
 * side, while a [WARNING] is accepted (Bridger imports the profile but flags
 * the discrepancy).
 */
enum class ValidationSeverity { ERROR, WARNING }

/** A single problem found while validating a profile. */
data class ValidationIssue(
    val severity: ValidationSeverity,
    /** The JSON field the issue relates to (dotted path), or `null` for document-level issues. */
    val field: String?,
    val message: String,
) {
    override fun toString(): String {
        val tag = if (severity == ValidationSeverity.ERROR) "error" else "warning"
        return if (field != null) "[$tag] $field: $message" else "[$tag] $message"
    }
}

/**
 * The outcome of validating a profile, listing every issue found.
 *
 * Validation never stops at the first problem — it collects all of them so a
 * partner can fix everything in one pass.
 */
data class ValidationResult(val issues: List<ValidationIssue>) {
    /** The blocking issues (severity [ValidationSeverity.ERROR]). */
    val errors: List<ValidationIssue> get() = issues.filter { it.severity == ValidationSeverity.ERROR }

    /** The non-blocking issues (severity [ValidationSeverity.WARNING]). */
    val warnings: List<ValidationIssue> get() = issues.filter { it.severity == ValidationSeverity.WARNING }

    /** `true` when there are no blocking errors. Warnings do not affect this. */
    val isValid: Boolean get() = errors.isEmpty()

    override fun toString(): String =
        if (issues.isEmpty()) "Valid (no issues)." else issues.joinToString("\n") { it.toString() }
}

/**
 * Validates Bridger ballistic profiles against the same rules the Bridger app
 * applies on import.
 *
 * The rules (from the Bridger integration spec):
 * - Reject if the bytes are not valid JSON.
 * - Reject if any of `chart_id`, `units`, `range_settings`, `ballistic_table`
 *   is missing. (`weapon_system` and `ammunition` are optional.)
 * - Reject if a numeric field holds a non-numeric value.
 * - Warn (accept) if `ballistic_table` length differs from `interval_count`.
 */
object ProfileValidator {

    // MARK: Model validation

    /** Validates an already-constructed [BallisticProfile]. */
    fun validate(profile: BallisticProfile): ValidationResult {
        val issues = mutableListOf<ValidationIssue>()

        if (profile.chartID.trim().isEmpty()) {
            issues += ValidationIssue(ValidationSeverity.WARNING, "chart_id", "chart_id is empty.")
        }

        if (profile.table.isEmpty()) {
            issues += ValidationIssue(
                ValidationSeverity.WARNING, "ballistic_table",
                "ballistic_table is empty; the watch will have no rows to show.",
            )
        }

        fun checkFinite(value: Double, field: String) {
            if (!value.isFinite()) {
                issues += ValidationIssue(
                    ValidationSeverity.ERROR, field, "value is not finite (NaN or infinity).",
                )
            }
        }
        profile.table.forEachIndexed { i, row ->
            val prefix = "ballistic_table[$i]"
            checkFinite(row.range, "$prefix.${profile.units.range.rowKey}")
            checkFinite(row.comeupDistance, "$prefix.Comeup_distance")
            checkFinite(row.comeupAngle, "$prefix.comeup_angle")
            row.comeupMRADAngle?.let { checkFinite(it, "$prefix.comeup_mrad_angle") }
            row.driftInches?.let { checkFinite(it, "$prefix.drift_in") }
        }
        profile.bullet?.let {
            checkFinite(it.weightGrains, "ammunition.bullet.weight_grains")
            checkFinite(it.muzzleVelocityFPS, "ammunition.bullet.muzzle_velocity_fps")
        }

        if (profile.rangeSettings.intervalCount != profile.table.size) {
            issues += ValidationIssue(
                ValidationSeverity.WARNING, "range_settings.interval_count",
                "interval_count (${profile.rangeSettings.intervalCount}) does not match the number of rows " +
                    "(${profile.table.size}). Bridger will accept the profile but flag the mismatch.",
            )
        }

        return ValidationResult(issues)
    }

    // MARK: Raw-JSON validation

    /** Validates raw JSON bytes the way the Bridger app does on import. */
    fun validate(jsonData: ByteArray): ValidationResult {
        val issues = mutableListOf<ValidationIssue>()

        val parsed: Any? = try {
            JSONTokener(jsonData.toString(Charsets.UTF_8)).nextValue()
        } catch (e: JSONException) {
            return ValidationResult(
                listOf(ValidationIssue(ValidationSeverity.ERROR, null, "Not valid JSON: ${e.message}")),
            )
        }

        val root = parsed as? JSONObject
            ?: return ValidationResult(
                listOf(ValidationIssue(ValidationSeverity.ERROR, null, "Top-level JSON value must be an object.")),
            )

        // Required top-level keys.
        for (key in listOf("chart_id", "units", "range_settings", "ballistic_table")) {
            if (!root.has(key)) {
                issues += ValidationIssue(ValidationSeverity.ERROR, key, "Required field is missing.")
            }
        }

        if (root.has("chart_id")) {
            val v = root.get("chart_id")
            if (v !is String) {
                issues += ValidationIssue(ValidationSeverity.ERROR, "chart_id", "Must be a string.")
            } else if (v.trim().isEmpty()) {
                issues += ValidationIssue(ValidationSeverity.WARNING, "chart_id", "chart_id is empty.")
            }
        }

        // range_settings numeric fields.
        var intervalCount: Int? = null
        if (root.has("range_settings")) {
            val rs = root.opt("range_settings")
            if (rs is JSONObject) {
                val numericFields = listOf(
                    "start_length", "end_length", "interval_length",
                    "interval_count", "zero_range_length", "table_display_range_length",
                )
                for (field in numericFields) {
                    if (rs.has(field)) {
                        val number = numericValue(rs.get(field))
                        if (number != null) {
                            if (field == "interval_count") intervalCount = number.toInt()
                        } else {
                            issues += ValidationIssue(
                                ValidationSeverity.ERROR, "range_settings.$field", "Must be a number.",
                            )
                        }
                    }
                }
            } else {
                issues += ValidationIssue(ValidationSeverity.ERROR, "range_settings", "Must be an object.")
            }
        }

        // units block (string-valued).
        if (root.has("units")) {
            val units = root.opt("units")
            if (units is JSONObject) {
                for (field in listOf("range", "Comeup_linear", "Comeup_angle_unit")) {
                    if (units.has(field) && units.get(field) !is String) {
                        issues += ValidationIssue(ValidationSeverity.ERROR, "units.$field", "Must be a string.")
                    }
                }
            } else {
                issues += ValidationIssue(ValidationSeverity.ERROR, "units", "Must be an object.")
            }
        }

        // ballistic_table.
        var rowCount = 0
        if (root.has("ballistic_table")) {
            val table = root.opt("ballistic_table")
            if (table is JSONArray) {
                rowCount = table.length()
                for (i in 0 until table.length()) {
                    val prefix = "ballistic_table[$i]"
                    val row = table.opt(i) as? JSONObject
                    if (row == null) {
                        issues += ValidationIssue(ValidationSeverity.ERROR, prefix, "Row must be an object.")
                        continue
                    }
                    if (!row.has("range_yards") && !row.has("range_meters")) {
                        issues += ValidationIssue(
                            ValidationSeverity.WARNING, prefix,
                            "Row has neither range_yards nor range_meters.",
                        )
                    }
                    val numericFields = listOf(
                        "range_yards", "range_meters", "Comeup_distance",
                        "comeup_angle", "comeup_mrad_angle", "drift_in",
                    )
                    for (field in numericFields) {
                        if (row.has(field) && numericValue(row.get(field)) == null) {
                            issues += ValidationIssue(
                                ValidationSeverity.ERROR, "$prefix.$field", "Must be a number.",
                            )
                        }
                    }
                }
            } else {
                issues += ValidationIssue(ValidationSeverity.ERROR, "ballistic_table", "Must be an array.")
            }
        }

        // ammunition.bullet numeric fields (optional block).
        (root.opt("ammunition") as? JSONObject)?.let { ammo ->
            (ammo.opt("bullet") as? JSONObject)?.let { bullet ->
                for (field in listOf("weight_grains", "muzzle_velocity_fps")) {
                    if (bullet.has(field) && numericValue(bullet.get(field)) == null) {
                        issues += ValidationIssue(
                            ValidationSeverity.ERROR, "ammunition.bullet.$field", "Must be a number.",
                        )
                    }
                }
            }
        }

        // interval_count vs row count — warn, never block.
        intervalCount?.let {
            if (it != rowCount) {
                issues += ValidationIssue(
                    ValidationSeverity.WARNING, "range_settings.interval_count",
                    "interval_count ($it) does not match the number of rows ($rowCount). " +
                        "Bridger will accept the profile but flag the mismatch.",
                )
            }
        }

        return ValidationResult(issues)
    }

    // MARK: Helpers

    /**
     * Returns the numeric value of [value] if it is a genuine JSON number, or
     * `null` if it is a string, boolean, null, array, or object. `org.json`
     * decodes booleans as [Boolean] (not [Number]), so they are excluded.
     */
    private fun numericValue(value: Any?): Double? = (value as? Number)?.toDouble()
}
