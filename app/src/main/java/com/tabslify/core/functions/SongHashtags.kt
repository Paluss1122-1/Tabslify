package com.tabslify.core.functions

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.content.edit
import com.tabslify.core.objects.Config
import com.tabslify.privatetabslifyapp.isOnline
import com.tabslify.quiethoursnotificationhelper.AiTarget
import com.tabslify.quiethoursnotificationhelper.aiSystemPrompt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import kotlin.time.Duration.Companion.milliseconds

object SongHashtags {

    const val STATE_IDLE = "idle"
    const val STATE_RUNNING = "running"
    const val STATE_DONE = "done"

    private const val LEGACY_PREFS = "spotify_hashtags"
    private const val STATE_PREFS = "song_hashtag_status"
    private const val KEY_TAGGED = "tagged_paths"
    private const val MODEL = "gemini-2.5-flash"
    private const val LOG_TAG = "SongHashtags"
    private const val REQUEST_TIMEOUT_MS = 25_000L
    private const val PREPARE_TIMEOUT_MS = 180_000L
    private const val BATCH_SIZE = 15
    private const val MAX_TAGS = 8
    private const val SERVICE_NAME = "songHashtags"

    data class SongRef(
        val path: String,
        val uri: Uri,
        val title: String,
        val artist: String,
        val album: String
    )

    data class TagResult(
        val path: String,
        val hashtags: String?,
        val written: Boolean,
        val skipped: Boolean = false
    )

    data class BatchProgress(val done: Int, val total: Int)

    data class PreparedSong(
        val url: String,
        val title: String,
        val artist: String,
        val album: String,
        val hashtags: String,
        val analysisError: String
    )

    data class PrepareOutcome(
        val prepared: PreparedSong?,
        val reason: String?
    )

    suspend fun prepareSong(context: Context, trackId: String): PrepareOutcome {
        if (!isOnline(context)) return PrepareOutcome(null, "Offline")
        if (trackId.isBlank()) return PrepareOutcome(null, "Ungültige Track-ID")

        val body = JSONObject().apply {
            put("action", "song_prepare")
            put("payload", JSONObject().apply { put("trackId", trackId) })
        }

        val response = try {
            withTimeoutOrNull(PREPARE_TIMEOUT_MS.milliseconds) {
                Config.apiProxyPost(context, body, LOG_TAG, PREPARE_TIMEOUT_MS.toInt())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errorInsert(
                SERVICE_NAME,
                "song_prepare fehlgeschlagen: ${e.message}",
                Instant.now().toString(),
                "ERROR"
            )
            null
        }

        if (response == null) return PrepareOutcome(null, "Server antwortet nicht")
        return readPrepareOutcome(response)
    }

    suspend fun releaseSong(context: Context, trackId: String) {
        if (trackId.isBlank()) return
        try {
            val body = JSONObject().apply {
                put("action", "song_release")
                put("payload", JSONObject().apply { put("trackId", trackId) })
            }
            Config.apiProxyPost(context, body, LOG_TAG, 15_000)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(LOG_TAG, "song_release fehlgeschlagen: ${e.message}")
        }
    }

    private fun readPrepareOutcome(response: String): PrepareOutcome {
        return try {
            val json = JSONObject(response)
            when (json.optString("status")) {
                "ready" -> {
                    val url = json.optString("url")
                    if (url.isBlank()) {
                        PrepareOutcome(null, "Server lieferte keine URL")
                    } else {
                        PrepareOutcome(
                            prepared = PreparedSong(
                                url = url,
                                title = json.optString("title", "Track"),
                                artist = json.optString("artist", "Unknown"),
                                album = json.optString("album", ""),
                                hashtags = json.optString("hashtags"),
                                analysisError = json.optString("analysisError")
                            ),
                            reason = null
                        )
                    }
                }

                "too_large" -> {
                    val megabytes = json.optLong("bytes", 0L) / (1024L * 1024L)
                    PrepareOutcome(
                        null,
                        if (megabytes > 0) "zu groß (${megabytes} MB) – ohne KI-Analyse"
                        else "zu groß – ohne KI-Analyse"
                    )
                }

                else -> PrepareOutcome(null, json.optString("error").ifBlank { "Serverfehler" })
            }
        } catch (e: Exception) {
            Log.w(LOG_TAG, "Antwort nicht lesbar: ${e.message}")
            PrepareOutcome(null, "Serverantwort unlesbar")
        }
    }

    suspend fun requestHashtags(
        context: Context,
        title: String,
        artist: String,
        album: String
    ): String? {
        if (!isOnline(context)) return null
        val prompt = "Erstelle 5 passende Hashtags für diesen Song.\n" +
            "Titel: $title\nKünstler: $artist\nAlbum: $album\n\n" +
            "Antworte nur mit den Hashtags, jeweils mit # davor und durch Leerzeichen getrennt."
        return try {
            parseHashtags(geminiText(context, "gemini", prompt, json = false))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errorInsert(
                SERVICE_NAME,
                "Hashtag-Abfrage fehlgeschlagen: ${e.message}",
                Instant.now().toString(),
                "ERROR"
            )
            null
        }
    }

    fun readHashtags(context: Context, uri: Uri, filePath: String?): String? =
        Id3Tags.readHashtags(context, uri, filePath)

    fun readLegacyHashtags(context: Context, trackId: String): String? =
        context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
            .getString(trackId, null)

    fun writeFileHashtags(context: Context, song: SongRef, hashtags: String): Boolean =
        Id3Tags.writeHashtags(context, song.uri, song.path, hashtags)

    fun isMarked(context: Context, path: String): Boolean =
        taggedPaths(context).contains(path)

    fun markTagged(context: Context, path: String) {
        val prefs = context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
        prefs.edit { putStringSet(KEY_TAGGED, taggedPaths(context) + path) }
    }

    fun taggedPaths(context: Context): Set<String> =
        context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_TAGGED, emptySet())?.toSet() ?: emptySet()

