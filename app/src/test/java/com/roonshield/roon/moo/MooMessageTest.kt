package com.roonshield.roon.moo

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MooMessageTest {

    @Test
    fun requestWithBody_roundTrips() {
        val body = """{"zone_or_output_id":"z1","control":"playpause"}""".toByteArray()
        val encoded = MooMessage.request("com.roonlabs.transport:2/control", 7, body).encode()

        val parsed = MooMessage.parse(encoded)
        assertEquals("REQUEST", parsed.verb)
        assertEquals("com.roonlabs.transport:2/control", parsed.name)
        assertEquals(7, parsed.requestId)
        assertEquals("application/json", parsed.contentType)
        assertArrayEquals(body, parsed.body)
    }

    @Test
    fun responseWithoutBody_roundTrips() {
        val encoded = MooMessage.response(MooMessage.VERB_COMPLETE, "Success", 3, null).encode()

        val parsed = MooMessage.parse(encoded)
        assertEquals("COMPLETE", parsed.verb)
        assertEquals("Success", parsed.name)
        assertEquals(3, parsed.requestId)
        assertNull(parsed.body)
    }

    @Test
    fun encode_producesExactWireFormat() {
        val body = "{}".toByteArray()
        val bytes = MooMessage.request("svc/op", 1, body).encode()
        val expected = "MOO/1 REQUEST svc/op\n" +
            "Request-Id: 1\n" +
            "Content-Length: 2\n" +
            "Content-Type: application/json\n" +
            "\n" +
            "{}"
        assertEquals(expected, String(bytes, Charsets.UTF_8))
    }

    @Test
    fun parse_respectsContentLengthFraming() {
        // Body declared as 12 bytes; parser must slice exactly that many.
        val json = """{"zones":[]}"""
        assertEquals(12, json.toByteArray().size)
        val raw = "MOO/1 CONTINUE Changed\n" +
            "Request-Id: 42\n" +
            "Content-Length: 12\n" +
            "Content-Type: application/json\n" +
            "\n" +
            json

        val parsed = MooMessage.parse(raw.toByteArray())
        assertEquals("CONTINUE", parsed.verb)
        assertEquals("Changed", parsed.name)
        assertEquals(42, parsed.requestId)
        assertEquals(json, parsed.bodyText)
    }

    @Test
    fun parse_handlesBinaryImageBody() {
        val imageBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x00, 0x10, 0x7F, 0x00)
        val header = "MOO/1 COMPLETE Success\nRequest-Id: 9\nContent-Length: ${imageBytes.size}\nContent-Type: image/jpeg\n\n"
        val raw = header.toByteArray() + imageBytes

        val parsed = MooMessage.parse(raw)
        assertEquals("image/jpeg", parsed.contentType)
        assertArrayEquals(imageBytes, parsed.body)
    }
}
