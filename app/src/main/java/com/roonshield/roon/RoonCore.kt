package com.roonshield.roon

import com.roonshield.roon.moo.Moo
import com.roonshield.roon.moo.MooMessage
import com.roonshield.roon.moo.MooTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.util.concurrent.TimeUnit

private const val AREA = "Core"

/** Exponential reconnect backoff, capped — pure so it can be unit-tested. */
object ReconnectPolicy {
    const val BASE_MS = 1000L
    const val MAX_MS = 15000L
    /** A connection must stay up this long after registering before its backoff counter resets. */
    const val STABLE_MS = 12000L
    fun backoffMs(attempt: Int): Long =
        (BASE_MS * (1L shl attempt.coerceIn(0, 4))).coerceAtMost(MAX_MS)
}

sealed interface ConnectionState {
    data object Idle : ConnectionState
    data object Discovering : ConnectionState
    data class Connecting(val host: String) : ConnectionState
    /** Registered request sent; waiting for the user to Enable the extension in Roon Settings. */
    data object WaitingForAuthorization : ConnectionState
    data class Connected(val coreName: String) : ConnectionState
    data class Error(val message: String) : ConnectionState
}

/**
 * Owns the WebSocket connection to a Roon Core and the MOO registration handshake:
 * `registry:1/info` -> `registry:1/register` -> "Registered" (+persisted token). Also answers
 * the core's provided-service requests (Ping keepalive, Status subscription).
 */
