package com.roonshield.roon

import com.roonshield.roon.model.BrowseResult
import com.roonshield.roon.model.LoadResult
import com.roonshield.roon.model.QueueChanged
import com.roonshield.roon.model.QueueResponse
import com.roonshield.roon.model.ZonesChanged
import com.roonshield.roon.model.ZonesResponse
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies the snake_case JSON from Roon maps onto the camelCase models via [RoonJson]. */
class RoonModelsTest {

    @Test
    fun parsesZonesSubscribedPayload() {
        val json = """
            {"zones":[{
              "zone_id":"1601","display_name":"Living Room","state":"playing",
              "is_next_allowed":true,"is_previous_allowed":true,"is_pause_allowed":true,
              "is_play_allowed":false,"is_seek_allowed":true,"queue_items_remaining":42,
              "settings":{"loop":"loop","shuffle":false,"auto_radio":true},
              "now_playing":{"seek_position":12,"length":240,"image_key":"abc",
                "three_line":{"line1":"Roads","line2":"Portishead","line3":"Dummy"},
                "two_line":{"line1":"Roads","line2":"Portishead"}},
              "outputs":[{"output_id":"1701","display_name":"Living Room",
                "volume":{"type":"number","min":0,"max":100,"value":42,"step":1,"is_muted":false}}]
            }]}
        """.trimIndent()

        val resp = RoonJson.decodeFromString<ZonesResponse>(json)
        val z = resp.zones.single()
        assertEquals("1601", z.zoneId)
        assertEquals("Living Room", z.displayName)
        assertTrue(z.isPlaying)
        assertTrue(z.isNextAllowed)
        assertFalse(z.isPlayAllowed)
        assertEquals(42, z.queueItemsRemaining)
        assertEquals("loop", z.settings?.loop)
        assertTrue(z.settings?.autoRadio == true)
        assertEquals("Roads", z.nowPlaying?.threeLine?.line1)
        assertEquals("Dummy", z.nowPlaying?.threeLine?.line3)
        assertEquals(12, z.nowPlaying?.seekPosition)
        assertEquals(240, z.nowPlaying?.length)
        assertEquals("1701", z.primaryOutputId)
        assertEquals(42.0, z.outputs.first().volume?.value!!, 0.001)
        assertFalse(z.outputs.first().volume?.isMuted!!)
    }

    @Test
    fun parsesZonesChangedWithSeek() {
        val json = """
            {"zones_changed":[{"zone_id":"z1","display_name":"LR","state":"paused","outputs":[]}],
             "zones_seek_changed":[{"zone_id":"z1","seek_position":33,"queue_time_remaining":100}]}
        """.trimIndent()
        val ch = RoonJson.decodeFromString<ZonesChanged>(json)
        assertEquals("paused", ch.zonesChanged.single().state)
        assertEquals(33, ch.zonesSeekChanged.single().seekPosition)
    }

    @Test
    fun parsesQueueResponseAndChanges() {
        val q = RoonJson.decodeFromString<QueueResponse>(
            """{"items":[{"queue_item_id":123,"length":200,"image_key":"k","two_line":{"line1":"T","line2":"A"}}]}""",
        )
        assertEquals(123L, q.items.single().queueItemId)
        assertEquals("T", q.items.single().twoLine?.line1)

        val ch = RoonJson.decodeFromString<QueueChanged>(
            """{"changes":[{"operation":"remove","index":0,"count":2},{"operation":"insert","index":0,"items":[{"queue_item_id":5}]}]}""",
        )
        assertEquals(2, ch.changes.size)
        assertEquals("remove", ch.changes.first().operation)
        assertEquals(5L, ch.changes.last().items.single().queueItemId)
    }

    @Test
    fun parsesBrowseAndLoadResults() {
        val br = RoonJson.decodeFromString<BrowseResult>(
            """{"action":"list","list":{"title":"Albums","count":50,"level":1}}""",
        )
        assertEquals("list", br.action)
        assertEquals("Albums", br.list?.title)
        assertEquals(50, br.list?.count)
        assertEquals(1, br.list?.level)

        val load = RoonJson.decodeFromString<LoadResult>(
            """{"items":[{"title":"Album X","subtitle":"Artist","item_key":"ik","image_key":"img","hint":"list",
               "input_prompt":{"prompt":"Search","action":"Search"}}],"offset":0}""",
        )
        val item = load.items.single()
        assertEquals("Album X", item.title)
        assertEquals("ik", item.itemKey)
        assertEquals("img", item.imageKey)
        assertEquals("Search", item.inputPrompt?.prompt)
    }
}
