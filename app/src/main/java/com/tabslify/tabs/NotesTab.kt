package com.tabslify.tabs

import android.annotation.SuppressLint
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.edit
import com.tabslify.R
import com.tabslify.core.ui.APP_COLOR
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

sealed class NoteType {
    data class Text(val content: String) : NoteType()
    data class Checklist(val items: List<ChecklistItem>) : NoteType()
}

data class ChecklistItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isChecked: Boolean = false
)

data class Note(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val type: NoteType,
    val timestamp: Long = System.currentTimeMillis(),
    val color: NoteColor = NoteColor.DEFAULT
)

enum class NoteColor(val color: Color) {
    DEFAULT(APP_COLOR),
    RED(Color(0xFFF28B82)),
    ORANGE(Color(0xFFFBBC04)),
    YELLOW(Color(0xFFFFF475)),
    GREEN(Color(0xFFCCFF90)),
    BLUE(Color(0xFF1666FF)),
    PURPLE(Color(0xFFCBB5F5))
}

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun NotizenApp() {
    val context = LocalContext.current

    var notes by remember { mutableStateOf(loadNotes(context)) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var selectedNote by remember { mutableStateOf<Note?>(null) }

    BackHandler(selectedNote !== null) {
        selectedNote = null
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.notiz_erstellen))
            }
        },
        modifier = Modifier.background(Color.Transparent),
        containerColor = Color.Transparent
    ) { _ ->
        if (notes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.keine_notizen_vorhanden),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(APP_COLOR),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(notes, key = { it.id }) { note ->
                    NoteCard(
                        modifier = Modifier.background(APP_COLOR),
                        note = note,
                        onClick = { selectedNote = note },
                        onDelete = {
                            notes = deleteNote(context, notes, note.id)
                        }
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateNoteDialog(
            onDismiss = { showCreateDialog = false },
            onNoteCreated = { note ->
                notes = notes + note
                selectedNote = note
                showCreateDialog = false
            }
        )
    }

    selectedNote?.let { note ->
        EditNoteDialog(
            note = note,
            onDismiss = { selectedNote = null },
            onNoteSaved = { updatedNote ->
                notes = notes.map { if (it.id == updatedNote.id) updatedNote else it }
                selectedNote = null
                saveNotes(context, notes)
            },
            onDelete = {
                notes = deleteNote(context, notes, note.id)
                selectedNote = null
            }
        )
    }
}

@Composable
private fun DeleteConfirmationDialog(
    message: String = stringResource(R.string.mochtest_du_diese_notiz_wirklich),
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = APP_COLOR)
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 20.dp)
                    .widthIn(min = 280.dp, max = 360.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.abbrechen), color = Color.LightGray)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) {
                        Text(stringResource(R.string.loschen))
                    }
                }
            }
        }
    }
}

