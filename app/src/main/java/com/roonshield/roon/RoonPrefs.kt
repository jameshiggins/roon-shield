package com.roonshield.roon

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

private val Context.dataStore by preferencesDataStore(name = "roon")

/** Persists the Core address, the per-core authorization token, and the last active zone. */
class RoonPrefs(private val context: Context) {

    private fun tokenKey(coreId: String) = stringPreferencesKey("token_$coreId")

    suspend fun getToken(coreId: String): String? =
        context.dataStore.data.map { it[tokenKey(coreId)] }.first()

    suspend fun setToken(coreId: String, token: String) {
        context.dataStore.edit { it[tokenKey(coreId)] = token }
    }

    suspend fun getLastCore(): Pair<String, Int>? {
        val prefs = context.dataStore.data.first()
        val host = prefs[LAST_HOST] ?: return null
        val port = prefs[LAST_PORT] ?: return null
        return host to port
    }

    suspend fun setLastCore(host: String, port: Int) {
        context.dataStore.edit {
            it[LAST_HOST] = host
            it[LAST_PORT] = port
        }
    }

    suspend fun getLastZoneId(): String? =
        context.dataStore.data.map { it[LAST_ZONE] }.first()

    suspend fun setLastZoneId(zoneId: String) {
        context.dataStore.edit { it[LAST_ZONE] = zoneId }
    }

    // ---- Listen Later ----

    private val listSerializer = ListSerializer(ListenLaterEntry.serializer())

    private fun decodeListenLater(raw: String?): List<ListenLaterEntry> =
        (raw?.let { runCatching { RoonJson.decodeFromString(listSerializer, it) }.getOrNull() } ?: emptyList())
            // Old builds could save the action verb ("Play Genre") instead of the item — hide those.
            .filterNot { BrowseHeuristics.isActionVerb(it.title) }

    val listenLater: Flow<List<ListenLaterEntry>> =
        context.dataStore.data.map { decodeListenLater(it[LISTEN_LATER]) }

    suspend fun addListenLater(entry: ListenLaterEntry) {
        context.dataStore.edit { prefs ->
            val current = decodeListenLater(prefs[LISTEN_LATER]).filterNot { it.title.equals(entry.title, ignoreCase = true) }
            prefs[LISTEN_LATER] = RoonJson.encodeToString(listSerializer, listOf(entry) + current)
        }
    }

    suspend fun removeListenLater(title: String) {
        context.dataStore.edit { prefs ->
            val current = decodeListenLater(prefs[LISTEN_LATER]).filterNot { it.title.equals(title, ignoreCase = true) }
            prefs[LISTEN_LATER] = RoonJson.encodeToString(listSerializer, current)
        }
    }

    // ---- Album play history (app-observed; Roon exposes no play counts) ----

    private val countsSerializer = MapSerializer(String.serializer(), Int.serializer())

    private fun decodeCounts(raw: String?): Map<String, Int> =
        raw?.let { runCatching { RoonJson.decodeFromString(countsSerializer, it) }.getOrNull() } ?: emptyMap()

    val playCounts: Flow<Map<String, Int>> =
        context.dataStore.data.map { decodeCounts(it[PLAY_COUNTS]) }

    suspend fun incrementPlayCount(key: String) {
        context.dataStore.edit { prefs ->
            val current = decodeCounts(prefs[PLAY_COUNTS]).toMutableMap()
            current[key] = (current[key] ?: 0) + 1
            prefs[PLAY_COUNTS] = RoonJson.encodeToString(countsSerializer, current)
        }
    }

    private companion object {
        val LAST_HOST = stringPreferencesKey("last_host")
        val LAST_PORT = intPreferencesKey("last_port")
        val LAST_ZONE = stringPreferencesKey("last_zone")
        val LISTEN_LATER = stringPreferencesKey("listen_later")
        val PLAY_COUNTS = stringPreferencesKey("play_counts")
    }
}
