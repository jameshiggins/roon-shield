package com.roonshield.roon

import android.content.Context
import com.roonshield.roon.model.BrowseResult
import com.roonshield.roon.model.LoadResult
import com.roonshield.roon.model.QueueItem
import com.roonshield.roon.model.Zone
import com.roonshield.roon.services.RoonBrowse
import com.roonshield.roon.services.RoonImage
import com.roonshield.roon.services.RoonTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

/**
 * Single source of truth for the UI. Owns the [RoonCore] connection, re-subscribes zones/queue
 * automatically across reconnects, tracks the active zone, and exposes transport + browse + image
 * operations. All state is published as [StateFlow]s the Compose layer collects.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RoonRepository(context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val prefs = RoonPrefs(context.applicationContext)
    private val core = RoonCore(scope, prefs)
    private val discovery = SoodDiscovery()

    private val transportFlow = MutableStateFlow<RoonTransport?>(null)
    private var browse: RoonBrowse? = null
    private var image: RoonImage? = null
    private var preferredZoneId: String? = null

    val connectionState: StateFlow<ConnectionState> = core.state
    val discoveredCores = MutableStateFlow<List<DiscoveredCore>>(emptyList())

    private val _activeZoneId = MutableStateFlow<String?>(null)
    val activeZoneId: StateFlow<String?> = _activeZoneId

    val zones: StateFlow<List<Zone>> = transportFlow
        .flatMapLatest { it?.subscribeZones() ?: flowOf(emptyList()) }
        .onEach { reconcileActiveZone(it) }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val activeZone: StateFlow<Zone?> = combine(zones, activeZoneId) { zs, id ->
        zs.firstOrNull { it.zoneId == id }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    val queue: StateFlow<List<QueueItem>> =
        combine(transportFlow, activeZoneId) { t, id -> t to id }
            .flatMapLatest { (t, id) ->
                if (t != null && id != null) t.subscribeQueue(id) else flowOf(emptyList())
            }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    init {
        core.onPaired = {
            val m = core.moo
            if (m != null) {
                RoonLog.i("Repo", "paired — wiring transport/browse/image services")
                browse = RoonBrowse(m)
                image = RoonImage(m)
                transportFlow.value = RoonTransport(m)
            }
        }
        core.onUnpaired = {
            RoonLog.i("Repo", "unpaired — tearing down services")
            transportFlow.value = null
            browse = null
            image = null
        }
        scope.launch { preferredZoneId = prefs.getLastZoneId() }
    }

    // ---- Connection ----

    suspend fun discover(timeoutMs: Long = 3000L): List<DiscoveredCore> {
        val found = discovery.discover(timeoutMs)
        discoveredCores.value = found
        return found
    }

    fun connect(host: String, port: Int) = core.connect(host, port)

    fun connect(core: DiscoveredCore) = connect(core.host, core.port)

    /** Re-attempt using the last successful Core address, if any. */
    fun connectLast(onNone: () -> Unit) {
        scope.launch {
            val last = prefs.getLastCore()
            if (last != null) core.connect(last.first, last.second) else onNone()
        }
    }

    fun disconnect() = core.disconnect()

    // ---- Zones ----

    fun setActiveZone(zoneId: String) {
        _activeZoneId.value = zoneId
        preferredZoneId = zoneId
        scope.launch { prefs.setLastZoneId(zoneId) }
    }

    private fun reconcileActiveZone(list: List<Zone>) {
        val current = _activeZoneId.value
        if (current != null && list.any { it.zoneId == current }) return
        val restored = preferredZoneId?.let { pref -> list.firstOrNull { it.zoneId == pref } }
        val fallback = restored
            ?: list.firstOrNull { it.isPlaying }
            ?: list.firstOrNull()
        RoonLog.d("Repo", "active zone -> ${fallback?.displayName} (${fallback?.zoneId})")
        _activeZoneId.value = fallback?.zoneId
    }

    // ---- Transport controls (target the active zone) ----

    private fun withTransportZone(block: suspend (RoonTransport, Zone) -> Unit) {
        val t = transportFlow.value ?: return
        val z = activeZone.value ?: return
        scope.launch { runCatching { block(t, z) } }
    }

    fun playPause() = withTransportZone { t, z -> t.control(z.zoneId, "playpause") }
    fun next() = withTransportZone { t, z -> t.control(z.zoneId, "next") }
    fun previous() = withTransportZone { t, z -> t.control(z.zoneId, "previous") }
    fun stop() = withTransportZone { t, z -> t.control(z.zoneId, "stop") }

    fun seekTo(seconds: Int) = withTransportZone { t, z -> t.seek(z.zoneId, seconds, "absolute") }
    fun seekBy(deltaSeconds: Int) = withTransportZone { t, z -> t.seek(z.zoneId, deltaSeconds, "relative") }

    fun nudgeVolume(delta: Int) = withTransportZone { t, z ->
        val out = z.primaryOutputId ?: return@withTransportZone
        t.changeVolume(out, delta, "relative")
    }

    fun toggleMute() = withTransportZone { t, z ->
        val output = z.outputs.firstOrNull() ?: return@withTransportZone
        val muted = output.volume?.isMuted == true
        t.mute(output.outputId, !muted)
    }

    fun toggleShuffle() = withTransportZone { t, z ->
        t.changeSettings(z.zoneId, shuffle = !(z.settings?.shuffle ?: false))
    }

    fun toggleRadio() = withTransportZone { t, z ->
        t.changeSettings(z.zoneId, autoRadio = !(z.settings?.autoRadio ?: false))
    }

    fun cycleLoop() = withTransportZone { t, z ->
        val next = when (z.settings?.loop) {
            "loop" -> "loop_one"
            "loop_one" -> "disabled"
            else -> "loop"
        }
        t.changeSettings(z.zoneId, loop = next)
    }

    fun playFromQueue(queueItemId: Long) = withTransportZone { t, z -> t.playFromHere(z.zoneId, queueItemId) }

    // ---- Browse / Search ----

    suspend fun browse(
        hierarchy: String = "browse",
        itemKey: String? = null,
        input: String? = null,
        popAll: Boolean = false,
        popLevels: Int? = null,
        multiSessionKey: String? = null,
    ): BrowseResult? = browse?.browse(
        hierarchy = hierarchy,
        itemKey = itemKey,
        input = input,
        popAll = popAll,
        popLevels = popLevels,
        zoneOrOutputId = _activeZoneId.value,
        multiSessionKey = multiSessionKey,
    )

    suspend fun load(
        hierarchy: String = "browse",
        offset: Int = 0,
        count: Int = 100,
        multiSessionKey: String? = null,
    ): LoadResult? = browse?.load(
        hierarchy = hierarchy,
        offset = offset,
        count = count,
        zoneOrOutputId = _activeZoneId.value,
        multiSessionKey = multiSessionKey,
    )

    suspend fun loadImage(imageKey: String, width: Int, height: Int): ByteArray? =
        image?.getImage(imageKey, width, height)

    // ---- Listen Later (app-local) ----

    val listenLater: StateFlow<List<ListenLaterEntry>> =
        prefs.listenLater.stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun addListenLater(entry: ListenLaterEntry) {
        scope.launch { prefs.addListenLater(entry) }
    }

    fun removeListenLater(title: String) {
        scope.launch { prefs.removeListenLater(title) }
    }

    /** True once a Core is paired and browse/transport services are live. */
    val isReady: Boolean get() = browse != null

    fun setVolumeAbsolute(value: Int) = withTransportZone { t, z ->
        val out = z.primaryOutputId ?: return@withTransportZone
        t.changeVolume(out, value, "absolute")
    }
}
