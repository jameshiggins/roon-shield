package com.roonshield.roon.moo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/** Dispatcher-level guarantees that underpin correct connection handling (the reconnect-loop fix
 *  relies on a replaced/cleared Moo never invoking a previous socket's handlers). */
class MooDispatchTest {

    private class FakeTransport : MooTransport {
        val sent = mutableListOf<ByteArray>()
        var closed = false
        override fun send(bytes: ByteArray) { sent.add(bytes) }
        override fun close() { closed = true }
    }

    @Test
    fun response_routesToHandlerByRequestId() {
        val moo = Moo(FakeTransport())
        var got: MooMessage? = null
        val id = moo.sendRequest("svc/op") { got = it; false }
        moo.onMessage(MooMessage.response(MooMessage.VERB_COMPLETE, "Success", id, null).encode())
        assertNotNull(got)
        assertEquals("Success", got!!.name)
    }

    @Test
    fun clear_removesHandlers_soLateResponsesAreIgnored() {
        val moo = Moo(FakeTransport())
        var called = 0
        val id = moo.sendRequest("svc/op") { called++; false }
        moo.clear() // simulates the socket being replaced on reconnect
        moo.onMessage(MooMessage.response(MooMessage.VERB_COMPLETE, "Success", id, null).encode())
        assertEquals(0, called)
    }

    @Test
    fun subscription_handlerStaysUntilComplete_thenStops() {
        val moo = Moo(FakeTransport())
        val names = mutableListOf<String>()
        val id = moo.sendRequest("svc/subscribe") { msg ->
            names.add(msg.name)
            msg.verb != MooMessage.VERB_COMPLETE
        }
        moo.onMessage(MooMessage.response(MooMessage.VERB_CONTINUE, "Subscribed", id, null).encode())
        moo.onMessage(MooMessage.response(MooMessage.VERB_CONTINUE, "Changed", id, null).encode())
        moo.onMessage(MooMessage.response(MooMessage.VERB_COMPLETE, "Unsubscribed", id, null).encode())
        // handler removed after COMPLETE — this one must be ignored
        moo.onMessage(MooMessage.response(MooMessage.VERB_CONTINUE, "Changed", id, null).encode())
        assertEquals(listOf("Subscribed", "Changed", "Unsubscribed"), names)
    }

    @Test
    fun incomingCoreRequest_goesToRequestHandler_notResponseHandlers() {
        val moo = Moo(FakeTransport())
        var reqName: String? = null
        moo.requestHandler = { _, msg -> reqName = msg.name }
        moo.onMessage(MooMessage.request("com.roonlabs.ping:1/ping", 99, null).encode())
        assertEquals("com.roonlabs.ping:1/ping", reqName)
    }

    @Test
    fun malformedFrame_isIgnoredSilently() {
        val moo = Moo(FakeTransport())
        // zero-length frame like the one seen right before a Core drop — must not throw
        moo.onMessage(ByteArray(0))
        moo.onMessage("garbage-not-moo".toByteArray())
    }
}
