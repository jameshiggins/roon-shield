package com.roonshield.roon

import com.roonshield.roon.moo.Moo
import com.roonshield.roon.moo.MooMessage
import com.roonshield.roon.moo.MooTransport
import kotlinx.coroutines.CoroutineScope
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

    private val client = OkHttpClient.Builder()
        .pingInterval(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    fun connect(host: String, port: Int) {
        manualClose = false
        this.host = host
        this.port = port
        openSocket()
    }

    fun disconnect() {
        manualClose = true
        webSocket?.close(1000, "client closing")
        webSocket = null
        moo?.clear()
        moo = null
        _state.value = ConnectionState.Idle
    }

    private fun openSocket() {
        _state.value = ConnectionState.Connecting(host)
        val request = Request.Builder().url("ws://$host:$port/api").build()
        webSocket = client.newWebSocket(request, listener)
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(ws: WebSocket, response: Response) {
            val transport = object : MooTransport {
                override fun send(bytes: ByteArray) {
                    ws.send(bytes.toByteString())
                }
                override fun close() {
                    ws.close(1000, null)
                }
            }
            val m = Moo(transport)
            m.requestHandler = ::handleCoreRequest
            moo = m
            reconnectAttempts = 0
            sendInfoThenRegister(m)
        }

        override fun onMessage(ws: WebSocket, bytes: ByteString) {
            moo?.onMessage(bytes.toByteArray())
        }

        override fun onMessage(ws: WebSocket, text: String) {
            // Roon frames are binary; ignore any text frames.
        }

        override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
            handleDrop(t.message ?: "connection failed")
        }

        override fun onClosed(ws: WebSocket, code: Int, reason: String) {
            handleDrop("closed: $reason")
        }
    }

    private fun handleDrop(message: String) {
        moo?.clear()
        moo = null
        onUnpaired?.invoke()
        if (manualClose) {
            _state.value = ConnectionState.Idle
            return
        }
        _state.value = ConnectionState.Error(message)
        scope.launch {
            val backoff = (1000L * (1 shl reconnectAttempts.coerceAtMost(4))).coerceAtMost(15000L)
            reconnectAttempts++
            delay(backoff)
            if (!manualClose) openSocket()
        }
    }

    private fun sendInfoThenRegister(m: Moo) {
        m.sendRequest(name = "${RoonExtension.SVC_REGISTRY}/info") { info ->
            // The info response carries core_id, which we need to look up a persisted token
            // for silent reconnect before sending /register.
            val infoJson = info.bodyText?.let { runCatching { RoonJson.parseToJsonElement(it).jsonObject }.getOrNull() }
            coreId = infoJson?.get("core_id")?.jsonPrimitive?.content
            scope.launch { register(m) }
            false
        }
    }

    private suspend fun register(m: Moo) {
        // Look up any persisted token keyed by the core id learned from /info.
        val token = coreId?.let { prefs.getToken(it) }
        val body = buildRegisterBody(token)
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
        onPaired?.invoke()
    }

    // ---- Provided services (core -> extension requests) ----

    private fun handleCoreRequest(m: Moo, msg: MooMessage) {
        when {
            msg.name.startsWith(RoonExtension.SVC_PING) -> {
                m.sendResponse(msg.requestId, MooMessage.VERB_COMPLETE, "Success")
            }
            msg.name == "${RoonExtension.SVC_STATUS}/subscribe_status" -> {
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
