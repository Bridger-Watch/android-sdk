package com.bridgerwatch.ballistics

import com.bridgerwatch.ballistics.transfer.PayloadCodec
import com.bridgerwatch.ballistics.transfer.PayloadEncoding
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

class PayloadCodecTests {

    private val json = TestSupport.sampleProfile().toJson(false)

    @Test
    fun `rawJSON is the json text`() {
        val out = PayloadCodec.encode(json, PayloadEncoding.RAW_JSON)
        assertEquals(json.toString(Charsets.UTF_8), out)
    }

    @Test
    fun `base64 round-trips`() {
        val out = PayloadCodec.encode(json, PayloadEncoding.BASE64_JSON)
        assertArrayEquals(json, Base64.getDecoder().decode(out))
    }

    @Test
    fun `gzip payload decodes back to the json (mirrors the receiver)`() {
        val out = PayloadCodec.encode(json, PayloadEncoding.GZIP_BASE64)
        val gz = Base64.getDecoder().decode(out)
        assertEquals(0x1f, gz[0].toInt() and 0xff) // gzip magic
        assertEquals(0x8b, gz[1].toInt() and 0xff)
        val inflated = GZIPInputStream(ByteArrayInputStream(gz)).readBytes()
        assertArrayEquals(json, inflated)
    }

    @Test
    fun `zip payload has sizes in the local header (no data descriptor) and decodes`() {
        val out = PayloadCodec.encode(json, PayloadEncoding.ZIP_BASE64)
        val zipBytes = Base64.getDecoder().decode(out)
        assertEquals(0x50, zipBytes[0].toInt() and 0xff) // PK
        assertEquals(0x4b, zipBytes[1].toInt() and 0xff)
        // General-purpose flags are bytes 6-7 of the local header; bit 3 (data
        // descriptor) must be clear for the Bridger iOS receiver to accept it.
        val flags = (zipBytes[6].toInt() and 0xff) or ((zipBytes[7].toInt() and 0xff) shl 8)
        assertEquals(0, flags and 0x08)

        val zis = ZipInputStream(ByteArrayInputStream(zipBytes))
        val entry = zis.nextEntry
        assertTrue(entry != null)
        assertArrayEquals(json, zis.readBytes())
    }
}
