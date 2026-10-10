package com.tabslify.tabs

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.edit
import com.google.gson.GsonBuilder
import com.tabslify.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

// ─────────────────────────────────────────────
// DESIGN TOKENS  (matching MediaTab palette)
// ─────────────────────────────────────────────
private val BgDeep = Color(0xFF121212)
private val BgSurface = Color(0xFF1E1E1E)
private val BgCard = Color(0xFF2A2A2A)
private val AccentViolet = Color(0xFF7C4DFF)
private val AccentVioletDim = Color(0xFF4A148C)
private val TextPrimary = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFFB0B0B0)
private val TextTertiary = Color(0xFF757575)
private val NeonGreen = Color(0xFF39FF14)
private val NeonBlue = Color(0xFF00CFFF)
private val NeonPink = Color(0xFFFF2D78)

// ─────────────────────────────────────────────
// DATA MODELS
// ─────────────────────────────────────────────

/**
 * A calendar entry occupying [startMinute, endMinute) on a given day.
 * startMinute / endMinute = minutes since midnight (0..1439).
 */
data class CalendarEntry(
    val id: String = UUID.randomUUID().toString(),
    val date: String,           // ISO "yyyy-MM-dd"
    val startMinute: Int,       // 0..1439
    val endMinute: Int? = null,         // startMinute+1..1440
    val title: String,
    val description: String = "",
    val color: Long = AccentViolet.value.toLong(),
    val createdAt: Long = System.currentTimeMillis()
)

// ─────────────────────────────────────────────
// REPOSITORY  (SharedPreferences + StateFlow)
// ─────────────────────────────────────────────

object CalendarRepository {

    private const val PREFS = "calendar_entries"
    private const val KEY = "entries_json"
    private val gson = GsonBuilder().create()

    private val _entries = MutableStateFlow<List<CalendarEntry>>(emptyList())
    val entries: StateFlow<List<CalendarEntry>> = _entries.asStateFlow()

    fun init(context: Context) {
        _entries.value = emptyList()
        _entries.value = load(context)
    }

    /** Returns all entries whose date falls within [from, to] inclusive. */
    fun query(from: LocalDate, to: LocalDate): List<CalendarEntry> {
        return _entries.value.filter {
            val d = LocalDate.parse(it.date)
            !d.isBefore(from) && !d.isAfter(to)
        }
    }

    fun add(context: Context, entry: CalendarEntry) {
        val updated = _entries.value + entry
        _entries.value = updated
        save(context, updated)
    }

    fun update(context: Context, old: CalendarEntry, new: CalendarEntry) {
        val updated = _entries.value.map { if (it.id == old.id) new else it }
        _entries.value = updated
        save(context, updated)
    }

    fun delete(context: Context, entry: CalendarEntry) {
        val updated = _entries.value.filter { it.id != entry.id }
        _entries.value = updated
        save(context, updated)
    }

    private fun save(context: Context, list: List<CalendarEntry>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit { putString(KEY, gson.toJson(list)) }
    }

    private fun load(context: Context): List<CalendarEntry> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyList()
        return try {
            val type = object : com.google.gson.reflect.TypeToken<List<CalendarEntry>>() {}.type
            gson.fromJson(raw, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }
}

// ─────────────────────────────────────────────
// HELPER EXTENSIONS
// ─────────────────────────────────────────────

private fun Int.toHHMM(): String = "%02d:%02d".format(this / 60, this % 60)
private fun entryColor(entry: CalendarEntry) = Color(entry.color.toULong())

// ─────────────────────────────────────────────
// TOP-LEVEL COMPOSABLE
// ─────────────────────────────────────────────

@Composable
fun CalendarTabContent() {
    val context = LocalContext.current

    LaunchedEffect(Unit) { CalendarRepository.init(context) }

    var selectedDate by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }
    var showDayView by rememberSaveable { mutableStateOf(false) }
    var createDialogMinute by remember { mutableStateOf<Int?>(null) }
    var editEntry by remember { mutableStateOf<CalendarEntry?>(null) }

