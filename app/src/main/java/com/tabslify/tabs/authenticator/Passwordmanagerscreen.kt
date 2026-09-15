package com.tabslify.tabs.authenticator

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.edit
import androidx.core.net.toUri
import com.tabslify.R
import com.tabslify.core.activities.Tabslify.Companion.appScope
import com.tabslify.core.objects.Config.realDevice
import com.tabslify.core.objects.prvt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

val Surface1 = Color(0xFF17171C)
val Surface2 = Color(0xFF1F1F27)
val Surface3 = Color(0xFF2A2A35)
val AccentBlue = Color(0xFF4A90E2)
private val AccentRed = Color(0xFFE74C3C)
val TextP = Color(0xFFEEEEF5)
val TextS = Color(0xFF8A8A9F)
private val TextT = Color(0xFF55556A)

@Composable
fun PasswordManagerScreen(
    db: PasswordDatabase,
    twoFaDb: TwoFADatabase,
    onSettingsClick: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    val keinNetzwerkVerfuegbarMsg = stringResource(R.string.kein_netzwerk_verfugbar)
    val syncFehlgeschlagenMsg = stringResource(R.string.sync_fehlgeschlagen)
    val eintragGespeichertMsg = stringResource(R.string.eintrag_gespeichert)
    val aktualisiertMsg = stringResource(R.string.aktualisiert_2)
    val passwortMsg = stringResource(R.string.passwort)
    val kopiertMsg = stringResource(R.string.kopiert)

    var entries by remember { mutableStateOf<List<PasswordEntry>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Alle") }
    var showAddDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var editEntry by remember { mutableStateOf<PasswordEntry?>(null) }
    var detailEntry by remember { mutableStateOf<PasswordEntry?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isSyncing by remember { mutableStateOf(false) }
    var pendingConflicts by remember { mutableStateOf<List<SyncConflict>>(emptyList()) }

    fun reload() {
        scope.launch {
            isLoading = true
            entries = db.passwordDao().getAll()
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        reload()
    }

    val visible = remember(entries, searchQuery, selectedCategory) {
        entries.filter { e ->
            val q = searchQuery.trim()
            val matchSearch = q.isEmpty() ||
                    e.name.contains(q, true) ||
                    e.username.contains(q, true) ||
                    e.url.contains(q, true)
            matchSearch
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        stringResource(R.string.passworter),
                        color = TextP,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row {
                        IconButton(
                            onClick = { showAddDialog = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Add, stringResource(R.string.hinzufugen), tint = AccentBlue)
                        }
                        IconButton(
                            onClick = { showImportDialog = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.FileUpload, stringResource(R.string.import_action_secondary), tint = AccentBlue)
                        }
                        IconButton(
                            onClick = onSettingsClick,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Settings, stringResource(R.string.einstellungen), tint = AccentBlue)
                        }
                        if (prvt()) {
                            var clickcount by remember { mutableIntStateOf(0) }
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        clickcount++; if (clickcount >= 5) {
                                        db.passwordDao().deleteAll(); reload(); clickcount = 0
                                    }
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Delete, stringResource(R.string.import_action_secondary), tint = AccentBlue)
                            }
                            IconButton(
                                onClick = {
                                    if (!isSyncing && realDevice) {
                                        val appContext = context.applicationContext
                                        val prefs = appContext.getSharedPreferences(
                                            "sync_prefs",
                                            Context.MODE_PRIVATE
                                        )
                                        val lastSyncTime =
                                            prefs.getLong("last_sync_pw_timestamp", 0L)
                                        val currentTime = System.currentTimeMillis()

                                        if (currentTime - lastSyncTime > 20_000L) {
                                            isSyncing = true
                                            appScope.launch {
                                                try {
                                                    val result = syncPasswordEntriesWithCloud(
                                                        db,
                                                        twoFaDb,
                                                        appContext
                                                    )
                                                    if (result.error != null) {
                                                        Toast.makeText(
                                                            appContext,
                                                            keinNetzwerkVerfuegbarMsg,
                                                            Toast.LENGTH_LONG
                                                        ).show()
                                                        return@launch
                                                    }
                                                    if (result.pendingConflicts.isNotEmpty()) {
                                                        pendingConflicts = result.pendingConflicts
                                                    }
                                                    entries = db.passwordDao().getAll()
                                                    prefs.edit(commit = true) {
                                                        putLong(
                                                            "last_sync_pw_timestamp",
                                                            System.currentTimeMillis()
                                                        )
                                                    }
                                                } catch (e: Exception) {
                                                    Toast.makeText(
                                                        appContext,
                                                        syncFehlgeschlagenMsg.format(e.message),
                                                        Toast.LENGTH_LONG
                                                    ).show()
                                                } finally {
                                                    isSyncing = false
                                                }
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Refresh, stringResource(R.string.import_action_secondary), tint = AccentBlue)
                            }
                        }
                    }
                }
                Text(
                    pluralStringResource(R.plurals.eintrage, entries.size, entries.size),
                    color = TextS,
                    fontSize = 12.sp
                )
            }
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            placeholder = { Text(stringResource(R.string.suche_platzhalter), color = TextT) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = TextS) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, null, tint = TextS)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentBlue,
                unfocusedBorderColor = Surface3,
                focusedTextColor = TextP,
                unfocusedTextColor = TextP,
                cursorColor = AccentBlue
            )
        )

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = AccentBlue) }
        } else if (visible.isEmpty()) {
            EmptyState(hasEntries = entries.isNotEmpty())
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(visible, key = { it.id }) { entry ->
                    PasswordCard(
                        entry = entry,
                        onClick = { detailEntry = entry },
                        onEdit = { editEntry = entry },
                        onLongPress = {
                            scope.launch {
                                val clipboard =
                                    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(
                                    ClipData.newPlainText(
                                        "2FA Code",
                                        TotpGenerator.generateTOTP(
                                            twoFaDb.twoFADao().getAll().firstOrNull { twoFaEntry ->
                                                val n = twoFaEntry.name.lowercase()
                                                val entryName = entry.name.lowercase()
                                                val entryUrl = entry.url.lowercase()
                                                n.contains(entryName) || entryName.contains(n) ||
                                                        (entryUrl.isNotEmpty() && n.split(" ")
                                                            .any { entryUrl.contains(it) })
                                            }?.secret ?: "", System.currentTimeMillis()
                                        )
                                    )
                                )
                            }
                        },
                        onDelete = {
                            scope.launch {
                                db.passwordDao().delete(entry)
                                reload()
                            }
                        },
                        onCopy = {
                            scope.launch {
                                copyToClipboard(context, passwortMsg, entry.password, kopiertMsg)
                            }
                        }
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    if (showAddDialog) {
        AddEditPasswordDialog(
            initial = null,
            onDismiss = { showAddDialog = false },
            twoFaDb = twoFaDb,
            onSave = { newEntry ->
                scope.launch {
                    db.passwordDao().insert(newEntry)
                    showAddDialog = false
                    reload()
                    Toast.makeText(context, eintragGespeichertMsg, Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    editEntry?.let { entry ->
        AddEditPasswordDialog(
            initial = entry,
            onDismiss = { editEntry = null },
            twoFaDb = twoFaDb,
            onSave = { updated ->
                scope.launch {
                    db.passwordDao().update(updated.copy(updatedAt = System.currentTimeMillis()))
                    editEntry = null
                    reload()
                    Toast.makeText(context, aktualisiertMsg, Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    detailEntry?.let { entry ->
        PasswordDetailSheet(
            entry = entry,
            twoFaDb = twoFaDb,
            onDismiss = { detailEntry = null },
            onEdit = { detailEntry = null; editEntry = entry },
            onDelete = {
                scope.launch {
                    db.passwordDao().delete(entry)
                    detailEntry = null
                    reload()
                }
            }
        )
    }

    if (showImportDialog) {
        ImportPasswordsDialog(
            passwordDb = db,
            twoFaDb = twoFaDb,
            onDismiss = { showImportDialog = false },
            onImportDone = { reload() }
        )
    }

    if (pendingConflicts.isNotEmpty()) {
        SyncConflictDialog(
            conflicts = pendingConflicts,
            onDecision = { conflict, decision ->
                scope.launch {
                    applySyncConflictDecision(conflict, decision)
                }
            },
            onDone = {
                pendingConflicts = emptyList()
                reload()
            }
        )
    }
}

@Composable
private fun PasswordCard(
    entry: PasswordEntry,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onLongPress: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val accent = MaterialTheme.colorScheme.primary
    entry.name.take(2).uppercase()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface1)
            .border(1.dp, Surface3, RoundedCornerShape(16.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongPress() }
                )
            }
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(4.dp)
                .height(48.dp)
                .clip(RoundedCornerShape(0.dp, 4.dp, 4.dp, 0.dp))
                .background(accent)
        )

        Row(
            modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.name,
                    color = TextP,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (entry.username.isNotEmpty()) {
                    Text(
                        entry.username,
                        color = TextS,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(onClick = onCopy, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.ContentCopy,
                    stringResource(R.string.kopieren),
                    tint = TextS,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            modifier = Modifier.background(Surface2)
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.bearbeiten_3), color = TextP) },
                onClick = { showMenu = false; onEdit() }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.passwort_kopieren), color = TextP) },
                onClick = { showMenu = false; onCopy() }
            )
            HorizontalDivider(color = Surface3)
            DropdownMenuItem(
                text = { Text(stringResource(R.string.loschen_2), color = AccentRed) },
                onClick = { showMenu = false; onDelete() }
            )
        }
    }
}

@Composable
private fun PasswordDetailSheet(
    entry: PasswordEntry,
    twoFaDb: TwoFADatabase,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var showPassword by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    var matchedTwoFa by remember { mutableStateOf<TwoFAEntry?>(null) }
    var totpCode by remember { mutableStateOf("------") }
    var totpSecondsLeft by remember { mutableIntStateOf(30) }

    val benutzernameMsg = stringResource(R.string.benutzername_2)
    val passwortMsg = stringResource(R.string.passwort)
    val kopiertMsg = stringResource(R.string.kopiert)

    LaunchedEffect(entry) {
        val allTwoFa = withContext(Dispatchers.IO) { twoFaDb.twoFADao().getAll() }
        matchedTwoFa = allTwoFa.firstOrNull { twoFaEntry ->
            val n = twoFaEntry.name.lowercase()
            val entryName = entry.name.lowercase()
            val entryUrl = entry.url.lowercase()
            n.contains(entryName) || entryName.contains(n) ||
                    (entryUrl.isNotEmpty() && n.split(" ").any { entryUrl.contains(it) })
        } ?: entry.totpSecret?.let { secret ->
            val extracted = extractTotpSecret(secret)
            TwoFAEntry(name = entry.name, secret = extracted)
        }
    }

    LaunchedEffect(matchedTwoFa) {
        while (matchedTwoFa != null) {
            val now = System.currentTimeMillis()
            totpCode = TotpGenerator.generateTOTP(matchedTwoFa!!.secret, now)
            totpSecondsLeft = (30 - (now / 1000) % 30).toInt()
            delay(1000.milliseconds)
        }
    }

    BackHandler { onDismiss() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.5f)
                .padding(horizontal = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Surface1)
                    .padding(24.dp)
            ) {
                // ── Titel (fix) ──────────────────────────────────────
                Text(
                    entry.name,
                    color = TextP,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(20.dp))

                // ── Scrollbarer Content ──────────────────────────────
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (entry.username.isNotEmpty()) {
                        DetailField(
                            label = stringResource(R.string.benutzername),
                            value = entry.username,
                            onCopy = { copyToClipboard(context, benutzernameMsg, entry.username, kopiertMsg) }
                        )
                        Spacer(Modifier.height(12.dp))
                    }

                    Column {
                        Text(
                            stringResource(R.string.passwort_2),
                            color = TextS,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Surface2)
                                .border(1.dp, Surface3, RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (showPassword) entry.password
                                else "•".repeat(entry.password.length),
                                color = if (showPassword) TextP else TextS,
                                fontSize = 14.sp,
                                fontFamily = if (showPassword) FontFamily.Monospace else FontFamily.Default,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { showPassword = !showPassword },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    null, tint = TextS, modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = { copyToClipboard(context, passwortMsg, entry.password, kopiertMsg) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    null,
                                    tint = AccentBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        if (entry.password.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            StrengthBar(PasswordGenerator.strength(entry.password))
                        }
                    }

                    matchedTwoFa?.let {
                        Spacer(Modifier.height(12.dp))
                        Column {
                            Text(
                                stringResource(R.string.s_2fa_code_2),
                                color = TextS,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Surface2)
                                    .border(1.dp, Surface3, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        totpCode,
                                        color = AccentBlue,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 4.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        stringResource(R.string.gultig_noch_s, totpSecondsLeft),
                                        color = TextT,
                                        fontSize = 11.sp
                                    )
                                }
                                IconButton(
                                    onClick = { copyToClipboard(context, "2FA-Code", totpCode, kopiertMsg) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        null,
                                        tint = AccentBlue,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (entry.url.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        DetailField(
                            label = stringResource(R.string.url),
                            value = entry.url,
                            onCopy = { copyToClipboard(context, "URL", entry.url, kopiertMsg) },
                            onOpen = {
                                val uri =
                                    if (entry.url.startsWith("http://") || entry.url.startsWith("https://"))
                                        entry.url.toUri()
                                    else
                                        "https://${entry.url}".toUri()
                                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                            }
                        )
                    }

                    if (entry.notes.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Column {
                            Text(
                                stringResource(R.string.notizen_2),
                                color = TextS,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Surface2)
                                    .border(1.dp, Surface3, RoundedCornerShape(10.dp))
                                    .padding(12.dp)
                            ) {
                                Text(entry.notes, color = TextP, fontSize = 13.sp)
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                } // Ende scrollbarer Content

                // ── Buttons (fix) ────────────────────────────────────
                Spacer(Modifier.height(16.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentRed),
                        border = BorderStroke(1.dp, AccentRed),
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.loschen)) }

                    Button(
                        onClick = onEdit,
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.bearbeiten)) }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = Surface1,
            title = { Text(stringResource(R.string.eintrag_loschen), color = TextP) },
            text = { Text(stringResource(R.string.wird_dauerhaft_geloscht, entry.name), color = TextS) },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; onDelete() }) {
                    Text(stringResource(R.string.loschen), color = AccentRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.abbrechen), color = TextS)
                }
            }
        )
    }
}

