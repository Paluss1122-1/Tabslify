package com.tabslify.quiethoursnotificationhelper

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.RequestTimeoutException
import com.google.firebase.ai.type.ServerException
import com.google.firebase.ai.type.content
import com.tabslify.core.objects.Config
import com.tabslify.core.objects.Config.DEF_GEMINI
import com.tabslify.tabs.aitab.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

enum class AiProvider {
    GEMINI, NVIDIA
}

private fun buildSystemPrompt(target: String = ""): String {
    val aiTab = if (target == "AITab") " in einem Tab namens AITab" else ""
    val aiTabInfo =
        if (target == "AITab") " Der Nutzer kann zwischen verschiedenen NVIDIA-Modellen und verschiedenen Gemini-Modellen auswählen – und hat sich für DICH entschieden." else ""
    val notif = if (target == "notif") " von einem Reply System" else ""
    return buildString {
        append(
            """Du wirst per API aus einer Multifunktions-Android-App (names Tabslify)${aiTab}${notif} aufgerufen.${aiTabInfo} Deine Aufgabe ist es, die Frage des Nutzers zu beantworten.
                    Wichtige Hinweise:
                        * Antworte kurz, klar und auf Deutsch.
                        * Sei ein hilfsbereiter Chat-Assistent."""
        )
        if (target == "AITab") {
            append(
                """* Nutze Markdown für Formatierungen (Überschriften, Listen, Fettschrift etc.).
                        * Nutze die folgenden Callouts für einprägsame Informationen (immer in einem eigenen Blockquote):
                          - [!TIP] oder [!HINT] oder [!IMPORTANT] für Tipps und wichtige Hinweise
                          - [!WARNING] oder [!CAUTION] oder [!ATTENTION] für einprägsame Informationen
                          - [!INFO] für allgemeine Informationen
                          - [!NOTE] für Notizen
                          - [!SUCCESS] oder [!CHECK] oder [!DONE] für Erfolgsmeldungen
                          - [!DANGER] oder [!ERROR] für Fehler""${'"'}"""
            )
        }
    }
}

private suspend fun sendGeminiRequest(
    history: List<ChatMessage> = emptyList(),
    userMessage: String,
    pic: String? = null,
    audioUri: android.net.Uri? = null,
    ctx: Context? = null,
    anlytic: Boolean = false,
    model: String = DEF_GEMINI,
    onToken: ((String) -> Unit)? = null,
    target: String = "AITab"
): String? {
    fun buildGeminiPrompt(history: List<ChatMessage>, userMessage: String) = buildString {
        if (anlytic) {
            append("Du bist ein cooler Musik-Assistent. Antworte auf Deutsch, total locker und umgangssprachlich, wie ein Kumpel. Mach 3-5 super knappe Sätze. Verwende Ausdrücke wie 'krass', 'geil', 'richtig lange', 'am Stück'. Red von 'heute' wenn es passt.\n\n")
        } else {
            append(buildSystemPrompt(target))
        }
        history.forEach { msg ->
            append(if (msg.own) "User: " else "Assistant: ")
            append(msg.text)
            append("\n")
        }
        append("\nUser: $userMessage")
    }

    val generativeModel = Firebase.ai(backend = GenerativeBackend.googleAI())
        .generativeModel(model)

    val promptText = buildGeminiPrompt(history, userMessage)
    val bmp = pic?.let { Base64.decode(it, Base64.NO_WRAP) }
        ?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    val (audioBytes, audioMimeType) = if (audioUri != null && ctx != null) {
        val mimeType = ctx.contentResolver.getType(audioUri) ?: "audio/mp3"
        val bytes = ctx.contentResolver.openInputStream(audioUri)?.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
            }
            output.toByteArray()
        }
        bytes to mimeType
    } else {
        null to null
    }

    val requestContent = content {
        bmp?.let { image(it) }
        audioBytes?.let {
            if (audioMimeType != null) {
                inlineData(it, audioMimeType)
            }
        }
        text(promptText)
    }

    return try {
        if (onToken != null) {
            val sb = StringBuilder()
            generativeModel.generateContentStream(requestContent).collect { chunk ->
                val delta = chunk.text ?: ""
                if (delta.isNotEmpty()) {
                    sb.append(delta)
                    withContext(Dispatchers.Main) { onToken(delta) }
                }
            }
            sb.toString().ifBlank { null }
        } else {
            generativeModel.generateContent(requestContent).text
        }
    } catch (e: ServerException) {
        if (e.message != null && e.message!!.contains("This model is currently experiencing high demand")) {
            return "[!ERROR] This model is currently experiencing high demand"
        }
        Log.e("GeminiAI", "generateContent failed for model=$model target=$target: ${e.message}", e)
        null
    }catch (e: RequestTimeoutException) {
        if (e.message != null && e.message!!.contains("The request failed to complete in the allotted time")) {
            return "[!ERROR] The request failed to complete in the allotted time"
        }
        Log.e("GeminiAI", "generateContent failed for model=$model target=$target: ${e.message}", e)
        null
    } catch (e: Exception) {
        Log.e("GeminiAI", "generateContent failed for model=$model target=$target: ${e.message}", e)
        null
    }
}