    val allEntries by CalendarRepository.entries.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDeep)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Header ──────────────────────────────────────
            CalendarHeader(
                currentMonth = currentMonth,
                onPrev = { currentMonth = currentMonth.minusMonths(1) },
                onNext = { currentMonth = currentMonth.plusMonths(1) },
                onToday = {
                    currentMonth = YearMonth.now()
                    selectedDate = LocalDate.now().toString()
                    showDayView = true
                }
            )

            // ── Month grid ──────────────────────────────────
            MonthGrid(
                yearMonth = currentMonth,
                selectedDate = selectedDate,
                entries = allEntries,
                onDayClick = { date ->
                    selectedDate = date.toString()
                    showDayView = true
                }
            )

            // ── Day view (expandable) ────────────────────────
            AnimatedVisibility(
                visible = showDayView,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                DayTimeline(
                    date = LocalDate.parse(selectedDate),
                    entries = allEntries.filter { it.date == selectedDate },
                    onMinuteTap = { minute -> createDialogMinute = minute },
                    onEntryClick = { entry -> editEntry = entry },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }

            if (!showDayView) Spacer(Modifier.weight(1f))
        }

        // ── FAB ─────────────────────────────────────────────
        FloatingActionButton(
            onClick = { createDialogMinute = LocalTime.now().hour * 60 + LocalTime.now().minute },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            containerColor = AccentViolet,
            contentColor = TextPrimary,
            shape = CircleShape
        ) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.neuer_eintrag))
        }
    }

    // ── Create dialog ────────────────────────────────────────
    createDialogMinute?.let { startMin ->
        EntryDialog(
            date = LocalDate.parse(selectedDate),
            initialStartMinute = startMin,
            existingEntry = null,
            onConfirm = { entry ->
                CalendarRepository.add(context, entry)
                createDialogMinute = null
            },
            onDismiss = { createDialogMinute = null }
        )
    }

    // ── Edit dialog ──────────────────────────────────────────
    editEntry?.let { entry ->
        EntryDialog(
            date = LocalDate.parse(entry.date),
            initialStartMinute = entry.startMinute,
            existingEntry = entry,
            onConfirm = { updated ->
                CalendarRepository.update(context, entry, updated)
                editEntry = null
            },
            onDelete = {
                CalendarRepository.delete(context, entry)
                editEntry = null
            },
            onDismiss = { editEntry = null }
        )
    }
}

// ─────────────────────────────────────────────
// CALENDAR HEADER
// ─────────────────────────────────────────────

@Composable
private fun CalendarHeader(
    currentMonth: YearMonth,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrev) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = TextSecondary)
        }

        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                currentMonth.month.getDisplayName(TextStyle.FULL, Locale.GERMAN)
                    .replaceFirstChar { it.uppercase() },
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                currentMonth.year.toString(),
                color = TextTertiary,
                fontSize = 13.sp
            )
        }

        TextButton(onClick = onToday) {
            Text(stringResource(R.string.heute), color = NeonGreen, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }

        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = TextSecondary)
        }
    }
}

// ─────────────────────────────────────────────
// MONTH GRID
// ─────────────────────────────────────────────

