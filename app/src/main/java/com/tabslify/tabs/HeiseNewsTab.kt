package com.tabslify.tabs

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.text.Html
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.core.net.toUri
import coil.compose.AsyncImage
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.tabslify.R
import com.tabslify.core.TabNavigationViewModel
import com.tabslify.core.objects.Config
import com.tabslify.core.objects.prvt
import com.tabslify.quiethoursnotificationhelper.AiProvider
import com.tabslify.quiethoursnotificationhelper.sendAiRequest
import com.tabslify.tabs.aitab.ChatMessage
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val HEISE_DEVELOPER_FEED_URL = "https://www.heise.de/developer/feed.xml"
private const val HEISE_IT_FEED_URL = "https://www.heise.de/rss/heise-Rubrik-IT-atom.xml"
private const val SRF_INTERNATIONAL_FEED_URL = "https://www.srf.ch/news/bnf/rss/1922"
private const val SRF_WIRTSCHAFT_FEED_URL = "https://www.srf.ch/news/bnf/rss/1926"
private const val SRF_TECHNIK_FEED_URL = "https://www.srf.ch/bnf/rss/19920122"
private const val SRF_IMAGE_SMALL_SEGMENT = "/320ws/"
private const val SRF_IMAGE_LARGE_SEGMENT = "/640ws/"
private const val ARTICLE_TEXT_LIMIT_FOR_AI = 18_000
private const val PARAGRAPH_BREAK_MARKER = "@@HEISE_PARA_BREAK@@"

private const val EMAIL_ARTICLE_LINK_PREFIX = "tabslify-email://"
private const val HEISE_DAILY_NEWSLETTER_SENDER = "heise-online-daily@newsletter.heise.de"
private const val HEISE_DEVELOPER_NEWSLETTER_SENDER = "newsletter@newsletter.heise.de"
private const val NEWSLETTER_EMAIL_LIMIT = 16L
private const val NEWSLETTER_EMAIL_SUMMARY_LIMIT = 260

private data class NewsletterSource(
    val senderFragment: String,
    val label: String,
    val useHeiseLogo: Boolean = false
)

private val NEWSLETTER_SOURCES = listOf(
    NewsletterSource(HEISE_DAILY_NEWSLETTER_SENDER, "📧 heise daily", useHeiseLogo = true),
    NewsletterSource(HEISE_DEVELOPER_NEWSLETTER_SENDER, "📧 heise Developer", useHeiseLogo = true)
)

private fun newsletterSourceFor(fromAddr: String): NewsletterSource? =
    NEWSLETTER_SOURCES.firstOrNull { fromAddr.contains(it.senderFragment, ignoreCase = true) }

private enum class NewsFeedFormat { ATOM, RSS }

private data class NewsFeed(
    val url: String,
    val source: String,
    val format: NewsFeedFormat,
    val maxItems: Int = Int.MAX_VALUE
)

private val NEWS_FEEDS = listOf(
    NewsFeed(HEISE_DEVELOPER_FEED_URL, "heise developer", NewsFeedFormat.ATOM),
    NewsFeed(HEISE_IT_FEED_URL, "heise IT", NewsFeedFormat.ATOM),
    NewsFeed(SRF_INTERNATIONAL_FEED_URL, "SRF International", NewsFeedFormat.RSS, maxItems = 25),
    NewsFeed(SRF_WIRTSCHAFT_FEED_URL, "SRF Wirtschaft", NewsFeedFormat.RSS, maxItems = 25),
    NewsFeed(SRF_TECHNIK_FEED_URL, "SRF Technik", NewsFeedFormat.RSS, maxItems = 15)
)

private data class MdLink(val range: IntRange, val url: String)

private fun parseInlineMarkdown(line: String): Pair<androidx.compose.ui.text.AnnotatedString, List<MdLink>> {
    val builder = androidx.compose.ui.text.AnnotatedString.Builder()
    val links = mutableListOf<MdLink>()
    var i = 0
    while (i < line.length) {
        val c = line[i]
        when {
            line.startsWith("**", i) -> {
                val end = line.indexOf("**", i + 2)
                if (end == -1) {
                    builder.append(c); i++
                } else {
                    builder.pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    builder.append(line.substring(i + 2, end))
                    builder.pop()
                    i = end + 2
                }
            }

            c == '*' -> {
                val end = line.indexOf('*', i + 1)
                if (end == -1) {
                    builder.append(c); i++
                } else {
                    builder.pushStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic))
                    builder.append(line.substring(i + 1, end))
                    builder.pop()
                    i = end + 1
                }
            }

            c == '[' -> {
                val closeBracket = line.indexOf(']', i + 1)
                if (closeBracket == -1 || closeBracket + 1 >= line.length || line[closeBracket + 1] != '(') {
                    builder.append(c); i++
                } else {
                    val closeParen = line.indexOf(')', closeBracket + 2)
                    if (closeParen == -1) {
                        builder.append(c); i++
                    } else {
                        val label = line.substring(i + 1, closeBracket)
                        val url = line.substring(closeBracket + 2, closeParen)
                        val start = builder.length
                        builder.pushStyle(
                            SpanStyle(
                                color = Color(0xFF64B5F6),
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        builder.append(label)
                        builder.pop()
                        links.add(MdLink(start until (start + label.length), url))
                        i = closeParen + 1
                    }
                }
            }

            else -> {
                builder.append(c); i++
            }
        }
    }
    return builder.toAnnotatedString() to links
}

