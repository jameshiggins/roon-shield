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