@Composable
private fun MonthGrid(
    yearMonth: YearMonth,
    selectedDate: String,
    entries: List<CalendarEntry>,
    onDayClick: (LocalDate) -> Unit
) {
    val today = LocalDate.now()
    val firstDayOfMonth = yearMonth.atDay(1)
    // Monday-first offset
    val startOffset = (firstDayOfMonth.dayOfWeek.value - 1)
    val daysInMonth = yearMonth.lengthOfMonth()
    val totalCells = startOffset + daysInMonth

    val entryDates = entries.map { it.date }.toSet()

    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 8.dp)) {
        // Weekday labels
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So").forEach { label ->
                Text(
                    label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    color = TextTertiary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        Spacer(Modifier.height(4.dp))

        // Grid cells
        val rows = ((totalCells + 6) / 7)
        for (row in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val cellIndex = row * 7 + col
                    val dayNumber = cellIndex - startOffset + 1
                    if (dayNumber !in 1..daysInMonth) {
                        Spacer(modifier = Modifier.weight(1f))
                    } else {
                        val date = yearMonth.atDay(dayNumber)
                        val isToday = date == today
                        val isSelected = date.toString() == selectedDate
                        val hasEntry = date.toString() in entryDates

                        DayCell(
                            day = dayNumber,
                            isToday = isToday,
                            isSelected = isSelected,
                            hasEntry = hasEntry,
                            modifier = Modifier.weight(1f),
                            onClick = { onDayClick(date) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: Int,
    isToday: Boolean,
    isSelected: Boolean,
    hasEntry: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bgColor = when {
        isSelected -> AccentViolet
        isToday -> AccentVioletDim.copy(alpha = 0.5f)
        else -> Color.Transparent
    }
    val textColor = when {
        isSelected -> TextPrimary
        isToday -> NeonGreen
        else -> TextSecondary
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(3.dp)
            .clip(CircleShape)
            .background(bgColor)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                day.toString(),
                color = textColor,
                fontSize = 14.sp,
                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal
            )
            if (hasEntry) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) NeonGreen else AccentViolet)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────
// DAY TIMELINE  (1 row = 1 minute)
// ─────────────────────────────────────────────

private const val MIN_MINUTE_HEIGHT = 1.5f
private const val MAX_MINUTE_HEIGHT = 8f
private val HOUR_LABEL_WIDTH = 44.dp

@Composable
private fun DayTimeline(
    date: LocalDate,
    entries: List<CalendarEntry>,
    onMinuteTap: (Int) -> Unit,
    onEntryClick: (CalendarEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    val fmt = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)
    val scrollState = rememberScrollState()
    val nowMinute = LocalTime.now().hour * 60 + LocalTime.now().minute

    var zoomScale by remember { mutableFloatStateOf(1f) }
    val minuteHeightPx = (2f * zoomScale).coerceIn(MIN_MINUTE_HEIGHT, MAX_MINUTE_HEIGHT)
    val minuteHeightDp = with(LocalDensity.current) { minuteHeightPx.toDp() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(date) {
        val target = ((nowMinute - 60).coerceAtLeast(0) * minuteHeightPx).toInt()
        scrollState.scrollTo(target)
    }

    Column(modifier = modifier) {
        // Day label strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgSurface)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                date.format(fmt).replaceFirstChar { it.uppercase() },
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                pluralStringResource(R.plurals.eintrage, entries.size, entries.size),
                color = TextTertiary,
                fontSize = 12.sp
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BgSurface)
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.ZoomIn,
                contentDescription = null,
                tint = TextTertiary,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                "%.1fx".format(zoomScale),
                color = TextTertiary,
                fontSize = 11.sp
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, _, zoomChange, _ ->
                        val oldScale = zoomScale
                        val newScale = (zoomScale * zoomChange).coerceIn(
                            MIN_MINUTE_HEIGHT / 2f,
                            MAX_MINUTE_HEIGHT / 2f
                        )
                        val oldMinH = 2f * oldScale
                        val newMinH = 2f * newScale
                        // Scroll so anpassen, dass der Inhalt unter centroid.y stabil bleibt
                        val newScroll =
                            ((scrollState.value + centroid.y) * (newMinH / oldMinH) - centroid.y)
                                .toInt().coerceAtLeast(0)
                        zoomScale = newScale
                        coroutineScope.launch { scrollState.scrollTo(newScroll) }
                    }
                }
        ) {
            // Scrollable minute grid
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                // Hour labels column
                Column(modifier = Modifier.width(HOUR_LABEL_WIDTH)) {
                    for (hour in 0..23) {
                        Box(
                            modifier = Modifier
                                .width(HOUR_LABEL_WIDTH)
                                .height(minuteHeightDp * 60),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Text(
                                "%02d:00".format(hour),
                                color = TextTertiary,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }

                // Timeline grid + entries
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(minuteHeightDp * 1440)
                ) {
                    // Minute tap grid
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    val minute = (offset.y / minuteHeightDp.toPx())
                                        .toInt()
                                        .coerceIn(0, 1439)
                                    onMinuteTap(minute)
                                }
                            }
                    ) {
                        // Hour separator lines
                        for (hour in 0..24) {
                            val y = hour * 60 * minuteHeightDp.toPx()
                            drawLine(
                                color = BgCard,
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = if (hour % 6 == 0) 1.5f else 0.8f
                            )
                        }
                        // 30-min dashes
                        for (slot in 0..47) {
                            val y = slot * 30 * minuteHeightDp.toPx()
                            drawLine(
                                color = BgCard.copy(alpha = 0.6f),
                                start = Offset(0f, y),
                                end = Offset(size.width * 0.3f, y),
                                strokeWidth = 0.5f
                            )
                        }
                        // "now" indicator
                        val nowY = nowMinute * minuteHeightDp.toPx()
                        drawLine(
                            color = NeonGreen,
                            start = Offset(0f, nowY),
                            end = Offset(size.width, nowY),
                            strokeWidth = 1.5f
                        )
                        drawCircle(NeonGreen, radius = 4f, center = Offset(0f, nowY))
                    }

                    // Render entries as overlapping blocks
                    entries.forEach { entry ->
                        EntryBlock(
                            entry = entry,
                            minuteHeightDp = minuteHeightDp,
                            onClick = { onEntryClick(entry) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EntryBlock(
    entry: CalendarEntry,
    minuteHeightDp: Dp,
    onClick: () -> Unit
) {
    val topDp = minuteHeightDp * entry.startMinute
    val heightDp = if (entry.endMinute != null)
        minuteHeightDp * (entry.endMinute - entry.startMinute).coerceAtLeast(1)
    else
        minuteHeightDp * 30
    val color = entryColor(entry)

    Box(
        modifier = Modifier
            .offset(y = topDp)
            .fillMaxWidth(0.92f)
            .height(heightDp)
            .padding(horizontal = 4.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        color.copy(alpha = 0.85f),
                        color.copy(alpha = 0.4f)
                    )
                )
            )
            .border(
                width = 1.dp,
                color = color,
                shape = RoundedCornerShape(4.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 2.dp)
    ) {
        if (heightDp >= 14.dp) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                        append(entry.startMinute.toHHMM())
                    }
                    append("  ${entry.title}")
                },
                color = TextPrimary,
                fontSize = 10.sp,
                maxLines = if (heightDp >= 30.dp) 2 else 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ─────────────────────────────────────────────
// ENTRY CREATE / EDIT DIALOG
// ─────────────────────────────────────────────

private val entryColors = listOf(
    AccentViolet, Color(0xFF00BCD4), NeonGreen, NeonPink,
    Color(0xFFFDD835), Color(0xFFFF7043), NeonBlue, Color(0xFF66BB6A)
)

@Composable
private fun EntryDialog(
    date: LocalDate,
    initialStartMinute: Int,
    existingEntry: CalendarEntry?,
    onConfirm: (CalendarEntry) -> Unit,
    onDelete: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(existingEntry?.title ?: "") }
    var description by remember { mutableStateOf(existingEntry?.description ?: "") }
    var startMin by remember { mutableIntStateOf(existingEntry?.startMinute ?: initialStartMinute) }
    var hasEndTime by remember { mutableStateOf(existingEntry?.endMinute != null) }
    var endMin by remember {
        mutableIntStateOf(existingEntry?.endMinute ?: (initialStartMinute + 60).coerceAtMost(1440))
    }
    var selectedColor by remember {
        mutableStateOf(
            entryColors.firstOrNull { it.value.toLong() == existingEntry?.color } ?: AccentViolet
        )
    }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val fmt = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)

    var colorscheme = MaterialTheme.colorScheme.copy(
        primary = selectedColor,
        primaryContainer = selectedColor.copy(alpha = 0.2f),
        onPrimary = TextPrimary
    )
    LaunchedEffect(selectedColor) {
        colorscheme = colorscheme.copy(primary = selectedColor)
    }

    MaterialTheme(colorScheme = colorscheme) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(BgSurface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Title bar
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (existingEntry == null) stringResource(R.string.neuer_eintrag) else stringResource(R.string.eintrag_bearbeiten),
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, null, tint = TextTertiary)
                        }
                    }

                    // Date chip
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            date.format(fmt).replaceFirstChar { it.uppercase() },
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Title field
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text(stringResource(R.string.titel), color = TextTertiary) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                        )
                    )

                    // Time pickers
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MinutePicker(
                            label = stringResource(R.string.von),
                            minute = startMin,
                            onMinuteChange = {
                                startMin = it
                                if (endMin <= it) endMin = (it + 30).coerceAtMost(1440)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Checkbox(
                                checked = hasEndTime,
                                onCheckedChange = { hasEndTime = it },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = AccentViolet,
                                    uncheckedColor = TextTertiary
                                )
                            )
                            Text(stringResource(R.string.endzeit_festlegen), color = TextSecondary, fontSize = 13.sp)
                        }
                        if (hasEndTime) {
                            MinutePicker(
                                label = stringResource(R.string.bis),
                                minute = endMin,
                                onMinuteChange = {
                                    endMin = it
                                    if (it <= startMin) startMin = (it - 1).coerceAtLeast(0)
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // Duration hint
                    if (hasEndTime) {
                        val durationMin = endMin - startMin
                        Text(
                            stringResource(R.string.dauer_h_min, durationMin / 60, durationMin % 60),
                            color = NeonGreen,
                            fontSize = 12.sp
                        )
                    }

                    // Description
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text(stringResource(R.string.beschreibung_optional), color = TextTertiary) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3,
                        colors = dialogTextFieldColors()
                    )

                    // Color picker
                    Text(stringResource(R.string.farbe), color = TextSecondary, fontSize = 13.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(entryColors) { color ->
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .then(
                                        if (color == selectedColor)
                                            Modifier.border(2.dp, TextPrimary, CircleShape)
                                        else Modifier
                                    )
                                    .clickable { selectedColor = color }
                            )
                        }
                    }

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (existingEntry != null && onDelete != null) {
                            OutlinedButton(
                                onClick = { showDeleteConfirm = true },
                                modifier = Modifier.weight(1f),
                                border = BorderStroke(1.dp, NeonPink),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonPink)
                            ) { Text(stringResource(R.string.loschen)) }
                        }
                        Button(
                            onClick = {
                                if (title.isNotBlank()) {
                                    onConfirm(
                                        CalendarEntry(
                                            id = existingEntry?.id ?: UUID.randomUUID().toString(),
                                            date = date.toString(),
                                            startMinute = startMin,
                                            endMinute = if (hasEndTime) endMin else null,
                                            title = title.trim(),
                                            description = description.trim(),
                                            color = selectedColor.value.toLong()
                                        )
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            enabled = title.isNotBlank() && (!hasEndTime || endMin > startMin)
                        ) {
                            Text(if (existingEntry == null) stringResource(R.string.erstellen) else stringResource(R.string.speichern))
                        }
                    }
                }
            }
        }
    }

    // Delete confirmation
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = BgSurface,
            title = { Text(stringResource(R.string.eintrag_loschen), color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    stringResource(R.string.wird_unwiderruflich_geloscht, existingEntry?.title ?: ""),
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = { onDelete?.invoke(); showDeleteConfirm = false }) {
                    Text(stringResource(R.string.loschen), color = NeonPink)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.abbrechen), color = TextTertiary)
                }
            }
        )
    }
}

