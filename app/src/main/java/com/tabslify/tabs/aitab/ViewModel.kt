package com.tabslify.tabs.aitab

import android.app.Application
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tabslify.R
import com.tabslify.core.objects.prvt
import com.tabslify.privatetabslifyapp.isOnline
import com.tabslify.quiethoursnotificationhelper.AiProvider
import com.tabslify.quiethoursnotificationhelper.AiTarget
import com.tabslify.quiethoursnotificationhelper.askServer
import com.tabslify.quiethoursnotificationhelper.sendAiRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.util.Calendar
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds

const val DAILY_LIMIT = 30
private const val USAGE_RESET_MS = 6 * 60 * 60 * 1000L
private const val USAGE_PREFS = "ai_usage"
private const val USAGE_KEY_COUNT = "usage_count"
private const val USAGE_KEY_RESET_AT = "usage_reset_at"
private const val TOKEN_THROTTLE_MS = 33L

data class AiUsage(
    val count: Int,
    val resetAt: Long,
    val remainingHours: Int,
    val remainingMinutes: Int
)

fun readAiUsage(ctx: Context): AiUsage {
    val prefs = ctx.getSharedPreferences(USAGE_PREFS, MODE_PRIVATE)
    val now = System.currentTimeMillis()
    val savedResetAt = prefs.getLong(USAGE_KEY_RESET_AT, 0L)
    val shouldReset = savedResetAt == 0L || now - savedResetAt >= USAGE_RESET_MS
    val resetAt = if (shouldReset) now else savedResetAt
    val count = if (shouldReset) 0 else prefs.getInt(USAGE_KEY_COUNT, 0)
    val remaining = resetAt + USAGE_RESET_MS - now
    return AiUsage(
        count = count,
        resetAt = resetAt,
        remainingHours = if (remaining <= 0) 0 else (remaining / 3_600_000).toInt(),
        remainingMinutes = if (remaining <= 0) 0 else ((remaining % 3_600_000) / 60_000).toInt()
    )
}

fun writeAiUsage(ctx: Context, count: Int, resetAt: Long) {
    ctx.getSharedPreferences(USAGE_PREFS, MODE_PRIVATE).edit {
        putInt(USAGE_KEY_COUNT, count)
        putLong(USAGE_KEY_RESET_AT, resetAt)
    }
}

fun aiUsageText(ctx: Context, usage: AiUsage): String {
    if (usage.remainingHours <= 0 && usage.remainingMinutes <= 0) {
        return ctx.getString(R.string.wird_gleich_zuruckgesetzt)
    }
    val resetTime = Calendar.getInstance().apply { timeInMillis = usage.resetAt + USAGE_RESET_MS }
    val hh = resetTime.get(Calendar.HOUR_OF_DAY).toString().padStart(2, '0')
    val mm = resetTime.get(Calendar.MINUTE).toString().padStart(2, '0')
    return ctx.getString(R.string.reset_um_h_m, hh, mm, usage.remainingHours, usage.remainingMinutes)
}

fun reserveAiUsage(ctx: Context, weight: Int): Boolean {
    val usage = readAiUsage(ctx)
    if (usage.count + weight > DAILY_LIMIT) return false
    writeAiUsage(ctx, usage.count + weight, usage.resetAt)
    return true
}

@Serializable
data class ChatMessage(
    val text: String,
    val ts: Long,
    val own: Boolean,
    val mode: String? = null
)

class AITabViewModel(application: Application) : AndroidViewModel(application) {
    var currentMode by mutableStateOf("Nvidia")
    var currentMsg by mutableStateOf("")
    var isLoading by mutableStateOf(false)
    var selectedImageUri by mutableStateOf<Uri?>(null)
    var selectedAudioUri by mutableStateOf<Uri?>(null)
    var showAiModels by mutableStateOf(false)
    var editIndex: Int? by mutableStateOf(null)
    var isEditMode by mutableStateOf(false)
    var currentEditMsg by mutableStateOf("")
    var selectedMsg: Int? by mutableStateOf(null)
    var lastSelectedMsg: Int? by mutableStateOf(null)
    val history = mutableStateListOf<ChatMessage>()
    var streamSeq by mutableLongStateOf(0L)