@Composable
fun NoteCard(
    note: Note,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier
) {
    val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMAN)
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (note.color == NoteColor.DEFAULT)
                Color.White.copy(alpha = 0.05f)
            else note.color.color
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = note.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.loschen),
                        modifier = Modifier.size(20.dp),
                        tint = Color.Red
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (val type = note.type) {
                is NoteType.Text -> {
                    Text(
                        text = type.content,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 5
                    )
                }

                is NoteType.Checklist -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        type.items.take(3).forEach { item ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = item.isChecked,
                                    onCheckedChange = null,
                                    enabled = false,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = item.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    textDecoration = if (item.isChecked) TextDecoration.LineThrough else null
                                )
                            }
                        }
                        if (type.items.size > 3) {
                            Text(
                                text = pluralStringResource(R.plurals.weitere, type.items.size - 3, type.items.size - 3),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = dateFormat.format(Date(note.timestamp)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
    if (showDeleteConfirm) {
        DeleteConfirmationDialog(
            onConfirm = {
                onDelete()
                showDeleteConfirm = false
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }
}

@Composable
fun CreateNoteDialog(
    onDismiss: () -> Unit,
    onNoteCreated: (Note) -> Unit
) {
    var noteTypeSelection by remember { mutableStateOf<String?>(null) }
    val newChecklistTitle = stringResource(R.string.neue_checkliste)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = APP_COLOR
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.notiztyp_auswahlen),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { noteTypeSelection = "text" },
                    border = BorderStroke(
                        2.dp,
                        if (noteTypeSelection == "text") MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline
                    ),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(0.05f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(stringResource(R.string.textnotiz), fontWeight = FontWeight.Bold, color = Color.White)
                            Text(
                                stringResource(R.string.normale_notiz_mit_text),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { noteTypeSelection = "checklist" },
                    border = BorderStroke(
                        2.dp,
                        if (noteTypeSelection == "checklist") MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline
                    ),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(0.05f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(stringResource(R.string.checkliste), fontWeight = FontWeight.Bold, color = Color.White)
                            Text(
                                stringResource(R.string.liste_mit_kontrollkastchen),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.abbrechen))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            when (noteTypeSelection) {
                                "text" -> onNoteCreated(
                                    Note(
                                        title = "",
                                        type = NoteType.Text("")
                                    )
                                )

                                "checklist" -> onNoteCreated(
                                    Note(
                                        title = newChecklistTitle,
                                        type = NoteType.Checklist(emptyList())
                                    )
                                )
                            }
                        },
                        enabled = noteTypeSelection != null
                    ) {
                        Text(stringResource(R.string.erstellen))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditNoteDialog(
    note: Note,
    onDismiss: () -> Unit,
    onNoteSaved: (Note) -> Unit,
    onDelete: () -> Unit
) {
    var title by remember { mutableStateOf(note.title) }
    var textContent by remember { mutableStateOf(if (note.type is NoteType.Text) note.type.content else "") }
    var checklistItems by remember {
        mutableStateOf(if (note.type is NoteType.Checklist) note.type.items else emptyList())
    }
    var newItemText by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(note.color) }
    var showColorPicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        val titleColor = if (selectedColor == NoteColor.ORANGE ||
            selectedColor == NoteColor.YELLOW ||
            selectedColor == NoteColor.GREEN ||
            selectedColor == NoteColor.PURPLE
        ) {
            Color.Black
        } else {
            Color.White
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (selectedColor == NoteColor.DEFAULT) {
                        APP_COLOR
                    } else {
                        selectedColor.color
                    }
                )
        ) {
            TopAppBar(
                title = { Text(stringResource(R.string.notiz_bearbeiten), color = titleColor) },
                colors = if (selectedColor == NoteColor.DEFAULT) {
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = APP_COLOR
                    )
                } else {
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = selectedColor.color
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.zuruck),
                            tint = titleColor
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showColorPicker = !showColorPicker }) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = stringResource(R.string.farbe),
                            tint = titleColor
                        )
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.loschen),
                            tint = Color.Red
                        )
                    }
                },
                windowInsets = WindowInsets(0, 0, 0, 0)
            )

            AnimatedVisibility(visible = showColorPicker) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    NoteColor.entries.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clickable { selectedColor = color },
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                modifier = Modifier.size(32.dp),
                                shape = RoundedCornerShape(16.dp),
                                color = color.color,
                                border = BorderStroke(
                                    2.dp,
                                    if (selectedColor == color) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline
                                )
                            ) {}
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.titel), color = titleColor) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = titleColor,
                        unfocusedTextColor = titleColor,
                        focusedLabelColor = titleColor,
                        unfocusedLabelColor = titleColor.copy(alpha = 0.6f)
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                when (note.type) {
                    is NoteType.Text -> {
                        OutlinedTextField(
                            value = textContent,
                            onValueChange = { textContent = it },
                            label = { Text(stringResource(R.string.notiz)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = titleColor,
                                unfocusedTextColor = titleColor,
                                focusedLabelColor = titleColor,
                                unfocusedLabelColor = titleColor.copy(alpha = 0.6f)
                            )
                        )
                    }

                    is NoteType.Checklist -> {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(checklistItems) { index, item ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Checkbox(
                                        checked = item.isChecked,
                                        onCheckedChange = { checked ->
                                            checklistItems =
                                                checklistItems.toMutableList().apply {
                                                    this[index] = item.copy(isChecked = checked)
                                                }
                                        }
                                    )
                                    Text(
                                        text = item.text,
                                        modifier = Modifier.weight(1f),
                                        textDecoration = if (item.isChecked) TextDecoration.LineThrough else null
                                    )
                                    IconButton(
                                        onClick = {
                                            checklistItems =
                                                checklistItems.filterIndexed { i, _ -> i != index }
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = stringResource(R.string.entfernen)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = newItemText,
                                onValueChange = { newItemText = it },
                                label = { Text(stringResource(R.string.neuer_punkt)) },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            IconButton(
                                onClick = {
                                    if (newItemText.isNotBlank()) {
                                        checklistItems =
                                            checklistItems + ChecklistItem(text = newItemText)
                                        newItemText = ""
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.hinzufugen))
                            }
                        }
                    }
                }
            }

            Button(
                onClick = {
                    val updatedNote = note.copy(
                        title = title,
                        type = when (note.type) {
                            is NoteType.Text -> NoteType.Text(textContent)
                            is NoteType.Checklist -> NoteType.Checklist(checklistItems)
                        },
                        color = selectedColor
                    )
                    onNoteSaved(updatedNote)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                enabled = title.isNotBlank()
            ) {
                Text(stringResource(R.string.speichern))
            }
        }
    }
    if (showDeleteConfirm) {
        DeleteConfirmationDialog(
            message = stringResource(R.string.willst_du_die_notiz_wirklich, note.title),
            onConfirm = {
                onDelete()
                showDeleteConfirm = false
                onDismiss()
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }
}

fun saveNotes(context: Context, notes: List<Note>) {
    val prefs = context.getSharedPreferences("notes_prefs", Context.MODE_PRIVATE)

    val serialized = notes.joinToString("||") { note ->
        val typePart = when (val type = note.type) {
            is NoteType.Text ->
                "TEXT;;${type.content}"

            is NoteType.Checklist ->
                "CHECK;;" + type.items.joinToString(";;") {
                    "${it.id},${it.text},${it.isChecked}"
                }
        }

        listOf(
            note.id,
            note.title,
            note.timestamp.toString(),
            note.color.name,
            typePart
        ).joinToString("##")
    }

    prefs.edit(commit = true) { putString("notes", serialized) }
}

fun deleteNote(context: Context, notes: List<Note>, noteId: String): List<Note> {
    val newList = notes.filterNot { it.id == noteId }

    saveNotes(context, newList)

    return newList
}

fun loadNotes(context: Context): List<Note> {
    val prefs = context.getSharedPreferences("notes_prefs", Context.MODE_PRIVATE)
    val raw = prefs.getString("notes", null) ?: return emptyList()

    return raw.split("||").mapNotNull { entry ->
        runCatching {
            val parts = entry.split("##")
            if (parts.size < 5) return@runCatching null

            val (id, title, timestamp, colorName, typeRaw) = parts

            val type = when {
                typeRaw.startsWith("TEXT;;") ->
                    NoteType.Text(typeRaw.removePrefix("TEXT;;"))

                typeRaw.startsWith("CHECK;;") -> {
                    val items = typeRaw
                        .removePrefix("CHECK;;")
                        .takeIf { it.isNotBlank() }
                        ?.split(";;")
                        ?.mapNotNull {
                            val i = it.split(",")
                            if (i.size < 3) null
                            else ChecklistItem(
                                id = i[0],
                                text = i[1],
                                isChecked = i[2].toBoolean()
                            )
                        } ?: emptyList()

                    NoteType.Checklist(items)
                }

                else -> return@runCatching null
            }

            Note(
                id = id,
                title = title,
                timestamp = timestamp.toLong(),
                color = NoteColor.valueOf(colorName),
                type = type
            )
        }.getOrNull()
    }
}