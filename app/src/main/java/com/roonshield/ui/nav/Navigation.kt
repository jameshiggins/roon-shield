package com.roonshield.ui.nav

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.staticCompositionLocalOf
import com.roonshield.roon.model.BrowseItem

/** Primary tabs (the left nav rail). Now Playing is reachable from the footer too. */
enum class Tab(val label: String) {
    Home("Home"),
    Browse("Browse"),
    Search("Search"),
    Queue("Queue"),
    NowPlaying("Now Playing"),
}

/** How a pushed detail page should be rendered. */
enum class DetailKind { Generic, Artist, Album, Genre, Playlist }

/** A pushed detail page: a Roon browse item to open, with a hint about its layout. */
data class DetailRoute(
    val itemKey: String,
    val title: String,
    val kind: DetailKind = DetailKind.Generic,
    val imageKey: String? = null,
    val hierarchy: String = "browse",
)

/** Holds the current tab and a stack of pushed detail pages. Screens push via [LocalNavigator]. */
class AppNavigator {
    var tab by mutableStateOf(Tab.Home)
        private set

    val detailStack: SnapshotStateList<DetailRoute> = mutableStateListOf()

    val currentDetail: DetailRoute? get() = detailStack.lastOrNull()

    fun selectTab(t: Tab) {
        tab = t
    }

    fun open(item: BrowseItem, kind: DetailKind = DetailKind.Generic, hierarchy: String = "browse") {
        val key = item.itemKey ?: return
        detailStack.add(
            DetailRoute(
                itemKey = key,
                title = item.title ?: "",
                kind = kind,
                imageKey = item.imageKey,
                hierarchy = hierarchy,
            ),
        )
    }

    fun openDetail(route: DetailRoute) {
        detailStack.add(route)
    }

    /** Pop one detail page. Returns false if there was nothing to pop. */
    fun pop(): Boolean {
        if (detailStack.isEmpty()) return false
        detailStack.removeAt(detailStack.lastIndex)
        return true
    }

    fun popToRoot() {
        detailStack.clear()
    }
}

val LocalNavigator = staticCompositionLocalOf<AppNavigator> {
    error("No AppNavigator provided")
}
