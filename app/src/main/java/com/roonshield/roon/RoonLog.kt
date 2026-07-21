package com.roonshield.roon

import android.util.Log

/**
 * Central logging for the Roon client. All tags share the "RoonShield" prefix so you can filter
 * logcat with:  `adb logcat -s RoonShield:*`  (or per-area, e.g. `RoonShield/SOOD`).
 * Verbose wire logging is gated behind [wire] to avoid flooding logcat with MOO traffic.
 */
object RoonLog {
    private const val BASE = "RoonShield"

    /** Toggle noisy per-message MOO/subscription logging. */
    @Volatile var wire: Boolean = true

    fun d(area: String, msg: String) = Log.d("$BASE/$area", msg)
    fun i(area: String, msg: String) = Log.i("$BASE/$area", msg)
    fun w(area: String, msg: String) = Log.w("$BASE/$area", msg)
    fun e(area: String, msg: String, t: Throwable? = null) =
        if (t != null) Log.e("$BASE/$area", msg, t) else Log.e("$BASE/$area", msg)

    fun v(area: String, msg: String) {
        if (wire) Log.d("$BASE/$area", msg)
    }
}
