package com.roonshield.ui.browse

import com.roonshield.roon.model.BrowseItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SortingTest {

    private fun item(title: String, subtitle: String? = null) =
        BrowseItem(title = title, subtitle = subtitle, imageKey = null, itemKey = "k_$title", hint = null)

    @Test
    fun naturalComparator_ordersNumbersNumericallyAndIgnoresCase() {
        assertTrue(NaturalKeyComparator.compare("Track 2", "Track 10") < 0)
        assertTrue(NaturalKeyComparator.compare("track 10", "Track 2") > 0)
        assertTrue(NaturalKeyComparator.compare("abba", "ZZ Top") < 0) // case-insensitive
        assertEquals(0, NaturalKeyComparator.compare("Same", "same"))
    }

    @Test
    fun nameSort_ascendingAndDescending() {
        val items = listOf(item("Charlie"), item("alpha"), item("Bravo"))
        val asc = applySort(items, SortMode.NameAsc, 0).map { it.title }
        assertEquals(listOf("alpha", "Bravo", "Charlie"), asc)
        val desc = applySort(items, SortMode.NameDesc, 0).map { it.title }
        assertEquals(listOf("Charlie", "Bravo", "alpha"), desc)
    }

    @Test
    fun subtitleSort_usesSubtitleField() {
        val items = listOf(item("X", "Wilco"), item("Y", "ABBA"), item("Z", "Metallica"))
        val asc = applySort(items, SortMode.SubtitleAsc, 0).map { it.subtitle }
        assertEquals(listOf("ABBA", "Metallica", "Wilco"), asc)
    }

    @Test
    fun reverse_reversesInputOrder() {
        val items = listOf(item("1"), item("2"), item("3"))
        assertEquals(listOf("3", "2", "1"), applySort(items, SortMode.Reverse, 0).map { it.title })
    }

    @Test
    fun shuffle_isDeterministicPerSeedAndPermutesTheSet() {
        val items = (1..20).map { item("t$it") }
        val a = applySort(items, SortMode.Shuffle, 42).map { it.title }
        val b = applySort(items, SortMode.Shuffle, 42).map { it.title }
        assertEquals(a, b) // same seed -> same order
        assertEquals(items.map { it.title }.toSet(), a.toSet()) // same set, no loss
    }

    @Test
    fun default_returnsInputUnchanged() {
        val items = listOf(item("b"), item("a"))
        assertEquals(items, applySort(items, SortMode.Default, 0))
    }

    @Test
    fun mostPlayed_sortsByCountDescending_tiesKeepOrder() {
        val items = listOf(item("Aja", "Steely Dan"), item("Kid A", "Radiohead"), item("Debut", "Björk"))
        val counts = mapOf(
            com.roonshield.roon.playCountKey("Kid A", "Radiohead") to 7,
            com.roonshield.roon.playCountKey("Debut", "Björk") to 2,
        )
        val sorted = applySort(items, SortMode.MostPlayed, 0, counts).map { it.title }
        assertEquals(listOf("Kid A", "Debut", "Aja"), sorted)
        // No counts at all -> order unchanged.
        assertEquals(items, applySort(items, SortMode.MostPlayed, 0))
    }

    @Test
    fun mostPlayed_fallsBackToTitleOnlyMatch_whenArtistCreditDiffers() {
        // Play was observed under a different artist credit than the album row shows.
        val counts = mapOf(com.roonshield.roon.playCountKey("World Clique", "Lady Miss Kier") to 3)
        assertEquals(3, playScore(item("World Clique", "Deee-Lite"), counts))
    }
}
