package com.bridgerwatch.ballistics

import com.bridgerwatch.ballistics.model.BallisticProfile
import com.bridgerwatch.ballistics.model.BallisticRow
import com.bridgerwatch.ballistics.model.Bullet
import com.bridgerwatch.ballistics.model.RangeSettings
import com.bridgerwatch.ballistics.model.Rifle
import com.bridgerwatch.ballistics.model.Units

/** Shared fixtures for the unit tests. */
object TestSupport {

    fun sampleProfile(): BallisticProfile {
        val rows = listOf(
            BallisticRow(100.0, 0.0, 0.0, 0.0, 0.0),
            BallisticRow(200.0, -6.1, -2.91, -0.85, 0.7),
            BallisticRow(300.0, -22.4, -7.13, -2.07, 1.9),
            BallisticRow(400.0, -50.9, -12.15, -3.53, 3.6),
            BallisticRow(500.0, -94.0, -17.95, -5.22, 6.0),
        )
        return BallisticProfile(
            chartID = "Weatherby 307 · 300 PRC · 225 ELD-M",
            units = Units(),
            rangeSettings = RangeSettings.fromRowCount(
                start = 100, end = 500, interval = 100, rowCount = rows.size,
                zeroRange = 100, tableDisplayRange = 100,
            ),
            rifle = Rifle(caliber = "300 PRC", name = "Weatherby 307"),
            bullet = Bullet("Hornady", "ELD-M", 225.0, 2810.0),
            table = rows,
        )
    }
}
