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
    GEMINI, NVIDIA, OPENROUTER
}

const val OPENROUTER_DEFAULT_MODEL = "nvidia/nemotron-3-ultra-550b-a55b:free"

enum class AiTarget {
    AITab,
    NotificationReply,
    VocabHint,
    MaterialOcr,
    MaterialSummary,
    HeiseSummary,
    HeiseQa,
    MusicSummary,
    Vision,
    SongHashtags,
    ChargingPrediction
}

fun aiSystemPrompt(target: AiTarget): String = when (target) {
    AiTarget.AITab -> """
        Du wirst per API aus einer Multifunktions-Android-App (names Tabslify) in einem Tab namens AITab aufgerufen. Der Nutzer kann zwischen verschiedenen NVIDIA-Modellen und verschiedenen Gemini-Modellen auswählen – und hat sich für DICH entschieden. Deine Aufgabe ist es, die Frage des Nutzers zu beantworten.
        Wichtige Hinweise:
            * Antworte kurz, klar und auf Deutsch.
            * Sei ein hilfsbereiter Chat-Assistent.
            * Nutze Markdown für Formatierungen (Überschriften, Listen, Fettschrift etc.).
            * Nutze die folgenden Callouts für einprägsame Informationen (immer in einem eigenen Blockquote):
              - [!TIP] oder [!HINT] oder [!IMPORTANT] für Tipps und wichtige Hinweise
              - [!WARNING] oder [!CAUTION] oder [!ATTENTION] für einprägsame Informationen
              - [!INFO] für allgemeine Informationen
              - [!NOTE] für Notizen
              - [!SUCCESS] oder [!CHECK] oder [!DONE] für Erfolgsmeldungen
              - [!DANGER] oder [!ERROR] für Fehler
    """.trimIndent()

    AiTarget.NotificationReply -> """
        Du wirst per API aus einer Multifunktions-Android-App (names Tabslify) von einem Reply System aufgerufen. Deine Aufgabe ist es, die Frage des Nutzers zu beantworten.
        Wichtige Hinweise:
            * Antworte kurz, klar und auf Deutsch.
            * Sei ein hilfsbereiter Chat-Assistent.
    """.trimIndent()

    AiTarget.VocabHint -> """
        Du bist ein Vokabel-Nachschlagewerk in einer Android-App. Antworte auf Deutsch, sehr knapp und ohne Anrede. Gib einen kurzen Beispielsatz mit der Vokabel und eine grammatische Kurzangabe zu Genus und Form, falls erkennbar. Verwende kein Markdown.
    """.trimIndent()

    AiTarget.MaterialOcr -> """
        Du bist ein hochpräzises System zur strukturierten Inhaltsextraktion für nachfolgende LLM-Verarbeitung. Analysiere das bereitgestellte Bild und generiere eine semantisch perfekt aufbereitete Textrekonstruktion. Antworte auf Deutsch, ausschließlich als Markdown, ohne Einleitung oder Vorrede.
    """.trimIndent()

    AiTarget.MaterialSummary -> """
        Du bist ein erfahrener, empathischer Pädagoge und Experte für Didaktik. Deine Aufgabe ist es, den extrahierten Inhalt eines Lernmaterials so aufzubereiten, dass Schüler oder Studierende das Thema intuitiv, tiefgründig und nachhaltig verstehen. Tonfall: motivierend, klar, verständlich, auf Augenhöhe, fehlerfrei auf Deutsch. Vermeide verschachtelte Sätze und kognitive Überlastung. Nutze Markdown.
        Für die kritischen Punkte (Abschnitt 4) gilt Styling-Pflicht: Setze jeden Punkt als Markdown-Callout. Erlaubte Typen sind ausschließlich [!warning], [!danger], [!info], [!tip], [!note] und [!success]. Syntax: `> [!warning] Text`; jede Folgezeile eines Callouts beginnt ebenfalls mit `> `. Andere Typen nicht verwenden, Callouts nicht ineinander verschachteln, nicht in Aufzählungen einrücken und nicht als `> [!warning]` ohne anschließenden Text stehen lassen.
    """.trimIndent()

    AiTarget.HeiseSummary -> """
        Du fasst deutschsprachige Tech-Artikel zusammen. Erstelle eine sehr knappe, gut lesbare deutsche Zusammenfassung mit maximal 3 bis 4 kurzen Bulletpoints (je maximal ein Satz) mit den wichtigsten Fakten, Zahlen, Produkt-/Versionsnamen und Auswirkungen. Bleib strikt beim Inhalt des Textes, erfinde nichts. Keine Füllwörter oder Wiederholungen, insgesamt so kurz wie möglich. Schließe mit einer kurzen Einschätzung in maximal einem Satz ab. Beginne direkt, ohne Einleitung.
        Markiere zusätzlich 4 bis 8 zentrale Fachbegriffe, Produktnamen, Firmen, Personen oder Abkürzungen inline als [[Begriff::Frage]] (Begriff max. 3 Wörter, Frage eine knappe konkrete Rückfrage; kein "[[" "::" "]]" innerhalb von Begriff oder Frage; nur inline in den Bulletpoints, nicht im Einschätzungssatz; kein anderes Markdown-Link-Format).
    """.trimIndent()

    AiTarget.HeiseQa -> """
        Du beantwortest Fragen eines Nutzers zu einem Artikel kurz, präzise und auf Deutsch (2-5 Sätze, bei Bedarf mit Stichpunkten). Nutze primär den Artikeltext als Quelle. Ergänze offensichtliches Allgemeinwissen nur wenn nötig und sag klar, wenn etwas nicht im Artikel steht. Beginne direkt, ohne Einleitung.
    """.trimIndent()

    AiTarget.MusicSummary -> """
        Du bist ein cooler Musik-Assistent. Antworte auf Deutsch, total locker und umgangssprachlich, wie ein Kumpel. Mach 3-5 super knappe Sätze. Verwende Ausdrücke wie 'krass', 'geil', 'richtig lange', 'am Stück'. Red von 'heute' wenn es passt.
    """.trimIndent()

    AiTarget.Vision -> """
        Du extrahierst Vokabeln aus einem Bild. Es gibt ZWEI Spalten: Latein links, Deutsch rechts. Jede Zeile ist ein Vokabelpaar. Ordne jedes lateinische Wort dem deutschen Wort auf derselben vertikalen Position zu. Ignoriere Seitenzahlen (z. B. "116") und wiederholte oder doppelte Blöcke am unteren Rand. Antworte ausschließlich mit einem JSON-Array der Form [{"latein":"...","deutsch":"..."}], ohne Markdown, ohne Erklärung.
    """.trimIndent()

    AiTarget.SongHashtags -> """
        Du bist ein Musik-Metadaten-Dienst. Du antwortest ausschließlich auf Deutsch und gibst ausschließlich Hashtags zurück, ohne Fließtext, ohne Nummerierung und ohne Anführungszeichen.
    """.trimIndent()

    AiTarget.ChargingPrediction -> """
        You are a battery charge-time prediction engine. Output ONLY a single integer: estimated minutes to reach 85%. No explanation, no units, no text.
    """.trimIndent()
}

