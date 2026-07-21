package com.roonshield.roon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

class SoodDiscoveryTest {

    private val sood = SoodDiscovery()

    private fun buildResponse(props: Map<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("SOOD".toByteArray(StandardCharsets.US_ASCII))
        out.write(2)
        out.write('R'.code)
        for ((k, v) in props) {
            val kb = k.toByteArray()
            out.write(kb.size and 0xFF)
            out.write(kb)
            val vb = v.toByteArray()
            out.write((vb.size ushr 8) and 0xFF)
            out.write(vb.size and 0xFF)
            out.write(vb)
        }
        return out.toByteArray()
    }

    @Test
    fun parsesValidCoreResponse() {
        val bytes = buildResponse(
            linkedMapOf(
                "service_id" to RoonExtension.SOOD_SERVICE_ID,
                "unique_id" to "core-abc",
                "http_port" to "9330",
                "name" to "Living Room Core",
                "display_version" to "2.0 (build 1)",
            ),
        )
        val core = sood.parseResponseData(bytes, bytes.size, "192.168.1.50")
        assertEquals("core-abc", core?.coreId)
        assertEquals(9330, core?.port)
        assertEquals("192.168.1.50", core?.host)
        assertEquals("Living Room Core", core?.name)
    }

    @Test
    fun ignoresResponseWithWrongServiceId() {
        val bytes = buildResponse(
            linkedMapOf(
                "service_id" to "not-roon",
                "unique_id" to "x",
                "http_port" to "9330",
            ),
        )
        assertNull(sood.parseResponseData(bytes, bytes.size, "10.0.0.1"))
    }

    @Test
    fun ignoresNonResponseTypeAndJunk() {
        assertNull(sood.parseResponseData("SOOD".toByteArray(), 4, "10.0.0.1"))
        assertNull(sood.parseResponseData("NOPE12".toByteArray(), 6, "10.0.0.1"))
    }

    @Test
    fun queryPacketHasCorrectMagicAndServiceId() {
        val q = sood.encodeQuery()
        assertEquals("SOOD", String(q, 0, 4, StandardCharsets.US_ASCII))
        assertEquals('Q'.code.toByte(), q[5])
        val text = String(q, StandardCharsets.UTF_8)
        assertTrue(text.contains("query_service_id"))
        assertTrue(text.contains(RoonExtension.SOOD_SERVICE_ID))
    }
}
