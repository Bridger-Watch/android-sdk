package com.bridgerwatch.ballistics.model

/**
 * The rifle metadata, emitted under `weapon_system.rifle`.
 *
 * Optional in a profile — Bridger does not require `weapon_system` — but
 * recommended so the profile is identifiable on the watch.
 */
data class Rifle(
    /** Caliber string, e.g. `"300 PRC"` (`caliber`). */
    val caliber: String,
    /** Display name, e.g. `"Weatherby 307"` (`Name` — capitalized on the wire). */
    val name: String,
) {
    internal fun encode(): LinkedHashMap<String, Any?> = linkedMapOf(
        "caliber" to caliber,
        "Name" to name,
    )
}

/**
 * The bullet / load metadata, emitted under `ammunition.bullet`.
 *
 * Optional in a profile — Bridger does not require `ammunition` — but
 * recommended.
 */
data class Bullet(
    /** Bullet / ammunition manufacturer (`manufacturer`). */
    val manufacturer: String,
    /** Bullet model, e.g. `"ELD-M"` (`model`). */
    val model: String,
    /** Bullet weight in grains (`weight_grains`). */
    val weightGrains: Double,
    /** Muzzle velocity in feet per second (`muzzle_velocity_fps`). */
    val muzzleVelocityFPS: Double,
) {
    internal fun encode(): LinkedHashMap<String, Any?> = linkedMapOf(
        "manufacturer" to manufacturer,
        "model" to model,
        "weight_grains" to weightGrains,
        "muzzle_velocity_fps" to muzzleVelocityFPS,
    )
}