@Composable
private fun DetailField(
    label: String, value: String, onCopy: () -> Unit,
    onOpen: (() -> Unit)? = null
) {
    Column {
        Text(label, color = TextS, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Surface2)
                .border(1.dp, Surface3, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                value,
                color = TextP,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.ContentCopy, null, tint = TextS, modifier = Modifier.size(16.dp))
            }
            onOpen?.let {
                IconButton(onClick = it, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.OpenInBrowser,
                        null,
                        tint = AccentBlue,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditPasswordDialog(
    initial: PasswordEntry?,
    twoFaDb: TwoFADatabase,
    onDismiss: () -> Unit,
    onSave: (PasswordEntry) -> Unit
) {
    val isEdit = initial != null
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf(initial?.name ?: "") }
    var url by remember { mutableStateOf(initial?.url ?: "") }
    var username by remember { mutableStateOf(initial?.username ?: "") }
    var password by remember { mutableStateOf(initial?.password ?: "") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var showPass by remember { mutableStateOf(false) }
    var showGen by remember { mutableStateOf(false) }

    var twoFaSecret by remember { mutableStateOf("") }
    var showScanner by remember { mutableStateOf(false) }
    var existingTwoFaEntry by remember { mutableStateOf<TwoFAEntry?>(null) }

    LaunchedEffect(initial) {
        if (initial != null) {
            val all = withContext(Dispatchers.IO) { twoFaDb.twoFADao().getAll() }
            existingTwoFaEntry = all.firstOrNull { twoFaEntry ->
                val n = twoFaEntry.name.lowercase()
                val entryName = initial.name.lowercase()
                val entryUrl = initial.url.lowercase()
                n.contains(entryName) || entryName.contains(n) ||
                        (entryUrl.isNotEmpty() && n.split(" ").any { entryUrl.contains(it) })
            }
            val raw = existingTwoFaEntry?.secret ?: ""
            twoFaSecret = if (raw == "null" || raw.isBlank()) "" else raw
        }
    }

    val strength = remember(password) { PasswordGenerator.strength(password) }
    val isValid = name.isNotBlank()

    if (showScanner) {
        SilentCaptureScreen(
            onDismiss = { showScanner = false },
            onSecretScanned = { scannedSecret ->
                twoFaSecret = scannedSecret
                showScanner = false
            }
        )
        return
    }

    if (showGen) {
        PasswordGeneratorSheet(
            onAccept = { generated -> password = generated; showGen = false },
            onDismiss = { showGen = false }
        )
        return
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Surface1)
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            Text(
                if (isEdit) stringResource(R.string.bearbeiten_3) else stringResource(R.string.neuer_eintrag_2),
                color = TextP, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(20.dp))
            PwField(label = stringResource(R.string.name_2), value = name, onValueChange = { name = it })
            Spacer(Modifier.height(12.dp))
            PwField(
                label = stringResource(R.string.url_domain),
                value = url,
                onValueChange = { url = it },
                keyboardType = KeyboardType.Uri
            )
            Spacer(Modifier.height(12.dp))
            PwField(
                label = stringResource(R.string.benutzername_e_mail),
                value = username,
                onValueChange = { username = it },
                keyboardType = KeyboardType.Email
            )
            Spacer(Modifier.height(12.dp))

            Column {
                Text(
                    stringResource(R.string.passwort_3),
                    color = TextS,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Surface2)
                        .border(1.dp, Surface3, RoundedCornerShape(10.dp))
                        .padding(end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = password,
                        onValueChange = { password = it },
                        singleLine = true,
                        visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                        textStyle = TextStyle(
                            color = TextP,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp, top = 12.dp, bottom = 12.dp)
                    )
                    IconButton(
                        onClick = { showPass = !showPass },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            if (showPass) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            null,
                            tint = TextS,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = { showGen = true }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.AutoFixHigh,
                            stringResource(R.string.generieren),
                            tint = AccentBlue,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                if (password.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    StrengthBar(strength)
                }
            }

            Spacer(Modifier.height(12.dp))

            HorizontalDivider(color = Surface3)
            Spacer(Modifier.height(12.dp))

            Column {
                Text(
                    stringResource(R.string.s_2fa_secret_optional),
                    color = TextS,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Surface2)
                        .border(
                            1.dp,
                            if (twoFaSecret.isNotEmpty()) AccentBlue.copy(alpha = 0.5f) else Surface3,
                            RoundedCornerShape(10.dp)
                        )
                        .padding(end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = twoFaSecret,
                        onValueChange = {
                            val v = extractTotpSecret(it)
                            twoFaSecret = if (v == "NULL") "" else v
                        },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = if (twoFaSecret.isNotEmpty()) AccentBlue else TextS,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp, top = 12.dp, bottom = 12.dp),
                        decorationBox = { inner ->
                            Box {
                                if (twoFaSecret.isEmpty()) {
                                    Text(
                                        stringResource(R.string.base32_schlussel_oder_qr_scannen),
                                        color = TextT,
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                inner()
                            }
                        }
                    )
                    if (twoFaSecret.isNotEmpty()) {
                        IconButton(
                            onClick = { twoFaSecret = "" },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.Clear,
                                null,
                                tint = TextS,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    IconButton(onClick = { showScanner = true }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.QrCodeScanner,
                            stringResource(R.string.qr_scannen),
                            tint = AccentBlue,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                if (twoFaSecret.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (existingTwoFaEntry != null) stringResource(R.string.vorhandener_2fa_eintrag_wird_aktualisiert)
                        else stringResource(R.string.neuer_2fa_eintrag_wird_verknupft),
                        color = AccentBlue, fontSize = 11.sp
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Column {
                Text(stringResource(R.string.notizen), color = TextS, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 80.dp),
                    maxLines = 4,
                    placeholder = { Text("Optional...", color = TextT) },
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentBlue, unfocusedBorderColor = Surface3,
                        focusedTextColor = TextP, unfocusedTextColor = TextP
                    )
                )
            }

            Spacer(Modifier.height(24.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.abbrechen), color = TextS)
                }
                Button(
                    onClick = {
                        val entry = if (isEdit) {
                            initial.copy(
                                name = name.trim(), url = url.trim(),
                                username = username.trim(), password = password,
                                notes = notes.trim()
                            )
                        } else {
                            PasswordEntry(
                                name = name.trim(), url = url.trim(),
                                username = username.trim(), password = password,
                                notes = notes.trim(),
                                totpSecret = null
                            )
                        }
                        if (twoFaSecret.isNotEmpty()) {
                            scope.launch {
                                val normalized = twoFaSecret.replace(" ", "").uppercase()
                                val existing2fa = existingTwoFaEntry
                                if (existing2fa != null) {
                                    twoFaDb.twoFADao().update(
                                        existing2fa.copy(
                                            secret = normalized,
                                            name = name.trim()
                                        )
                                    )
                                } else {
                                    val fa = TwoFAEntry(name = name.trim(), secret = normalized)
                                    twoFaDb.twoFADao().insertOrIgnore(fa)
                                    saveTwoFaEntryToSupabase(fa, twoFaDb)
                                }
                            }
                        } else {
                            existingTwoFaEntry?.let {
                                scope.launch {
                                    twoFaDb.twoFADao().delete(
                                        existingTwoFaEntry!!
                                    )
                                }
                            }
                        }
                        onSave(entry)
                    },
                    enabled = isValid,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.speichern)) }
            }
        }
    }
}


@Composable
fun PasswordGeneratorSheet(
    onAccept: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var length by remember { mutableFloatStateOf(20f) }
    var useLower by remember { mutableStateOf(true) }
    var useUpper by remember { mutableStateOf(true) }
    var useDigits by remember { mutableStateOf(true) }
    var useSymbols by remember { mutableStateOf(true) }
    var noAmbiguous by remember { mutableStateOf(false) }
    var generated by remember { mutableStateOf("") }
    val context = LocalContext.current

    val passwortMsg = stringResource(R.string.passwort)
    val kopiertMsg = stringResource(R.string.kopiert)

    LaunchedEffect(length, useLower, useUpper, useDigits, useSymbols, noAmbiguous) {
        generated = PasswordGenerator.generate(
            length = length.toInt(),
            lower = useLower,
            upper = useUpper,
            digits = useDigits,
            symbols = useSymbols,
            noAmbiguous = noAmbiguous
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Surface1)
                .padding(24.dp)
        ) {
            Text(
                stringResource(R.string.passwort_generator),
                color = TextP,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Surface2)
                    .border(1.dp, Surface3, RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        generated,
                        color = TextP,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { copyToClipboard(context, passwortMsg, generated, kopiertMsg) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            null,
                            tint = TextS,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    val hasCharset = useLower || useUpper || useDigits || useSymbols
                    IconButton(
                        onClick = {
                            if (hasCharset) {
                                generated = PasswordGenerator.generate(
                                    length.toInt(),
                                    useLower,
                                    useUpper,
                                    useDigits,
                                    useSymbols,
                                    noAmbiguous
                                )
                            }
                        },
                        enabled = hasCharset,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            null,
                            tint = if (hasCharset) AccentBlue else TextT,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.lange), color = TextS, fontSize = 13.sp, modifier = Modifier.width(60.dp))
                Slider(
                    value = length,
                    onValueChange = { length = it },
                    valueRange = 8f..64f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = AccentBlue,
                        activeTrackColor = AccentBlue
                    )
                )
                Text(
                    "${length.toInt()}",
                    color = AccentBlue,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(32.dp)
                )
            }

            Spacer(Modifier.height(12.dp))

            ToggleRow(stringResource(R.string.kleinbuchstaben_a_z), useLower) { useLower = it }
            ToggleRow(stringResource(R.string.grosbuchstaben_a_z), useUpper) { useUpper = it }
            ToggleRow(stringResource(R.string.ziffern_0_9), useDigits) { useDigits = it }
            ToggleRow(stringResource(R.string.sonderzeichen), useSymbols) { useSymbols = it }
            ToggleRow(stringResource(R.string.keine_ahnl_zeichen_0ol1), noAmbiguous) { noAmbiguous = it }

            Spacer(Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.abbrechen), color = TextS)
                }
                Button(
                    onClick = { onAccept(generated) },
                    enabled = generated.isNotEmpty() && (useLower || useUpper || useDigits || useSymbols),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.ubernehmen)) }
            }
        }
    }
}


