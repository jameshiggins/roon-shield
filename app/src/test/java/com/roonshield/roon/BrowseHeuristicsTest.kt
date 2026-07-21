package com.roonshield.roon

import com.roonshield.roon.model.BrowseItem
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowseHeuristicsTest {

    private fun item(title: String, hint: String? = null, image: String? = null) =
        BrowseItem(title = title, subtitle = null, imageKey = image, itemKey = "k_$title", hint = hint)

    @Test
    fun isActionList_trueOnlyWhenAllAreActions() {
        assertTrue(BrowseHeuristics.isActionList(listOf(item("Play Now", "action"), item("Queue", "action"))))
        assertTrue(BrowseHeuristics.isActionList(listOf(item("Play Album", "action_list"))))
        assertFalse(BrowseHeuristics.isActionList(listOf(item("Play Now", "action"), item("Some Track", null))))
        assertFalse(BrowseHeuristics.isActionList(emptyList()))
    }

    @Test
    fun looksLikeGrid_trueForArtHeavyLists_falseForTracksOrActions() {
        val albums = listOf(item("A", image = "i1"), item("B", image = "i2"), item("C", image = "i3"))
        assertTrue(BrowseHeuristics.looksLikeGrid(albums))

        val tracks = listOf(item("1. Song"), item("2. Song"), item("3. Song"))
        assertFalse(BrowseHeuristics.looksLikeGrid(tracks))

        val withAction = listOf(item("Play Album", "action_list"), item("A", image = "i1"))
        assertFalse(BrowseHeuristics.looksLikeGrid(withAction))

        assertFalse(BrowseHeuristics.looksLikeGrid(emptyList()))
    }

    @Test
    fun isActionVerb_detectsVerbsNotNames() {
        assertTrue(BrowseHeuristics.isActionVerb("Play Album"))
        assertTrue(BrowseHeuristics.isActionVerb("Play Now"))
        assertTrue(BrowseHeuristics.isActionVerb("Shuffle"))
        assertTrue(BrowseHeuristics.isActionVerb("Add to Queue"))
        assertTrue(BrowseHeuristics.isActionVerb("Start Radio"))
        assertFalse(BrowseHeuristics.isActionVerb("Pop/Rock"))
        assertFalse(BrowseHeuristics.isActionVerb("Radiohead"))
        assertFalse(BrowseHeuristics.isActionVerb(null))
    }
}
