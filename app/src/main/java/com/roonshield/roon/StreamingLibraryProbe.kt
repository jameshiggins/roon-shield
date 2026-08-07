package com.roonshield.roon

import com.roonshield.roon.model.BrowseItem

private const val AREA = "LocalFilter"

/** Normalized identity for an album row ("title|artist"), used to match a library album against
 *  the streaming services' library lists. */
fun albumKey(item: BrowseItem): String =
    "${item.title?.trim()?.lowercase().orEmpty()}|${item.subtitle?.trim()?.lowercase().orEmpty()}"

/**
 * Walks the generic `browse` hierarchy to collect the albums that live in a linked streaming
 * service's library (TIDAL / Qobuz), so the Home daily sample can exclude them and show only
 * albums from the local library. Best-effort by design: Roon's public API has no storage-location
 * focus, so this is the only way to tell local from streaming. If no streaming service is linked
 * (or the tree doesn't match), it returns an empty set and no filtering happens.
 */
class StreamingLibraryProbe(private val repo: RoonRepository) {

    private val session = BrowseSession(repo, key = "streaming-probe", hierarchy = "browse")

    suspend fun collect(): Set<String> {
        val root = session.home()
        RoonLog.i(AREA, "browse root: ${root.items.map { it.title }}")
        val services = root.items.filter { isStreamingService(it.title) }
        if (services.isEmpty()) {
            RoonLog.i(AREA, "no streaming services linked — sample is local-only already")
            return emptySet()
        }
        val out = HashSet<String>()
        services.forEach { svc ->
            // home() pops to root before each walk so the cursor is always in a known place.
            val fresh = session.home().items.firstOrNull { it.title == svc.title } ?: return@forEach
            out += collectServiceAlbums(fresh)
        }
        RoonLog.i(AREA, "collected ${out.size} streaming-library album keys")
        return out
    }

    private suspend fun collectServiceAlbums(service: BrowseItem): Set<String> {
        val page = session.open(service.itemKey ?: return emptySet())
        RoonLog.i(AREA, "${service.title} children: ${page.items.map { it.title }}")
        // The service's library albums sit either directly ("Albums") or one level down inside a
        // "My Library" / "Your Favorites"-style node.
        val direct = page.items.firstOrNull { isAlbumsNode(it.title) }
        if (direct != null) return pageAllAlbums(direct)
        val libraryNode = page.items.firstOrNull { isLibraryNode(it.title) } ?: return emptySet()
        val libPage = session.open(libraryNode.itemKey ?: return emptySet())
        RoonLog.i(AREA, "${service.title} / ${libraryNode.title} children: ${libPage.items.map { it.title }}")
        val albums = libPage.items.firstOrNull { isAlbumsNode(it.title) } ?: return emptySet()
        return pageAllAlbums(albums)
    }

    private suspend fun pageAllAlbums(albumsNode: BrowseItem): Set<String> {
        val page = session.open(albumsNode.itemKey ?: return emptySet())
        val out = HashSet<String>()
        page.items.forEach { out += albumKey(it) }
        var offset = page.items.size
        val total = page.totalCount.coerceAtMost(MAX_ALBUMS)
        while (offset < total) {
            val window = session.page(offset, 200)
            if (window.isEmpty()) break
            window.forEach { out += albumKey(it) }
            offset += window.size
        }
        return out
    }

    private fun isStreamingService(title: String?): Boolean {
        val t = title?.trim() ?: return false
        return t.equals("TIDAL", ignoreCase = true) || t.equals("Qobuz", ignoreCase = true) ||
            t.equals("KKBOX", ignoreCase = true)
    }

    private fun isLibraryNode(title: String?): Boolean {
        val t = title?.trim()?.lowercase() ?: return false
        return "library" in t || "favorite" in t || t.startsWith("my ")
    }

    private fun isAlbumsNode(title: String?): Boolean {
        val t = title?.trim()?.lowercase() ?: return false
        return t == "albums" || t == "my albums" || t == "favorite albums"
    }

    private companion object {
        /** Safety cap so a huge streaming library can't stall the Home sample forever. */
        const val MAX_ALBUMS = 5000
    }
}
