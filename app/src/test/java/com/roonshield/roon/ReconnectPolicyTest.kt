package com.roonshield.roon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReconnectPolicyTest {

    @Test
    fun stableThreshold_isSeveralSecondsSoAQuickDropNeverResetsBackoff() {
        // Must be long enough that a register->drop loop (drops within ~1-2s) never resets backoff.
        assertTrue(ReconnectPolicy.STABLE_MS >= 5000L)
    }


    @Test
    fun backoffGrowsExponentiallyThenCaps() {
        assertEquals(1000L, ReconnectPolicy.backoffMs(0))
        assertEquals(2000L, ReconnectPolicy.backoffMs(1))
        assertEquals(4000L, ReconnectPolicy.backoffMs(2))
        assertEquals(8000L, ReconnectPolicy.backoffMs(3))
        assertEquals(15000L, ReconnectPolicy.backoffMs(4)) // 16000 capped to 15000
        assertEquals(15000L, ReconnectPolicy.backoffMs(10)) // stays capped
    }

    @Test
    fun negativeAttemptClampsToBase() {
        assertEquals(1000L, ReconnectPolicy.backoffMs(-3))
    }
}