@Composable
fun StrengthBar(strength: PasswordStrength) {
    val animFraction by animateFloatAsState(
        targetValue = strength.fraction,
        animationSpec = tween(400),
        label = "strength"
    )
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(stringResource(R.string.starke), color = TextT, fontSize = 11.sp)
            Text(
                stringResource(strength.labelRes),
                color = strength.color,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(3.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Surface3)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animFraction)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(strength.color)
            )
        }
    }
}

@Composable
private fun PwField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    Column {
        Text(label, color = TextS, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentBlue,
                unfocusedBorderColor = Surface3,
                focusedTextColor = TextP,
                unfocusedTextColor = TextP,
                cursorColor = AccentBlue
            )
        )
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextP, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = AccentBlue,
                uncheckedTrackColor = Surface3
            )
        )
    }
}

@Composable
private fun EmptyState(hasEntries: Boolean) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (hasEntries) "🔍" else "🔐", fontSize = 56.sp)
            Spacer(Modifier.height(16.dp))
            Text(
                if (hasEntries) stringResource(R.string.keine_treffer) else stringResource(R.string.noch_keine_passworter),
                color = TextP,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                if (hasEntries) stringResource(R.string.passe_die_suche_an) else stringResource(R.string.tippe_auf_um_loszulegen),
                color = TextS, fontSize = 14.sp
            )
        }
    }
}


