package com.roonshield.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.roonshield.roon.RoonRepository
import kotlinx.coroutines.launch

enum class Destination(val label: String) {
    NowPlaying("Now Playing"),
    Browse("Browse"),
    Search("Search"),
    Queue("Queue"),
    Zones("Zones"),
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

    init {
        // Attempt a silent reconnect to the last-used Core on launch.
        repo.connectLast(onNone = {})
    }

    // ---- Connection ----
    fun discover() = viewModelScope.launch { repo.discover() }
    fun connect(host: String, port: Int) = repo.connect(host, port)
    fun connectDiscovered(core: com.roonshield.roon.DiscoveredCore) = repo.connect(core)
    fun disconnect() = repo.disconnect()

    // ---- Zones ----
    fun setZone(zoneId: String) = repo.setActiveZone(zoneId)

    // ---- Transport ----
    fun playPause() = repo.playPause()
    fun next() = repo.next()
    fun previous() = repo.previous()
    fun stop() = repo.stop()
    fun seekBy(delta: Int) = repo.seekBy(delta)
    fun seekTo(seconds: Int) = repo.seekTo(seconds)
    fun volumeUp() = repo.nudgeVolume(2)
    fun volumeDown() = repo.nudgeVolume(-2)
    fun toggleMute() = repo.toggleMute()
    fun playFromQueue(queueItemId: Long) = repo.playFromQueue(queueItemId)

    // ---- Images ----
    suspend fun loadArt(imageKey: String, width: Int, height: Int) = repo.loadImage(imageKey, width, height)
}
