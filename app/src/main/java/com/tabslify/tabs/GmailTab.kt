package com.tabslify.tabs

import android.content.Context
import android.text.Html
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.tabslify.R
import com.tabslify.core.objects.Config
import com.tabslify.core.objects.prvt
import com.tabslify.core.ui.AlertDialogTabslify
import com.tabslify.core.ui.SharedViewModel
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Suppress("PropertyName")
@Serializable
data class EmailRow(
    val account: String = "",
    val uid: String = "",
    val subject: String = "",
    val from_addr: String = "",
    val date_str: String = "",
    val timestamp: Long = 0L,
    val body: String = "",
    val summary: String? = null,
    val has_summary: Boolean = false,
)

data class EmailItem(
    val id: String,
    val account: String,
    val subject: String,
    val from: String,
    val date: String,
    val timestamp: Long,
    val body: String,
    val summary: String?,
    val hasSummary: Boolean,
)

private const val EMAIL_COLUMNS = "account,uid,subject,from_addr,date_str,timestamp,body,summary,has_summary"

private fun formatEmailDate(ts: Long, fallback: String): String {
    if (ts <= 0L) return fallback.take(16)
    return try {
        SimpleDateFormat("dd.MM.yy", Locale.getDefault()).format(Date(ts))
    } catch (_: Exception) {
        fallback.take(16)
    }
}

private fun EmailRow.toItem(context: Context): EmailItem = EmailItem(
    id = uid,
    account = account,
    subject = subject.ifBlank { context.getString(R.string.kein_betreff_2) },
    from = from_addr.ifBlank { context.getString(R.string.unbekannt) },
    date = formatEmailDate(timestamp, date_str),
    timestamp = timestamp,
    body = body,
    summary = summary?.takeIf { it.isNotBlank() },
    hasSummary = !summary.isNullOrBlank(),
)

private suspend fun loadEmailRows(): List<EmailRow> = Config.safeCall {
    Config.client.from("emails")
        .select(Columns.list(EMAIL_COLUMNS)) {
            order("timestamp", Order.DESCENDING)
        }
        .decodeList<EmailRow>()
}

private suspend fun deleteEmailRow(account: String, uid: String) = Config.safeCall {
    Config.client.from("emails").delete {
        filter {
            eq("account", account)
            eq("uid", uid)
        }
    }
}

private suspend fun triggerGmailSync() = withContext(Dispatchers.IO) {
    try {
        (URL("${Config.SUPABASE_URL}/functions/v1/gmail-sync")
            .openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Authorization", "Bearer ${Config.SUPABASE_PUBLISHABLE_KEY}")
            connectTimeout = 8_000
            readTimeout = 8_000
            doOutput = true
            outputStream.use { it.write(ByteArray(0)) }
            responseCode
            disconnect()
        }
    } catch (_: Exception) {
    }
}

