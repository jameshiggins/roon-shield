package com.roonshield.wiki

import com.roonshield.roon.RoonLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

private const val AREA = "Wiki"

data class WikiEntry(val title: String, val extract: String)

/**
 * Fetches album / artist summaries from Wikipedia for the Now Playing info panel. Roon's public
 * Extension API exposes none of the native app's TiVo review/bio text, so Wikipedia is the best
 * available source. Tries exact title patterns first ("X (album)", "X (band)"), then falls back to
 * full-text search; disambiguation pages are skipped.
 */
object WikipediaService {

    private val client = OkHttpClient.Builder()
        .callTimeout(10, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }
    private val cache = HashMap<String, WikiEntry?>()

    suspend fun albumInfo(album: String, artist: String?): WikiEntry? = cached("album|$album|$artist") {
        val clean = cleanTitle(album)
        // A hit must actually be about the record: bare titles like "Tour De France" otherwise
        // resolve to whatever shares the name (the bicycle race, a city, a film…).
        val needles = listOfNotNull("album", "EP", "soundtrack", artist)
        val guesses = listOfNotNull(
            artist?.let { "$clean ($it album)" },
            "$clean (album)",
            clean,
        )
        byTitles(guesses, needles) ?: search(listOfNotNull(clean, artist, "album").joinToString(" "), needles)
    }

    suspend fun artistInfo(artist: String): WikiEntry? = cached("artist|$artist") {
        val needles = listOf(
            "band", "musician", "singer", "songwriter", "rapper", "composer", "music", "DJ", "record producer",
        )
        byTitles(listOf("$artist (band)", "$artist (musician)", "$artist (singer)", artist), needles)
            ?: search("$artist musician", needles)
    }

    /** Whole-word match — substring matching would let "EP" hit "except", "band" hit "husband"… */
    fun relevant(entry: WikiEntry, needles: List<String>): Boolean = needles.any { n ->
        val re = Regex("(?i)\\b${Regex.escape(n)}\\b")
        re.containsMatchIn(entry.title) || re.containsMatchIn(entry.extract)
    }

    private val noiseWords =
        Regex("(?i)\\b(remaster(ed)?|deluxe|edition|expanded|anniversary|bonus|reissue|digital|mono|stereo|version|remix(es)?)\\b")

    /** Strips release-noise qualifiers so lookups use the canonical album name:
     *  "Tour De France (2009 Digital Remaster; 2009 Remaster)" -> "Tour De France". Parenthesized
     *  segments that don't look like noise (real subtitles) are kept. Pure; tested. */
    fun cleanTitle(raw: String): String {
        val stripped = Regex("\\s*[\\[(]([^)\\]]*)[)\\]]").replace(raw) { m ->
            val inner = m.groupValues[1].trim()
            if (noiseWords.containsMatchIn(inner) || Regex("^(19|20)\\d{2}$").matches(inner)) "" else m.value
        }
        return stripped.replace(Regex("\\s{2,}"), " ").trim().trimEnd('-', '–').trim()
            .ifBlank { raw.trim() }
    }

    private suspend fun cached(key: String, fetch: suspend () -> WikiEntry?): WikiEntry? {
        synchronized(cache) { if (cache.containsKey(key)) return cache[key] }
        val entry = runCatching { fetch() }
            .onFailure { RoonLog.w(AREA, "lookup failed for $key: ${it.message}") }
            .getOrNull()
        synchronized(cache) { if (cache.size > 40) cache.clear(); cache[key] = entry }
        return entry
    }

    private suspend fun byTitles(titles: List<String>, needles: List<String>): WikiEntry? {
        for (t in titles) {
            val url = api()
                .addQueryParameter("titles", t)
                .build()
            val entry = fetchEntries(url.toString()).firstOrNull { relevant(it, needles) }
            if (entry != null) return entry
        }
        return null
    }

    private suspend fun search(query: String, needles: List<String>): WikiEntry? {
        val url = api()
            .addQueryParameter("generator", "search")
            .addQueryParameter("gsrsearch", query)
            .addQueryParameter("gsrlimit", "4")
            .build()
        return fetchEntries(url.toString()).firstOrNull { relevant(it, needles) }
    }

    private fun api() = "https://en.wikipedia.org/w/api.php".toHttpUrl().newBuilder()
        .addQueryParameter("action", "query")
        .addQueryParameter("prop", "extracts|pageprops")
        .addQueryParameter("exintro", "1")
        .addQueryParameter("explaintext", "1")
        .addQueryParameter("redirects", "1")
        .addQueryParameter("format", "json")

    private suspend fun fetchEntries(url: String): List<WikiEntry> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "RoonShield/0.1 (Android TV remote; personal use)")
            .build()
        val body = client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext emptyList()
            resp.body?.string() ?: return@withContext emptyList()
        }
        parseEntries(body)
    }

    /** Non-missing, non-disambiguation pages with usable extracts, in search-rank order (the
     *  `pages` map is unordered; generator=search stamps each page with an `index`). Pure; tested. */
    fun parseEntries(body: String): List<WikiEntry> {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return emptyList()
        val pages = root["query"]?.jsonObject?.get("pages")?.jsonObject ?: return emptyList()
        val out = ArrayList<Pair<Int, WikiEntry>>()
        for ((id, page) in pages) {
            if (id == "-1") continue
            val obj = page.jsonObject
            if (obj["pageprops"]?.jsonObject?.containsKey("disambiguation") == true) continue
            val title = obj["title"]?.jsonPrimitive?.content ?: continue
            val extract = obj["extract"]?.jsonPrimitive?.content?.trim().orEmpty()
            if (extract.length < 40 || extract.contains("may refer to", ignoreCase = true)) continue
            val rank = obj["index"]?.jsonPrimitive?.content?.toIntOrNull() ?: Int.MAX_VALUE
            out += rank to WikiEntry(title = title, extract = extract)
        }
        return out.sortedBy { it.first }.map { it.second }
    }

    fun parseEntry(body: String): WikiEntry? = parseEntries(body).firstOrNull()
}
