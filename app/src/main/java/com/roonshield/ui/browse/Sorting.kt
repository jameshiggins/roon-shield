package com.roonshield.ui.browse

import com.roonshield.roon.model.BrowseItem

/** Client-side sort modes for a browse level (Roon's public API has no server-side sort). */
enum class SortMode(val label: String) {
    Default("Default order"),
    MostPlayed("Most played"),
    NameAsc("Name (A–Z)"),
    NameDesc("Name (Z–A)"),
    SubtitleAsc("Artist / subtitle (A–Z)"),
    SubtitleDesc("Artist / subtitle (Z–A)"),
    Reverse("Reverse order"),
    Shuffle("Shuffle"),
}

/** Applies a [SortMode] to browse items using their visible fields. Pure + unit-testable.
 *  [playCounts] feeds [SortMode.MostPlayed] (see [playScore]); ties keep the current order. */
fun applySort(items: List<BrowseItem>, mode: SortMode, seed: Int, playCounts: Map<String, Int> = emptyMap()): List<BrowseItem> = when (mode) {
    SortMode.Default -> items
    SortMode.MostPlayed -> items.sortedByDescending { playScore(it, playCounts) }
    SortMode.NameAsc -> items.sortedWith(compareBy(NaturalKeyComparator) { it.title.orEmpty() })
    SortMode.NameDesc -> items.sortedWith(compareBy(NaturalKeyComparator) { it.title.orEmpty() }).reversed()
    SortMode.SubtitleAsc -> items.sortedWith(compareBy(NaturalKeyComparator) { it.subtitle.orEmpty() })
    SortMode.SubtitleDesc -> items.sortedWith(compareBy(NaturalKeyComparator) { it.subtitle.orEmpty() }).reversed()
    SortMode.Reverse -> items.reversed()
    SortMode.Shuffle -> items.shuffled(kotlin.random.Random(seed.toLong()))
}

/** Play count for an album row: exact "title|artist" match first, else any album with the same
 *  title (covers artist-credit mismatches like "A feat. B" vs the album's primary artist). */
fun playScore(item: BrowseItem, counts: Map<String, Int>): Int {
    if (counts.isEmpty()) return 0
    val title = item.title?.takeIf { it.isNotBlank() } ?: return 0
    counts[com.roonshield.roon.playCountKey(title, item.subtitle)]?.let { return it }
    val t = title.trim().lowercase()
    return counts.entries.filter { it.key.substringBefore('|') == t }.sumOf { it.value }
}

/** Case-insensitive, digit-aware string comparator so "Track 2" sorts before "Track 10". */
val NaturalKeyComparator = Comparator<String> { a, b ->
    val x = a.lowercase(); val y = b.lowercase()
    var i = 0; var j = 0
    while (i < x.length && j < y.length) {
        val cx = x[i]; val cy = y[j]
        if (cx.isDigit() && cy.isDigit()) {
            var ni = i; while (ni < x.length && x[ni].isDigit()) ni++
            var nj = j; while (nj < y.length && y[nj].isDigit()) nj++
            val nx = x.substring(i, ni).trimStart('0').ifEmpty { "0" }
            val ny = y.substring(j, nj).trimStart('0').ifEmpty { "0" }
            val cmp = if (nx.length != ny.length) nx.length - ny.length else nx.compareTo(ny)
            if (cmp != 0) return@Comparator cmp
            i = ni; j = nj
        } else {
            if (cx != cy) return@Comparator cx.compareTo(cy)
            i++; j++
        }
    }
    x.length - y.length
}