    suspend fun tagSongs(
        context: Context,
        songs: List<SongRef>,
        onProgress: suspend (BatchProgress) -> Unit = {}
    ): List<TagResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<TagResult>()
        val pending = mutableListOf<SongRef>()

        songs.forEach { song ->
            if (readHashtags(context, song.uri, song.path) != null ||
                isMarked(context, song.path)
            ) {
                results.add(TagResult(song.path, null, written = false, skipped = true))
            } else {
                pending.add(song)
            }
        }
        var done = results.size
        onProgress(BatchProgress(done, songs.size))

        pending.chunked(BATCH_SIZE).forEach { chunk ->
            val ready = mutableMapOf<String, String>()
            val needsAi = mutableListOf<SongRef>()
            chunk.forEach { song ->
                val stored = readStored(context, song)
                if (stored != null) ready[song.path] = stored else needsAi.add(song)
            }

            val fromAi = requestBatch(context, needsAi)

            chunk.forEach { song ->
                val stored = ready[song.path] ?: fromAi[song.path]
                if (stored != null) {
                    val written = writeFileHashtags(context, song, stored)
                    if (written) markTagged(context, song.path)
                    results.add(TagResult(song.path, stored, written = written))
                }
                done++
                onProgress(BatchProgress(done, songs.size))
            }
        }

