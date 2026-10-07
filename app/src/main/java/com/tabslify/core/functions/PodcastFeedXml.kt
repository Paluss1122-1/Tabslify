package com.tabslify.core.functions

import android.content.Context
import android.os.Environment
import androidx.core.content.edit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.w3c.dom.Document
import org.xml.sax.InputSource
import java.io.File
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.time.Duration.Companion.hours

const val PODCAST_CHECK_PREFS = "podcast_check"
const val PODCAST_FAVS_PREFS = "podcast_favs"
const val PODCAST_DOWNLOAD_PREFS = "podcast_downloads"

private const val KEY_FAVS = "favs"
private const val KEY_CACHE = "new_episodes_cache"
private const val KEY_CHECKED_AT = "new_episodes_checked_at"
private const val KEY_SEEN_PREFIX = "seen_"
private const val KEY_DOWNLOADED_PREFIX = "dl_"
private const val KEY_PENDING_PREFIX = "pending_"

private const val FEED_TIMEOUT_MS = 15_000
private const val MAX_REDIRECTS = 5
private const val MAX_ITEMS = 200

private val PODCAST_CHECK_TTL_MS = 6.hours.inWholeMilliseconds

fun podcastCheckPrefs(context: Context) =
    context.getSharedPreferences(PODCAST_CHECK_PREFS, Context.MODE_PRIVATE)

fun podcastFavPrefs(context: Context) =
    context.getSharedPreferences(PODCAST_FAVS_PREFS, Context.MODE_PRIVATE)

fun podcastDownloadPrefs(context: Context) =
    context.getSharedPreferences(PODCAST_DOWNLOAD_PREFS, Context.MODE_PRIVATE)

@Suppress("DEPRECATION")
fun podcastDestDir(): File =
    File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PODCASTS),
        "Tabslify"
    )

fun podcastEpisodeFileName(title: String): String =
    title.replace(Regex("[/\\\\:*?\"<>|]"), "_") + ".mp3"

fun markPodcastSeen(context: Context, feedUrl: String, at: Long = System.currentTimeMillis()) {
    podcastCheckPrefs(context).edit { putLong(KEY_SEEN_PREFIX + feedUrl, at) }
}

fun loadPodcastSeenAt(context: Context): Map<String, Long> {
    val result = mutableMapOf<String, Long>()
    for ((key, value) in podcastCheckPrefs(context).all) {
        if (key.startsWith(KEY_SEEN_PREFIX) && value is Long) {
            result[key.removePrefix(KEY_SEEN_PREFIX)] = value
        }
    }
    return result
}

fun markPodcastEpisodeDownloaded(context: Context, audioUrl: String) {
    podcastDownloadPrefs(context).edit { putBoolean(KEY_DOWNLOADED_PREFIX + audioUrl, true) }
}

fun clearPodcastEpisodeDownloaded(context: Context, audioUrl: String) {
    podcastDownloadPrefs(context).edit { remove(KEY_DOWNLOADED_PREFIX + audioUrl) }
}

fun isPodcastEpisodeDownloaded(context: Context, audioUrl: String): Boolean =
    podcastDownloadPrefs(context).getBoolean(KEY_DOWNLOADED_PREFIX + audioUrl, false)

fun loadDownloadedPodcastUrls(context: Context): Set<String> {
    val urls = mutableSetOf<String>()
    for ((key, value) in podcastDownloadPrefs(context).all) {
        if (key.startsWith(KEY_DOWNLOADED_PREFIX) && value == true) {
            urls.add(key.removePrefix(KEY_DOWNLOADED_PREFIX))
        }
    }
    return urls
}

fun loadPendingPodcastDownloads(context: Context): Map<Long, String> {
    val result = mutableMapOf<Long, String>()
    for ((key, value) in podcastDownloadPrefs(context).all) {
        if (!key.startsWith(KEY_PENDING_PREFIX) || value !is String) continue
        val downloadId = key.removePrefix(KEY_PENDING_PREFIX).toLongOrNull() ?: continue
        val audioUrl = runCatching { JSONObject(value).optString("audioUrl") }.getOrDefault("")
        if (audioUrl.isNotEmpty()) result[downloadId] = audioUrl
    }
    return result
}

suspend fun loadDownloadedPodcastFiles(): Set<String> = withContext(Dispatchers.IO) {
    podcastDestDir().listFiles()?.filter { it.isFile }?.map { it.name }?.toSet() ?: emptySet()
}

fun loadCachedNewEpisodes(context: Context): List<JSONObject> {
    val raw = podcastCheckPrefs(context).getString(KEY_CACHE, null) ?: return emptyList()
    return runCatching {
        val arr = JSONArray(raw)
        (0 until arr.length()).map { arr.getJSONObject(it) }
    }.getOrDefault(emptyList())
}

fun podcastCheckIsFresh(context: Context): Boolean {
    val checkedAt = podcastCheckPrefs(context).getLong(KEY_CHECKED_AT, 0L)
    return checkedAt > 0L && System.currentTimeMillis() - checkedAt < PODCAST_CHECK_TTL_MS
}

fun storeNewEpisodes(
    context: Context,
    episodes: List<JSONObject>,
    at: Long = System.currentTimeMillis()
) {
    val arr = JSONArray()
    episodes.forEach { arr.put(it) }
    podcastCheckPrefs(context).edit {
        putString(KEY_CACHE, arr.toString())
        putLong(KEY_CHECKED_AT, at)
    }
}

private fun podcastDocumentBuilderFactory(): DocumentBuilderFactory =
    DocumentBuilderFactory.newInstance().apply {
        runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
        runCatching {
            setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "")
        }
        runCatching {
            setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "")
        }
        runCatching { isXIncludeAware = false }
        runCatching { isExpandEntityReferences = false }
    }