@Composable
private fun DottedMarkdownText(
    content: String,
    textColor: Color,
    onLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 14.sp,
    lineHeight: androidx.compose.ui.unit.TextUnit = 19.sp
) {
    Column(modifier) {
        content.split("\n").forEach { rawLine ->
            if (rawLine.isBlank()) {
                Spacer(Modifier.height(6.dp))
                return@forEach
            }
            val isBullet =
                rawLine.trimStart().startsWith("* ") || rawLine.trimStart().startsWith("- ")
            val lineText = if (isBullet) {
                rawLine.trimStart().removePrefix("* ").removePrefix("- ")
            } else rawLine

            val (annotated, links) = parseInlineMarkdown(lineText)

            var layoutResult by remember(annotated) {
                mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                if (isBullet) {
                    Text(
                        text = "•  ",
                        color = textColor,
                        fontSize = fontSize,
                        lineHeight = lineHeight
                    )
                }
                Text(
                    text = annotated,
                    color = textColor,
                    fontSize = fontSize,
                    lineHeight = lineHeight,
                    onTextLayout = { layoutResult = it },
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .then(
                            if (links.isNotEmpty()) {
                                Modifier
                                    .drawWithContent {
                                        drawContent()
                                        val lr = layoutResult ?: return@drawWithContent
                                        val strokeWidth = 2.8.dp.toPx()
                                        val dashWidth = 0.3.dp.toPx()
                                        val gapWidth = 7.dp.toPx()
                                        val paint = Paint().apply {
                                            this.color = Color(0xFF64B5F6).toArgb()
                                            this.strokeWidth = strokeWidth
                                            this.style = Paint.Style.STROKE
                                            this.strokeCap = Paint.Cap.ROUND
                                            this.pathEffect = android.graphics.DashPathEffect(
                                                floatArrayOf(dashWidth, gapWidth), 0f
                                            )
                                            this.isAntiAlias = true
                                        }
                                        drawIntoCanvas { canvas ->
                                            links.forEach { link ->
                                                val startOffset = link.range.first
                                                val endOffset = link.range.last + 1
                                                if (startOffset >= lr.layoutInput.text.length) return@forEach
                                                val startLine = lr.getLineForOffset(startOffset)
                                                val endLine =
                                                    lr.getLineForOffset(endOffset.coerceAtMost(lr.layoutInput.text.length))
                                                for (line in startLine..endLine) {
                                                    val lineStart =
                                                        if (line == startLine) lr.getHorizontalPosition(
                                                            startOffset,
                                                            true
                                                        ) else lr.getLineLeft(line)
                                                    val lineEnd =
                                                        if (line == endLine) lr.getHorizontalPosition(
                                                            endOffset,
                                                            true
                                                        ) else lr.getLineRight(line)
                                                    val y = lr.getLineBottom(line) + 2.dp.toPx()
                                                    canvas.nativeCanvas.drawLine(
                                                        lineStart,
                                                        y,
                                                        lineEnd,
                                                        y,
                                                        paint
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    .then(
                                        Modifier.pointerInput(links, lineText) {
                                            detectTapGestures { pos ->
                                                val lr = layoutResult ?: return@detectTapGestures
                                                val offset = lr.getOffsetForPosition(pos)
                                                links.firstOrNull { offset in it.range }
                                                    ?.let { onLinkClick(it.url) }
                                            }
                                        }
                                    )
                            } else Modifier
                        )
                )
            }
        }
    }
}

private data class HeiseNewsItem(
    val id: String,
    val title: String,
    val summary: String,
    val link: String,
    val imageUrl: String?,
    val publishedAt: String,
    val rawTimestamp: Long,
    val source: String = "",
    val emailBody: String? = null
)

private fun HeiseNewsItem.metaLine(): String =
    if (source.isBlank()) publishedAt else "$source · $publishedAt"

private data class HeiseChatMessage(
    val text: String,
    val own: Boolean
)

private data class HeiseTermQuestion(
    val term: String,
    val question: String
)

/**
 * Der Prompt lässt die KI wichtige Begriffe als `[[Begriff::Frage]]` markieren.
 * Diese Funktion wandelt das in normale Markdown-Links (`[Begriff](ask://<index>)`) um,
 * damit der bestehende Markdown-Renderer sie klickbar darstellt, ohne dass die KI die
 * Frage selbst URL-kodieren müsste. Die eigentlichen Fragen werden separat zurückgegeben.
 */
private val SUMMARY_TERM_MARKUP_REGEX = Regex("\\[\\[(.+?)::(.+?)]]")

private const val HEISE_PREFS_NAME = "news_prefs"
private const val HEISE_SUMMARY_PREFIX = "summary_"
private const val HEISE_CHAT_PREFIX = "chat_"
private const val HEISE_ARTICLES_KEY = "articles_"
private const val HEISE_ARTICLES_TIMESTAMP_KEY = "articles_timestamp_"
private const val CACHE_VALIDITY_MS = 30 * 60 * 1000L

private fun saveSummaryToPrefs(context: Context, articleLink: String, summary: String) {
    val prefs = context.getSharedPreferences(HEISE_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit { putString(HEISE_SUMMARY_PREFIX + articleLink, summary) }
}

private fun loadSummaryFromPrefs(context: Context, articleLink: String): String? {
    val prefs = context.getSharedPreferences(HEISE_PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getString(HEISE_SUMMARY_PREFIX + articleLink, null)
}

private fun saveChatToPrefs(
    context: Context,
    articleLink: String,
    chatMessages: List<HeiseChatMessage>
) {
    val prefs = context.getSharedPreferences(HEISE_PREFS_NAME, Context.MODE_PRIVATE)
    val jsonArray = JSONArray()
    for (msg in chatMessages) {
        val jsonObj = JSONObject().apply {
            put("text", msg.text)
            put("own", msg.own)
        }
        jsonArray.put(jsonObj)
    }
    prefs.edit { putString(HEISE_CHAT_PREFIX + articleLink, jsonArray.toString()) }
}

private fun loadChatFromPrefs(context: Context, articleLink: String): List<HeiseChatMessage> {
    val prefs = context.getSharedPreferences(HEISE_PREFS_NAME, Context.MODE_PRIVATE)
    val jsonStr = prefs.getString(HEISE_CHAT_PREFIX + articleLink, null) ?: return emptyList()
    return try {
        val jsonArray = JSONArray(jsonStr)
        val list = mutableListOf<HeiseChatMessage>()
        for (i in 0 until jsonArray.length()) {
            val jsonObj = jsonArray.getJSONObject(i)
            val text = jsonObj.optString("text").orEmpty()
            val own = jsonObj.optBoolean("own", false)
            list.add(HeiseChatMessage(text, own))
        }
        list
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList()
    }
}

private fun saveArticlesToPrefs(context: Context, articles: List<HeiseNewsItem>) {
    val prefs = context.getSharedPreferences(HEISE_PREFS_NAME, Context.MODE_PRIVATE)
    val jsonArray = JSONArray()
    for (article in articles) {
        val jsonObj = JSONObject().apply {
            put("id", article.id)
            put("title", article.title)
            put("summary", article.summary)
            put("link", article.link)
            put("imageUrl", article.imageUrl)
            put("publishedAt", article.publishedAt)
            put("rawTimestamp", article.rawTimestamp)
            put("source", article.source)
            put("emailBody", article.emailBody)
        }
        jsonArray.put(jsonObj)
    }
    prefs.edit {
        putString(HEISE_ARTICLES_KEY, jsonArray.toString())
        putLong(HEISE_ARTICLES_TIMESTAMP_KEY, System.currentTimeMillis())
    }
}

private fun loadArticlesFromPrefs(context: Context): Pair<List<HeiseNewsItem>, Long> {
    val prefs = context.getSharedPreferences(HEISE_PREFS_NAME, Context.MODE_PRIVATE)
    val jsonStr =
        prefs.getString(HEISE_ARTICLES_KEY, null) ?: return emptyList<HeiseNewsItem>() to 0L
    val timestamp = prefs.getLong(HEISE_ARTICLES_TIMESTAMP_KEY, 0L)
    return try {
        val jsonArray = JSONArray(jsonStr)
        val list = mutableListOf<HeiseNewsItem>()
        for (i in 0 until jsonArray.length()) {
            val jsonObj = jsonArray.getJSONObject(i)
            list.add(
                HeiseNewsItem(
                    id = jsonObj.getString("id"),
                    title = jsonObj.getString("title"),
                    summary = jsonObj.getString("summary"),
                    link = jsonObj.getString("link"),
                    imageUrl = jsonObj.optString("imageUrl", ""),
                    publishedAt = jsonObj.getString("publishedAt"),
                    rawTimestamp = jsonObj.getLong("rawTimestamp"),
                    source = jsonObj.optString("source", ""),
                    emailBody = jsonObj.optString("emailBody", "")
                )
            )
        }
        list to timestamp
    } catch (e: Exception) {
        e.printStackTrace()
        emptyList<HeiseNewsItem>() to 0L
    }
}

private fun preprocessSummaryMarkup(raw: String): Pair<String, List<HeiseTermQuestion>> {
    val questions = mutableListOf<HeiseTermQuestion>()
    val transformed = SUMMARY_TERM_MARKUP_REGEX.replace(raw) { match ->
        val term = match.groupValues[1].trim()
        val question = match.groupValues[2].trim()
        val index = questions.size
        questions.add(HeiseTermQuestion(term, question))
        "[$term](ask://$index)"
    }
    return transformed to questions
}

@Composable
fun HeiseNewsTabContent(
    modifier: Modifier = Modifier,
    paddingValues: PaddingValues = PaddingValues(0.dp),
    viewModel: TabNavigationViewModel
) {
    val context = LocalContext.current

    val unbekannterFehlerMsg = stringResource(R.string.unbekannter_fehler)
    val artikeltextKonntNichtGeladenMsg =
        stringResource(R.string.artikeltext_konnte_nicht_geladen_werden)
    val keineZusammenfassungMsg = stringResource(R.string.keine_zusammenfassung_erhalten)
    val zusammenfassungFehlgeschlagenMsg = stringResource(R.string.zusammenfassung_fehlgeschlagen)
    val keineAntwortMsg = stringResource(R.string.keine_antwort_erhalten)
    val fehlerMsgTemplate = stringResource(R.string.fehler_msg)
    val antwortFehlgeschlagenMsg = stringResource(R.string.antwort_fehlgeschlagen)
    val bitteWartenMsg = stringResource(R.string.bitte_warten_bis_die_antwort)
    val artikelKonntNichtGeoffnetMsg = stringResource(R.string.artikel_konnte_nicht_geoffnet_werden)

    val scope = rememberCoroutineScope()
    val articleTextCache = remember { mutableStateMapOf<String, String>() }
    val summaryCache = remember { mutableStateMapOf<String, String>() }

    var articles by remember { mutableStateOf<List<HeiseNewsItem>>(emptyList()) }
    var selectedArticle by remember { mutableStateOf<HeiseNewsItem?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var loadingArticleLink by remember { mutableStateOf<String?>(null) }
    var articleError by remember { mutableStateOf<String?>(null) }
    var summarizingArticleLink by remember { mutableStateOf<String?>(null) }
    var summaryError by remember { mutableStateOf<String?>(null) }
    val chatCache = remember { mutableStateMapOf<String, SnapshotStateList<HeiseChatMessage>>() }
    var askingArticleLink by remember { mutableStateOf<String?>(null) }
    var chatError by remember { mutableStateOf<String?>(null) }

    fun <K, V> MutableMap<K, V>.cap(maxSize: Int) {
        if (size > maxSize) {
            val toRemove = keys.take(size - maxSize)
            toRemove.forEach { remove(it) }
        }
    }

    LaunchedEffect(selectedArticle) {
        val canGoBack = selectedArticle != null
        viewModel.updateBackState(
            canNavigateBack = canGoBack,
            onNavigateBack = {
                selectedArticle = null
            }
        )
    }

    fun loadNews(forceRefresh: Boolean = false) {
        scope.launch {
            isLoading = true
            errorMessage = null
            try {
                if (!forceRefresh) {
                    val (cachedArticles, timestamp) = loadArticlesFromPrefs(context)
                    val isCacheValid = (System.currentTimeMillis() - timestamp) < CACHE_VALIDITY_MS
                    if (cachedArticles.isNotEmpty() && isCacheValid) {
                        articles = cachedArticles
                        isLoading = false
                        return@launch
                    }
                }

                val freshArticles = fetchHeiseNews(context.packageName)
                articles = freshArticles
                saveArticlesToPrefs(context, freshArticles)
            } catch (e: Exception) {
                errorMessage = e.message ?: unbekannterFehlerMsg
                val (cachedArticles, _) = loadArticlesFromPrefs(context)
                if (cachedArticles.isNotEmpty()) {
                    articles = cachedArticles
                }
            } finally {
                isLoading = false
            }
        }
    }

    fun loadFullArticle(article: HeiseNewsItem) {
        if (articleTextCache.containsKey(article.link) || loadingArticleLink == article.link) return
        if (article.emailBody != null) {
            articleTextCache[article.link] = article.emailBody
            articleTextCache.cap(20)
            return
        }
        scope.launch {
            loadingArticleLink = article.link
            articleError = null
            try {
                articleTextCache[article.link] = fetchFullArticleText(article.link)
                articleTextCache.cap(20)
            } catch (e: Exception) {
                articleError = e.message ?: artikeltextKonntNichtGeladenMsg
            } finally {
                loadingArticleLink = null
            }
        }
    }

    fun summarizeArticle(article: HeiseNewsItem) {
        val articleText = articleTextCache[article.link]
        if (articleText.isNullOrBlank() || summarizingArticleLink == article.link) return
        scope.launch {
            summarizingArticleLink = article.link
            summaryError = null
            try {
                val prompt = buildString {
                    appendLine("Du bekommst den Volltext eines Artikels${article.source.takeIf { it.isNotBlank() }?.let { " von $it" } ?: ""}. Erstelle daraus eine sehr knappe, gut lesbare deutsche Zusammenfassung.")
                    appendLine()
                    appendLine("Format der Zusammenfassung:")
                    appendLine("- Maximal 3 bis 4 kurze Bulletpoints (je maximal ein Satz) mit den wichtigsten Fakten, Zahlen, Produkt-/Versionsnamen und Auswirkungen.")
                    appendLine("- Bleib strikt beim Inhalt des Textes, erfinde nichts.")
                    appendLine("- Keine Füllwörter oder Wiederholungen, insgesamt so kurz wie möglich.")
                    appendLine("- Schließe mit einer kurzen Einschätzung in maximal einem Satz ab.")
                    appendLine()
                    appendLine("Zusätzlich: Markiere 4 bis 8 zentrale Fachbegriffe, Produktnamen, Firmen, Personen oder Abkürzungen aus der Zusammenfassung, zu denen eine Rückfrage sinnvoll wäre. Nutze dafür GENAU diese Syntax, sonst nichts:")
                    appendLine("[[Begriff::Frage]]")
                    appendLine("- \"Begriff\" ist das kurze Wort/die kurze Wortgruppe (max. 3 Wörter) exakt wie im Fließtext.")
                    appendLine("- \"Frage\" ist eine knappe, konkrete Rückfrage, die automatisch gestellt wird, wenn der Nutzer auf den Begriff tippt, z. B. \"Was ist Kubernetes und wofür wird es hier eingesetzt?\" oder \"Wer ist Person X und welche Rolle spielt sie hier?\".")
                    appendLine("- Kein \"[[\" oder \"::\" oder \"]]\" innerhalb von Begriff oder Frage verwenden.")
                    appendLine("- Verwende diese Markierung nur inline in den Bulletpoints, nicht im Einschätzungssatz, und nutze sonst kein Markdown-Link-Format.")
                    appendLine("- Beispiel: \"- [[Kubernetes::Was ist Kubernetes und wofür wird es hier eingesetzt?]] erhält in Version 1.30 ein neues Feature ...\"")
                    appendLine()
                    appendLine("Titel: ${article.title}")
                    appendLine("Quelle: ${article.link}")
                    appendLine()
                    appendLine("Note: Der Nutzer sieht deine ganze antwort, also beginne **NICHT** beisielshaft mit 'alles klar hier ist deine Zusammenfassung', sondern beginne direkt")
                    append(articleText.take(ARTICLE_TEXT_LIMIT_FOR_AI))
                }
                val response = sendAiRequest(
                    context = context,
                    serviceKey = "heise_news_summary",
                    userMessage = prompt,
                    target = "",
                    provider = AiProvider.NVIDIA
                )
                val summaryResult = response?.ifBlank { null } ?: keineZusammenfassungMsg
                summaryCache[article.link] = summaryResult
                summaryCache.cap(20)
                saveSummaryToPrefs(context, article.link, summaryResult)
            } catch (e: Exception) {
                summaryError = e.message ?: zusammenfassungFehlgeschlagenMsg
            } finally {
                summarizingArticleLink = null
            }
        }
    }

    fun askAboutArticle(article: HeiseNewsItem, question: String) {
        val trimmedQuestion = question.trim()
        val articleText = articleTextCache[article.link]
        if (trimmedQuestion.isEmpty() || articleText.isNullOrBlank()) return
        if (summarizingArticleLink == article.link || askingArticleLink == article.link) return

        val messages = chatCache.getOrPut(article.link) { mutableStateListOf() }
        chatCache.cap(20)
        val priorHistory = messages.map { ChatMessage(text = it.text, ts = 0L, own = it.own) }
        messages.add(HeiseChatMessage(text = trimmedQuestion, own = true))
        saveChatToPrefs(context, article.link, messages)
        val answerIndex = messages.size
        messages.add(HeiseChatMessage(text = "", own = false))

        scope.launch {
            askingArticleLink = article.link
            chatError = null
            try {
                val prompt = buildString {
                    appendLine("Beantworte die folgende Frage eines Nutzers zu einem Artikel${article.source.takeIf { it.isNotBlank() }?.let { " von $it" } ?: ""} kurz, präzise und auf Deutsch (2-5 Sätze, bei Bedarf mit Stichpunkten).")
                    appendLine("Nutze primär den Artikeltext als Quelle. Ergänze offensichtliches Allgemeinwissen nur wenn nötig und sag klar, wenn etwas nicht im Artikel steht.")
                    appendLine()
                    appendLine("Titel: ${article.title}")
                    appendLine("Artikeltext:")
                    appendLine(articleText.take(ARTICLE_TEXT_LIMIT_FOR_AI))
                    appendLine()
                    append("Frage: $trimmedQuestion")
                }
                val response = sendAiRequest(
                    context = context,
                    serviceKey = "heise_news_summary",
                    history = priorHistory,
                    userMessage = prompt,
                    target = "",
                    onToken = { delta ->
                        val current = messages[answerIndex]
                        messages[answerIndex] = current.copy(text = current.text + delta)
                    }
                )
                if (messages[answerIndex].text.isBlank()) {
                    messages[answerIndex] = messages[answerIndex].copy(
                        text = response?.ifBlank { null } ?: keineAntwortMsg
                    )
                }
                saveChatToPrefs(context, article.link, messages)
            } catch (e: Exception) {
                messages[answerIndex] = messages[answerIndex].copy(
                    text = fehlerMsgTemplate.format(e.message ?: antwortFehlgeschlagenMsg)
                )
                saveChatToPrefs(context, article.link, messages)
            } finally {
                askingArticleLink = null
            }
        }
    }

    fun onTermQuestionClick(article: HeiseNewsItem, question: String) {
        if (summarizingArticleLink == article.link || askingArticleLink == article.link) {
            Toast.makeText(
                context,
                bitteWartenMsg,
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        askAboutArticle(article, question)
    }

    LaunchedEffect(Unit) {
        loadNews()
    }

    selectedArticle?.let { article ->
        BackHandler { selectedArticle = null }
        LaunchedEffect(article.link) {
            loadFullArticle(article)
            if (!summaryCache.containsKey(article.link)) {
                val savedSummary = loadSummaryFromPrefs(context, article.link)
                if (savedSummary != null) {
                    summaryCache[article.link] = savedSummary
                    summaryCache.cap(20)
                }
            }
            if (!chatCache.containsKey(article.link)) {
                val savedChat = loadChatFromPrefs(context, article.link)
                if (savedChat.isNotEmpty()) {
                    val list = mutableStateListOf<HeiseChatMessage>()
                    list.addAll(savedChat)
                    chatCache[article.link] = list
                    chatCache.cap(20)
                }
            }
        }
        HeiseArticleDetail(
            article = article,
            articleText = articleTextCache[article.link],
            summary = summaryCache[article.link],
            isLoadingArticle = loadingArticleLink == article.link,
            isSummarizing = summarizingArticleLink == article.link,
            articleError = articleError,
            summaryError = summaryError,
            chatMessages = chatCache[article.link] ?: emptyList(),
            isAsking = askingArticleLink == article.link,
            chatError = chatError,
            onBack = { selectedArticle = null },
            onRetryArticle = { loadFullArticle(article) },
            onSummarize = { summarizeArticle(article) },
            onSendChat = { question -> askAboutArticle(article, question) },
            onTermQuestionClick = { question -> onTermQuestionClick(article, question) },
            onOpenOriginal = {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, article.link.toUri()))
                }.onFailure {
                    Toast.makeText(
                        context,
                        artikelKonntNichtGeoffnetMsg,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            modifier = modifier,
            paddingValues = paddingValues
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .padding(
                top = paddingValues.calculateTopPadding(),
                bottom = paddingValues.calculateBottomPadding()
            )
            .padding(horizontal = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "heise & SRF News",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (articles.isEmpty()) stringResource(R.string.aktuelle_news_per_atom_feed) else pluralStringResource(
                        R.plurals.meldungen_geladen,
                        articles.size,
                        articles.size
                    ),
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 12.sp
                )
            }
            IconButton(onClick = { loadNews(forceRefresh = true) }, enabled = !isLoading) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.news_aktualisieren),
                        tint = Color.White
                    )
                }
            }
        }

        when {
            isLoading && articles.isEmpty() -> LoadingNewsState()
            errorMessage != null && articles.isEmpty() -> ErrorNewsState(errorMessage!!) { loadNews() }
            articles.isEmpty() -> EmptyNewsState { loadNews() }
            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(articles, key = { it.id }) { article ->
                    HeiseNewsCard(
                        article = article,
                        onClick = { selectedArticle = article }
                    )
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

@Composable
private fun HeiseNewsCard(
    article: HeiseNewsItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C22).copy(alpha = 0.92f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            if (!article.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = article.imageUrl,
                    contentDescription = article.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(14.dp))
                )
                Spacer(Modifier.height(12.dp))
            }

            Text(
                text = article.title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = article.summary,
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 14.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = article.metaLine(),
                color = Color.White.copy(alpha = 0.52f),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun HeiseArticleDetail(
    article: HeiseNewsItem,
    articleText: String?,
    summary: String?,
    isLoadingArticle: Boolean,
    isSummarizing: Boolean,
    articleError: String?,
    summaryError: String?,
    chatMessages: List<HeiseChatMessage>,
    isAsking: Boolean,
    chatError: String?,
    onBack: () -> Unit,
    onRetryArticle: () -> Unit,
    onSummarize: () -> Unit,
    onSendChat: (String) -> Unit,
    onTermQuestionClick: (String) -> Unit,
    onOpenOriginal: () -> Unit,
    modifier: Modifier = Modifier,
    paddingValues: PaddingValues = PaddingValues(0.dp)
) {
    val hazeState = remember { HazeState() }
    var headerHeightDp by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current

    val isAiBusy = isSummarizing || isAsking
    val realUriHandler = LocalUriHandler.current
    val (displaySummary, termQuestions) = remember(summary) {
        summary?.let { preprocessSummaryMarkup(it) } ?: ("" to emptyList())
    }
    val summaryUriHandler = remember(termQuestions, onTermQuestionClick) {
        object : UriHandler {
            override fun openUri(uri: String) {
                val askIndex = uri.removePrefix("ask://").toIntOrNull()
                if (uri.startsWith("ask://") && askIndex != null) {
                    termQuestions.getOrNull(askIndex)?.let { onTermQuestionClick(it.question) }
                } else {
                    realUriHandler.openUri(uri)
                }
            }
        }
    }
    var chatInput by remember(article.id) { mutableStateOf("") }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = paddingValues.calculateBottomPadding())
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .hazeSource(state = hazeState)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(Modifier.height(headerHeightDp))
            }
            item {
                Text(
                    text = article.title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    lineHeight = 26.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = article.metaLine(),
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 12.sp
                )
            }

            if (!article.imageUrl.isNullOrBlank()) {
                item {
                    AsyncImage(
                        model = article.imageUrl,
                        contentDescription = article.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                            .clip(RoundedCornerShape(18.dp))
                    )
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF1C1C22).copy(
                            alpha = 0.92f
                        )
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            text = stringResource(R.string.ai_summary),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        when {
                            isSummarizing -> Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.zusammenfassung_wird_erstellt),
                                    color = Color.White.copy(alpha = 0.75f)
                                )
                            }

                            summary != null -> DottedMarkdownText(
                                content = displaySummary,
                                textColor = Color.White.copy(alpha = 0.86f),
                                onLinkClick = { summaryUriHandler.openUri(it) }
                            )

                            articleText.isNullOrBlank() -> Text(
                                text = stringResource(R.string.lade_zuerst_den_vollstandigen_artikeltext),
                                color = Color.White.copy(alpha = 0.65f),
                                fontSize = 14.sp
                            )

                            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = onSummarize) {
                                    Text(stringResource(R.string.ai_zusammenfassung_erstellen))
                                }
                            }
                        }

                        if (!summaryError.isNullOrBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(summaryError, color = Color(0xFFFF8A80), fontSize = 12.sp)
                        }
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF1C1C22).copy(
                            alpha = 0.92f
                        )
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            text = stringResource(R.string.fragen_zum_artikel),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.tippe_auf_einen_markierten_begriff),
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 12.sp
                        )

                        if (chatMessages.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            chatMessages.forEachIndexed { index, message ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = if (message.own) Arrangement.End else Arrangement.Start
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .widthIn(max = 280.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(
                                                if (message.own) Color(0xFF3B4CCA).copy(alpha = 0.85f)
                                                else Color(0xFF2A2A32)
                                            )
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        if (!message.own && message.text.isBlank() && isAsking && index == chatMessages.lastIndex) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(14.dp),
                                                    strokeWidth = 2.dp,
                                                    color = Color.White
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text(
                                                    text = stringResource(R.string.denkt_nach),
                                                    color = Color.White.copy(alpha = 0.75f),
                                                    fontSize = 13.sp
                                                )
                                            }
                                        } else if (message.own) {
                                            Text(
                                                message.text,
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                lineHeight = 19.sp
                                            )
                                        } else {
                                            Markdown(
                                                content = message.text,
                                                colors = markdownColor(
                                                    text = Color.White.copy(
                                                        alpha = 0.9f
                                                    )
                                                ),
                                                typography = markdownTypography(
                                                    text = MaterialTheme.typography.bodyMedium.copy(
                                                        fontSize = 14.sp,
                                                        lineHeight = 19.sp
                                                    )
                                                )
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                        }

                        if (!chatError.isNullOrBlank()) {
                            Text(chatError, color = Color(0xFFFF8A80), fontSize = 12.sp)
                            Spacer(Modifier.height(8.dp))
                        }

                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TextField(
                                value = chatInput,
                                onValueChange = { chatInput = it },
                                modifier = Modifier.weight(1f),
                                enabled = !isAiBusy && !articleText.isNullOrBlank(),
                                singleLine = true,
                                shape = RoundedCornerShape(24.dp),
                                placeholder = {
                                    Text(
                                        if (isAiBusy) stringResource(R.string.bitte_warten) else stringResource(
                                            R.string.frage_zum_artikel_stellen
                                        ),
                                        color = Color(0xFF888888)
                                    )
                                },
                                colors = TextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    disabledTextColor = Color.White.copy(alpha = 0.5f),
                                    cursorColor = Color.White,
                                    focusedContainerColor = Color(0xFF2A2A2A),
                                    unfocusedContainerColor = Color(0xFF2A2A2A),
                                    disabledContainerColor = Color(0xFF232328),
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent
                                ),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(onSend = {
                                    if (chatInput.isNotBlank() && !isAiBusy) {
                                        onSendChat(chatInput)
                                        chatInput = ""
                                    }
                                })
                            )

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(Color(0xFF333333))
                                    .size(44.dp)
                                    .clickable(enabled = !isAiBusy && chatInput.isNotBlank()) {
                                        onSendChat(chatInput)
                                        chatInput = ""
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = stringResource(R.string.frage_senden),
                                    tint = if (!isAiBusy && chatInput.isNotBlank()) Color.White else Color.White.copy(
                                        alpha = 0.4f
                                    )
                                )
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF1C1C22).copy(
                            alpha = 0.92f
                        )
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            text = stringResource(R.string.artikeltext),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        when {
                            isLoadingArticle -> Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = Color.White
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    stringResource(R.string.artikel_wird_geladen),
                                    color = Color.White.copy(alpha = 0.75f)
                                )
                            }

                            !articleError.isNullOrBlank() -> {
                                Text(articleError, color = Color(0xFFFF8A80), fontSize = 13.sp)
                                Spacer(Modifier.height(8.dp))
                                TextButton(onClick = onRetryArticle) { Text(stringResource(R.string.erneut_laden)) }
                            }

                            articleText.isNullOrBlank() -> Text(
                                text = stringResource(R.string.kein_artikeltext_gefunden),
                                color = Color.White.copy(alpha = 0.65f)
                            )

                            else -> Text(
                                text = articleText,
                                color = Color.White.copy(alpha = 0.82f),
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(8.dp)) }

        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned {
                    headerHeightDp = with(density) { it.size.height.toDp() }
                }
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .hazeBlur(
                        input = HazeInput.Sources(state = hazeState),
                        style = HazeBlurStyle {
                            backgroundColor(Color(0xFF0C1017))
                            colorEffects(listOf(HazeColorEffect.tint(Color(0xFF0C1017).copy(alpha = 0.7f))))
                            blurRadius(60.dp)
                            noiseFactor(0f)
                            progressive(
                                HazeProgressive.verticalGradient(
                                    startIntensity = 1f,
                                    endIntensity = 0f
                                )
                            )
                        }
                    )
                    .drawWithContent {
                        drawContent()
                        val fadePx = 24.dp.toPx()
                        val bottomStop = (1f - fadePx / size.height).coerceIn(0f, 1f)
                        drawRect(
                            brush = Brush.verticalGradient(
                                0f to Color.Black,
                                bottomStop to Color.Black,
                                1f to Color.Transparent
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    }
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = paddingValues.calculateTopPadding())
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        stringResource(R.string.zuruck),
                        tint = Color.White
                    )
                }
                Text(
                    text = article.title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp)
                )
                if (!article.link.startsWith(EMAIL_ARTICLE_LINK_PREFIX)) {
                    IconButton(onClick = onOpenOriginal) {
                        Icon(
                            Icons.AutoMirrored.Filled.OpenInNew,
                            stringResource(R.string.original_offnen),
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingNewsState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Color.White)
    }
}

