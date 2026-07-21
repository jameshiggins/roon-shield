package com.roonshield.roon.moo

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Abstracts the byte transport (an OkHttp WebSocket in production, a fake in tests). */
interface MooTransport {
    fun send(bytes: ByteArray)
    fun close()
}

/**
 * MOO RPC dispatcher over a [MooTransport].
 *
 * - Outgoing [sendRequest]s get a unique Request-Id; responses (COMPLETE/CONTINUE) are
 *   routed back to the per-request handler. A handler returns `true` to stay registered
 *   (subscriptions that keep receiving CONTINUE "Changed"), `false` to be removed. Any
 *   COMPLETE always removes the handler.
 * - Incoming REQUESTs from the core (for provided services such as Ping and Status) are
 *   dispatched to [requestHandler], which replies via [sendResponse] using the same id.
 */
class Moo(private val transport: MooTransport) {

    private val reqIdCounter = AtomicInteger(0)
    private val handlers = ConcurrentHashMap<Int, (MooMessage) -> Boolean>()

    /** Handles REQUESTs initiated by the core (provided services). */
    @Volatile
    var requestHandler: ((moo: Moo, msg: MooMessage) -> Unit)? = null

    fun sendRequest(
        name: String,
        body: ByteArray? = null,
        contentType: String? = null,
        onResponse: (MooMessage) -> Boolean,
    ): Int {
        val id = reqIdCounter.getAndIncrement()
        handlers[id] = onResponse
        transport.send(MooMessage.request(name, id, body, contentType).encode())
        return id
    }

    fun sendResponse(
        requestId: Int,
        verb: String,
        name: String,
        body: ByteArray? = null,
        contentType: String? = null,
    ) {
        transport.send(MooMessage.response(verb, name, requestId, body, contentType).encode())
    }

    fun cancel(requestId: Int) {
        handlers.remove(requestId)
    }

    /** Feed a received WebSocket binary frame. */
    fun onMessage(bytes: ByteArray) {
        val msg = try {
            MooMessage.parse(bytes)
        } catch (_: Exception) {
            return
        }
        if (msg.verb == MooMessage.VERB_REQUEST) {
            requestHandler?.invoke(this, msg)
            return
        }
        val handler = handlers[msg.requestId] ?: return
        val keep = try {
            handler(msg)
        } catch (_: Exception) {
            false
        }
        if (!keep || msg.verb == MooMessage.VERB_COMPLETE) {
            handlers.remove(msg.requestId)
        }
    }

    fun clear() {
        handlers.clear()
        requestHandler = null
    }
}
