package com.bridgerwatch.ballistics

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EncodingTests {

    @Test
    fun `emits exact wire keys`() {
        val json = TestSupport.sampleProfile().toJson(false).toString(Charsets.UTF_8)
        val root = JSONObject(json)

        assertTrue(root.has("chart_id"))
        assertTrue(root.has("units"))
        assertTrue(root.has("range_settings"))
        assertTrue(root.has("weapon_system"))
        assertTrue(root.has("ammunition"))
        assertTrue(root.has("ballistic_table"))

        val units = root.getJSONObject("units")
        assertEquals("yards", units.getString("range"))
        assertEquals("inches", units.getString("Comeup_linear"))
        assertEquals("MOA", units.getString("Comeup_angle_unit"))

        val rs = root.getJSONObject("range_settings")
        assertTrue(rs.has("start_length"))
        assertTrue(rs.has("end_length"))
        assertTrue(rs.has("interval_length"))
        assertTrue(rs.has("interval_count"))
        assertTrue(rs.has("zero_range_length"))
        assertTrue(rs.has("table_display_range_length"))

        val rifle = root.getJSONObject("weapon_system").getJSONObject("rifle")
        assertEquals("300 PRC", rifle.getString("caliber"))
        assertEquals("Weatherby 307", rifle.getString("Name")) // capital N

        val bullet = root.getJSONObject("ammunition").getJSONObject("bullet")
        assertEquals("Hornady", bullet.getString("manufacturer"))
        assertEquals(225, bullet.getInt("weight_grains"))
        assertEquals(2810, bullet.getInt("muzzle_velocity_fps"))

        val row0 = root.getJSONArray("ballistic_table").getJSONObject(0)
        assertTrue(row0.has("range_yards"))
        assertTrue(row0.has("Comeup_distance")) // capital C
        assertTrue(row0.has("comeup_angle"))    // lowercase c
    }

    @Test
    fun `canonical numbers have no trailing decimal`() {
        val json = TestSupport.sampleProfile().toJson(false).toString(Charsets.UTF_8)
        // 225.0 grains and 0.0 come-up should serialize as 225 and 0, not 225.0 / 0.0.
        assertTrue(json.contains("\"weight_grains\":225"))
        assertFalse(json.contains("225.0"))
        assertTrue(json.contains("\"Comeup_distance\":0,") || json.contains("\"Comeup_distance\":0}"))
    }

    @Test
    fun `meters profile uses range_meters row key`() {
        val p = TestSupport.sampleProfile().copy(
            units = com.bridgerwatch.ballistics.model.Units(
                range = com.bridgerwatch.ballistics.model.RangeUnit.METERS,
            ),
        )
        val row0 = JSONObject(p.toJson(false).toString(Charsets.UTF_8))
            .getJSONArray("ballistic_table").getJSONObject(0)
        assertTrue(row0.has("range_meters"))
        assertFalse(row0.has("range_yards"))
    }

    @Test
    fun `round-trips through fromJson`() {
        val original = TestSupport.sampleProfile()
        val decoded = com.bridgerwatch.ballistics.model.BallisticProfile.fromJson(original.toJson(false))
        assertEquals(original.chartID, decoded.chartID)
        assertEquals(original.rifle, decoded.rifle)
        assertEquals(original.bullet, decoded.bullet)
        assertEquals(original.table.size, decoded.table.size)
        assertEquals(original.table.first(), decoded.table.first())
        assertEquals(original.rangeSettings, decoded.rangeSettings)
    }
}