@Composable
private fun ErrorNewsState(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.feed_konnte_nicht_geladen_werden),
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = message,
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.erneut_versuchen))
            }
        }
    }
}

@Composable
private fun EmptyNewsState(onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.keine_news_gefunden), color = Color.White)
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.aktualisieren_2))
            }
        }
    }
}

private suspend fun fetchHeiseNews(packageName: String): List<HeiseNewsItem> =
    withContext(Dispatchers.IO) {
        val feedItems = mutableListOf<HeiseNewsItem>()

        for (feed in NEWS_FEEDS) {
            val connection = (URL(feed.url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000
                readTimeout = 12_000
                requestMethod = "GET"
                setRequestProperty(
                    "Accept",
                    "application/atom+xml, application/rss+xml, application/xml, text/xml"
                )
                setRequestProperty("User-Agent", "Tabslify/1.0")
            }

            try {
                if (connection.responseCode in 200..299) {
                    connection.inputStream.use { stream ->
                        val parsed = when (feed.format) {
                            NewsFeedFormat.ATOM -> parseAtomFeed(stream, feed.source)
                            NewsFeedFormat.RSS -> parseRssFeed(stream, feed.source)
                        }
                        feedItems += parsed.take(feed.maxItems)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                connection.disconnect()
            }
        }

        val newsletterItems = fetchNewsletterEmailItems(packageName).distinctBy { it.id }

        (newsletterItems + feedItems.distinctBy { it.id }).sortedByDescending { it.rawTimestamp }
    }

private suspend fun fetchNewsletterEmailItems(packageName: String): List<HeiseNewsItem> =
    withContext(Dispatchers.IO) {
        val items = mutableListOf<HeiseNewsItem>()
        if (NEWSLETTER_SOURCES.isEmpty() || !prvt()) return@withContext items

        try {
            val raw = Config.safeCall {
                Config.client.from("emails")
                    .select(Columns.list("account,uid,subject,from_addr,timestamp,body,summary")) {
                        filter {
                            ilikeAny(
                                "from_addr",
                                NEWSLETTER_SOURCES.map { "%${it.senderFragment}%" }
                            )
                        }
                        order("timestamp", Order.DESCENDING)
                        limit(NEWSLETTER_EMAIL_LIMIT)
                    }
                    .data
            }
            val heiseLogoUrl = "android.resource://$packageName/${R.drawable.heise_logo}"
            val jsonArray = JSONArray(raw)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val uid = obj.optString("uid")
                val account = obj.optString("account")
                if (uid.isBlank() || account.isBlank()) continue
                val fromAddr = obj.optString("from_addr")
                val newsletterSource = newsletterSourceFor(fromAddr) ?: continue
                val body = obj.optString("body")
                val aiSummary = obj.optString("summary").takeIf { it.isNotBlank() }
                val timestamp = obj.optLong("timestamp", 0L)
                items += HeiseNewsItem(
                    id = "email:$account:$uid",
                    title = obj.optString("subject").ifBlank { newsletterSource.label },
                    summary = aiSummary ?: body.trim().take(NEWSLETTER_EMAIL_SUMMARY_LIMIT),
                    link = "$EMAIL_ARTICLE_LINK_PREFIX$account/$uid",
                    imageUrl = if (newsletterSource.useHeiseLogo) heiseLogoUrl else null,
                    publishedAt = formatEmailPublishedAt(timestamp),
                    rawTimestamp = timestamp,
                    source = newsletterSource.label,
                    emailBody = body
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        items
    }

private fun formatEmailPublishedAt(timestampMillis: Long): String {
    if (timestampMillis <= 0L) return ""
    return runCatching {
        val formatter = DateTimeFormatter
            .ofPattern("dd. MMM yyyy, HH:mm", Locale.GERMANY)
            .withZone(ZoneId.systemDefault())
        formatter.format(Instant.ofEpochMilli(timestampMillis))
    }.getOrDefault("")
}

private suspend fun fetchFullArticleText(articleUrl: String): String =
    withContext(Dispatchers.IO) {
        val connection = (URL(articleUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 20_000
            requestMethod = "GET"
            setRequestProperty("Accept", "text/html,application/xhtml+xml")
            setRequestProperty("Accept-Language", "de-DE,de;q=0.9,en;q=0.7")
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36"
            )
        }

        try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("HTTP ${connection.responseCode}")
            }
            val html =
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            extractArticleText(html).ifBlank {
                throw IllegalStateException("Kein Volltext im HTML gefunden")
            }
        } finally {
            connection.disconnect()
        }
    }

private fun XmlPullParser.readElementTextSafely(): String {
    if (eventType != XmlPullParser.START_TAG) return ""

    val builder = StringBuilder()
    var depth = 1
    var currentEvent = next()

    while (depth > 0 && currentEvent != XmlPullParser.END_DOCUMENT) {
        when (currentEvent) {
            XmlPullParser.START_TAG -> depth++
            XmlPullParser.END_TAG -> {
                depth--
                if (depth == 0) {
                    return builder.toString().trim()
                }
            }

            XmlPullParser.TEXT,
            XmlPullParser.CDSECT,
            XmlPullParser.ENTITY_REF -> builder.append(text)

            XmlPullParser.COMMENT,
            XmlPullParser.IGNORABLE_WHITESPACE,
            XmlPullParser.PROCESSING_INSTRUCTION,
            XmlPullParser.DOCDECL -> Unit
        }

        currentEvent = next()
    }

    return builder.toString().trim()
}

private fun parseAtomFeed(inputStream: InputStream, source: String): List<HeiseNewsItem> {
    val parser = XmlPullParserFactory.newInstance().apply {
        isNamespaceAware = true
    }.newPullParser()
    parser.setInput(inputStream, null)

    val items = mutableListOf<HeiseNewsItem>()
    var inEntry = false
    var id = ""
    var title = ""
    var summary = ""
    var link = ""
    var content = ""
    var published = ""
    var updated = ""

    var eventType = parser.eventType
    while (eventType != XmlPullParser.END_DOCUMENT) {
        when (eventType) {
            XmlPullParser.START_TAG -> when (parser.name) {
                "entry" -> {
                    inEntry = true
                    id = ""
                    title = ""
                    summary = ""
                    link = ""
                    content = ""
                    published = ""
                    updated = ""
                }

                "id" -> if (inEntry) id = parser.readElementTextSafely()
                "title" -> if (inEntry) {
                    title = parser.readElementTextSafely()
                    if (title.contains("heise-Angebot")) continue
                }

                "summary" -> if (inEntry) summary = parser.readElementTextSafely()
                "content" -> if (inEntry) content = parser.readElementTextSafely()
                "published" -> if (inEntry) published = parser.readElementTextSafely()
                "updated" -> if (inEntry) updated = parser.readElementTextSafely()
                "link" -> if (inEntry && link.isBlank()) {
                    link = parser.getAttributeValue(null, "href").orEmpty()
                }
            }

            XmlPullParser.END_TAG -> if (parser.name == "entry" && inEntry) {
                val cleanedTitle = cleanHtml(title)
                val cleanedSummary = cleanHtml(summary).ifBlank { cleanHtml(content) }
                if (cleanedTitle.isNotBlank() && link.isNotBlank()) {
                    val rawDate = published.ifBlank { updated }
                    if (title.contains("heise-Angebot")) continue
                    val item = HeiseNewsItem(
                        id = id.ifBlank { link },
                        title = cleanedTitle,
                        summary = cleanedSummary,
                        link = link,
                        imageUrl = extractImageUrl(content),
                        publishedAt = formatFeedDate(rawDate),
                        rawTimestamp = parseRawTimestamp(rawDate),
                        source = source
                    )
                    items += item
                }
                inEntry = false
            }
        }
        eventType = parser.next()
    }

    return items
}

private fun parseRssFeed(inputStream: InputStream, source: String): List<HeiseNewsItem> {
    val parser = XmlPullParserFactory.newInstance().apply {
        isNamespaceAware = true
    }.newPullParser()
    parser.setInput(inputStream, null)

    val items = mutableListOf<HeiseNewsItem>()
    var inItem = false
    var guid = ""
    var title = ""
    var description = ""
    var content = ""
    var link = ""
    var pubDate = ""
    var enclosureUrl = ""

    var eventType = parser.eventType
    while (eventType != XmlPullParser.END_DOCUMENT) {
        when (eventType) {
            XmlPullParser.START_TAG -> when (parser.name) {
                "item" -> {
                    inItem = true
                    guid = ""
                    title = ""
                    description = ""
                    content = ""
                    link = ""
                    pubDate = ""
                    enclosureUrl = ""
                }

                "guid" -> if (inItem) guid = parser.readElementTextSafely()
                "title" -> if (inItem) title = parser.readElementTextSafely()
                "description" -> if (inItem) description = parser.readElementTextSafely()
                "encoded" -> if (inItem) content = parser.readElementTextSafely()
                "link" -> if (inItem && link.isBlank()) {
                    link = parser.getAttributeValue(null, "href").orEmpty()
                        .ifBlank { parser.readElementTextSafely() }
                }

                "pubDate" -> if (inItem) pubDate = parser.readElementTextSafely()
                "enclosure", "thumbnail" -> if (inItem && enclosureUrl.isBlank()) {
                    enclosureUrl = parser.getAttributeValue(null, "url").orEmpty()
                }

                "content" -> if (inItem && enclosureUrl.isBlank()) {
                    enclosureUrl = parser.getAttributeValue(null, "url").orEmpty()
                }
            }

            XmlPullParser.END_TAG -> if (parser.name == "item" && inItem) {
                val cleanedTitle = cleanHtml(title)
                val imageSource = description.ifBlank { content }
                val cleanedSummary = cleanHtml(description).ifBlank { cleanHtml(content) }
                if (cleanedTitle.isNotBlank() && link.isNotBlank()) {
                    items += HeiseNewsItem(
                        id = guid.ifBlank { link },
                        title = cleanedTitle,
                        summary = cleanedSummary,
                        link = link,
                        imageUrl = upscaleFeedImageUrl(
                            extractImageUrl(imageSource) ?: enclosureUrl.ifBlank { null }
                        ),
                        publishedAt = formatFeedDate(pubDate),
                        rawTimestamp = parseRawTimestamp(pubDate),
                        source = source
                    )
                }
                inItem = false
            }
        }
        eventType = parser.next()
    }

    return items
}

private fun upscaleFeedImageUrl(url: String?): String? =
    url?.replace(SRF_IMAGE_SMALL_SEGMENT, SRF_IMAGE_LARGE_SEGMENT)

private fun extractDivByClass(html: String, className: String): String? =
    extractElementByAttribute(
        html,
        "div",
        """class=["'][^"']*\b${Regex.escape(className)}\b[^"']*["']"""
    )

private fun extractElementByAttribute(
    html: String,
    tag: String,
    attributePattern: String
): String? {
    val openTagRegex = Regex(
        """<$tag\b[^>]*$attributePattern[^>]*>""",
        RegexOption.IGNORE_CASE
    )
    val openMatch = openTagRegex.find(html) ?: return null
    val contentStart = openMatch.range.last + 1

    val tagRegex = Regex("""<$tag\b[^>]*>|</$tag\s*>""", RegexOption.IGNORE_CASE)
    var depth = 1
    var searchFrom = contentStart
    var tagsScanned = 0
    while (depth > 0) {
        val nextTag = tagRegex.find(html, searchFrom) ?: return html.substring(contentStart)
        tagsScanned++
        if (nextTag.value.startsWith("</")) {
            depth--
            if (depth == 0) {
                val result = html.substring(contentStart, nextTag.range.first)
                return result
            }
        } else {
            depth++
        }
        searchFrom = nextTag.range.last + 1
    }
    return null
}

private fun extractArticleText(html: String): String {
    val jsonLdBody = extractArticleBodyFromJsonLd(html)
    if (jsonLdBody != null) {
        if (isUsableArticleText(jsonLdBody)) {
            return jsonLdBody
        }
    }

    val articleContentDiv = extractDivByClass(html, "article-content")
    if (articleContentDiv != null) {
        val converted = htmlCandidateToArticleText(articleContentDiv)
        if (isUsableArticleText(converted)) {
            return converted
        }
    }

    val itemPropBody = extractElementByAttribute(
        html,
        "section",
        """itemprop=["']articleBody["']"""
    ) ?: extractElementByAttribute(html, "div", """itemprop=["']articleBody["']""")
    if (itemPropBody != null) {
        val converted = htmlCandidateToArticleText(itemPropBody)
        if (isUsableArticleText(converted)) {
            return converted
        }
    }

    val candidateHtmlBlocks = buildList {
        val articleMatches = Regex(
            """<article\b[^>]*>(.*?)</article>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).findAll(html).toList()
        articleMatches.forEachIndexed { _, match ->
            add(match.groupValues[1])
        }

        val mainMatches = Regex(
            """<main\b[^>]*>(.*?)</main>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).findAll(html).toList()
        mainMatches.forEachIndexed { _, match ->
            add(match.groupValues[1])
        }

        val genericMatches = Regex(
            """<(?:div|section)\b[^>]*(?:class|id)=["'][^"']*(?:article|meldung|content|text|post)[^"']*["'][^>]*>(.*?)</(?:div|section)>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).findAll(html).toList()
        genericMatches.forEachIndexed { _, match ->
            add(match.groupValues[1])
        }

        add(html)
    }

    val scoredCandidates = candidateHtmlBlocks
        .asSequence()
        .map(::htmlCandidateToArticleText)
        .filter { candidate ->
            val usable = isUsableArticleText(candidate)
            usable
        }
        .map { candidate -> candidate to articleTextScore(candidate) }
        .toList()

    val best = scoredCandidates.maxByOrNull { it.second }
    if (best == null) {
        return ""
    }

    return best.first
}

private fun htmlCandidateToArticleText(articleHtml: String): String {
    val afterScriptStyleStrip = articleHtml.replace(
        Regex(
            """<(script|style|noscript|svg|nav|aside|footer|form|template)\b[^>]*>.*?</\1>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ),
        " "
    )

    val afterTocStrip = afterScriptStyleStrip.replace(
        Regex(
            """<a-collapse\b[^>]*>.*?</a-collapse>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ),
        " "
    )

    val afterNoticeStrip = afterTocStrip.replace(
        Regex(
            """<details\b[^>]*class=["'][^"']*notice-banner[^"']*["'][^>]*>.*?</details>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ),
        " "
    )

    val afterStrip = afterNoticeStrip.replace(
        Regex(
            """<div\b[^>]*data-component=["']RecommendationBox["'][^>]*>.*?</section>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ),
        " "
    )

    val afterNewlines = afterStrip
        .replace(
            Regex("""<(br|p|div|section|h[1-6]|li)\b[^>]*>""", RegexOption.IGNORE_CASE),
            PARAGRAPH_BREAK_MARKER
        )
        .replace(
            Regex("""</(p|div|section|h[1-6]|li)>""", RegexOption.IGNORE_CASE),
            PARAGRAPH_BREAK_MARKER
        )

    val afterHtmlDecode = cleanHtml(afterNewlines)

    val plainText = afterHtmlDecode.replace(PARAGRAPH_BREAK_MARKER, "\n")

    val allLines = plainText.lines().map { it.trim() }
    val keptLines = allLines.filter(::isArticleTextLine)

    val result = keptLines
        .distinct()
        .joinToString("\n\n")
        .trim()

    return result
}

private fun isArticleTextLine(line: String): Boolean {
    if (line.length <= 25) return false
    if (isTemplatePlaceholderText(line)) return false

    return !line.equals("Anzeige", ignoreCase = true) &&
            !line.equals("OBJ", ignoreCase = true) &&
            !line.contains("heise online", ignoreCase = true) &&
            !line.contains("JavaScript", ignoreCase = true) &&
            !line.contains("Cookie", ignoreCase = true) &&
            !line.contains("Weiterlesen nach der Anzeige", ignoreCase = true) &&
            !line.contains("Kommentare lesen", ignoreCase = true) &&
            !line.contains("Videos by heise", ignoreCase = true) &&
            !line.contains("Newsletter", ignoreCase = true)
}

private fun isUsableArticleText(text: String): Boolean {
    val cleaned = text.trim()
    if (cleaned.length < 300) {
        return false
    }
    if (isTemplatePlaceholderText(cleaned)) {
        return false
    }

    val letterCount = cleaned.count { it.isLetter() }
    val usable = letterCount > cleaned.length * 0.55
    return usable
}

private fun isTemplatePlaceholderText(text: String): Boolean {
    val compact = text
        .lowercase(Locale.ROOT)
        .filterNot { it.isWhitespace() }

    if (
        compact.contains("{intro}") ||
        compact.contains("{body}") ||
        compact.contains("{title}") ||
        compact.contains("{lead}") ||
        compact.contains("{article") ||
        compact.contains($$"${intro}") ||
        compact.contains($$"${body}") ||
        compact.contains($$"${title}") ||
        compact.contains($$"${lead}")
    ) {
        return true
    }

    var placeholderCount = 0
    var searchFrom = 0
    while (searchFrom < compact.length) {
        val openBrace = compact.indexOf('{', startIndex = searchFrom)
        if (openBrace == -1) break

        val closeBrace = compact.indexOf('}', startIndex = openBrace + 1)
        if (closeBrace == -1) break

        val nameStart =
            if (openBrace > 0 && compact[openBrace - 1] == '$') openBrace - 1 else openBrace
        val placeholder = compact.substring(nameStart, closeBrace + 1)
        val inner = compact.substring(openBrace + 1, closeBrace)
        if (
            inner.isNotBlank() &&
            inner.all { it.isLetterOrDigit() || it == '_' || it == '.' || it == '-' } &&
            placeholder.length <= 48
        ) {
            placeholderCount++
            if (placeholderCount >= 2) return true
        }

        searchFrom = closeBrace + 1
    }

    return false
}

private fun articleTextScore(text: String): Int {
    val paragraphCount = text.lines().count { it.length > 80 }
    return text.length + paragraphCount * 250
}

private fun extractArticleBodyFromJsonLd(html: String): String? {
    val scripts = Regex(
        """<script[^>]+type=["']application/ld\+json["'][^>]*>(.*?)</script>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    ).findAll(html).map { it.groupValues[1].trim() }.toList()

    scripts.forEachIndexed { _, script ->
        val decoded = Html.fromHtml(script, Html.FROM_HTML_MODE_LEGACY).toString().trim()
        val body = findArticleBodyInJson(decoded) ?: return@forEachIndexed
        val clean = cleanHtml(body)
        if (isUsableArticleText(clean)) {
            return clean
        }
    }
    return null
}

private fun findArticleBodyInJson(json: String): String? = runCatching {
    when {
        json.startsWith("[") -> findArticleBody(JSONArray(json))
        json.startsWith("{") -> findArticleBody(JSONObject(json))
        else -> null
    }
}.getOrNull()

private fun findArticleBody(value: Any?): String? {
    return when (value) {
        is JSONObject -> {
            value.optString("articleBody").takeIf { it.isNotBlank() }
                ?: value.optString("text").takeIf { it.length > 300 }
                ?: value.keys().asSequence()
                    .firstNotNullOfOrNull { key -> findArticleBody(value.opt(key)) }
        }

        is JSONArray -> (0 until value.length())
            .firstNotNullOfOrNull { index -> findArticleBody(value.opt(index)) }

        else -> null
    }
}

private fun cleanHtml(value: String): String =
    Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY)
        .toString()
        .replace(Regex("""[ \t\u000B\u000C\r]+"""), " ")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()

private fun extractImageUrl(content: String): String? = Regex(
    """<img[^>]+src=["']([^"']+)["']""",
    RegexOption.IGNORE_CASE
).find(content)
    ?.groupValues
    ?.getOrNull(1)
    ?.replace("&amp;", "&")

private fun parseFeedInstant(value: String): Instant? {
    val trimmed = value.trim()
    if (trimmed.isBlank()) return null
    runCatching { return Instant.parse(trimmed) }
    runCatching {
        return OffsetDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
    }
    runCatching { return OffsetDateTime.parse(trimmed).toInstant() }
    runCatching {
        return LocalDateTime.parse(trimmed).atZone(ZoneId.systemDefault()).toInstant()
    }
    return null
}

private fun formatFeedDate(value: String): String {
    val instant = parseFeedInstant(value) ?: return value
    return runCatching {
        val formatter = DateTimeFormatter
            .ofPattern("dd. MMM yyyy, HH:mm", Locale.GERMANY)
            .withZone(ZoneId.systemDefault())
        formatter.format(instant)
    }.getOrDefault(value)
}

private fun parseRawTimestamp(value: String): Long =
    parseFeedInstant(value)?.toEpochMilli() ?: 0L