package com.tabslify.tabs.ainotify

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val AI_NOTIFY_TOPIC = "ai_owner"
const val AI_NOTIFY_CHANNEL = "ai_notify"
const val AI_NOTIFY_KIND_INFO = "info"
const val AI_NOTIFY_KIND_QUESTIONS = "questions"
const val AI_NOTIFY_KIND_CANCEL = "cancel"
const val AI_NOTIFY_ANSWERS_TABLE = "ai_notify_answers"
const val AI_NOTIFY_TYPE_SINGLE = "single"
const val AI_NOTIFY_TYPE_MULTI = "multi"
const val AI_NOTIFY_TYPE_TEXT = "text"
const val AI_NOTIFY_CUSTOM = "__eigene_antwort__"

@Serializable
data class AiNotifyQuestion(
    val id: String,
    val text: String,
    val type: String = AI_NOTIFY_TYPE_SINGLE,
    val options: List<String> = emptyList()
)

@Serializable
data class AiNotifySession(
    val sessionId: String,
    val title: String,
    val body: String,
    val source: String,
    val questions: List<AiNotifyQuestion>,
    val receivedAt: Long = System.currentTimeMillis()
)

@Serializable
data class AiNotifyAnswerRow(
    val session_id: String,
    val answers: Map<String, List<String>>
)

object AiNotifyStore {
    private const val PREFS = "ai_notify"
    private const val CANCELLED = "cancelled_ids"

    fun saveSession(context: Context, session: AiNotifySession) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(key(session.sessionId), Json.encodeToString(session))
        }
    }

    fun loadSession(context: Context, sessionId: String): AiNotifySession? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(key(sessionId), null) ?: return null
        return runCatching { Json.decodeFromString<AiNotifySession>(raw) }.getOrNull()
    }

    fun removeSession(context: Context, sessionId: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            remove(key(sessionId))
        }
    }

    fun markCancelled(context: Context, sessionId: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cancelled = prefs.getStringSet(CANCELLED, emptySet()).orEmpty().toMutableSet()
        if (cancelled.size >= 100) cancelled.clear()
        cancelled.add(sessionId)
        prefs.edit { putStringSet(CANCELLED, cancelled) }
    }

    fun isCancelled(context: Context, sessionId: String): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(CANCELLED, emptySet()).orEmpty().contains(sessionId)
    }

    private fun key(sessionId: String) = "session_$sessionId"
}
