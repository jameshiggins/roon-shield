package com.roonshield.roon

import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Test

class ListenLaterEntryTest {

    private val serializer = ListSerializer(ListenLaterEntry.serializer())

    @Test
    fun roundTripsThroughJson() {
        val entries = listOf(
            ListenLaterEntry(title = "OK Computer", subtitle = "Radiohead", imageKey = "img1", query = "OK Computer Radiohead"),
            ListenLaterEntry(title = "Dummy", subtitle = "Portishead", imageKey = null, query = "Dummy Portishead"),
        )
        val json = RoonJson.encodeToString(serializer, entries)
        val decoded = RoonJson.decodeFromString(serializer, json)
        assertEquals(entries, decoded)
    }

    @Test
    fun emptyListRoundTrips() {
        val json = RoonJson.encodeToString(serializer, emptyList())
        assertEquals(emptyList<ListenLaterEntry>(), RoonJson.decodeFromString(serializer, json))
    }
}