private val PODCAST_PUBDATE_PATTERNS = listOf(
    "EEE, d MMM yyyy HH:mm:ss zzz",
    "EEE, d MMM yyyy HH:mm zzz",
    "d MMM yyyy HH:mm:ss zzz",
    "yyyy-MM-dd'T'HH:mm:ssXXX",
    "yyyy-MM-dd'T'HH:mm:ss'Z'"
)

private fun parsePodcastPubDate(raw: String): Long? {
    val text = raw.trim()
    if (text.isEmpty()) return null
    try {
        return ZonedDateTime.parse(
            text,
            DateTimeFormatter.RFC_1123_DATE_TIME.withLocale(Locale.ENGLISH)
        ).toInstant().toEpochMilli()
    } catch (_: Exception) {
    }
    try {
        return Instant.parse(text).toEpochMilli()
    } catch (_: Exception) {
    }
    for (pattern in PODCAST_PUBDATE_PATTERNS) {
        try {
            return SimpleDateFormat(pattern, Locale.ENGLISH)
                .apply { isLenient = false }
                .parse(text)
                ?.time
        } catch (_: Exception) {
        }
    }
    return null
}

suspend fun fetchPodcastFeed(feedUrl: String): Document = withContext(Dispatchers.IO) {
    var url = URL(feedUrl)
    var conn: HttpURLConnection
    var redirects = 0
    while (true) {
        conn = url.openConnection() as HttpURLConnection
        conn.setRequestProperty("Accept-Charset", "UTF-8")
        conn.setRequestProperty("User-Agent", "Mozilla/5.0")
        conn.connectTimeout = FEED_TIMEOUT_MS
        conn.readTimeout = FEED_TIMEOUT_MS
        conn.instanceFollowRedirects = true
        val code = conn.responseCode
        if (code in 300..399 && redirects < MAX_REDIRECTS) {
            val location = conn.getHeaderField("Location")
            if (location == null) break
            conn.disconnect()
            url = URL(url, location)
            redirects++
            continue
        }
        if (code !in 200..299) {
            conn.disconnect()
            throw IllegalStateException("HTTP $code fuer $feedUrl")
        }
        break
    }
    val xml = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    conn.disconnect()
    podcastDocumentBuilderFactory().newDocumentBuilder()
        .parse(InputSource(StringReader(xml)))
}

private fun podcastCheckThreshold(): Long = ZonedDateTime.now(ZoneId.systemDefault())
    .minusDays(7)
    .withHour(15)
    .withMinute(30)
    .withSecond(0)
    .withNano(0)
    .toInstant()
    .toEpochMilli()

data class PodcastRawEpisode(
    val title: String,
    val audioUrl: String,
    val publishedAt: Long,
)

fun readPodcastEpisodes(doc: Document, limit: Int = MAX_ITEMS): List<PodcastRawEpisode> {
    val items = doc.getElementsByTagName("item")
    val result = mutableListOf<PodcastRawEpisode>()
    for (i in 0 until minOf(items.length, limit)) {
        val children = items.item(i).childNodes
        var title = ""
        var audioUrl = ""
        var pubDate = ""
        for (j in 0 until children.length) {
            val node = children.item(j)
            when (node.nodeName) {
                "title" -> if (title.isEmpty()) title = node.textContent.trim()
                "enclosure" -> audioUrl =
                    node.attributes?.getNamedItem("url")?.nodeValue ?: ""

                "pubDate" -> pubDate = node.textContent.trim()
                "dc:date" -> if (pubDate.isEmpty()) pubDate = node.textContent.trim()
            }
        }
        if (audioUrl.isEmpty()) continue
        val publishedAt = parsePodcastPubDate(pubDate) ?: continue
        result.add(PodcastRawEpisode(title, audioUrl, publishedAt))
    }
    return result
}

suspend fun runPodcastCheck(context: Context): List<JSONObject> {
    val favs = podcastFavPrefs(context).getString(KEY_FAVS, null)
        ?.let { runCatching { JSONArray(it) }.getOrNull() }
    if (favs == null || favs.length() == 0) {
        storeNewEpisodes(context, emptyList())
        return emptyList()
    }
    val threshold = podcastCheckThreshold()
    val destDir = podcastDestDir()
    destDir.mkdirs()

    val perFeed = coroutineScope {
        (0 until favs.length()).map { i ->
            async(Dispatchers.IO) {
                val feedFound = mutableListOf<JSONObject>()
                try {
                    val o = favs.getJSONObject(i)
                    val feedTitle = o.optString("title")
                    val feedUrl = o.optString("feedUrl")
                    if (feedUrl.isEmpty()) return@async feedFound
                    for (ep in readPodcastEpisodes(fetchPodcastFeed(feedUrl))) {
                        if (ep.publishedAt <= threshold) continue
                        if (isPodcastEpisodeDownloaded(context, ep.audioUrl)) continue
                        if (File(destDir, podcastEpisodeFileName(ep.title)).exists()) continue
                        feedFound.add(
                            JSONObject().apply {
                                put("audioUrl", ep.audioUrl)
                                put("title", ep.title)
                                put("showName", feedTitle)
                                put("publishedAt", ep.publishedAt)
                            }
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    errorInsert(
                        "podcastCheck",
                        "Feed-Abruf fehlgeschlagen: ${e.message}",
                        Instant.now().toString(),
                        "ERROR"
                    )
                }
                feedFound
            }
        }.awaitAll()
    }

    val found = perFeed.flatten().sortedByDescending { it.optLong("publishedAt") }
    storeNewEpisodes(context, found)
    return found
}

fun isUnseenPodcastEpisode(episode: JSONObject, seenAt: Long): Boolean =
    seenAt <= 0L || episode.optLong("publishedAt") > seenAt
