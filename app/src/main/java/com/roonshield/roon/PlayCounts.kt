package com.roonshield.roon

/**
 * Key for the app-local album play history: "album title|primary artist", normalized. Roon's
 * public API exposes no play counts, so the app counts plays itself from the zone events it
 * observes while connected — every zone in the house, not just the active one.
 */
fun playCountKey(album: String, artist: String?): String {
    val a = artist.orEmpty().substringBefore(" / ").substringBefore(" feat.").trim().lowercase()
    return "${album.trim().lowercase()}|$a"
}

/** A finished track observed on a zone — feeds the queue panel's "previously played" section.
 *  Held in memory only (Roon's API serves no played history), so it covers the current app run. */
data class PlayedTrack(
    val title: String?,
    val subtitle: String?,
    val imageKey: String?,
)
