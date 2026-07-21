package com.roonshield.roon

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.util.UUID

/** A Roon Core found on the LAN via SOOD. */
data class DiscoveredCore(
    val host: String,
    val port: Int,
    val coreId: String,
    val name: String,
    val displayVersion: String,
)

/**
 * SOOD is Roon's UDP multicast discovery protocol. We multicast a `Query` for the well-known
 * Roon Core service id; Cores reply (unicast) with a `Response` carrying `unique_id` and
 * `http_port`. The WebSocket API is then reachable at `ws://<host>:<http_port>/api`.
 */
class SoodDiscovery {

    suspend fun discover(timeoutMs: Long = 3000L): List<DiscoveredCore> = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        RoonLog.d(AREA, "discover() start, window=${timeoutMs}ms")
        val results = LinkedHashMap<String, DiscoveredCore>()
        val group = InetAddress.getByName(MULTICAST_GROUP)
        try {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = RECV_POLL_MS
                val query = encodeQuery()
                // Send a couple of times to survive packet loss.
                repeat(2) {
                    runCatching {
                        socket.send(DatagramPacket(query, query.size, group, SOOD_PORT))
                    }.onFailure { RoonLog.w(AREA, "query send failed: ${it.message}") }
                }
                RoonLog.v(AREA, "SOOD query sent to $MULTICAST_GROUP:$SOOD_PORT (${query.size} bytes)")
                var packets = 0
                val deadline = System.currentTimeMillis() + timeoutMs
                val buf = ByteArray(2048)
                while (System.currentTimeMillis() < deadline) {
                    val packet = DatagramPacket(buf, buf.size)
                    try {
                        socket.receive(packet)
                        packets++
                    } catch (_: SocketTimeoutException) {
                        continue
                    } catch (e: Exception) {
                        RoonLog.w(AREA, "receive error: ${e.message}")
                        break
                    }
                    val core = parseResponse(packet)
                    if (core != null) {
                        val isNew = !results.containsKey(core.coreId)
                        results[core.coreId] = core
                        if (isNew) RoonLog.i(AREA, "found Core '${core.name}' @ ${core.host}:${core.port} (Roon ${core.displayVersion})")
                    } else {
                        RoonLog.v(AREA, "ignored packet from ${packet.address?.hostAddress} (${packet.length} bytes)")
                    }
                }
                RoonLog.d(AREA, "discover() done in ${System.currentTimeMillis() - started}ms: ${results.size} core(s), $packets packet(s)")
            }
        } catch (e: Exception) {
            RoonLog.e(AREA, "discover() failed: ${e.message}", e)
        }
        results.values.toList()
    }

    internal fun encodeQuery(): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("SOOD".toByteArray(StandardCharsets.US_ASCII))
        out.write(SOOD_VERSION.toInt())
        out.write('Q'.code)
        writeProp(out, "query_service_id", RoonExtension.SOOD_SERVICE_ID)
        writeProp(out, "_tid", UUID.randomUUID().toString())
        return out.toByteArray()
    }

    private fun writeProp(out: ByteArrayOutputStream, name: String, value: String?) {
        val nameBytes = name.toByteArray(StandardCharsets.UTF_8)
        out.write(nameBytes.size and 0xFF)
        out.write(nameBytes)
        if (value == null) {
            out.write(0xFF); out.write(0xFF)
        } else {
            val v = value.toByteArray(StandardCharsets.UTF_8)
            out.write((v.size ushr 8) and 0xFF)
            out.write(v.size and 0xFF)
            out.write(v)
        }
    }

    private fun parseResponse(packet: DatagramPacket): DiscoveredCore? =
        parseResponseData(packet.data, packet.length, packet.address.hostAddress)

    /** Pure decoder for a SOOD Response packet — unit-testable without a socket. */
    internal fun parseResponseData(data: ByteArray, len: Int, host: String?): DiscoveredCore? {
        if (len < 6) return null
        if (String(data, 0, 4, StandardCharsets.US_ASCII) != "SOOD") return null
        // data[4] = version, data[5] = type
        if (data[5].toInt().toChar() != 'R') return null

        val props = HashMap<String, String>()
        var i = 6
        while (i < len) {
            val nameLen = data[i].toInt() and 0xFF
            i += 1
            if (i + nameLen > len) break
            val name = String(data, i, nameLen, StandardCharsets.UTF_8)
            i += nameLen
            if (i + 2 > len) break
            val valLen = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            i += 2
            if (valLen == 0xFFFF) {
                props[name] = ""
                continue
            }
            if (i + valLen > len) break
            props[name] = String(data, i, valLen, StandardCharsets.UTF_8)
            i += valLen
        }

        if (props["service_id"] != RoonExtension.SOOD_SERVICE_ID) return null
        val port = props["http_port"]?.toIntOrNull() ?: return null
        val coreId = props["unique_id"] ?: return null
        return DiscoveredCore(
            host = host ?: return null,
            port = port,
            coreId = coreId,
            name = props["name"] ?: "Roon Core",
            displayVersion = props["display_version"] ?: "",
        )
    }

    companion object {
        private const val AREA = "SOOD"
        private const val MULTICAST_GROUP = "239.255.90.90"
        private const val SOOD_PORT = 9003
        private const val SOOD_VERSION: Byte = 2
        private const val RECV_POLL_MS = 400
    }
}