    val availableModels
        get() = when (currentMode) {
            "Nvidia" -> nvidiaModels
            "OpenRouter" -> openrouterModels
            "Server" -> serverModels
            "Gemini" -> geminiModels
            else -> emptyList()
        }

    var selectedModel by mutableStateOf(availableModels[0])

    var todayUsage by mutableIntStateOf(0)
    var usageResetAt by mutableLongStateOf(0L)

    var historyLoaded = false

    var showLimitReached by mutableStateOf(false)

    fun loadHistory() {
        if (historyLoaded) return
        historyLoaded = true
        val ctx = getApplication<Application>()
        checkUsageResetIfNeeded(ctx)
        val json = ctx.getSharedPreferences("ai_prefs", MODE_PRIVATE)
            .getString("ai_history", null) ?: return
        try {
            history.addAll(Json.decodeFromString<List<ChatMessage>>(json))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateTodayUsage(count: Int) {
        todayUsage = count
        writeAiUsage(getApplication(), count, usageResetAt)
    }

    fun checkUsageResetIfNeeded(ctx: Context) {
        val usage = readAiUsage(ctx)
        usageResetAt = usage.resetAt
        todayUsage = usage.count
        writeAiUsage(ctx, usage.count, usage.resetAt)
    }

    fun getUsageProgress(): Float = (todayUsage.toFloat() / DAILY_LIMIT).coerceIn(0f, 1f)

    fun getUsageResetText(): String = aiUsageText(getApplication(), readAiUsage(getApplication()))

    fun setMode(mode: String) {
        if (currentMode == mode) return
        currentMode = mode
        selectedModel = availableModels[0]
    }

    fun selectModel(model: Model) {
        selectedModel = model
        if (!model.vision) selectedImageUri = null
        if (!model.audio) selectedAudioUri = null
    }

    fun clearHistory() {
        history.clear()
        persistHistory()
    }

    fun sendMessage() {
        val ctx = getApplication<Application>()
        val userText = if (isEditMode) currentEditMsg else currentMsg.trim()
        val fetchedEditIndex = editIndex
        if (isEditMode && fetchedEditIndex != null) {
            val toRemove = history.drop(fetchedEditIndex)
            history.removeAll(toRemove)
        }
        isEditMode = false
        if (userText.isEmpty() && selectedImageUri == null) return

        val isServer = currentMode == "Server"
        val isPrivate = prvt()

        if (!isServer && !isPrivate) {
            checkUsageResetIfNeeded(ctx)
            if (todayUsage + selectedModel.weight > DAILY_LIMIT) {
                showLimitReached = true
                return
            }
        }

        val modeAtSend = currentMode

        history.add(
            ChatMessage(
                text = userText.ifEmpty { ctx.getString(R.string.beschreibe_das_bild) },
                ts = System.currentTimeMillis(),
                own = true
            )
        )
        persistHistory()
        currentMsg = ""
        isLoading = true

        if (!isServer && !isPrivate) {
            updateTodayUsage(todayUsage + selectedModel.weight)
        }

        val placeholderTs = System.currentTimeMillis()
        history.add(ChatMessage("", placeholderTs, false, modeAtSend))
        val placeholderIndex = history.lastIndex

        val buffer = StringBuilder()
        var lastFlushMs = 0L

        val onToken: (String) -> Unit = { delta ->
            buffer.append(delta)
            val now = System.currentTimeMillis()
            if (now - lastFlushMs >= TOKEN_THROTTLE_MS) {
                lastFlushMs = now
                history[placeholderIndex] = ChatMessage(
                    text = buffer.toString(),
                    ts = placeholderTs,
                    own = false,
                    mode = modeAtSend
                )
                streamSeq = System.nanoTime()
            }
        }

        viewModelScope.launch {
            try {
                val effectivePic = if (selectedModel.vision && selectedImageUri != null) {
                    withContext(Dispatchers.IO) { selectedImageUri?.let { encodeImage(ctx, it) } }
                } else null
                val response = withContext(Dispatchers.IO) {
                    send(
                        ctx,
                        userText.ifEmpty { ctx.getString(R.string.beschreibe_das_bild) },
                        effectivePic,
                        onToken
                    )
                }
                selectedImageUri = null
                selectedAudioUri = null

                if (placeholderIndex < history.size) {
                    history[placeholderIndex] = ChatMessage(
                        text = response.ifBlank { ctx.getString(R.string.fehler) },
                        ts = placeholderTs,
                        own = false,
                        mode = modeAtSend
                    )
                }

                sendAITabBackgroundNotification(
                    ctx,
                    title = ctx.getString(R.string.aitab_answer),
                    message = response
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (placeholderIndex < history.size) {
                    history[placeholderIndex] = ChatMessage(
                        ctx.getString(R.string.fehler_msg, e.message),
                        placeholderTs,
                        false,
                        modeAtSend
                    )
                }
            } finally {
                isLoading = false
                persistHistory()
            }
        }
    }

    private suspend fun send(
        ctx: Context,
        txt: String,
        pic: String?,
        onToken: (String) -> Unit
    ): String {
        if (!isOnline(ctx)) return ctx.getString(R.string.kein_netzwerk)
        return when (currentMode) {
            "Nvidia" -> sendAiRequest(
                ctx,
                txt,
                history,
                pic,
                target = AiTarget.AITab,
                model = selectedModel.realname,
                provider = AiProvider.NVIDIA,
                onToken = onToken
            ) ?: ctx.getString(R.string.fehler)

            "Server" -> askServer(history, txt, selectedModel.realname, pic)
            "OpenRouter" -> sendAiRequest(
                ctx,
                txt,
                history,
                pic,
                target = AiTarget.AITab,
                model = selectedModel.realname,
                provider = AiProvider.OPENROUTER,
                onToken = onToken
            ) ?: ctx.getString(R.string.fehler)
            "Gemini" -> sendAiRequest(
                context = ctx,
                userMessage = txt,
                history = history,
                pic = pic,
                audioUri = selectedAudioUri,
                target = AiTarget.AITab,
                model = selectedModel.realname,
                provider = AiProvider.GEMINI,
                onToken = onToken
            ) ?: ctx.getString(R.string.fehler)

            else -> ctx.getString(R.string.wahle_einen_modus)
        }
    }

    private fun persistHistory() {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            val json = Json.encodeToString(history.toList())
            ctx.getSharedPreferences("ai_prefs", MODE_PRIVATE)
                .edit { putString("ai_history", json) }
        }
    }

    private fun encodeImage(ctx: Context, uri: Uri): String? = try {
        val (outWidth, outHeight) = ctx.contentResolver.openInputStream(uri)?.use { input ->
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(input, null, bounds)
            bounds.outWidth to bounds.outHeight
        } ?: return null
        if (outWidth <= 0 || outHeight <= 0) return null

        val targetDimension = 1280
        val maxDimension = maxOf(outWidth, outHeight)
        var sampleSize = 1
        while (maxDimension / (sampleSize * 2) >= targetDimension) sampleSize *= 2

        ctx.contentResolver.openInputStream(uri)?.use { input ->
            val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bmp = BitmapFactory.decodeStream(input, null, opts) ?: return null
            try {
                val output = ByteArrayOutputStream()
                android.util.Base64OutputStream(output, Base64.NO_WRAP).use { base64Out ->
                    bmp.compress(Bitmap.CompressFormat.JPEG, 90, base64Out)
                }
                output.toString("UTF-8")
            } finally {
                bmp.recycle()
            }
        }
    } catch (_: Exception) {
        null
    }

    suspend fun animateAlpha(alpha: Animatable<Float, AnimationVector1D>) {
        delay(100.milliseconds)
        alpha.animateTo(
            1f,
            animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing)
        )
    }
}
