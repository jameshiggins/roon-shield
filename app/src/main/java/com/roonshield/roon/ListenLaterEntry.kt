package com.roonshield.roon

import kotlinx.serialization.Serializable

/**
 * An app-local "Listen Later" bookmark. Roon browse item_keys are session-scoped and expire, so we
 * store durable display info + a [query] used to re-resolve the item via Search when the user opens it.
 */
@Serializable
data class ListenLaterEntry(
    val title: String,
    val subtitle: String? = null,
    val imageKey: String? = null,
    val query: String,
)
