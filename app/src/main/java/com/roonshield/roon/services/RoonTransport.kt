package com.roonshield.roon.services

import com.roonshield.roon.RoonExtension
import com.roonshield.roon.RoonJson
import com.roonshield.roon.RoonLog
import com.roonshield.roon.ZoneReducer
import com.roonshield.roon.model.QueueChanged
import com.roonshield.roon.model.QueueItem
import com.roonshield.roon.model.QueueResponse
import com.roonshield.roon.model.Zone
import com.roonshield.roon.model.ZonesChanged
import com.roonshield.roon.model.ZonesResponse
import com.roonshield.roon.moo.Moo
import com.roonshield.roon.moo.MooMessage
import com.roonshield.roon.moo.requestComplete
import kotlinx.serialization.decodeFromString
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.concurrent.atomic.AtomicInteger

/** Roon Transport service (com.roonlabs.transport:2): zones, playback, volume, queue. */
class RoonTransport(private val moo: Moo) {

    fun subscribeZones(): Flow<List<Zone>> = callbackFlow {
        val key = nextSubscriptionKey()
        val zones = LinkedHashMap<String, Zone>()
        val body = buildJsonObject { put("subscription_key", key) }.toString().toByteArray()
        val id = moo.sendRequest("${RoonExtension.SVC_TRANSPORT}/subscribe_zones", body) { msg ->
            when (msg.name) {
                "Subscribed" -> {
                    val resp = decode<ZonesResponse>(msg.bodyText) ?: ZonesResponse()
                    zones.clear()
                    zones.putAll(ZoneReducer.subscribed(resp))
                    RoonLog.i("Zones", "subscribed: ${zones.size} zone(s): ${zones.values.map { it.displayName }}")
                    trySend(zones.values.toList())
                }
                "Changed" -> {
                    val ch = decode<ZonesChanged>(msg.bodyText) ?: return@sendRequest true
                    if (ch.zonesAdded.isNotEmpty() || ch.zonesChanged.isNotEmpty() || ch.zonesRemoved.isNotEmpty()) {
                        RoonLog.v("Zones", "changed: +${ch.zonesAdded.size} ~${ch.zonesChanged.size} -${ch.zonesRemoved.size} seek=${ch.zonesSeekChanged.size}")
                    }
                    ZoneReducer.applyChanged(zones, ch)
                    trySend(zones.values.toList())
                }
            }
            msg.verb != MooMessage.VERB_COMPLETE
        }
        awaitClose {
            runCatching {
                val unsub = buildJsonObject { put("subscription_key", key) }.toString().toByteArray()
                moo.sendRequest("${RoonExtension.SVC_TRANSPORT}/unsubscribe_zones", unsub) { false }
            }
            moo.cancel(id)
        }
    }

    fun subscribeQueue(zoneOrOutputId: String, maxItems: Int = 100): Flow<List<QueueItem>> = callbackFlow {
        val key = nextSubscriptionKey()
        val items = ArrayList<QueueItem>()
        val body = buildJsonObject {
            put("zone_or_output_id", zoneOrOutputId)
            put("max_item_count", maxItems)
            put("subscription_key", key)
        }.toString().toByteArray()
        val id = moo.sendRequest("${RoonExtension.SVC_TRANSPORT}/subscribe_queue", body) { msg ->
            when (msg.name) {
                "Subscribed" -> {
                    val resp = decode<QueueResponse>(msg.bodyText)
                    items.clear()
                    resp?.items?.let { items.addAll(it) }
                    trySend(items.toList())
                }
                "Changed" -> {
                    val ch = decode<QueueChanged>(msg.bodyText) ?: return@sendRequest true
                    for (c in ch.changes) {
                        when (c.operation) {
                            "insert" -> items.addAll((c.index ?: items.size).coerceIn(0, items.size), c.items)
                            "remove" -> {
                                val from = (c.index ?: 0).coerceIn(0, items.size)
                                val to = (from + (c.count ?: 0)).coerceIn(from, items.size)
                                if (to > from) items.subList(from, to).clear()
                            }
                        }
                    }
                    trySend(items.toList())
                }
            }
            msg.verb != MooMessage.VERB_COMPLETE
        }
        awaitClose {
            runCatching {
                val unsub = buildJsonObject { put("subscription_key", key) }.toString().toByteArray()
                moo.sendRequest("${RoonExtension.SVC_TRANSPORT}/unsubscribe_queue", unsub) { false }
            }
            moo.cancel(id)
        }
    }

    suspend fun control(zoneOrOutputId: String, control: String) {
        val body = buildJsonObject {
            put("zone_or_output_id", zoneOrOutputId)
            put("control", control)
        }.toString().toByteArray()
        moo.requestComplete("${RoonExtension.SVC_TRANSPORT}/control", body)
    }

    suspend fun seek(zoneOrOutputId: String, seconds: Int, how: String = "absolute") {
        val body = buildJsonObject {
            put("zone_or_output_id", zoneOrOutputId)
            put("how", how)
            put("seconds", seconds)
        }.toString().toByteArray()
        moo.requestComplete("${RoonExtension.SVC_TRANSPORT}/seek", body)
    }

    suspend fun changeVolume(outputId: String, value: Int, how: String = "absolute") {
        val body = buildJsonObject {
            put("output_id", outputId)
            put("how", how)
            put("value", value)
        }.toString().toByteArray()
        moo.requestComplete("${RoonExtension.SVC_TRANSPORT}/change_volume", body)
    }

    suspend fun mute(outputId: String, mute: Boolean) {
        val body = buildJsonObject {
            put("output_id", outputId)
            put("how", if (mute) "mute" else "unmute")
        }.toString().toByteArray()
        moo.requestComplete("${RoonExtension.SVC_TRANSPORT}/mute", body)
    }

    suspend fun changeSettings(zoneOrOutputId: String, shuffle: Boolean? = null, autoRadio: Boolean? = null, loop: String? = null) {
        val body = buildJsonObject {
            put("zone_or_output_id", zoneOrOutputId)
            shuffle?.let { put("shuffle", it) }
            autoRadio?.let { put("auto_radio", it) }
            loop?.let { put("loop", it) }
        }.toString().toByteArray()
        moo.requestComplete("${RoonExtension.SVC_TRANSPORT}/change_settings", body)
    }

    suspend fun playFromHere(zoneOrOutputId: String, queueItemId: Long) {
        val body = buildJsonObject {
            put("zone_or_output_id", zoneOrOutputId)
            put("queue_item_id", queueItemId)
        }.toString().toByteArray()
        moo.requestComplete("${RoonExtension.SVC_TRANSPORT}/play_from_here", body)
    }

    private inline fun <reified T> decode(text: String?): T? =
        text?.let { runCatching { RoonJson.decodeFromString<T>(it) }.getOrNull() }

    companion object {
        private val subKey = AtomicInteger(1)
        private fun nextSubscriptionKey(): Int = subKey.getAndIncrement()
    }
}
