package com.roonshield.roon

import com.roonshield.roon.model.BrowseItem
import com.roonshield.roon.model.BrowseList

/** One page of a browse level: its list metadata + the loaded items (+ optional message). */
data class BrowsePage(
    val list: BrowseList? = null,
    val items: List<BrowseItem> = emptyList(),
    val message: String? = null,
    val level: Int = 0,
) {
    val title: String get() = list?.title ?: ""
    val totalCount: Int get() = list?.count ?: items.size
}

/**
 * An isolated Roon browse cursor. Each screen (Home, Artist, Album, Search, …) uses its own
 * [key] so their navigation never corrupts each other's position — Roon multiplexes independent
 * browse sessions by `multi_session_key`.
 */
class BrowseSession(
    private val repo: RoonRepository,
    val key: String,
    val hierarchy: String = "browse",
) {
    suspend fun home(): BrowsePage =
        toPage(repo.browse(hierarchy = hierarchy, popAll = true, multiSessionKey = key))

    suspend fun open(itemKey: String): BrowsePage =
        toPage(repo.browse(hierarchy = hierarchy, itemKey = itemKey, multiSessionKey = key))

    suspend fun input(text: String): BrowsePage =
        toPage(repo.browse(hierarchy = hierarchy, input = text, multiSessionKey = key))

    suspend fun back(levels: Int = 1): BrowsePage =
        toPage(repo.browse(hierarchy = hierarchy, popLevels = levels, multiSessionKey = key))

    /** Invoke an action item (e.g. "Play Now") that produces no new list. */
    suspend fun invoke(itemKey: String): String? =
        repo.browse(hierarchy = hierarchy, itemKey = itemKey, multiSessionKey = key)?.message

    /** Load an arbitrary page window of the current level (for paging / load-more). */
    suspend fun page(offset: Int, count: Int = 100): List<BrowseItem> =
        repo.load(hierarchy = hierarchy, offset = offset, count = count, multiSessionKey = key)?.items ?: emptyList()

    private suspend fun toPage(res: com.roonshield.roon.model.BrowseResult?): BrowsePage {
        if (res == null) return BrowsePage(message = "No response from Core")
        if (res.isError == true) return BrowsePage(list = res.list, message = res.message ?: "Browse error")
        return when (res.action) {
            "list" -> {
                val count = (res.list?.count ?: 100).coerceIn(0, 1000).coerceAtMost(200)
                val items = repo.load(hierarchy = hierarchy, offset = 0, count = count, multiSessionKey = key)?.items ?: emptyList()
                BrowsePage(list = res.list, items = items, level = res.list?.level ?: 0)
            }
            else -> BrowsePage(list = res.list, message = res.message, level = res.list?.level ?: 0)
        }
    }
}
