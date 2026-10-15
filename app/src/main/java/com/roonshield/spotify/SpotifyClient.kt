package com.roonshield.spotify

import com.roonshield.roon.RoonLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

private const val AREA = "Spotify"
private const val TOKEN_URL = "https://accounts.spotify.com/api/token"
private const val CURRENTLY_PLAYING = "https://api.spotify.com/v1/me/player/currently-playing"

/** What Spotify says is playing right now, flattened to the fields Now Playing needs. */
data class SpotifyTrack(
    val title: String,
    val artist: String,
    val album: String?,
    val artUrl: String?,
    val positionMs: Int,
    val durationMs: Int,
)

/** Tokens as returned by the authorization-code / refresh exchanges. */
@Serializable
private data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresIn: Int = 3600,
    // PKCE apps get a rotated refresh token on some refreshes; when present it must replace the
    // stored one, or the next refresh after this one's lifetime fails.
    @SerialName("refresh_token") val refreshToken: String? = null,
)

@Serializable
private data class CurrentlyPlaying(
    @SerialName("is_playing") val isPlaying: Boolean = false,
    @SerialName("progress_ms") val progressMs: Int? = null,
    val item: Item? = null,
) {
    @Serializable
    data class Item(
        val name: String,
        @SerialName("duration_ms") val durationMs: Int = 0,
        val artists: List<Artist> = emptyList(),
        val album: Album? = null,
    )

    @Serializable
    data class Artist(val name: String)

    @Serializable
    data class Album(val name: String? = null, val images: List<Image> = emptyList())

    @Serializable
    data class Image(val url: String, val width: Int = 0, val height: Int = 0)
}

/**
 * Reads the signed-in listener's currently-playing track from Spotify's Web API.
 *
 * Why this exists: when a phone AirPlays Spotify straight to the Hegel, Roon is not in the path at
 * all — the Core sees nothing, so the Extension API can never name that track. The phone is still
 * the decoder though, so Spotify's own API keeps reporting it (verified live against the Hegel).
 * This is the only route to song/artist for AirPlay'd music, and it is deliberately a second,
 * non-Roon data source — the same exception already made for [com.roonshield.wiki.WikipediaService].
 *
 * Spotify has no idea the audio is being AirPlay'd: it reports the phone as the device whether the
 * sound comes out of the Hegel or out of headphones a mile away. Knowing the music is actually in
 * the room is the Hegel input gate's job, not this class's.
 */
class SpotifyClient(
    private val clientId: String,
    initialRefreshToken: String,
    /** Called when Spotify rotates the refresh token, so the caller can persist the new one. */
    private val onRefreshTokenRotated: suspend (String) -> Unit = {},
) {

    private val client = OkHttpClient.Builder()
        .callTimeout(10, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    private var refreshToken: String = initialRefreshToken
    private var accessToken: String? = null
    private var accessTokenExpiresAt: Long = 0L

    /**
     * Current track, or null when nothing is playing (Spotify answers 204) or the item is not a
     * track we can render. Throws nothing — failures log and return null, because this runs on a
     * poll loop and a transient network blip must not take the UI down.
     */
    suspend fun currentlyPlaying(): SpotifyTrack? = withContext(Dispatchers.IO) {
        val token = accessToken() ?: return@withContext null
        val req = Request.Builder()
            .url(CURRENTLY_PLAYING)
            .header("Authorization", "Bearer $token")
            .build()
        runCatching {
            client.newCall(req).execute().use { res ->
                when {
                    // 204 = nothing playing / no active device. The common idle case, not an error.
                    res.code == 204 -> null
                    res.code == 401 -> {
                        // Access token died early (revoked, or scopes changed). Drop it so the next
                        // poll re-mints one instead of looping on 401s.
                        RoonLog.w(AREA, "401 from currently-playing; discarding access token")
                        accessToken = null
                        null
                    }
                    !res.isSuccessful -> {
                        RoonLog.w(AREA, "currently-playing HTTP ${res.code}")
                        null
                    }
                    else -> res.body?.string()?.let { parseTrack(it) }
                }
            }
        }.getOrElse {
            RoonLog.w(AREA, "currently-playing failed: ${it.message}")
            null
        }
    }

    private fun parseTrack(body: String): SpotifyTrack? {
        val now = runCatching { json.decodeFromString<CurrentlyPlaying>(body) }.getOrElse {
            RoonLog.w(AREA, "could not parse currently-playing: ${it.message}")
            return null
        }
        if (!now.isPlaying) return null
        val item = now.item ?: return null // podcast episodes come back without a track item
        return SpotifyTrack(
            title = item.name,
            artist = item.artists.joinToString(", ") { it.name },
            album = item.album?.name,
            // Images are ordered widest-first; the ~300px one is right for Now Playing art without
            // pulling a 640px JPEG on every track change.
            artUrl = item.album?.images?.minByOrNull { kotlin.math.abs(it.width - 300) }?.url,
            positionMs = now.progressMs ?: 0,
            durationMs = item.durationMs,
        )
    }

    /** A live access token, minting one from the refresh token when the current one nears expiry. */
    private suspend fun accessToken(): String? {
        val cached = accessToken
        if (cached != null && System.currentTimeMillis() < accessTokenExpiresAt) return cached
        return refreshAccessToken()
    }

    private suspend fun refreshAccessToken(): String? = withContext(Dispatchers.IO) {
        val form = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)
            .add("client_id", clientId) // PKCE app: client id in the body, no secret
            .build()
        val req = Request.Builder().url(TOKEN_URL).post(form).build()
        runCatching {
            client.newCall(req).execute().use { res ->
                val body = res.body?.string()
                if (!res.isSuccessful || body == null) {
                    RoonLog.w(AREA, "token refresh HTTP ${res.code}: $body")
                    return@use null
                }
                val token = json.decodeFromString<TokenResponse>(body)
                accessToken = token.accessToken
                // Refresh a minute early so a poll never races the expiry.
                accessTokenExpiresAt =
                    System.currentTimeMillis() + (token.expiresIn - 60).coerceAtLeast(30) * 1000L
                token.refreshToken?.let { rotated ->
                    if (rotated != refreshToken) {
                        refreshToken = rotated
                        onRefreshTokenRotated(rotated)
                        RoonLog.i(AREA, "refresh token rotated; persisted")
                    }
                }
                RoonLog.i(AREA, "access token refreshed (expires in ${token.expiresIn}s)")
                token.accessToken
            }
        }.getOrElse {
            RoonLog.w(AREA, "token refresh failed: ${it.message}")
            null
        }
    }
}