@Composable
fun GmailTabContent(
    svm: SharedViewModel = viewModel(),
) {
    val context = LocalContext.current
    if (!prvt()) {
        Toast.makeText(context, stringResource(R.string.forbidden), Toast.LENGTH_SHORT).show()
        return
    }
    val scope = rememberCoroutineScope()
    val pendingEmailOpen by svm.pendingEmailOpen.collectAsState()

    var allEmails by remember { mutableStateOf<List<EmailItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var selectedEmail by remember { mutableStateOf<EmailItem?>(null) }
    var accountFilter by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<EmailItem?>(null) }
    val listState = rememberLazyListState()
    LaunchedEffect(accountFilter) {
        listState.scrollToItem(0)
    }
    val verbindungsfehler = stringResource(R.string.verbindungsfehler)
    val geloeschtMsg = stringResource(R.string.email_geloscht)
    val loeschFehlerMsg = stringResource(R.string.email_loschen_fehlgeschlagen)

    suspend fun reload() {
        isLoading = true
        errorMsg = null
        try {
            val fresh = loadEmailRows().map { it.toItem(context) }
            val freshById = fresh.associateBy { it.account to it.id }
            val updated = allEmails.mapNotNull { existing ->
                freshById[existing.account to existing.id]
            }
            val newOnes = fresh.filter { item ->
                allEmails.none { it.account == item.account && it.id == item.id }
            }
            allEmails = (newOnes + updated)
                .sortedByDescending { it.timestamp }
            selectedEmail?.let { sel ->
                selectedEmail = allEmails.find { it.account == sel.account && it.id == sel.id }
            }
        } catch (e: Exception) {
            if (allEmails.isEmpty()) errorMsg = String.format(verbindungsfehler, e.message)
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        reload()
    }

    LaunchedEffect(allEmails, pendingEmailOpen) {
        val target = pendingEmailOpen ?: return@LaunchedEffect
        val (account, uid) = target
        allEmails.find { it.account == account && it.id == uid }?.let { match ->
            selectedEmail = match
            svm.setPendingEmailOpen(null)
        }
    }

    val accounts = remember(allEmails) { allEmails.map { it.account }.distinct().sorted() }
    val emails = remember(allEmails, accountFilter) {
        accountFilter?.let { f -> allEmails.filter { it.account == f } } ?: allEmails
    }

    fun refresh() {
        scope.launch { reload() }
        scope.launch { triggerGmailSync() }
    }

    fun performDelete(email: EmailItem) {
        pendingDelete = null
        val before = allEmails
        allEmails = allEmails.filterNot { it.id == email.id && it.account == email.account }
        if (selectedEmail?.let { it.id == email.id && it.account == email.account } == true) {
            selectedEmail = null
        }
        scope.launch {
            try {
                deleteEmailRow(email.account, email.id)
                Toast.makeText(context, geloeschtMsg, Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                allEmails = before
                Toast.makeText(context, loeschFehlerMsg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        PullToRefreshBox(
            isRefreshing = isLoading,
            onRefresh = {
                refresh()
            },
            modifier = Modifier
                .fillMaxSize(),
            indicator = {}
        ) {
            Column(Modifier.fillMaxSize()) {
                if (accounts.size > 1) {
                    AccountFilterRow(
                        accounts = accounts,
                        selected = accountFilter,
                        onSelect = { accountFilter = it },
                        cacheInfo = if (isLoading) stringResource(R.string.ladt_bisher, emails.size)
                        else pluralStringResource(R.plurals.emails, emails.size, emails.size),
                    )
                }
                when {
                    isLoading && emails.isEmpty() -> LoadingPlaceholder()
                    errorMsg != null && emails.isEmpty() -> ErrorPlaceholder(errorMsg!!) { refresh() }
                    emails.isEmpty() -> EmptyPlaceholder { refresh() }
                    else -> {
                        if (isLoading) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp),
                                color = Color(0xFF4285F4),
                                trackColor = Color.Transparent
                            )
                        }
                        EmailList(
                            emails = emails,
                            listState = listState,
                            onClick = { selectedEmail = it },
                            onLongClick = { pendingDelete = it }
                        )
                    }
                }
            }
        }


        selectedEmail?.let { sel ->
            EmailDetailView(
                email = sel,
                onBack = { selectedEmail = null },
                onDelete = { pendingDelete = sel }
            )
        }


        pendingDelete?.let { target ->
            AlertDialogTabslify(
                onConfirm = { performDelete(target) },
                onDismiss = { pendingDelete = null },
                title = stringResource(R.string.email_loschen_titel),
                text = stringResource(R.string.email_loschen_text)
            )
        }
    }
}

@Composable
fun AccountFilterRow(
    accounts: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    cacheInfo: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        AccountChip(label = stringResource(R.string.alle), active = selected == null) { onSelect(null) }
        accounts.forEach { acc ->
            AccountChip(label = "${acc.substringBefore("@")}${if (selected == acc) " $cacheInfo" else ""}", active = selected == acc) { onSelect(acc) }
        }
    }
}

@Composable
private fun AccountChip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) Color(0xFF1E2A3A) else Color(0xFF1A1A22))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            label,
            color = if (active) Color(0xFF7EB8F7) else Color(0xFF8888AA),
            fontSize = 12.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
