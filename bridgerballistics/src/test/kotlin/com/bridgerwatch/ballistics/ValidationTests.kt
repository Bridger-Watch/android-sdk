package com.bridgerwatch.ballistics

import com.bridgerwatch.ballistics.validation.ProfileValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidationTests {

    @Test
    fun `valid profile passes`() {
        val result = ProfileValidator.validate(TestSupport.sampleProfile())
        assertTrue(result.isValid)
        assertTrue(result.issues.isEmpty())
    }

    @Test
    fun `interval_count mismatch is a warning not an error`() {
        val p = TestSupport.sampleProfile().copy(
            rangeSettings = TestSupport.sampleProfile().rangeSettings.copy(intervalCount = 99),
        )
        val result = ProfileValidator.validate(p)
        assertTrue(result.isValid) // still valid
        assertTrue(result.warnings.any { it.field == "range_settings.interval_count" })
    }

    @Test
    fun `raw json missing required field is rejected`() {
        val json = """{"units":{},"range_settings":{},"ballistic_table":[]}""".toByteArray()
        val result = ProfileValidator.validate(json)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.field == "chart_id" })
    }

    @Test
    fun `raw json non-numeric field is rejected`() {
        val json = """
            {"chart_id":"x","units":{"range":"yards"},
             "range_settings":{"interval_count":"oops"},
             "ballistic_table":[{"range_yards":100,"Comeup_distance":0,"comeup_angle":0}]}
        """.trimIndent().toByteArray()
        val result = ProfileValidator.validate(json)
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.field == "range_settings.interval_count" })
    }

    @Test
    fun `boolean is not accepted as numeric`() {
        val json = """
            {"chart_id":"x","units":{},"range_settings":{"start_length":true},
             "ballistic_table":[]}
        """.trimIndent().toByteArray()
        val result = ProfileValidator.validate(json)
        assertTrue(result.errors.any { it.field == "range_settings.start_length" })
    }

    @Test
    fun `not json is rejected`() {
        val result = ProfileValidator.validate("not json at all".toByteArray())
        assertFalse(result.isValid)
        assertEquals(1, result.errors.size)
    }
}
