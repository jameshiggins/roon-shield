package com.roonshield.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.roonshield.roon.BrowseSession
import com.roonshield.roon.ConnectionState
import com.roonshield.roon.RoonLog
import com.roonshield.roon.RoonRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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

    // Process-wide singleton: survives Activity recreation so a relaunch reuses the live
    // connection instead of spawning a second one that fights the first over the extension id.
    private val repo = RoonRepository.get(app)

    /** Optional initial tab (deep-link / testing via `--es start_tab <id>`). */
    var initialTab: String? = null

    val connectionState = repo.connectionState
    val zones = repo.zones
    val activeZone = repo.activeZone
    val activeZoneId = repo.activeZoneId
    val queue = repo.queue
    val discovered = repo.discoveredCores
    val pairingEpoch = repo.pairingEpoch
    val recentlyPlayed = repo.recentlyPlayed
    val listenLaterAvailable = repo.listenLaterAvailable

    /** True after we've been connected at least once this run. While a transient drop is being
     *  reconnected the main UI stays mounted (with an overlay) instead of bouncing to discovery. */
    private val _everConnected = MutableStateFlow(false)
    val everConnected: StateFlow<Boolean> = _everConnected.asStateFlow()

    /** True only during the initial silent-reconnect window, so the UI shows a splash instead of
     *  flashing the discovery screen (and kicking off a needless SOOD scan) while we reconnect. */
    private val _booting = MutableStateFlow(true)
    val booting: StateFlow<Boolean> = _booting.asStateFlow()

    init {
        // Attempt a silent reconnect to the last-used Core on launch.
        repo.connectLast(onNone = { _booting.value = false })
        // Leave the boot window as soon as we start connecting (or hit any non-idle state).
        viewModelScope.launch {
            connectionState.collect {
                if (it !is ConnectionState.Idle) _booting.value = false
                when (it) {
                    is ConnectionState.Connected -> _everConnected.value = true
                    // Idle only happens on manual disconnect — fall back to the connect screen.
                    is ConnectionState.Idle -> _everConnected.value = false
                    else -> Unit
                }
            }
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

    // ---- Screensaver / idle ----
    private val _screensaver = MutableStateFlow(false)
    val screensaver: StateFlow<Boolean> = _screensaver.asStateFlow()
    private var idleJob: Job? = null

    init {
        // The screensaver only makes sense while music plays: when playback starts, arm the idle
        // countdown; when it stops while the screensaver is up, drop back to where the user was.
        viewModelScope.launch {
            activeZone.map { it?.isPlaying == true }.distinctUntilChanged().collect { playing ->
                if (playing) restartIdleCountdown()
                else if (_screensaver.value) _screensaver.value = false
            }
        }
    }

    /** Called on any user input. Wakes from the screensaver and restarts the idle countdown. */
    fun onUserInteraction() {
        if (_screensaver.value) _screensaver.value = false
        restartIdleCountdown()
    }

    private fun restartIdleCountdown() {
        idleJob?.cancel()
        idleJob = viewModelScope.launch {
            delay(SCREENSAVER_IDLE_MS)
            // Never take over the screen when nothing is playing.
            if (activeZone.value?.isPlaying == true) _screensaver.value = true
        }
    }

    /** Stop the idle countdown (e.g. when the app is backgrounded). */
    fun cancelIdle() {
        idleJob?.cancel()
    }

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

    // ---- Local-library filter ----
    suspend fun streamingAlbumKeys(): Set<String> = repo.streamingAlbumKeys()

    companion object {
        /** Idle time with no input before the now-playing screensaver appears (playing only). */
        const val SCREENSAVER_IDLE_MS = 30_000L
    }
}
