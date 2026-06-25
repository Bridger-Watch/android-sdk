//
//  HornadyIntegrationExample.kt
//  BridgerAndroidBallistics — REFERENCE ONLY
//
//  This file is NOT part of the library module and is NOT compiled by Gradle.
//  It's a copy-paste reference showing how a partner Android app (using
//  "Hornady" as a stand-in) would integrate BridgerAndroidBallistics. Drop the
//  relevant pieces into your own app and adjust to your data model.
//
//  Prerequisite: add the library (see README.md). The required <queries> entry
//  for Android 11+ package visibility is contributed by the library manifest, so
//  there is no manual manifest step for the deep-link path.
//

package com.example.integration

import android.content.Context
import android.widget.Toast
import com.bridgerwatch.ballistics.Bridger
import com.bridgerwatch.ballistics.model.BallisticProfile
import com.bridgerwatch.ballistics.model.BallisticRow
import com.bridgerwatch.ballistics.model.Bullet
import com.bridgerwatch.ballistics.model.RangeSettings
import com.bridgerwatch.ballistics.model.Rifle
import com.bridgerwatch.ballistics.model.Units
import com.bridgerwatch.ballistics.support.BridgerError

// 1. Your app's existing model (example).
data class HornadyPoint(
    val rangeYards: Double,
    val dropInches: Double,
    val elevationMOA: Double,
    val windDriftInches: Double?,
)

data class HornadySolution(
    val name: String,
    val caliber: String,
    val rifle: String,
    val bulletModel: String,
    val bulletGrains: Double,
    val muzzleVelocityFPS: Double,
    val zeroYards: Int,
    val points: List<HornadyPoint>,
)

// 2. Map it to a BallisticProfile (do this once).
fun HornadySolution.toBridgerProfile(): BallisticProfile {
    val rows = points.map {
        BallisticRow(
            range = it.rangeYards,
            comeupDistance = it.dropInches,
            comeupAngle = it.elevationMOA,
            comeupMRADAngle = null,        // include if you compute MRAD
            driftInches = it.windDriftInches,
        )
    }
    val interval = if (points.size > 1) (points[1].rangeYards - points[0].rangeYards).toInt() else 1

    return BallisticProfile(
        chartID = name,
        units = Units(),                   // yards / inches / MOA defaults
        rangeSettings = RangeSettings.fromRowCount(
            start = points.firstOrNull()?.rangeYards?.toInt() ?: 0,
            end = points.lastOrNull()?.rangeYards?.toInt() ?: 0,
            interval = interval,
            rowCount = rows.size,
            zeroRange = zeroYards,
            tableDisplayRange = zeroYards,
        ),
        rifle = Rifle(caliber = caliber, name = rifle),
        bullet = Bullet(
            manufacturer = "Hornady",
            model = bulletModel,
            weightGrains = bulletGrains,
            muzzleVelocityFPS = muzzleVelocityFPS,
        ),
        table = rows,
    )
}

// 3. Send it.
fun sendToBridger(context: Context, solution: HornadySolution) {
    val profile = solution.toBridgerProfile()

    // Surface blocking problems in your own UI first (open() also validates).
    val validation = Bridger.validate(profile)
    if (!validation.isValid) {
        Toast.makeText(context, validation.errors.joinToString("\n") { it.message }, Toast.LENGTH_LONG).show()
        return
    }

    try {
        if (Bridger.isInstalled(context)) {
            // Path 1 — launch Bridger directly.
            Bridger.open(context, profile, slot = com.bridgerwatch.ballistics.transfer.BridgerSlot.SLOT0)
        } else {
            // Path 2 — share-sheet fallback when Bridger can't be opened directly.
            context.startActivity(Bridger.shareIntent(context, profile))
        }
    } catch (e: BridgerError) {
        Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
    }
}

// 4. Or: you already have Bridger-format JSON.
fun sendExistingJson(context: Context, jsonBytes: ByteArray) {
    if (!Bridger.validate(jsonBytes).isValid) return
    Bridger.open(context, jsonBytes, slot = com.bridgerwatch.ballistics.transfer.BridgerSlot.SLOT0)
}
