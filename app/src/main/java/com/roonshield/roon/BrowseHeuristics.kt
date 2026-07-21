package com.roonshield.roon

import com.roonshield.roon.model.BrowseItem

/**
 * Pure classification helpers for browse levels. Roon doesn't strongly type levels, so we infer
 * whether a loaded level is an action list, a card grid, or a detail/track list. Kept side-effect
 * free so the UI's branching logic is unit-testable.
 */
object BrowseHeuristics {

    /** A level whose items are all Roon actions (Play Now / Play Next / Queue / …) -> show a sheet. */
    fun isActionList(items: List<BrowseItem>): Boolean =
        items.isNotEmpty() && items.all { it.hint == "action" || it.hint == "action_list" }

    /** A deeper level that looks like a grid of albums/artists rather than a detail/track list. */
    fun looksLikeGrid(items: List<BrowseItem>): Boolean {
        if (items.isEmpty()) return false
        if (items.any { it.hint == "action" || it.hint == "action_list" }) return false
        val withArt = items.count { !it.imageKey.isNullOrEmpty() }
        return withArt * 2 >= items.size
    }

    /** True when a label is a verb/action ("Play Album", "Shuffle", "Add to Queue") rather than a name. */
    fun isActionVerb(title: String?): Boolean {
        val t = title.orEmpty().trim().lowercase()
        return t.startsWith("play") || t.startsWith("shuffle") || t.startsWith("start radio") ||
            t.startsWith("add") || t.startsWith("queue") || t.startsWith("start ")
    }
}
