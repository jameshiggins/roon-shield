package com.roonshield.roon

/** A finished track observed on a zone — feeds the queue panel's "previously played" section.
 *  Held in memory only (Roon's API serves no played history), so it covers the current app run. */
data class PlayedTrack(
    val title: String?,
    val subtitle: String?,
    val imageKey: String?,
)