private fun buildNvidiaAITabMessages(
    history: List<ChatMessage>,
    userMessage: String,
    pic: String?
): JSONArray = JSONArray().apply {
    put(JSONObject().apply {
        put("role", "system")
        put(
            "content",
            "Du bist ein hilfreicher Chat-Assistent. Antworte kurz, klar und auf Deutsch und verwende keine Markdown Syntax."
        )
    })

    history.forEach { msg ->
        put(JSONObject().apply {
            put("role", if (msg.own) "user" else "assistant")
            put("content", msg.text)
        })
    }

    put(JSONObject().apply {
        put("role", "user")
        if (pic != null) {
            put("content", JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "text")
                    put("text", userMessage)
                })
                put(JSONObject().apply {
                    put("type", "image_url")
                    put("image_url", JSONObject().apply {
                        put("url", "data:image/jpeg;base64,$pic")
                    })
                })
            })
        } else {
            put("content", userMessage)
        }
    })
}

private suspend fun sendNvidiaChatMessageAITab(
    ctx: Context,
    history: List<ChatMessage>,
    userMessage: String,
    model: String,
    pic: String? = null,
    onToken: ((String) -> Unit)? = null
): String {
    val messages = buildNvidiaAITabMessages(history, userMessage, pic)

    val payload = JSONObject().apply {
        put("model", model)
        put("messages", messages)
        put("temperature", 0.3)
        put("max_tokens", 1024)
        put("stream", onToken != null)
    }

    val requestBody = JSONObject().apply {
        put("action", "nvidia")
        put("payload", payload)
        put("apiKey", Config.userApiKey(ctx, "nvidia"))
    }.toString()

    return withContext(Dispatchers.IO) {
        val connection = Config.openApiProxyConnection(ctx, 60_000)
            ?: return@withContext "Proxy Connection Setup Failed (Signature NULL)"
        try {
            connection.outputStream.use { it.write(requestBody.toByteArray(Charsets.UTF_8)) }

            if (connection.responseCode != 200) {
                val errorText =
                    connection.errorStream?.bufferedReader()?.readText() ?: "No error body"
                Log.e(
                    "AI",
                    "Nvidia proxy failed with code ${connection.responseCode}: $errorText"
                )
                
                try {
                    val jsonObj = JSONObject(errorText)
                    if (jsonObj.has("error")) {
                        val err = jsonObj.get("error")
                        if (err is JSONObject && err.has("message")) {
                            return@withContext "API Error: " + err.getString("message")
                        } else if (err is String) {
                            return@withContext "API Error: $err"
                        }
                    }
                } catch (_: Exception) {}
                
                return@withContext "Error ${connection.responseCode}: $errorText"
            }

            if (onToken != null) {
                val sb = StringBuilder()
                var apiError: String? = null
                connection.inputStream.bufferedReader().useLines { lines ->
                    for (line in lines) {
                        if (!line.startsWith("data:")) continue
                        val data = line.removePrefix("data:").trim()
                        if (data == "[DONE]") break
                        try {
                            val jsonObj = JSONObject(data)
                            if (jsonObj.has("error")) {
                                val err = jsonObj.get("error")
                                apiError = if (err is JSONObject && err.has("message")) {
                                    err.getString("message")
                                } else err as? String ?: err.toString()
                                break
                            }
                            
                            val delta = jsonObj
                                .getJSONArray("choices")
                                .getJSONObject(0)
                                .getJSONObject("delta")
                                .optString("content", "")
                            if (delta.isNotEmpty()) {
                                sb.append(delta)
                                withContext(Dispatchers.Main) { onToken(delta) }
                            }
                        } catch (_: Exception) {
                        }
                    }
                }
                if (apiError != null) return@withContext "API Error: $apiError"
                sb.toString().ifBlank { "Stream returned 200 OK but was empty." }
            } else {
                val response = JSONObject(connection.inputStream.bufferedReader().readText())
                    .getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").getString("content").trim()
                    .ifBlank { "Response was 200 OK but empty." }
                response
            }
        } finally {
            connection.disconnect()
        }
    }
}

fun getPreferredAiProvider(context: Context, serviceKey: String): String {
    val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    val global = prefs.getString("ai_pref_global", "gemini") ?: "gemini"
    val specific = prefs.getString("ai_pref_service_$serviceKey", "default") ?: "default"
    return if (specific == "default") global else specific
}

suspend fun sendAiRequest(
    context: Context,
    userMessage: String,
    history: List<ChatMessage> = emptyList(),
    pic: String? = null,
    audioUri: android.net.Uri? = null,
    target: String = "AITab",
    anlytic: Boolean = false,
    provider: AiProvider? = null,
    serviceKey: String = "default",
    model: String? = null,
    onToken: ((String) -> Unit)? = null
): String? {
    val resolvedProvider = provider ?: if (getPreferredAiProvider(context, serviceKey) == "nvidia") AiProvider.NVIDIA else AiProvider.GEMINI
    
    return if (resolvedProvider == AiProvider.NVIDIA) {
        val resolvedModel = model ?: if (pic != null) "meta/llama-3.2-90b-vision-instruct" else "openai/gpt-oss-20b"
        sendNvidiaChatMessageAITab(context, history, userMessage, resolvedModel, pic, onToken)
    } else {
        val resolvedModel = model ?: DEF_GEMINI
        sendGeminiRequest(history, userMessage, pic, audioUri, context, anlytic, resolvedModel, onToken, target)
    }
}

