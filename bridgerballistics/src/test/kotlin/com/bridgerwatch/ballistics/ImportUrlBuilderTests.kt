package com.bridgerwatch.ballistics

import com.bridgerwatch.ballistics.transfer.BridgerSlot
import com.bridgerwatch.ballistics.transfer.ImportUrlBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportUrlBuilderTests {

    @Test
    fun `pinned slot adds slot and filename`() {
        val url = ImportUrlBuilder.url("PAYLOAD", BridgerSlot.SLOT2, "bdata2.json")
        assertTrue(url.startsWith("bridgerwatch://import-ballistics?"))
        assertTrue(url.contains("slot=2"))
        assertTrue(url.contains("filename=bdata2.json"))
        assertTrue(url.contains("data=PAYLOAD"))
    }

    @Test
    fun `auto slot omits slot and filename`() {
        val url = ImportUrlBuilder.url("PAYLOAD", BridgerSlot.AUTO, ImportUrlBuilder.defaultFilename(BridgerSlot.AUTO))
        assertFalse(url.contains("slot="))
        assertFalse(url.contains("filename="))
        assertTrue(url.contains("data=PAYLOAD"))
    }

    @Test
    fun `base64 special chars are strictly escaped`() {
        // A base64 value containing +, /, = must survive form-decoding intact.
        val url = ImportUrlBuilder.url("ab+/cd==", BridgerSlot.SLOT0, "bdata0.json")
        assertTrue(url.contains("data=ab%2B%2Fcd%3D%3D"))
        assertFalse(url.contains("+")) // no bare plus that a form-decoder would turn into a space
    }

    @Test
    fun `defaultFilename follows slot index`() {
        assertEquals("bdata0.json", ImportUrlBuilder.defaultFilename(BridgerSlot.SLOT0))
        assertEquals("bdata3.json", ImportUrlBuilder.defaultFilename(BridgerSlot.SLOT3))
        assertEquals(null, ImportUrlBuilder.defaultFilename(BridgerSlot.AUTO))
    }
}