fun EmailList(
    emails: List<EmailItem>,
    listState: LazyListState,
    onClick: (EmailItem) -> Unit,
    onLongClick: (EmailItem) -> Unit
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(emails, key = { "${it.account}/${it.id}" }) { email ->
            EmailRow(
                email = email,
                onClick = { onClick(email) },
                onLongClick = { onLongClick(email) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EmailRow(email: EmailItem, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .background(Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val initial = email.from.firstOrNull { it.isLetter() }?.uppercase() ?: "?"
            val avatarColor = remember(email.from) {
                val colors = listOf(
                    0xFF4285F4L, 0xFFEA4335L, 0xFF34A853L, 0xFFFBBC05L,
                    0xFF9C27B0L, 0xFF00BCD4L, 0xFFFF5722L, 0xFF607D8BL
                )
                Color(colors[email.from.hashCode().and(0x7FFFFFFF) % colors.size])
            }
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(avatarColor),
                contentAlignment = Alignment.Center
            ) {
                Text(initial, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = extractDisplayName(email.from),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = email.date,
                        color = Color(0xFF555568),
                        fontSize = 11.sp,
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = email.subject,
                    color = Color(0xFFCCCCDD),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (email.hasSummary && email.summary != null) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E2A3A))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text("🤖", fontSize = 13.sp)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = email.summary,
                    color = Color(0xFF7EB8F7),
                    fontSize = 12.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(top = 10.dp),
            color = Color(0xFF1E1E2A)
        )
    }
}


@Composable
fun EmailDetailView(email: EmailItem, onBack: () -> Unit, onDelete: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF111114))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A1A22))
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
            }
            Text(
                text = stringResource(R.string.e_mail),
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    stringResource(R.string.loschen),
                    tint = Color(0xFFE57373)
                )
            }
        }
        HorizontalDivider(color = Color(0xFF222230))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = email.subject,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 27.sp
                )
            }
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1A1A22))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    MetaRow(stringResource(R.string.von), email.from)
                    MetaRow(stringResource(R.string.datum), email.date)
                }
            }

            if (email.hasSummary && email.summary != null) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF131E2E))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🤖", fontSize = 16.sp)
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.ki_zusammenfassung),
                                color = Color(0xFF4285F4),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = email.summary,
                            color = Color(0xFF9EC8F5),
                            fontSize = 14.sp,
                            lineHeight = 21.sp
                        )
                    }
                }
            }

            item {
                val body = remember(email.body) { emailBodyToMarkdown(email.body) }
                val bodyBlocks = remember(body, email.body) {
                    val content = body.ifBlank { email.body }
                    content.split(Regex("\\n[ \\t]*\\n"))
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .ifEmpty { listOf(content.trim()) }
                }
                val mdColors = markdownColor(
                    text = Color(0xFFE2E2EC),
                    dividerColor = Color(0xFF1E1E2A)
                )
                val mdTypography = markdownTypography(
                    text = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, color = Color(0xFFE2E2EC)),
                    paragraph = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, color = Color(0xFFE2E2EC)),
                    list = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, color = Color(0xFFE2E2EC)),
                    ordered = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, color = Color(0xFFE2E2EC)),
                    bullet = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, color = Color(0xFFE2E2EC)),
                    h1 = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF2F2F7)),
                    h2 = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFF0F0F5)),
                    h3 = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFECECF2)),
                    quote = TextStyle(fontSize = 15.sp, lineHeight = 21.sp, fontStyle = FontStyle.Italic, color = Color(0xFFBBBBCC)),
                    textLink = TextLinkStyles(
                        style = SpanStyle(
                            color = Color(0xFF72B7FF),
                            fontWeight = FontWeight.SemiBold,
                            textDecoration = TextDecoration.Underline
                        )
                    ),
                )
                SelectionContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        bodyBlocks.forEach { block ->
                            Markdown(
                                content = block,
                                colors = mdColors,
                                typography = mdTypography
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MetaRow(label: String, value: String) {
    Row {
        Text(
            text = "$label:  ",
            color = Color(0xFF555568),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            color = Color(0xFF9999AA),
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}


@Composable
fun LoadingPlaceholder() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(color = Color(0xFF4285F4))
            Text(stringResource(R.string.emails_werden_geladen), color = Color(0xFF555568), fontSize = 14.sp)
        }
    }
}

@Composable
fun ErrorPlaceholder(error: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Text("⚠️", fontSize = 40.sp)
            Text(
                stringResource(R.string.fehler_beim_laden_2),
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(error, color = Color(0xFF555568), fontSize = 13.sp, textAlign = TextAlign.Center)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF4285F4))
                    .clickable { onRetry() }
                    .padding(horizontal = 24.dp, vertical = 10.dp)
            ) {
                Text(
                    stringResource(R.string.erneut_versuchen),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun EmptyPlaceholder(onRefresh: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("📭", fontSize = 48.sp)
            Text(
                stringResource(R.string.keine_emails),
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF4285F4))
                    .clickable { onRefresh() }
                    .padding(horizontal = 24.dp, vertical = 10.dp)
            ) {
                Text(
                    stringResource(R.string.laden),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

private fun extractDisplayName(from: String): String {
    val match = Regex("""^"?([^"<]+)"?\s*<""").find(from.trim())
    return match?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
        ?: from.substringBefore("<").trim().takeIf { it.isNotBlank() }
        ?: from
}
private val htmlTagRegex = Regex("""<[^>]*>""")
private val plainTextAutolinkRegex = Regex(
    """<((?:https?://|www\.)[^\s<>]+|[^\s<>@]+@[^\s<>@]+)>""",
    RegexOption.IGNORE_CASE
)
private val plainTextTagRegex = Regex(
    """</?(?:a|article|b|blockquote|body|br|code|del|div|em|font|h[1-6]|head|hr|html|i|img|li|ol|p|pre|s|section|span|strike|strong|style|table|td|th|tr|tt|u|ul)(?=[\s/>])[^>]*>""",
    RegexOption.IGNORE_CASE
)

fun emailBodyToMarkdown(body: String): String {
    if (body.isBlank()) return body
    if (!looksLikeHtml(body)) return plainTextToMarkdown(body)
    return try {
        htmlToMarkdown(body)
    } catch (_: Exception) {
        plainTextToMarkdown(Html.fromHtml(body, Html.FROM_HTML_MODE_LEGACY).toString())
    }
}

fun plainTextToMarkdown(text: String): String {
    if (text.isBlank()) return text
    var normalized = text.replace("\r\n", "\n").replace('\r', '\n')
    if (normalized.contains("=\n")) {
        normalized = normalized.replace("=\n", "")
        if (Regex("=[0-9A-Fa-f]{2}").findAll(normalized).count() >= 3) {
            normalized = Regex("=([0-9A-Fa-f]{2})").replace(normalized) { m ->
                val code = m.groupValues[1].toIntOrNull(16)
                code?.toChar()?.toString() ?: m.value
            }
        }
    }
    val splitEscapeWrap = Regex("(https?://\\S*(?:%|[&=+?/_-]))[ \\t]*\\n[ \\t]*(?![-=*_~#•·]+[ \\t]*(?:\\n|$))(\\S+)")
    val longQueryWrap = Regex("(https?://\\S{40,})[ \\t]*\\n[ \\t]*(?![-=*_~#•·]+[ \\t]*(?:\\n|$))(\\S*[%&=+?#]\\S*)")
    fun joinOnce(value: String): String =
        splitEscapeWrap.replace(longQueryWrap.replace(value) { m -> m.groupValues[1] + m.groupValues[2] }) { m -> m.groupValues[1] + m.groupValues[2] }
    var joined = joinOnce(normalized)
    var rejoined = joinOnce(joined)
    while (rejoined != joined) {
        joined = rejoined
        rejoined = joinOnce(joined)
    }
    val urlThenText = Regex("(https?://\\S*[^\\s.,;:!?)])[ \\t]+(?=[\\p{Lu}„])")
    joined = urlThenText.replace(joined) { m -> m.groupValues[1] + "\n\n" }
    val blocks = joined.split(Regex("\\n[ \\t]*\\n"))
    return blocks.mapNotNull { block ->
        plainTextBlockToMarkdown(block).takeIf { it.isNotBlank() }
    }.joinToString("\n\n").replace(Regex("\\n{3,}"), "\n\n").trim()
}

private fun plainTextBlockToMarkdown(rawBlock: String): String {
    val lines = rawBlock.lines().map { it.trim() }.filter { it.isNotEmpty() }
    if (lines.isEmpty()) return ""
    if (lines.size == 1 && isSeparatorLine(lines[0])) return "---"
    val outLines = mutableListOf<String>()
    val paragraphLines = mutableListOf<String>()
    var inList = false
    fun flushParagraph() {
        if (paragraphLines.isEmpty()) return
        outLines.add(paragraphLines.joinToString("  \n"))
        paragraphLines.clear()
    }
    for (line in lines) {
        val headingTitle = framedHeadingTitle(line)
        if (headingTitle != null) {
            flushParagraph()
            inList = false
            outLines.add("**" + paragraphToMarkdown(headingTitle) + "**")
            continue
        }
        if (isSeparatorLine(line)) {
            flushParagraph()
            inList = false
            outLines.add("---")
            continue
        }
        val withoutTags = line
            .replace(plainTextAutolinkRegex) { m -> m.groupValues[1] }
            .replace(plainTextTagRegex, "")
        val trimmedStart = withoutTags.trimStart()
        if (trimmedStart.startsWith(">")) {
            flushParagraph()
            inList = false
            outLines.add("> " + paragraphToMarkdown(trimmedStart.removePrefix(">").trim()))
            continue
        }
        val listContent = Regex("^[-*•]\\s+(.+)$").find(trimmedStart)?.groupValues?.get(1)
        if (listContent != null) {
            val item = "- " + paragraphToMarkdown(listContent.trim())
            if (inList) {
                outLines[outLines.lastIndex] += "\n$item"
            } else {
                flushParagraph()
                outLines.add(item)
                inList = true
            }
            continue
        }
        val text = paragraphToMarkdown(decodePlainEntities(withoutTags).trim())
        if (text.isEmpty()) continue
        if (inList) {
            outLines[outLines.lastIndex] += "  \n$text"
        } else {
            paragraphLines.add(text)
        }
    }
    flushParagraph()
    return outLines.joinToString("\n\n")
}

private fun paragraphToMarkdown(raw: String): String {
    val cleaned = raw.replace(Regex("\\s+"), " ").trim()
    if (cleaned.isEmpty()) return ""
    val urlPattern = Regex("(https?://[^\\s)\"'<>\\]]+|www\\.[^\\s)\"'<>\\]]+)")
    val emailPattern = Regex("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}")
    val targets = mutableListOf<Pair<String, String>>()
    val withUrlHolders = urlPattern.replace(cleaned) { m ->
        val full = m.value.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '"', '\'')
        val trail = m.value.substring(full.length)
        val target = if (full.startsWith("www.")) "https://$full" else full
        targets.add(full to target)
        "\uE000${targets.lastIndex}\uE000$trail"
    }
    val withHolders = emailPattern.replace(withUrlHolders) { m ->
        val mail = m.value.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '"', '\'')
        val trail = m.value.substring(mail.length)
        targets.add(mail to "mailto:$mail")
        "\uE000${targets.lastIndex}\uE000$trail"
    }
    var escaped = withHolders
        .replace("\\", "\\\\")
        .replace("`", "\\`")
        .replace("*", "\\*")
        .replace("_", "\\_")
        .replace("[", "\\[")
        .replace("]", "\\]")
        .replace("|", "\\|")
    if (escaped.startsWith("#")) escaped = "\\$escaped"
    escaped = Regex("^(\\d+)([.)])").replace(escaped) { m -> m.groupValues[1] + "\\" + m.groupValues[2] }
    escaped = Regex("^([+\\->])").replace(escaped) { m -> "\\" + m.groupValues[1] }
    return Regex("\uE000(\\d+)\uE000").replace(escaped) { m ->
        val index = m.groupValues[1].toIntOrNull()
        val pair = if (index == null) null else targets.getOrNull(index)
        if (pair == null) m.value else "[${pair.first}](${pair.second})"
    }
}

private fun isSeparatorLine(line: String): Boolean {
    val compact = line.replace(" ", "").replace("\t", "")
    return compact.length >= 3 && compact.all { it == '=' || it == '-' || it == '_' || it == '~' || it == '*' || it == '#' || it == '•' || it == '·' }
}

private fun framedHeadingTitle(line: String): String? {
    val trimmed = line.trim()
    if (trimmed.length < 8) return null
    val framed = Regex("^([-=*_~—–]{3,})\\s*(.+?)\\s*\\1\\s*$").find(trimmed)
    if (framed != null) {
        val middle = framed.groupValues[2].trim()
        if (middle.any { it.isLetterOrDigit() }) return middle
    }
    val leaded = Regex("^([-=*_~—–]{5,})\\s+(\\S.*)$").find(trimmed)
    if (leaded != null) {
        val rest = leaded.groupValues[2].trim().trim('-', '=', '*', '_', '~', '—', '–', ' ')
        if (rest.length >= 3 && rest.any { it.isLetter() }) return rest
    }
    return null
}

private fun decodePlainEntities(value: String): String {
    var out = value
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
    out = Regex("&#(\\d+);").replace(out) { m ->
        val code = m.groupValues[1].toIntOrNull()
        code?.toChar()?.toString() ?: m.value
    }
    return out
}

private fun looksLikeHtml(text: String): Boolean {
    val compact = text.lowercase(Locale.ROOT)
    return compact.contains("<html") ||
        compact.contains("<body") ||
        compact.contains("<table") ||
        compact.contains("<tr>") ||
        compact.contains("<td") ||
        compact.contains("<div") ||
        compact.contains("<span") ||
        compact.contains("<font") ||
        compact.contains("<a ") ||
        compact.contains("<a>") ||
        compact.contains("<br") ||
        compact.contains("<b>") ||
        compact.contains("<b ") ||
        compact.contains("<strong") ||
        compact.contains("<em") ||
        compact.contains("<i>") ||
        compact.contains("<i ") ||
        compact.contains("<u>") ||
        compact.contains("<u ") ||
        compact.contains("<!doctype") ||
        (compact.contains("<p") && compact.contains("</p"))
}

private fun htmlToMarkdown(html: String): String {
    val stripped = html.replace(
        Regex("""<(script|style|head|template|noscript)\b[^>]*>.*?</\1>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
        " "
    )

    var out = stripped

    out = out.replace(
        Regex("""<(p|div|section|article|table|ul|ol|blockquote|h[1-6])\b[^>]*>""", RegexOption.IGNORE_CASE),
        "\n\n"
    )
    out = out.replace(
        Regex("""</(p|div|section|article|table|ul|ol|blockquote|h[1-6])>""", RegexOption.IGNORE_CASE),
        "\n\n"
    )
    out = out.replace(Regex("""<(br|hr|/tr)>""", RegexOption.IGNORE_CASE), "\n")

    out = out.replace(
        Regex("""<h([1-6])\b[^>]*>(.*?)</h\1>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
    ) { m ->
        val level = m.groupValues[1].toInt()
        "${"#".repeat(level.coerceIn(1, 6))} ${m.groupValues[2].trim()}"
    }

    out = out.replace(
        Regex("""<a\s+[^>]*href=["']([^"']*)["'][^>]*>(.*?)</a>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
    ) { m ->
        val url = m.groupValues[1].trim()
        val label = inlineToPlain(m.groupValues[2]).trim().ifBlank { url }
        "[$label]($url)"
    }

    out = out.replace(
        Regex("""<img\b[^>]*>""", RegexOption.IGNORE_CASE),
        ""
    )

    out = out.replace(Regex("""<li\b[^>]*>(.*?)</li>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))) { m ->
        "- ${inlineToPlain(m.groupValues[1]).trim()}"
    }

    out = out.replace(
        Regex("""<(b|strong)\b[^>]*>(.*?)</\1>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
        "**$2**"
    )
    out = out.replace(
        Regex("""<(i|em)\b[^>]*>(.*?)</\1>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
        "*$2*"
    )
    out = out.replace(
        Regex("""<(code|tt)\b[^>]*>(.*?)</\1>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
        "`$2`"
    )
    out = out.replace(Regex("""</?(u|s|strike|del)\b[^>]*>""", RegexOption.IGNORE_CASE), "")

    out = out.replace(Regex("""</?(th|td)\b[^>]*>""", RegexOption.IGNORE_CASE), "\t")
    out = out.replace(htmlTagRegex, "")

    out = decodeEntities(out)

    return out
        .lines()
        .joinToString("\n") { it.trimEnd() }
        .replace(Regex("\n[ \t]+"), "\n")
        .replace(Regex(" {2,}"), " ")
        .replace(Regex("\n{3,}"), "\n\n")
        .replace(Regex("(?m)^[ \t]+$"), "")
        .trim()
}

private fun inlineToPlain(text: String): String =
    decodeEntities(text.replace(htmlTagRegex, " ")).replace(Regex("\\s+"), " ").trim()

private fun decodeEntities(text: String): String =
    Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY).toString()
