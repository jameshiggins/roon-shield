package com.roonshield.roon

import com.roonshield.roon.model.Zone
import com.roonshield.roon.model.ZonesChanged
import com.roonshield.roon.model.ZonesResponse

/**
 * Pure reducer for the Transport zones subscription. The Core sends one "Subscribed" snapshot then
 * incremental "Changed" deltas (added / changed / removed / seek-only). Keeping this logic pure and
 * side-effect-free makes the trickiest part of the state machine unit-testable without a socket.
 */
object ZoneReducer {

    fun subscribed(resp: ZonesResponse): LinkedHashMap<String, Zone> {
        val map = LinkedHashMap<String, Zone>()
        resp.zones.forEach { map[it.zoneId] = it }
        return map
    }

    /** Applies a delta in-place and returns the same map for chaining/convenience. */
    fun applyChanged(current: MutableMap<String, Zone>, ch: ZonesChanged): MutableMap<String, Zone> {
        ch.zonesRemoved.forEach { current.remove(it) }
        ch.zonesAdded.forEach { current[it.zoneId] = it }
        ch.zonesChanged.forEach { current[it.zoneId] = it }
        ch.zonesSeekChanged.forEach { sc ->
            val z = current[sc.zoneId] ?: return@forEach
            val np = z.nowPlaying?.copy(seekPosition = sc.seekPosition ?: z.nowPlaying.seekPosition)
            current[sc.zoneId] = z.copy(nowPlaying = np)
        }
        return current
    }
}