fun copyToClipboard(context: Context, label: String, value: String, kopiertMsg: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, value))
    Toast.makeText(context, kopiertMsg.format(label), Toast.LENGTH_SHORT).show()
}

@Composable
fun SyncConflictDialog(
    conflicts: List<SyncConflict>,
    onDecision: (SyncConflict, SyncConflictDecision) -> Unit,
    onDone: () -> Unit
) {
    var index by remember { mutableIntStateOf(0) }

    if (index >= conflicts.size) {
        LaunchedEffect(Unit) { onDone() }
        return
    }

    val conflict = conflicts[index]

    Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Surface1)
                .padding(24.dp)
        ) {
            Text(
                stringResource(R.string.sync_konflikte),
                color = TextP,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.fortschritt_von, index + 1, conflicts.size), color = TextS, fontSize = 12.sp)
            Spacer(Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Surface2)
                    .padding(16.dp)
            ) {
                val entryName = conflict.cloudEntry?.name
                    ?: conflict.localEntry?.name
                    ?: "?"
                Text(entryName, color = TextP, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                val sub = conflict.cloudEntry?.username?.takeIf { it.isNotEmpty() }
                    ?: conflict.localEntry?.username?.takeIf { it.isNotEmpty() }
                    ?: ""
                if (sub.isNotEmpty()) Text(sub, color = TextS, fontSize = 13.sp)

                Spacer(Modifier.height(8.dp))
                Text(
                    when (conflict.type) {
                        SyncConflictType.CLOUD_ONLY_ENTRY -> stringResource(R.string.sync_konflikt_cloud_only_entry)
                        SyncConflictType.LOCAL_ONLY_ENTRY -> stringResource(R.string.sync_konflikt_local_only_entry)
                        SyncConflictType.TOTP_CLOUD_ONLY -> stringResource(R.string.sync_konflikt_totp_cloud_only)
                        SyncConflictType.TOTP_LOCAL_ONLY -> stringResource(R.string.sync_konflikt_totp_local_only)
                        SyncConflictType.TOTP_DIFFERENT -> stringResource(R.string.sync_konflikt_totp_different)
                    },
                    color = TextS,
                    fontSize = 13.sp
                )
            }

            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.sync_konflikt_entscheiden), color = TextP, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))

            when (conflict.type) {
                SyncConflictType.CLOUD_ONLY_ENTRY -> {
                    Button(
                        onClick = {
                            onDecision(conflict, SyncConflictDecision.KEEP_CLOUD)
                            index++
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.sync_cloud_behalten)) }

                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            onDecision(conflict, SyncConflictDecision.DELETE_CLOUD)
                            index++
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.loschen), color = AccentRed) }
                }

                SyncConflictType.LOCAL_ONLY_ENTRY -> {
                    Button(
                        onClick = {
                            onDecision(conflict, SyncConflictDecision.UPLOAD_LOCAL)
                            index++
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.sync_lokal_hochladen)) }

                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            onDecision(conflict, SyncConflictDecision.KEEP_LOCAL)
                            index++
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.sync_uberspringen), color = TextS) }
                }

                SyncConflictType.TOTP_CLOUD_ONLY -> {
                    Button(
                        onClick = {
                            onDecision(conflict, SyncConflictDecision.KEEP_CLOUD)
                            index++
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.sync_totp_cloud_behalten)) }

                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            onDecision(conflict, SyncConflictDecision.DELETE_CLOUD)
                            index++
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.sync_totp_cloud_loschen), color = AccentRed) }
                }

                SyncConflictType.TOTP_LOCAL_ONLY,
                SyncConflictType.TOTP_DIFFERENT -> {
                    Button(
                        onClick = {
                            onDecision(conflict, SyncConflictDecision.UPLOAD_LOCAL)
                            index++
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.sync_totp_lokal_ubernehmen)) }

                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            onDecision(conflict, SyncConflictDecision.KEEP_CLOUD)
                            index++
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.sync_totp_cloud_behalten), color = TextS) }
                }
            }
        }
    }
}