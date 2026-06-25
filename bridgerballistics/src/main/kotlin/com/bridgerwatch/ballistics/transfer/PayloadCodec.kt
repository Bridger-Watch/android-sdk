package com.bridgerwatch.ballistics.transfer

import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.CRC32
import java.util.zip.Deflater

/**
 * Produces the `data` query value for the `bridgerwatch://import-ballistics`
 * URL in any of the four formats the Bridger app accepts.
 *
 * The gzip and zip writers mirror the byte layout Bridger's receiver expects:
 * a standard gzip member (10-byte header + raw DEFLATE + CRC32 + ISIZE) and a
 * single-entry zip whose sizes live in the local header (no trailing data
 * descriptor — the Bridger iOS receiver rejects streamed/data-descriptor zips).
 *
 * Uses only the JDK (`java.util.zip`, `java.util.Base64`) — no third-party deps.
 */
internal object PayloadCodec {

    /**
     * Encodes [jsonData] for transport using [encoding].
     * @return the string to pass as the `data` query value (before percent-encoding).
     */
    fun encode(
        jsonData: ByteArray,
        encoding: PayloadEncoding,
        zipEntryName: String = "bdata0.json",
    ): String = when (encoding) {
        PayloadEncoding.RAW_JSON -> jsonData.toString(Charsets.UTF_8)
        PayloadEncoding.BASE64_JSON -> base64(jsonData)
        PayloadEncoding.GZIP_BASE64 -> base64(gzip(jsonData))
        PayloadEncoding.ZIP_BASE64 -> base64(zip(jsonData, zipEntryName))
    }

    private fun base64(data: ByteArray): String = Base64.getEncoder().encodeToString(data)

    // MARK: - gzip (Format 3)

    /** Wraps [json] in a standard gzip member. */
    fun gzip(json: ByteArray): ByteArray {
        val deflated = rawDeflate(json)
        val out = ByteArrayOutputStream()
        // Header: ID1 ID2 CM FLG MTIME(4) XFL OS(0xff = unknown)
        out.write(byteArrayOf(0x1f, 0x8b.toByte(), 0x08, 0x00, 0, 0, 0, 0, 0x00, 0xff.toByte()))
        out.write(deflated)
        writeLE32(out, crc32(json))                       // CRC-32 of the uncompressed data
        writeLE32(out, json.size.toLong() and 0xffffffffL) // ISIZE (mod 2^32)
        return out.toByteArray()
    }

    // MARK: - zip (Format 4)

    /** Wraps [json] in a single-entry zip archive (sizes in the local header). */
    fun zip(json: ByteArray, entryName: String): ByteArray {
        val name = entryName.toByteArray(Charsets.UTF_8)
        val comp = rawDeflate(json)
        val crc = crc32(json)
        val out = ByteArrayOutputStream()

        // Local file header
        out.write(byteArrayOf(0x50, 0x4b, 0x03, 0x04))
        writeLE16(out, 20)                 // version needed
        writeLE16(out, 0)                  // flags
        writeLE16(out, 8)                  // method: deflate
        writeLE16(out, 0)                  // mod time
        writeLE16(out, 0)                  // mod date
        writeLE32(out, crc)
        writeLE32(out, comp.size.toLong())
        writeLE32(out, json.size.toLong())
        writeLE16(out, name.size)          // file name length
        writeLE16(out, 0)                  // extra field length
        out.write(name)
        out.write(comp)

        // Central directory
        val cdOffset = out.size()
        out.write(byteArrayOf(0x50, 0x4b, 0x01, 0x02))
        writeLE16(out, 20)                 // version made by
        writeLE16(out, 20)                 // version needed
        writeLE16(out, 0)                  // flags
        writeLE16(out, 8)                  // method
        writeLE16(out, 0)                  // mod time
        writeLE16(out, 0)                  // mod date
        writeLE32(out, crc)
        writeLE32(out, comp.size.toLong())
        writeLE32(out, json.size.toLong())
        writeLE16(out, name.size)          // file name length
        writeLE16(out, 0)                  // extra field length
        writeLE16(out, 0)                  // comment length
        writeLE16(out, 0)                  // disk number start
        writeLE16(out, 0)                  // internal attrs
        writeLE32(out, 0)                  // external attrs
        writeLE32(out, 0)                  // local-header offset
        out.write(name)
        val cdSize = out.size() - cdOffset

        // End of central directory
        out.write(byteArrayOf(0x50, 0x4b, 0x05, 0x06))
        writeLE16(out, 0)                  // disk number
        writeLE16(out, 0)                  // disk with central dir
        writeLE16(out, 1)                  // entries on this disk
        writeLE16(out, 1)                  // total entries
        writeLE32(out, cdSize.toLong())
        writeLE32(out, cdOffset.toLong())
        writeLE16(out, 0)                  // comment length
        return out.toByteArray()
    }

    // MARK: - DEFLATE

    /**
     * Raw DEFLATE (RFC 1951) of [src]. `Deflater(nowrap = true)` emits a raw
     * stream with no zlib header / adler trailer — exactly what both gzip and
     * zip wrap, and what the Bridger receiver inflates.
     */
    fun rawDeflate(src: ByteArray): ByteArray {
        if (src.isEmpty()) return ByteArray(0)
        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, true)
        deflater.setInput(src)
        deflater.finish()
        val out = ByteArrayOutputStream(src.size)
        val buf = ByteArray(64 * 1024)
        while (!deflater.finished()) {
            val n = deflater.deflate(buf)
            out.write(buf, 0, n)
        }
        deflater.end()
        return out.toByteArray()
    }

    // MARK: - CRC-32

    fun crc32(data: ByteArray): Long {
        val crc = CRC32()
        crc.update(data)
        return crc.value
    }

    // MARK: - Little-endian helpers

    private fun writeLE16(out: ByteArrayOutputStream, value: Int) {
        out.write(value and 0xff)
        out.write((value ushr 8) and 0xff)
    }

    private fun writeLE32(out: ByteArrayOutputStream, value: Long) {
        out.write((value and 0xff).toInt())
        out.write(((value ushr 8) and 0xff).toInt())
        out.write(((value ushr 16) and 0xff).toInt())
        out.write(((value ushr 24) and 0xff).toInt())
    }
}
