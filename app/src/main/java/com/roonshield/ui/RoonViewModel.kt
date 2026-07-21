package com.roonshield.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.roonshield.roon.BrowseSession
import com.roonshield.roon.ConnectionState
import com.roonshield.roon.RoonLog
import com.roonshield.roon.RoonRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class Destination(val label: String) {
    NowPlaying("Now Playing"),
    Browse("Browse"),
    Search("Search"),
    Queue("Queue"),
}

/** Bridges [RoonRepository] to Compose: exposes connection/zone/queue state, browse controllers,
 *  and transport commands the controller-input layer calls. */
class RoonViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = RoonRepository(app)

    val connectionState = repo.connectionState
    val zones = repo.zones
    val activeZone = repo.activeZone
    val activeZoneId = repo.activeZoneId
    val queue = repo.queue
    val discovered = repo.discoveredCores

    val library = BrowseController(repo, viewModelScope, rootHierarchy = "browse")
    val search = BrowseController(repo, viewModelScope, rootHierarchy = "search")

    /** True only during the initial silent-reconnect window, so the UI shows a splash instead of
     *  flashing the discovery screen (and kicking off a needless SOOD scan) while we reconnect. */
    private val _booting = MutableStateFlow(true)
    val booting: StateFlow<Boolean> = _booting.asStateFlow()

    init {
        // Attempt a silent reconnect to the last-used Core on launch.
        repo.connectLast(onNone = { _booting.value = false })
        // Leave the boot window as soon as we start connecting (or hit any non-idle state).
        viewModelScope.launch {
            connectionState.collect { if (it !is ConnectionState.Idle) _booting.value = false }
        }
    }

    // ---- Connection ----
    fun discover() = viewModelScope.launch {
        RoonLog.d("UI", "discover() triggered")
        repo.discover()
    }
    fun connect(host: String, port: Int) = repo.connect(host, port)
    fun connectDiscovered(core: com.roonshield.roon.DiscoveredCore) = repo.connect(core)
    fun disconnect() = repo.disconnect()

    // ---- Zones / volume ----
    fun setZone(zoneId: String) = repo.setActiveZone(zoneId)
    fun nudgeVolume(delta: Int) = repo.nudgeVolume(delta)
    fun setVolume(value: Int) = repo.setVolumeAbsolute(value)
    fun toggleMute() = repo.toggleMute()

    // ---- Browse ----
    /** A fresh isolated browse cursor for a screen (Home/Artist/Album/Search…). */
    fun browseSession(key: String, hierarchy: String = "browse"): BrowseSession =
        BrowseSession(repo, key, hierarchy)

    // ---- Transport ----
    fun playPause() = repo.playPause()
    fun next() = repo.next()
    fun previous() = repo.previous()
    fun stop() = repo.stop()
    fun seekBy(delta: Int) = repo.seekBy(delta)
    fun seekTo(seconds: Int) = repo.seekTo(seconds)
    fun toggleShuffle() = repo.toggleShuffle()
    fun toggleRadio() = repo.toggleRadio()
    fun cycleLoop() = repo.cycleLoop()
    fun playFromQueue(queueItemId: Long) = repo.playFromQueue(queueItemId)

    // ---- Cross-tab search request (e.g. from Listen Later -> Search) ----
    private val _pendingSearch = MutableStateFlow<String?>(null)
    val pendingSearch: StateFlow<String?> = _pendingSearch
    fun requestSearch(query: String) { _pendingSearch.value = query }
    fun consumePendingSearch(): String? {
        val v = _pendingSearch.value
        _pendingSearch.value = null
        return v
    }

    // ---- Listen Later ----
    val listenLater = repo.listenLater
    fun addListenLater(item: com.roonshield.roon.model.BrowseItem) {
        val title = item.title ?: return
        repo.addListenLater(
            com.roonshield.roon.ListenLaterEntry(
                title = title,
                subtitle = item.subtitle,
                imageKey = item.imageKey,
                query = listOfNotNull(item.title, item.subtitle?.substringBefore(" · ")).joinToString(" ").ifBlank { title },
            ),
        )
    }
    fun removeListenLater(title: String) = repo.removeListenLater(title)

    // ---- Images ----
    suspend fun loadArt(imageKey: String, width: Int, height: Int) = repo.loadImage(imageKey, width, height)
}
