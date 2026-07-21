package com.roonshield.roon

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

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

    private companion object {
        val LAST_HOST = stringPreferencesKey("last_host")
        val LAST_PORT = intPreferencesKey("last_port")
        val LAST_ZONE = stringPreferencesKey("last_zone")
    }
}