        results
    }

    private fun readStored(context: Context, song: SongRef): String? {
        readHashtags(context, song.uri, song.path)?.let { return it }
        val legacyTrackId = Id3Tags.readSpotifyTrackId(context, song.uri, song.path)
        return legacyTrackId?.let { parseHashtags(readLegacyHashtags(context, it)) }
    }

    private suspend fun requestBatch(
        context: Context,
        songs: List<SongRef>
    ): Map<String, String> {
        if (songs.isEmpty()) return emptyMap()
        if (!isOnline(context)) return emptyMap()

        val input = JSONArray()
        songs.forEach { song ->
            input.put(
                JSONObject().apply {
                    put("title", song.title)
                    put("artist", song.artist)
                    put("album", song.album)
                }
            )
        }
        val prompt = "Erstelle für jeden Song 5 passende Hashtags.\n" +
            "Die Songs liegen als JSON-Array vor und die Reihenfolge muss erhalten bleiben.\n" +
            "Antworte ausschließlich als JSON-Array mit einem Objekt pro Song: " +
            "{\"hashtags\": [\"#genre\", ...]}. Kein Text, kein Codeblock.\n" +
            "Eingabe: " + input.toString()

        val parsed = try {
            parseBatchResponse(geminiText(context, "gemini_batch", prompt, json = true), songs.size)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }

        val mapped = mutableMapOf<String, String>()
        if (parsed != null) {
            songs.forEachIndexed { index, song ->
                parseHashtags(parsed[index])?.let { mapped[song.path] = it }
            }
        }
        if (mapped.size == songs.size) return mapped

        songs.forEach { song ->
            if (mapped.containsKey(song.path)) return@forEach
            requestHashtags(context, song.title, song.artist, song.album)
                ?.let { mapped[song.path] = it }
        }
        return mapped
    }

    private fun parseBatchResponse(raw: String?, expected: Int): List<String>? {
        if (raw == null) return null
        return try {
            val text = raw.trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
            val array = JSONArray(text)
            if (array.length() != expected) return null
            (0 until expected).map { index ->
                when (val entry = array.opt(index)) {
                    is JSONArray -> entry.toString()
                    is JSONObject -> entry.optString("hashtags")
                    else -> ""
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun geminiText(
        context: Context,
        action: String,
        prompt: String,
        json: Boolean
    ): String? {
        val body = JSONObject().apply {
            put("action", action)
            put("apiKey", Config.userApiKey(context, "gemini"))
            put("payload", JSONObject().apply {
                put("model", MODEL)
                put("system", aiSystemPrompt(AiTarget.SongHashtags))
                put("parts", JSONArray().put(JSONObject().put("text", prompt)))
                put("temperature", 0.2)
                put("maxOutputTokens", if (json) 2048 else 256)
                if (json) put("json", true)
            })
        }

        val response = withTimeoutOrNull(REQUEST_TIMEOUT_MS.milliseconds) {
            Config.apiProxyPost(context, body, LOG_TAG, REQUEST_TIMEOUT_MS.toInt())
        }
        if (response == null) {
            errorInsert(
                SERVICE_NAME,
                "Keine Antwort von api-proxy ($action)",
                Instant.now().toString(),
                "ERROR"
            )
            return null
        }
        return extractText(response)
    }

    private fun extractText(response: String): String? {
        return try {
            val candidates = JSONObject(response).optJSONArray("candidates") ?: return null
            if (candidates.length() == 0) return null
            val parts = candidates.getJSONObject(0).optJSONObject("content")
                ?.optJSONArray("parts") ?: return null
            val text = buildString {
                for (index in 0 until parts.length()) {
                    parts.optJSONObject(index)?.optString("text")?.let { append(it) }
                }
            }
            text.trim().takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            errorInsert(
                SERVICE_NAME,
                "Antwort nicht lesbar: ${e.message}",
                Instant.now().toString(),
                "ERROR"
            )
            null
        }
    }

    fun parseHashtags(raw: String?): String? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        if (text.contains("[!ERROR]", ignoreCase = true)) return null

        val cleaned = if (text.contains('#')) {
            Regex("#([\\p{L}\\p{N}_-]+)")
                .findAll(text)
                .map { it.groupValues[1].lowercase() }
                .toList()
        } else {
            text.split(Regex("[\\s,;]+"))
                .map { it.trim().trim('#', '"', '\'', '.') }
                .filter { it.length in 2..40 }
                .map { it.lowercase() }
                .toList()
        }

        return cleaned.distinct().take(MAX_TAGS)
            .joinToString(" ") { "#$it" }
            .takeIf { it.isNotBlank() }
    }
}