private suspend fun sendGeminiRequest(
    history: List<ChatMessage> = emptyList(),
    userMessage: String,
    pic: String? = null,
    audioUri: android.net.Uri? = null,
    ctx: Context? = null,
    model: String = DEF_GEMINI,
    onToken: ((String) -> Unit)? = null,
    target: AiTarget = AiTarget.AITab
): String? {
    fun buildGeminiPrompt(history: List<ChatMessage>, userMessage: String) = buildString {
        append(aiSystemPrompt(target))
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

private fun buildChatCompletionsMessages(
    history: List<ChatMessage>,
    userMessage: String,
    pic: String?,
    systemPrompt: String
): JSONArray = JSONArray().apply {
    put(JSONObject().apply {
        put("role", "system")
        put("content", systemPrompt)
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

private suspend fun sendChatCompletionsMessage(
    ctx: Context,
    history: List<ChatMessage>,
    userMessage: String,
    model: String,
    action: String,
    apiKeyName: String,
    logTag: String,
    systemPrompt: String,
    pic: String? = null,
    onToken: ((String) -> Unit)? = null
): String {
    val messages = buildChatCompletionsMessages(history, userMessage, pic, systemPrompt)

    val payload = JSONObject().apply {
        put("model", model)
        put("messages", messages)
        put("temperature", 0.3)
        put("max_tokens", 1024)
        put("stream", onToken != null)
    }

    val requestBody = JSONObject().apply {
        put("action", action)
        put("payload", payload)
        put("apiKey", Config.userApiKey(ctx, apiKeyName))
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
                    logTag,
                    "$logTag proxy failed with code ${connection.responseCode}: $errorText"
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

suspend fun sendOpenrouterMessage(
    context: Context,
    userMessage: String,
    history: List<ChatMessage> = emptyList(),
    systemPrompt: String,
    pic: String? = null,
    model: String? = null,
    onToken: ((String) -> Unit)? = null
): String = sendChatCompletionsMessage(
    ctx = context,
    history = history,
    userMessage = userMessage,
    model = model ?: OPENROUTER_DEFAULT_MODEL,
    action = "openrouter",
    apiKeyName = "openrouter",
    logTag = "OpenRouter",
    systemPrompt = systemPrompt,
    pic = pic,
    onToken = onToken
)

suspend fun sendAiRequest(
    context: Context,
    userMessage: String,
    history: List<ChatMessage> = emptyList(),
    pic: String? = null,
    audioUri: android.net.Uri? = null,
    target: AiTarget = AiTarget.AITab,
    provider: AiProvider? = null,
    serviceKey: String = "default",
    model: String? = null,
    onToken: ((String) -> Unit)? = null
): String? {
    val resolvedProvider = provider ?: when (getPreferredAiProvider(context, serviceKey)) {
        "nvidia" -> AiProvider.NVIDIA
        "openrouter" -> AiProvider.OPENROUTER
        else -> AiProvider.GEMINI
    }
    val systemPrompt = aiSystemPrompt(target)

    return when (resolvedProvider) {
        AiProvider.NVIDIA -> {
            val resolvedModel = model ?: if (pic != null) "meta/llama-3.2-90b-vision-instruct" else "openai/gpt-oss-20b"
            sendChatCompletionsMessage(
                ctx = context,
                history = history,
                userMessage = userMessage,
                model = resolvedModel,
                action = "nvidia",
                apiKeyName = "nvidia",
                logTag = "Nvidia",
                systemPrompt = systemPrompt,
                pic = pic,
                onToken = onToken
            )
        }

        AiProvider.OPENROUTER -> sendOpenrouterMessage(
            context = context,
            userMessage = userMessage,
            history = history,
            systemPrompt = systemPrompt,
            pic = pic,
            model = model,
            onToken = onToken
        )

        AiProvider.GEMINI -> {
            val resolvedModel = model ?: DEF_GEMINI
            sendGeminiRequest(history, userMessage, pic, audioUri, context, resolvedModel, onToken, target)
        }
    }
}

