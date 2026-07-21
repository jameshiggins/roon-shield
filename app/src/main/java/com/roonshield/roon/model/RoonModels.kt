package com.roonshield.roon.model

import kotlinx.serialization.Serializable

// ---- Transport / zones ----

@Serializable
data class Lines(
    val line1: String? = null,
    val line2: String? = null,
    val line3: String? = null,
)

@Serializable
data class Volume(
    val type: String? = null,
    val min: Double? = null,
    val max: Double? = null,
    val value: Double? = null,
    val step: Double? = null,
    val isMuted: Boolean? = null,
    val hardLimitMin: Double? = null,
    val hardLimitMax: Double? = null,
    val softLimit: Double? = null,
)

@Serializable
data class Output(
    val outputId: String,
    val displayName: String? = null,
    val zoneId: String? = null,
    val volume: Volume? = null,
)

@Serializable
data class NowPlaying(
    val seekPosition: Int? = null,
    val length: Int? = null,
    val imageKey: String? = null,
    val oneLine: Lines? = null,
    val twoLine: Lines? = null,
    val threeLine: Lines? = null,
)

@Serializable
data class ZoneSettings(
    val loop: String? = null,
    val shuffle: Boolean? = null,
    val autoRadio: Boolean? = null,
)

@Serializable
data class Zone(
    val zoneId: String,
    val displayName: String? = null,
    val state: String? = null,
    val outputs: List<Output> = emptyList(),
    val nowPlaying: NowPlaying? = null,
    val isNextAllowed: Boolean = false,
    val isPreviousAllowed: Boolean = false,
    val isPauseAllowed: Boolean = false,
    val isPlayAllowed: Boolean = false,
    val isSeekAllowed: Boolean = false,
    val queueItemsRemaining: Int? = null,
    val settings: ZoneSettings? = null,
) {
    val isPlaying: Boolean get() = state == "playing" || state == "loading"
    /** Primary output id — the target most transport/volume calls address. */
    val primaryOutputId: String? get() = outputs.firstOrNull()?.outputId
}

@Serializable
data class SeekChanged(
    val zoneId: String,
    val seekPosition: Int? = null,
    val queueTimeRemaining: Int? = null,
)

@Serializable
data class ZonesResponse(
    val zones: List<Zone> = emptyList(),
)

@Serializable
data class ZonesChanged(
    val zonesChanged: List<Zone> = emptyList(),
    val zonesAdded: List<Zone> = emptyList(),
    val zonesRemoved: List<String> = emptyList(),
    val zonesSeekChanged: List<SeekChanged> = emptyList(),
)

// ---- Queue ----

@Serializable
data class QueueItem(
    val queueItemId: Long? = null,
    val length: Int? = null,
    val imageKey: String? = null,
    val oneLine: Lines? = null,
    val twoLine: Lines? = null,
    val threeLine: Lines? = null,
)

@Serializable
data class QueueResponse(
    val items: List<QueueItem> = emptyList(),
)

@Serializable
data class QueueChange(
    val operation: String,
    val index: Int? = null,
    val count: Int? = null,
    val items: List<QueueItem> = emptyList(),
)

@Serializable
data class QueueChanged(
    val changes: List<QueueChange> = emptyList(),
)

// ---- Browse ----

@Serializable
data class InputPrompt(
    val prompt: String? = null,
    val action: String? = null,
    val value: String? = null,
    val isPassword: Boolean? = null,
)

@Serializable
data class BrowseItem(
    val title: String? = null,
    val subtitle: String? = null,
    val imageKey: String? = null,
    val itemKey: String? = null,
    val hint: String? = null,
    val inputPrompt: InputPrompt? = null,
)

@Serializable
data class BrowseList(
    val title: String? = null,
    val count: Int = 0,
    val level: Int = 0,
    val subtitle: String? = null,
    val imageKey: String? = null,
    val hint: String? = null,
    val displayOffset: Int? = null,
)

@Serializable
data class BrowseResult(
    val action: String? = null,
    val item: BrowseItem? = null,
    val list: BrowseList? = null,
    val message: String? = null,
    val isError: Boolean? = null,
)

@Serializable
data class LoadResult(
    val items: List<BrowseItem> = emptyList(),
    val offset: Int = 0,
    val list: BrowseList? = null,
)
