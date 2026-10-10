package com.tabslify.core.functions

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.tabslify.core.activities.Tabslify.Companion.appScope
import com.tabslify.core.objects.Config
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.json.JSONObject
import java.time.Instant

const val PODCAST_PROGRESS_PREFS = "podcast_progress"

private const val KEY_COMPLETED_PREFIX = "completed_"
private const val FILE_KEY_PREFIX = "file:"
private const val PROGRESS_TABLE = "podcast_progress"
private const val PROGRESS_COLUMNS =
    "key,audio_url,file_name,episode_title,show_name,source,completed_at"

data class PodcastCompletion(
    val key: String,
    val audioUrl: String,
    val fileName: String,
    val episodeTitle: String,
    val showName: String,
    val source: String,
    val completedAt: Long,
)

@Serializable
@Suppress("PropertyName")
private data class PodcastProgressRow(
    val key: String = "",
    val audio_url: String = "",
    val file_name: String = "",
    val episode_title: String = "",
    val show_name: String = "",
    val source: String = "",
    val completed_at: Long = 0L,
)

fun podcastProgressPrefs(context: Context): SharedPreferences =
    context.getSharedPreferences(PODCAST_PROGRESS_PREFS, Context.MODE_PRIVATE)

fun podcastFileCompletionKey(fileName: String) = FILE_KEY_PREFIX + fileName

fun podcastCompletionKey(audioUrl: String, fileName: String): String =
    audioUrl.ifBlank { podcastFileCompletionKey(fileName) }

fun loadPodcastCompletions(context: Context): Map<String, PodcastCompletion> {
    val result = mutableMapOf<String, PodcastCompletion>()
    for ((key, value) in podcastProgressPrefs(context).all) {
        if (!key.startsWith(KEY_COMPLETED_PREFIX) || value !is String) continue
        val raw = runCatching { JSONObject(value) }.getOrNull() ?: continue
        val completionKey = key.removePrefix(KEY_COMPLETED_PREFIX)
        result[completionKey] = PodcastCompletion(
            key = completionKey,
            audioUrl = raw.optString("audio_url"),
            fileName = raw.optString("file_name"),
            episodeTitle = raw.optString("episode_title"),
            showName = raw.optString("show_name"),
            source = raw.optString("source"),
            completedAt = raw.optLong("completed_at")
        )
    }
    return result
}

fun markPodcastEpisodeCompleted(
    context: Context,
    audioUrl: String,
    fileName: String,
    episodeTitle: String,
    showName: String,
    source: String,
    at: Long = System.currentTimeMillis()
) {
    val appContext = context.applicationContext
    val completion = PodcastCompletion(
        key = podcastCompletionKey(audioUrl, fileName),
        audioUrl = audioUrl,
        fileName = fileName,
        episodeTitle = episodeTitle,
        showName = showName,
        source = source,
        completedAt = at
    )
    storePodcastCompletion(appContext, completion)
    appScope.launch(Dispatchers.IO) { pushPodcastCompletions(listOf(completion)) }
}

suspend fun syncPodcastProgress(context: Context): Set<String> = withContext(Dispatchers.IO) {
    val appContext = context.applicationContext
    val local = loadPodcastCompletions(appContext)
    pushPodcastCompletions(local.values.toList())
    val remote = runCatching { pullPodcastCompletions() }.getOrDefault(emptyList())
    val merged = local.toMutableMap()
    for (completion in remote) {
        val existing = merged[completion.key]
        if (existing == null || completion.completedAt > existing.completedAt) {
            merged[completion.key] = completion
        }
    }
    merged.forEach { (key, completion) ->
        if (local[key] != completion) storePodcastCompletion(appContext, completion)
    }
    merged.keys
}

private fun storePodcastCompletion(context: Context, completion: PodcastCompletion) {
    podcastProgressPrefs(context).edit {
        putString(
            KEY_COMPLETED_PREFIX + completion.key,
            JSONObject().apply {
                put("audio_url", completion.audioUrl)
                put("file_name", completion.fileName)
                put("episode_title", completion.episodeTitle)
                put("show_name", completion.showName)
                put("source", completion.source)
                put("completed_at", completion.completedAt)
            }.toString()
        )
    }
}

private suspend fun pushPodcastCompletions(completions: List<PodcastCompletion>) {
    if (completions.isEmpty()) return
    val payload = buildJsonArray {
        completions.forEach { completion ->
            add(
                buildJsonObject {
                    put("key", completion.key)
                    put("audio_url", completion.audioUrl)
                    put("file_name", completion.fileName)
                    put("episode_title", completion.episodeTitle)
                    put("show_name", completion.showName)
                    put("source", completion.source)
                    put("completed_at", completion.completedAt)
                }
            )
        }
    }
    runCatching {
        Config.safeCall {
            Config.client.from(PROGRESS_TABLE).upsert(payload) { onConflict = "key" }
        }
    }.onFailure {
        errorInsert(
            "pushPodcastCompletions",
            "Sync fehlgeschlagen: ${it.message}",
            Instant.now().toString(),
            "ERROR"
        )
    }
}

private suspend fun pullPodcastCompletions(): List<PodcastCompletion> = Config.safeCall {
    Config.client.from(PROGRESS_TABLE)
        .select(Columns.list(PROGRESS_COLUMNS))
        .decodeList<PodcastProgressRow>()
        .map { row ->
            PodcastCompletion(
                key = row.key,
                audioUrl = row.audio_url,
                fileName = row.file_name,
                episodeTitle = row.episode_title,
                showName = row.show_name,
                source = row.source,
                completedAt = row.completed_at
            )
        }
}
