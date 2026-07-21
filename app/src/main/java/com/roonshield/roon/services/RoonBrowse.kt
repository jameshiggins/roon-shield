package com.roonshield.roon.services

import com.roonshield.roon.RoonExtension
import com.roonshield.roon.RoonJson
import com.roonshield.roon.model.BrowseResult
import com.roonshield.roon.model.LoadResult
import com.roonshield.roon.moo.Moo
import com.roonshield.roon.moo.requestComplete
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Roon Browse service (com.roonlabs.browse:1). Navigation is a browse()/load() loop:
 * browse() moves the cursor (into an item, back up, or into a search), load() pages the
 * resulting list. Passing [zoneOrOutputId] makes item actions (Play Now, etc.) target that zone.
 */
class RoonBrowse(private val moo: Moo) {

    suspend fun browse(
        hierarchy: String = "browse",
        itemKey: String? = null,
        input: String? = null,
        popAll: Boolean = false,
        popLevels: Int? = null,
        refreshList: Boolean = false,
        setDisplayOffset: Int? = null,
        zoneOrOutputId: String? = null,
    ): BrowseResult? {
        val body = buildJsonObject {
            put("hierarchy", hierarchy)
            itemKey?.let { put("item_key", it) }
            input?.let { put("input", it) }
            if (popAll) put("pop_all", true)
            popLevels?.let { put("pop_levels", it) }
            if (refreshList) put("refresh_list", true)
            setDisplayOffset?.let { put("set_display_offset", it) }
            zoneOrOutputId?.let { put("zone_or_output_id", it) }
        }.toString().toByteArray()
        val msg = moo.requestComplete("${RoonExtension.SVC_BROWSE}/browse", body)
        return msg.bodyText?.let { runCatching { RoonJson.decodeFromString<BrowseResult>(it) }.getOrNull() }
    }

    suspend fun load(
        hierarchy: String = "browse",
        offset: Int = 0,
        count: Int = 100,
        level: Int? = null,
        setDisplayOffset: Int? = null,
        zoneOrOutputId: String? = null,
    ): LoadResult? {
        val body = buildJsonObject {
            put("hierarchy", hierarchy)
            put("offset", offset)
            put("count", count)
            level?.let { put("level", it) }
            setDisplayOffset?.let { put("set_display_offset", it) }
            zoneOrOutputId?.let { put("zone_or_output_id", it) }
        }.toString().toByteArray()
        val msg = moo.requestComplete("${RoonExtension.SVC_BROWSE}/load", body)
        return msg.bodyText?.let { runCatching { RoonJson.decodeFromString<LoadResult>(it) }.getOrNull() }
    }
}
