package com.roonshield.roon.moo

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Suspends until the core sends the terminal COMPLETE for this request, returning that message. */
suspend fun Moo.requestComplete(name: String, body: ByteArray? = null): MooMessage =
    suspendCancellableCoroutine { cont ->
        val id = sendRequest(name, body) { msg ->
            if (msg.verb == MooMessage.VERB_COMPLETE) {
                if (cont.isActive) cont.resume(msg)
                false
            } else {
                true
            }
        }
        cont.invokeOnCancellation { cancel(id) }
    }
