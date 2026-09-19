package com.roonshield.ui.browse

import android.app.Application
import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import com.roonshield.ui.RoonViewModel

/**
 * Roon's browse API lists playlists without artwork, so derive a cover from each playlist's own
 * tracks: a scratch browse session (its own multi_session_key, so it never disturbs the visible
 * cursor) peeks into each playlist and keeps its first few distinct track covers.
 *
 * The title -> image-key map is persisted, so covers show instantly on later runs; each run then
 * re-checks every playlist once in the background (uncached ones first) to pick up edits.
 */
internal object PlaylistCovers {
    /** Covers needed for the 2x2 mosaic; fewer falls back to a single cover. */
    const val MOSAIC = 4
    /** Rows peeked per playlist — enough to find [MOSAIC] distinct covers past same-album runs. */
    private const val PEEK_ROWS = 24

    /** Playlist title -> up to [MOSAIC] distinct cover image keys (empty = looked, found none). */
    val byTitle = mutableStateMapOf<String, List<String>>()

    private var restored = false
    private val checkedThisRun = HashSet<String>()

    private fun prefs(viewModel: RoonViewModel) =
        viewModel.getApplication<Application>().getSharedPreferences("playlist_covers", Context.MODE_PRIVATE)

    /** Load the persisted map (first call only) so the grid has covers before any browsing. */
    fun restore(viewModel: RoonViewModel) {
        if (restored) return
        restored = true
        prefs(viewModel).all.forEach { (title, value) ->
            (value as? String)?.let { byTitle[title] = it.split('\n').filter(String::isNotEmpty) }
        }
    }

    suspend fun resolve(viewModel: RoonViewModel) {
        restore(viewModel)
        val session = viewModel.browseSession("playlistcovers", "playlists")
        val home = session.home()
        val titles = home.items
            .filter { it.imageKey.isNullOrEmpty() }
            .mapNotNull { it.title }
            .distinct()
            .filter { it !in checkedThisRun }
            .sortedBy { it in byTitle } // blanks fill in before cached covers are re-verified
        for (title in titles) {
            val key = home.items.firstOrNull { it.title == title }?.itemKey ?: continue
            val tracks = session.peek(key, PEEK_ROWS)
            val keys = tracks.items
                .mapNotNull { it.imageKey?.takeIf(String::isNotEmpty) }
                .distinct()
                .take(MOSAIC)
            // Never let a failed peek (dropped connection) wipe a good cached cover.
            if (keys != byTitle[title] && (keys.isNotEmpty() || title !in byTitle)) {
                byTitle[title] = keys
                prefs(viewModel).edit().putString(title, keys.joinToString("\n")).apply()
            }
            if (tracks.message == null) checkedThisRun += title
            if (tracks.level > home.level) session.pop()
        }
    }
}