class RoonCore(
    private val scope: CoroutineScope,
    private val prefs: RoonPrefs,
) {
    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    @Volatile var moo: Moo? = null
        private set

    /** Fired when registration succeeds — repository (re)subscribes zones here. */
    @Volatile var onPaired: (() -> Unit)? = null
    @Volatile var onUnpaired: (() -> Unit)? = null

    private var webSocket: WebSocket? = null
    private var host: String = ""
    private var port: Int = 0
    private var coreId: String? = null
    private var statusRequestId: Int? = null
    @Volatile private var manualClose = false
    @Volatile private var reconnectAttempts = 0
    /** Monotonic id of the current socket. Callbacks from an older generation are ignored so a
     *  stale socket's failure can never tear down a newer, healthy connection. */
    @Volatile private var generation = 0
    private var reconnectJob: Job? = null
    private var stabilityJob: Job? = null
    @Volatile private var pairedGeneration = -1

    private val client = OkHttpClient.Builder()
        .pingInterval(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    fun connect(host: String, port: Int) {
        RoonLog.i(AREA, "connect($host:$port)")
        manualClose = false
        reconnectAttempts = 0
        reconnectJob?.cancel()
        this.host = host
        this.port = port
        openSocket()
    }

    fun disconnect() {
        RoonLog.i(AREA, "disconnect() requested")
        manualClose = true
        reconnectJob?.cancel()
        stabilityJob?.cancel()
        generation++ // invalidate any in-flight socket callbacks
        webSocket?.let { runCatching { it.cancel() } }
        webSocket = null
        moo?.clear()
        moo = null
        _state.value = ConnectionState.Idle
    }

    private fun openSocket() {
        // Invalidate the previous socket first so its late callbacks are ignored, and close it.
        generation++
        val gen = generation
        webSocket?.let { runCatching { it.cancel() } }
        moo?.clear()
        moo = null
        val url = "ws://$host:$port/api"
        RoonLog.d(AREA, "opening WebSocket $url (gen $gen, attempt ${reconnectAttempts + 1})")
        _state.value = ConnectionState.Connecting(host)
        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, RoonListener(gen))
    }

    private inner class RoonListener(private val gen: Int) : WebSocketListener() {
        private val isCurrent: Boolean get() = gen == generation

        override fun onOpen(ws: WebSocket, response: Response) {
            if (!isCurrent) { runCatching { ws.cancel() }; return }
            RoonLog.i(AREA, "WebSocket open (gen $gen)")
            val transport = object : MooTransport {
                override fun send(bytes: ByteArray) {
                    RoonLog.v(AREA, "-> ${bytes.size}b: ${bytes.decodeToString().substringBefore('\n')}")
                    ws.send(bytes.toByteString())
                }
                override fun close() {
                    ws.close(1000, null)
                }
            }
            val m = Moo(transport)
            m.requestHandler = ::handleCoreRequest
            moo = m
            sendInfoThenRegister(m)
        }

        override fun onMessage(ws: WebSocket, bytes: ByteString) {
            if (!isCurrent) return
            val ba = bytes.toByteArray()
            RoonLog.v(AREA, "<- ${ba.size}b: ${ba.decodeToString().substringBefore('\n')}")
            moo?.onMessage(ba)
        }

        override fun onMessage(ws: WebSocket, text: String) {
            // Roon frames are binary; ignore any text frames.
        }

        override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
            if (!isCurrent) {
                RoonLog.d(AREA, "ignoring failure from stale gen $gen (current $generation)")
                return
            }
            handleDrop("failure: ${t.message}", gen)
        }

        override fun onClosed(ws: WebSocket, code: Int, reason: String) {
            if (!isCurrent) return
            handleDrop("closed[$code]: $reason", gen)
        }
    }

    private fun handleDrop(message: String, gen: Int) {
        if (gen != generation) return // stale socket — ignore
        stabilityJob?.cancel() // dropped before proving stable -> keep backoff growing
        moo?.clear()
        moo = null
        webSocket = null
        onUnpaired?.invoke()
        if (manualClose) {
            RoonLog.d(AREA, "dropped after manual close ($message)")
            _state.value = ConnectionState.Idle
            return
        }
        _state.value = ConnectionState.Error(message)
        // Only one pending reconnect at a time.
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            val backoff = ReconnectPolicy.backoffMs(reconnectAttempts)
            reconnectAttempts++
            RoonLog.w(AREA, "connection dropped gen $gen ($message); reconnect in ${backoff}ms (attempt $reconnectAttempts)")
            delay(backoff)
            // Skip if a newer socket already came up or the user disconnected.
            if (!manualClose && gen == generation) openSocket()
        }
    }

    private fun sendInfoThenRegister(m: Moo) {
        m.sendRequest(name = "${RoonExtension.SVC_REGISTRY}/info") { info ->
            // The info response carries core_id, which we need to look up a persisted token
            // for silent reconnect before sending /register.
            val infoJson = info.bodyText?.let { runCatching { RoonJson.parseToJsonElement(it).jsonObject }.getOrNull() }
            coreId = infoJson?.get("core_id")?.jsonPrimitive?.content
            RoonLog.d(AREA, "registry/info: coreId=$coreId")
            scope.launch { register(m) }
            false
        }
    }

    private suspend fun register(m: Moo) {
        // Look up any persisted token keyed by the core id learned from /info.
        val token = coreId?.let { prefs.getToken(it) }
        val body = buildRegisterBody(token)
        RoonLog.i(AREA, "sending register (token=${if (token != null) "present" else "none"})")
        _state.value = ConnectionState.WaitingForAuthorization
        m.sendRequest(name = "${RoonExtension.SVC_REGISTRY}/register", body = body) { msg ->
            when (msg.name) {
                "Registered" -> {
                    onRegistered(msg)
                    true // keep: registration is long-lived and may re-fire on pairing changes
                }
                else -> true
            }
        }
    }

    private fun onRegistered(msg: MooMessage) {
        val bodyJson = msg.bodyText?.let { runCatching { RoonJson.parseToJsonElement(it).jsonObject }.getOrNull() }
        val id = bodyJson?.get("core_id")?.jsonPrimitive?.content
        val token = bodyJson?.get("token")?.jsonPrimitive?.content
        val name = bodyJson?.get("display_name")?.jsonPrimitive?.content ?: "Roon Core"
        RoonLog.i(AREA, "Registered with core '$name' (coreId=$id, token=${if (token != null) "received" else "none"})")
        // Reset backoff ONLY after the connection proves stable. If the Core registers us then drops
        // shortly after (duplicate extension id, Core quirk), this delayed reset is cancelled in
        // handleDrop, so the backoff keeps growing instead of a tight register->drop->register loop.
        stabilityJob?.cancel()
        stabilityJob = scope.launch {
            delay(ReconnectPolicy.STABLE_MS)
            reconnectAttempts = 0
            RoonLog.d(AREA, "connection stable for ${ReconnectPolicy.STABLE_MS}ms; backoff reset")
        }
        if (id != null) {
            coreId = id
            scope.launch {
                prefs.setLastCore(host, port)
                if (token != null) prefs.setToken(id, token)
            }
        }
        _state.value = ConnectionState.Connected(name)
        // Push an initial status so the app shows as connected in Roon Settings.
        pushStatus("Connected", isError = false)
        // "Registered" can re-fire on the same socket (pairing changes); only wire services once per
        // connection so the repository's zone/queue subscriptions don't churn.
        if (pairedGeneration != generation) {
            pairedGeneration = generation
            onPaired?.invoke()
        }
    }

    // ---- Provided services (core -> extension requests) ----

    private fun handleCoreRequest(m: Moo, msg: MooMessage) {
        when {
            msg.name.startsWith(RoonExtension.SVC_PING) -> {
                m.sendResponse(msg.requestId, MooMessage.VERB_COMPLETE, "Success")
            }
            msg.name == "${RoonExtension.SVC_STATUS}/subscribe_status" -> {
                RoonLog.d(AREA, "core subscribed to our status")
                statusRequestId = msg.requestId
                m.sendResponse(
                    msg.requestId,
                    MooMessage.VERB_CONTINUE,
                    "Subscribed",
                    statusBody("Connected", false),
                )
            }
            msg.name == "${RoonExtension.SVC_STATUS}/unsubscribe_status" -> {
                statusRequestId = null
                m.sendResponse(msg.requestId, MooMessage.VERB_COMPLETE, "Unsubscribed")
            }
            else -> {
                // Unknown provided request — acknowledge so the core isn't left hanging.
                m.sendResponse(msg.requestId, MooMessage.VERB_COMPLETE, "Success")
            }
        }
    }

    private fun pushStatus(message: String, isError: Boolean) {
        val id = statusRequestId ?: return
        moo?.sendResponse(id, MooMessage.VERB_CONTINUE, "Changed", statusBody(message, isError))
    }

    private fun statusBody(message: String, isError: Boolean): ByteArray =
        buildJsonObject {
            put("message", message)
            put("is_error", isError)
        }.toString().toByteArray()

    private fun buildRegisterBody(token: String?): ByteArray {
        val obj = buildJsonObject {
            put("extension_id", RoonExtension.EXTENSION_ID)
            put("display_name", RoonExtension.DISPLAY_NAME)
            put("display_version", RoonExtension.DISPLAY_VERSION)
            put("publisher", RoonExtension.PUBLISHER)
            put("email", RoonExtension.EMAIL)
            putJsonArray("required_services") { RoonExtension.REQUIRED_SERVICES.forEach { add(it) } }
            putJsonArray("optional_services") { RoonExtension.OPTIONAL_SERVICES.forEach { add(it) } }
            putJsonArray("provided_services") { RoonExtension.PROVIDED_SERVICES.forEach { add(it) } }
            if (token != null) put("token", token)
        }
        return obj.toString().toByteArray()
    }
}
