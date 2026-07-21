package com.roonshield.roon

import com.roonshield.roon.model.NowPlaying
import com.roonshield.roon.model.SeekChanged
import com.roonshield.roon.model.Zone
import com.roonshield.roon.model.ZonesChanged
import com.roonshield.roon.model.ZonesResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoneReducerTest {

    private fun zone(id: String, name: String, seek: Int = 0, len: Int = 100) =
        Zone(zoneId = id, displayName = name, nowPlaying = NowPlaying(seekPosition = seek, length = len))

    @Test
    fun subscribed_buildsKeyedMapPreservingOrder() {
        val map = ZoneReducer.subscribed(ZonesResponse(listOf(zone("a", "A"), zone("b", "B"))))
        assertEquals(listOf("a", "b"), map.keys.toList())
        assertEquals("A", map["a"]?.displayName)
    }

    @Test
    fun applyChanged_addsChangesAndRemoves() {
        val current = ZoneReducer.subscribed(ZonesResponse(listOf(zone("a", "A"), zone("b", "B"))))
        val delta = ZonesChanged(
            zonesChanged = listOf(zone("a", "A-renamed")),
            zonesAdded = listOf(zone("c", "C")),
            zonesRemoved = listOf("b"),
        )
        ZoneReducer.applyChanged(current, delta)

        assertEquals("A-renamed", current["a"]?.displayName)
        assertEquals("C", current["c"]?.displayName)
        assertFalse(current.containsKey("b"))
        assertEquals(2, current.size)
    }

    @Test
    fun applyChanged_seekOnlyUpdatesPositionWithoutDroppingTrack() {
        val current = ZoneReducer.subscribed(ZonesResponse(listOf(zone("a", "A", seek = 10, len = 240))))
        ZoneReducer.applyChanged(current, ZonesChanged(zonesSeekChanged = listOf(SeekChanged("a", seekPosition = 55))))

        assertEquals(55, current["a"]?.nowPlaying?.seekPosition)
        assertEquals(240, current["a"]?.nowPlaying?.length) // length preserved
    }

    @Test
    fun applyChanged_seekForUnknownZoneIsIgnored() {
        val current = ZoneReducer.subscribed(ZonesResponse(listOf(zone("a", "A"))))
        ZoneReducer.applyChanged(current, ZonesChanged(zonesSeekChanged = listOf(SeekChanged("ghost", seekPosition = 5))))
        assertEquals(1, current.size)
        assertNull(current["ghost"])
    }

    @Test
    fun applyChanged_emptyDeltaIsNoOp() {
        val current = ZoneReducer.subscribed(ZonesResponse(listOf(zone("a", "A"))))
        ZoneReducer.applyChanged(current, ZonesChanged())
        assertTrue(current.containsKey("a"))
        assertEquals(1, current.size)
    }
}