// ─────────────────────────────────────────────
// MINUTE PICKER WHEEL  (scrollable HH:MM)
// ─────────────────────────────────────────────

@Composable
private fun MinutePicker(
    label: String,
    minute: Int,
    onMinuteChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Pre-scroll to current value when opened
    LaunchedEffect(expanded) {
        if (expanded) listState.scrollToItem(minute.coerceIn(0, 1439))
    }

    Column(modifier = modifier) {
        Text(label, color = TextTertiary, fontSize = 11.sp)
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(BgCard)
                .border(
                    1.dp,
                    if (expanded) AccentViolet else Color.Transparent,
                    RoundedCornerShape(10.dp)
                )
                .clickable { expanded = !expanded }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                minute.toHHMM(),
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        AnimatedVisibility(visible = expanded) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(BgCard)
                    .border(1.dp, AccentViolet.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 60.dp)
                ) {
                    items(1440) { m ->
                        val isSelected = m == minute
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isSelected) AccentViolet.copy(alpha = 0.2f) else Color.Transparent)
                                .clickable {
                                    onMinuteChange(m)
                                    expanded = false
                                }
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                m.toHHMM(),
                                color = if (isSelected) AccentViolet else TextSecondary,
                                fontSize = if (isSelected) 15.sp else 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
                // Center selection indicator lines
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .height(28.dp)
                        .border(
                            width = 1.dp,
                            brush = Brush.horizontalGradient(listOf(AccentViolet, NeonBlue)),
                            shape = RoundedCornerShape(4.dp)
                        )
                )
            }
        }
    }
}

// ─────────────────────────────────────────────
// HELPERS
// ─────────────────────────────────────────────

@Composable
private fun dialogTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedBorderColor = AccentViolet,
    unfocusedBorderColor = BgCard,
    cursorColor = AccentViolet,
    focusedContainerColor = BgCard,
    unfocusedContainerColor = BgCard
)