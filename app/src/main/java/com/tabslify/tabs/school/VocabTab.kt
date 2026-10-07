package com.tabslify.tabs.school

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.Transparent
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.tabslify.R
import com.tabslify.core.objects.Config
import com.tabslify.core.objects.prvt
import com.tabslify.core.ui.AccentViolet
import com.tabslify.core.ui.AccentVioletDim
import com.tabslify.core.ui.AlertDialogTabslify
import com.tabslify.core.ui.BgCard
import com.tabslify.core.ui.BgSurface
import com.tabslify.core.ui.TextPrimary
import com.tabslify.core.ui.TextSecondary
import com.tabslify.core.ui.TextTertiary
import com.tabslify.quiethoursnotificationhelper.AiTarget
import com.tabslify.quiethoursnotificationhelper.callNvidiaVisionApi
import com.tabslify.quiethoursnotificationhelper.flashcardVokabelnFlow
import com.tabslify.quiethoursnotificationhelper.getPreferredAiProvider
import com.tabslify.quiethoursnotificationhelper.sendAiRequest
import com.tabslify.quiethoursnotificationhelper.trySendImageToLaptop
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date


data class Vokabel(val latein: String, val deutsch: String, val id: Int)
data class VokabelSet(
    val name: String,
    val vokabeln: List<Vokabel>,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsed: Long = System.currentTimeMillis()
)

data class VokabelProgress(
    val vokabelId: Int,
    val correctCount: Int = 0,
    val wrongCount: Int = 0,
    val lastPracticed: Long = 0L,
    val streak: Int = 0
)

data class SetProgress(
    val setCreatedAt: Long,
    val vokabelProgress: Map<Int, VokabelProgress> = emptyMap(),
    val totalSessions: Int = 0,
    val lastSession: Long = 0L
)

data class KartenVerweis(val setId: Long, val id: Int, val richtung: Boolean)

data class LernKarte(
    val vokabel: Vokabel,
    val setId: Long,
    val richtung: Boolean
) {
    val frage: String get() = if (richtung) vokabel.deutsch else vokabel.latein
    val antwort: String get() = if (richtung) vokabel.latein else vokabel.deutsch
    val verweis: String get() = "$setId:${vokabel.id}"
}

data class LernErgebnis(
    val setId: Long,
    val vokabel: Vokabel,
    val richtig: Boolean,
    val richtung: Boolean
)

data class AntwortFeedback(val karte: LernKarte, val gewaehlt: String, val korrekt: Boolean)

data class SessionState(
    val cards: List<KartenVerweis>,
    val currentIndex: Int,
    val richtig: Int,
    val falsch: Int,
    val falscheKarten: List<LernKarte>,
    val richtung: Boolean,
    val timestamp: Long
)

enum class LernModus { AUSWAHL, TIPPEN, AUFECKEN }

enum class VokabelTabScreen { DASHBOARD, HOME, UPLOAD, REVIEW, LEARN, MATERIALIEN, NOTEN }

private val LEITNER_INTERVALLE = intArrayOf(0, 1, 2, 4, 8, 16, 32)

private const val SITZT_STREAK = 2

private const val TAG_MS = 24L * 60L * 60L * 1000L

fun leitnerIntervallTage(streak: Int): Int =
    LEITNER_INTERVALLE[streak.coerceIn(0, LEITNER_INTERVALLE.lastIndex)]

fun istFaellig(progress: VokabelProgress?, jetzt: Long = System.currentTimeMillis()): Boolean {
    if (progress == null || progress.lastPracticed == 0L) return true
    return jetzt - progress.lastPracticed >= leitnerIntervallTage(progress.streak) * TAG_MS
}

fun faelligeVokabeln(
    set: VokabelSet,
    prefs: SharedPreferences,
    jetzt: Long = System.currentTimeMillis()
): List<Vokabel> {
    val progress = loadSetProgress(prefs, set.createdAt)
    return set.vokabeln.filter { istFaellig(progress.vokabelProgress[it.id], jetzt) }
}

private fun normiereAntwort(text: String): String =
    text.trim().lowercase().replace(Regex("\\s+"), " ").trim()

private fun falteDiakritika(text: String): String = text
    .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")

fun vergleicheAntwort(eingabe: String, korrekt: String): Boolean {
    val a = normiereAntwort(eingabe)
    val b = normiereAntwort(korrekt)
    if (a.isEmpty()) return false
    if (a == b) return true
    return falteDiakritika(a) == falteDiakritika(b)
}

private const val LERNMODUS_KEY = "lern_modus"

fun ladeLernModus(prefs: SharedPreferences): LernModus {
    val roh = prefs.getString(LERNMODUS_KEY, null) ?: return LernModus.AUSWAHL
    return runCatching { LernModus.valueOf(roh) }.getOrDefault(LernModus.AUSWAHL)
}

fun speichereLernModus(prefs: SharedPreferences, modus: LernModus) {
    prefs.edit { putString(LERNMODUS_KEY, modus.name) }
}

fun ladeHinweis(prefs: SharedPreferences, verweis: String): String? =
    prefs.getString("hinweis_$verweis", null)?.takeIf { it.isNotBlank() }

fun speichereHinweis(prefs: SharedPreferences, verweis: String, text: String) {
    prefs.edit { putString("hinweis_$verweis", text) }
}

enum class ExtractionEngine { LAPTOP, NVIDIA, GEMINI }

private fun parseRecentMaterial(serialized: String): RecentMaterial? {
    val parts = serialized.split("\u001f")
    if (parts.size != 3) return null
    val subject = parts[0]
    val fileName = parts[1].ifBlank { null }
    val lastUsed = parts[2].toLongOrNull() ?: return null
    return RecentMaterial(subject = subject, fileName = fileName, lastUsed = lastUsed)
}

private fun isImageFile(name: String) =
    name.lowercase().let {
        it.endsWith(".jpg") || it.endsWith(".jpeg") || it.endsWith(".png") || it.endsWith(".webp")
    }

@Composable
fun VocabTab(paddingValues: PaddingValues) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("vocab_sets", Context.MODE_PRIVATE) }
    val materialPrefs =
        remember { context.getSharedPreferences("material_cache", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()

    var screen by remember { mutableStateOf(VokabelTabScreen.DASHBOARD) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            bitmap?.recycle()
            bitmap = null
        }
    }
    var vokabeln by remember { mutableStateOf<List<Vokabel>>(emptyList()) }
    var isExtracting by remember { mutableStateOf(false) }
    var usedEngine by remember { mutableStateOf<ExtractionEngine?>(null) }
    val cloudEngine = remember {
        if (getPreferredAiProvider(context, "vision") == "nvidia") ExtractionEngine.NVIDIA
        else ExtractionEngine.GEMINI
    }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var savedSets by remember { mutableStateOf(loadVokabelSets(prefs)) }
    var activeSet by remember { mutableStateOf<VokabelSet?>(null) }
    var cachedSetName by remember { mutableStateOf<String?>(null) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var saveNameInput by remember { mutableStateOf("") }
    var lernKarten by remember { mutableStateOf<List<LernKarte>>(emptyList()) }
    var lernZurueck by remember { mutableStateOf(VokabelTabScreen.HOME) }
    var showMergeDialog by remember { mutableStateOf(false) }
    var extractionJob by remember { mutableStateOf<Job?>(null) }
    var comingFromScan by remember { mutableStateOf(false) }
    var rawRecentMaterials by remember {
        mutableStateOf(
            materialPrefs.getString("recent_materials", null)
                ?.split("\u001e")
                ?.filter { it.isNotBlank() }
                ?: emptyList()
        )
    }
    var recentMaterialPreviews by remember { mutableStateOf<List<RecentMaterial>>(emptyList()) }

    val lokaleExtraktionLeer = stringResource(R.string.lokale_extraktion_leer)
    val keineVokabelnErkannt = stringResource(R.string.keine_vokabeln_erkannt)
    val fehlerMsgPattern = stringResource(R.string.fehler_msg)

    fun starteLernen(
        set: VokabelSet?,
        karten: List<LernKarte>,
        name: String? = set?.name,
        zurueck: VokabelTabScreen = VokabelTabScreen.HOME
    ) {
        activeSet = set
        cachedSetName = name
        lernKarten = karten
        lernZurueck = zurueck
        screen = VokabelTabScreen.LEARN
    }

    fun openSetAndUpdateLastUsed(set: VokabelSet) {
        val updatedSet = set.copy(lastUsed = System.currentTimeMillis())
        savedSets = saveVokabelSet(prefs, updatedSet)
        starteLernen(
            set = updatedSet,
            karten = updatedSet.vokabeln.map { LernKarte(it, updatedSet.createdAt, false) }
        )
    }

    val eigeneSetId: Long? = lernKarten.map { it.setId }.distinct().singleOrNull()
    val schreibbar = eigeneSetId != null && eigeneSetId == activeSet?.createdAt

    LaunchedEffect(rawRecentMaterials) {
        val parsed = rawRecentMaterials.mapNotNull(::parseRecentMaterial)
        recentMaterialPreviews = parsed.map { material ->
            if (material.fileName != null && isImageFile(material.fileName)) {
                val url = resolveFileUrl(context, material.subject, material.fileName)
                material.copy(previewUrl = url)
            } else material
        }
    }
    LaunchedEffect(Unit) {
        try {
            val parsed = rawRecentMaterials.mapNotNull(::parseRecentMaterial)
            val valid = parsed.filter { material ->
                if (material.fileName == null) return@filter false
                val folderFiles = try {
                    Config.client.storage.from("school").list(material.subject)
                } catch (_: Exception) {
                    return@filter true
                }
                folderFiles.any { it.name == material.fileName }
            }
            if (valid.size != parsed.size) {
                rawRecentMaterials = valid.map(::serializeRecentMaterial)
                materialPrefs.edit {
                    putString(
                        "recent_materials",
                        rawRecentMaterials.joinToString("\u001e")
                    )
                }
            }
        } catch (_: Exception) {
        } finally {
        }
    }
    var selectedMaterialSubject by remember { mutableStateOf<String?>(null) }
    var selectedMaterialFile by remember { mutableStateOf<String?>(null) }

    if (showMergeDialog) {
        MergeVocabSetsDialog(
            prefs = prefs,
            allSets = savedSets,
            onDismiss = { showMergeDialog = false },
            onMergeComplete = { mergedSet ->
                savedSets = saveVokabelSet(prefs, mergedSet)
                vokabeln = mergedSet.vokabeln
                starteLernen(
                    set = mergedSet,
                    karten = mergedSet.vokabeln.map { LernKarte(it, mergedSet.createdAt, false) }
                )
                showMergeDialog = false
            }
        )
    }

    val imagePicker =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                bitmap?.recycle()
                bitmap = uriToBitmap(context, it)
                vokabeln = emptyList()
                usedEngine = null
                activeSet = null
                cachedSetName = null
                screen = VokabelTabScreen.UPLOAD
            }
        }

    if (showSaveDialog) {
        SaveSetDialog(
            initial = saveNameInput,
            onConfirm = { name ->
                scope.launch {
                    val alterBestand = activeSet
                    val set = VokabelSet(
                        name = name.trim(),
                        vokabeln = vokabeln,
                        createdAt = alterBestand?.createdAt ?: System.currentTimeMillis()
                    )
                    savedSets = saveVokabelSet(prefs, set)
                    cachedSetName = name.trim()
                    showSaveDialog = false
                    saveNameInput = ""
                    comingFromScan = false
                    activeSet = set
                    screen = VokabelTabScreen.HOME
                }
            },
            onDismiss = { showSaveDialog = false }
        )
    }

    Crossfade(
        targetState = screen,
        label = "tab_transition"
    ) { current ->
        when (current) {
            VokabelTabScreen.DASHBOARD -> SchoolDashboard(
                savedSets = savedSets,
                faelligGesamt = savedSets.sumOf { faelligeVokabeln(it, prefs).size },
                onVocabClick = { screen = VokabelTabScreen.HOME },
                onDueClick = {
                    val jetzt = System.currentTimeMillis()
                    val faellig = savedSets.flatMap { s ->
                        faelligeVokabeln(s, prefs, jetzt).map { LernKarte(it, s.createdAt, false) }
                    }
                    if (faellig.isNotEmpty()) {
                        starteLernen(
                            set = null,
                            karten = faellig.shuffled(),
                            name = null,
                            zurueck = VokabelTabScreen.DASHBOARD
                        )
                    }
                },
                onMaterialClick = {
                    selectedMaterialSubject = null
                    selectedMaterialFile = null
                    screen = VokabelTabScreen.MATERIALIEN
                },
                onNotenClick = {
                    screen = VokabelTabScreen.NOTEN
                },
                onOpenSet = { set -> openSetAndUpdateLastUsed(set) },
                paddingValues = paddingValues,
                recentMaterials = recentMaterialPreviews,
                onOpenMaterial = {
                    selectedMaterialSubject = it.subject
                    selectedMaterialFile = it.fileName
                    screen = VokabelTabScreen.MATERIALIEN
                }
            )

            VokabelTabScreen.HOME -> VocabTabContent(
                savedSets = savedSets,
                prefs = prefs,
                onNewSet = {
                    usedEngine = null
                    activeSet = null
                    cachedSetName = null
                    screen = VokabelTabScreen.UPLOAD
                },
                onOpenSet = { set -> openSetAndUpdateLastUsed(set) },
                onLearnWeak = { set ->
                    val schwach = loadWeakVokabeln(prefs, set.createdAt)
                    val lernKarteListe = schwach.map { LernKarte(it, set.createdAt, false) }
                    if (lernKarteListe.isEmpty()) openSetAndUpdateLastUsed(set)
                    else starteLernen(set = set, karten = lernKarteListe)
                },
                onLearnWithMix = { set ->
                    val eigene = set.vokabeln.map { LernKarte(it, set.createdAt, false) }
                    val fremde = savedSets
                        .filter { it.createdAt != set.createdAt }
                        .flatMap { s -> s.vokabeln.map { LernKarte(it, s.createdAt, false) } }
                        .shuffled()
                    val mixAnzahl = (5..10).random().coerceAtMost(fremde.size)
                    starteLernen(
                        set = set,
                        karten = (eigene + fremde.take(mixAnzahl)).shuffled()
                    )
                },
                onDeleteSet = { set ->
                    savedSets = deleteVokabelSet(prefs, set)
                },
                onMergeClick = { showMergeDialog = true },
                onBack = { screen = VokabelTabScreen.DASHBOARD },
                paddingValues = paddingValues,
                onOpenMaterial = {
                    selectedMaterialSubject = it.subject
                    selectedMaterialFile = it.fileName
                    screen = VokabelTabScreen.MATERIALIEN
                }
            )

            VokabelTabScreen.UPLOAD -> UploadScreen(
                bitmap = bitmap,
                isExtracting = isExtracting,
                errorMessage = errorMessage,
                onPickImage = { imagePicker.launch("image/*") },
                onBack = { screen = VokabelTabScreen.HOME },
                onExtract = {
                    bitmap?.let { bmp ->
                        isExtracting = true
                        comingFromScan = true
                        errorMessage = null
                        extractionJob = scope.launch {
                            try {
                                val bytes = ByteArrayOutputStream().also {
                                    bmp.compress(Bitmap.CompressFormat.JPEG, 90, it)
                                }.toByteArray()
                                val sent =
                                    if (!Config.realDevice) false else trySendImageToLaptop(bytes)
                                if (sent) {
                                    usedEngine = ExtractionEngine.LAPTOP
                                    val result = flashcardVokabelnFlow.first { it != null }
                                    vokabeln = result ?: emptyList()
                                    if (vokabeln.isNotEmpty()) screen = VokabelTabScreen.REVIEW
                                    else {
                                        errorMessage = lokaleExtraktionLeer
                                    }
                                } else {
                                    usedEngine = cloudEngine
                                    callNvidiaVisionApi(
                                        context,
                                        bmp,
                                        onError = { msg -> errorMessage = msg },
                                        onVocabChange = { vocab -> vokabeln = vocab },
                                        onProgress = { str ->
                                            val lastBrace = str.lastIndexOf("},")
                                            val jsonToParse = if (lastBrace >= 0) str.substring(
                                                0,
                                                lastBrace + 1
                                            ) + "]" else null
                                            if (jsonToParse != null) {
                                                try {
                                                    val arr = JSONArray(jsonToParse)
                                                    val parsed = (0 until arr.length()).map {
                                                        val o = arr.getJSONObject(it)
                                                        Vokabel(
                                                            o.getString("latein"),
                                                            o.getString("deutsch"),
                                                            it
                                                        )
                                                    }
                                                    if (parsed.isNotEmpty()) {
                                                        vokabeln = parsed
                                                        screen = VokabelTabScreen.REVIEW
                                                    }
                                                } catch (_: Exception) {
                                                }
                                            }
                                        })
                                    if (vokabeln.isNotEmpty()) screen = VokabelTabScreen.REVIEW
                                    else errorMessage = keineVokabelnErkannt
                                }
                            } catch (e: Exception) {
                                errorMessage = String.format(fehlerMsgPattern, e.localizedMessage)
                            } finally {
                                isExtracting = false
                            }
                        }
                    }
                },
                plannedEngine = if (Config.realDevice) ExtractionEngine.LAPTOP else cloudEngine,
                fallbackEngine = cloudEngine,
                usedEngine = usedEngine,
                paddingValues = paddingValues
            )

            VokabelTabScreen.REVIEW -> ReviewScreen(
                vokabeln = vokabeln,
                setName = activeSet?.name ?: cachedSetName,
                isExtracting = isExtracting,
                fromScan = comingFromScan,
                onVokabelnChanged = { vokabeln = it },
                onStartLearning = {
                    lernKarten = vokabeln.map { LernKarte(it, 0L, false) }
                    lernZurueck = VokabelTabScreen.REVIEW
                    screen = VokabelTabScreen.LEARN
                },
                onSave = { saveNameInput = activeSet?.name ?: ""; showSaveDialog = true },
                onBack = {
                    screen =
                        if (activeSet != null) VokabelTabScreen.LEARN else VokabelTabScreen.UPLOAD
                },
                checkExist = activeSet != null,
                onCancelExtraction = if (isExtracting) {
                    {
                        extractionJob?.cancel()
                        isExtracting = false
                        screen = VokabelTabScreen.UPLOAD
                    }
                } else null,
                paddingValues = paddingValues
            )

            VokabelTabScreen.LEARN -> LearnScreen(
                karten = lernKarten,
                prefs = prefs,
                sessionKey = eigeneSetId ?: 0L,
                onBack = {
                    screen = lernZurueck
                    activeSet = null
                },
                setName = activeSet?.name ?: cachedSetName,
                onVokabelnUpdated = { updatedVokabeln ->
                    if (schreibbar) {
                        vokabeln = updatedVokabeln
                        val updatedSet = activeSet?.copy(vokabeln = updatedVokabeln)
                        activeSet = updatedSet
                        if (updatedSet != null) savedSets = saveVokabelSet(prefs, updatedSet)
                    }
                },
                onRenameRequest = {
                    if (activeSet != null) {
                        saveNameInput = activeSet?.name ?: " "
                        showSaveDialog = true
                    }
                },
                paddingValues = paddingValues
            )

            VokabelTabScreen.MATERIALIEN -> MaterialienScreen(
                onBack = {
                    selectedMaterialSubject = null
                    selectedMaterialFile = null
                    screen = VokabelTabScreen.DASHBOARD
                },
                onOpenSet = { set -> openSetAndUpdateLastUsed(set) },
                paddingValues = paddingValues,
                initialSubject = selectedMaterialSubject,
                initialFile = selectedMaterialFile
            )

            VokabelTabScreen.NOTEN -> NotenScreen(
                onBack = {
                    screen = VokabelTabScreen.DASHBOARD
                },
                paddingValues = paddingValues
            )
        }
    }
}

@Composable
fun SchoolDashboard(
    savedSets: List<VokabelSet>,
    onVocabClick: () -> Unit,
    onDueClick: () -> Unit,
    onMaterialClick: () -> Unit,
    onNotenClick: () -> Unit,
    onOpenSet: (VokabelSet) -> Unit,
    paddingValues: PaddingValues,
    recentMaterials: List<RecentMaterial> = emptyList(),
    onOpenMaterial: (RecentMaterial) -> Unit = {},
    faelligGesamt: Int = 0
) {
    Column(modifier = Modifier.fillMaxSize()) {
        SchoolHeader(
            title = stringResource(R.string.schule),
            subtitle = stringResource(R.string.dein_dashboard),
            savedSets = savedSets,
            onOpenSet = onOpenSet,
            recentMaterials = recentMaterials,
            onOpenMaterial = onOpenMaterial,
            showDashboard = true,
            paddingValues = paddingValues
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { onVocabClick() }
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                        .weight(1f)
                ) {
                    Icon(
                        Icons.Default.Style,
                        contentDescription = "",
                        tint = LocalContentColor.current.copy(0.4f)
                    )
                    Text(
                        stringResource(R.string.vokabeln),
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (prvt()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable { onMaterialClick() }
                            .padding(horizontal = 20.dp, vertical = 18.dp)
                            .weight(1f)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.StickyNote2,
                            contentDescription = "",
                            tint = LocalContentColor.current.copy(0.4f)
                        )
                        Text(
                            stringResource(R.string.materialien),
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
              if (prvt()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { onNotenClick() }
                        .padding(horizontal = 10.dp, vertical = 18.dp)
                        .weight(1f)
                ) {
                    Text("📊", fontSize = 18.sp)
                    Text(
                        stringResource(R.string.noten),
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }}
        }

        if (savedSets.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (faelligGesamt > 0) MaterialTheme.colorScheme.primary else BgSurface)
                    .clickable(enabled = faelligGesamt > 0) { onDueClick() }
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("📅", fontSize = 22.sp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.heute_due),
                            color = if (faelligGesamt > 0) TextPrimary else TextSecondary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (faelligGesamt > 0) pluralStringResource(
                                R.plurals.faellige_vokabeln, faelligGesamt, faelligGesamt
                            ) else stringResource(R.string.alles_gelernt),
                            color = TextTertiary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VocabTabContent(
    savedSets: List<VokabelSet>,
    prefs: SharedPreferences,
    onNewSet: () -> Unit,
    onOpenSet: (VokabelSet) -> Unit,
    onLearnWeak: (VokabelSet) -> Unit,
    onLearnWithMix: (VokabelSet) -> Unit,
    onDeleteSet: (VokabelSet) -> Unit,
    onMergeClick: () -> Unit = {},
    onBack: () -> Unit,
    paddingValues: PaddingValues,
    onOpenMaterial: (RecentMaterial) -> Unit = {}
) {
    var setToDelete by remember { mutableStateOf<VokabelSet?>(null) }
    var menuOpenFor by remember { mutableStateOf<Long?>(null) }

    BackHandler {
        onBack()
    }

    val sortedSets = savedSets.sortedByDescending { it.lastUsed }

    if (setToDelete != null) {
        AlertDialogTabslify(
            title = stringResource(R.string.set_loschen),
            text = stringResource(R.string.wird_geloscht, setToDelete!!.name),
            onConfirm = { onDeleteSet(setToDelete!!); setToDelete = null },
            onDismiss = { setToDelete = null },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            SchoolHeader(
                title = stringResource(R.string.vokabeln),
                subtitle = stringResource(R.string.ubersicht_scannen),
                onBack = onBack,
                savedSets = savedSets,
                onOpenSet = onOpenSet,
                recentMaterials = emptyList(),
                onOpenMaterial = onOpenMaterial,
                showDashboard = false,
                paddingValues = paddingValues,
                drawGradient = true
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .clickable { onNewSet() }
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 20.dp, vertical = 18.dp)
                            .weight(1f)
                    ) {
                        Text("📷", fontSize = 22.sp)
                        Text(
                            stringResource(R.string.scannen),
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            if (savedSets.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("📚", fontSize = 56.sp)
                        Text(
                            stringResource(R.string.noch_keine_sets),
                            color = TextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            stringResource(R.string.scan_ein_vokabelbild_zum_starten),
                            color = TextTertiary,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                Text(
                    stringResource(R.string.gespeicherte_sets, savedSets.size),
                    color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(sortedSets, key = { it.createdAt }) { set ->
                        val session = remember { loadSessionState(prefs, set.createdAt) }
                        val schwachListe = remember { loadWeakVokabeln(prefs, set.createdAt) }
                        val setFortschritt = remember { loadSetProgress(prefs, set.createdAt) }
                        val sitzt = set.vokabeln.count {
                            (setFortschritt.vokabelProgress[it.id]?.streak ?: 0) >= SITZT_STREAK
                        }
                        val faellig = set.vokabeln.count {
                            istFaellig(setFortschritt.vokabelProgress[it.id])
                        }
                        val masteryQuote by animateFloatAsState(
                            targetValue = if (set.vokabeln.isEmpty()) 0f
                            else sitzt.toFloat() / set.vokabeln.size,
                            label = "mastery_$set"
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable { onOpenSet(set) }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                Brush.linearGradient(
                                                    listOf(
                                                        AccentViolet,
                                                        AccentVioletDim
                                                    )
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("📖", fontSize = 22.sp)
                                    }
                                    Spacer(Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            set.name,
                                            color = TextPrimary,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                stringResource(
                                                    R.string.sitzende_vokabeln, sitzt, set.vokabeln.size
                                                ),
                                                color = TextTertiary,
                                                fontSize = 11.sp
                                            )
                                            if (faellig > 0) {
                                                Text(
                                                    stringResource(
                                                        R.string.faellig_kurz, faellig
                                                    ),
                                                    color = AccentViolet,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            } else if (setFortschritt.totalSessions > 0) {
                                                Text(
                                                    stringResource(
                                                        R.string.letzte_sitzung,
                                                        SimpleDateFormat(
                                                            "dd.MM.",
                                                            LocalLocale.current.platformLocale
                                                        ).format(Date(setFortschritt.lastSession))
                                                    ),
                                                    color = TextTertiary,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                    }
                                    val weakCount = schwachListe.size
                                    if (weakCount > 0) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color(0xFFB71C1C).copy(alpha = 0.2f))
                                                .clickable { onLearnWeak(set) }
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                "✗ $weakCount",
                                                color = Color(0xFFEF5350),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Spacer(Modifier.width(6.dp))
                                    }

                                    Box {
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(BgCard)
                                                .clickable { menuOpenFor = set.createdAt },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.MoreVert,
                                                contentDescription = null,
                                                tint = TextSecondary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        val alpha by animateFloatAsState(
                                            targetValue = if (menuOpenFor == set.createdAt) 1f else 0f,
                                            animationSpec = if (menuOpenFor == set.createdAt)
                                                tween(durationMillis = 80)
                                            else
                                                tween(durationMillis = 300),
                                            label = "menuAlpha"
                                        )

                                        DropdownMenu(
                                            expanded = menuOpenFor == set.createdAt,
                                            onDismissRequest = { menuOpenFor = null },
                                            containerColor = BgCard,
                                            modifier = Modifier.alpha(alpha)
                                        ) {
                                            if (weakCount > 0) {
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        stringResource(
                                                            R.string.nur_schwache, weakCount
                                                        ),
                                                        color = Color(0xFFEF5350),
                                                        fontSize = 14.sp
                                                    )
                                                },
                                                onClick = {
                                                    menuOpenFor = null; onLearnWeak(set)
                                                }
                                            )
                                        }
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    stringResource(R.string.mix_modus),
                                                    color = TextPrimary,
                                                    fontSize = 14.sp
                                                )
                                            },
                                            onClick = {
                                                menuOpenFor = null; onLearnWithMix(set)
                                            }
                                        )
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        stringResource(R.string.loschen_3),
                                                        color = Color(0xFFEF5350),
                                                        fontSize = 14.sp
                                                    )
                                                },
                                                onClick = { menuOpenFor = null; setToDelete = set }
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(
                                                Brush.linearGradient(
                                                    listOf(
                                                        AccentViolet.copy(0.2f),
                                                        AccentVioletDim.copy(0.2f)
                                                    )
                                                )
                                            )
                                    ) {
                                        if (masteryQuote > 0f) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth(masteryQuote)
                                                    .fillMaxHeight()
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(
                                                        Brush.linearGradient(
                                                            listOf(
                                                                AccentViolet,
                                                                AccentVioletDim
                                                            )
                                                        )
                                                    )
                                            )
                                        }
                                    }

                                    Spacer(Modifier.width(10.dp))

                                    if (session != null) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(AccentViolet.copy(alpha = 0.2f))
                                                .clickable { onOpenSet(set) }
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                "▶ ${session.currentIndex}/${set.vokabeln.size}",
                                                color = AccentViolet,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Spacer(Modifier.width(6.dp))
                                    }
                                }

                            }
                        }
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
        if (savedSets.size >= 2) {
            FloatingActionButton(
                onClick = onMergeClick,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
                containerColor = AccentViolet,
                contentColor = TextPrimary
            ) {
                Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = stringResource(R.string.sets_mischen)
                )
            }
        }
    }
}

@Composable
fun UploadScreen(
    bitmap: Bitmap?,
    isExtracting: Boolean,
    errorMessage: String?,
    onPickImage: () -> Unit,
    onBack: () -> Unit,
    onExtract: () -> Unit,
    plannedEngine: ExtractionEngine,
    fallbackEngine: ExtractionEngine,
    usedEngine: ExtractionEngine?,
    paddingValues: PaddingValues
) {
    BackHandler {
        onBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.zuruck),
                    tint = TextPrimary
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(BgSurface)
                        .clickable { onPickImage() },
                    contentAlignment = Alignment.Center
                ) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("📷", fontSize = 48.sp)
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.bild_auswahlen), color = TextSecondary, fontSize = 15.sp)
                            Text(stringResource(R.string.format_latein_deutsch), color = TextTertiary, fontSize = 12.sp)
                        }
                    }
                }
            }

            item {
                ExtractionEngineBadge(
                    plannedEngine = plannedEngine,
                    fallbackEngine = fallbackEngine,
                    usedEngine = usedEngine
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(BgSurface)
                            .clickable { onPickImage() }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (bitmap != null) stringResource(R.string.anderes_bild) else stringResource(R.string.bild_wahlen),
                            color = TextSecondary, fontSize = 14.sp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (bitmap != null && !isExtracting) SolidColor(
                                    MaterialTheme.colorScheme.primary
                                ) else Brush.horizontalGradient(listOf(BgCard, BgCard))
                            )
                            .clickable(enabled = bitmap != null && !isExtracting) { onExtract() }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isExtracting) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = TextPrimary
                                )
                                Text(stringResource(R.string.erkenne_platzhalter), color = TextPrimary, fontSize = 14.sp)
                            }
                        } else {
                            Text(
                                stringResource(R.string.text_erkennen),
                                color = if (bitmap != null) TextPrimary else TextTertiary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            errorMessage?.let {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFB71C1C).copy(alpha = 0.15f))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⚠️", fontSize = 16.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(it, color = Color(0xFFEF9A9A), fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExtractionEngineBadge(
    plannedEngine: ExtractionEngine,
    fallbackEngine: ExtractionEngine,
    usedEngine: ExtractionEngine?
) {
    val engine = usedEngine ?: plannedEngine
    val localFirst = usedEngine == null && engine == ExtractionEngine.LAPTOP
    val label = if (localFirst) {
        stringResource(R.string.engine_laptop_fallback, engineName(fallbackEngine))
    } else {
        engineName(engine)
    }
    val subtitle = when {
        usedEngine != null -> stringResource(R.string.engine_genutzt)
        localFirst -> stringResource(R.string.engine_plan_lokal)
        else -> stringResource(R.string.engine_plan_cloud)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BgSurface)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(engineEmoji(engine), fontSize = 16.sp)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.erkennung_ueber, label),
                color = TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Text(subtitle, color = TextTertiary, fontSize = 11.sp)
        }
    }
}

@Composable
private fun engineName(engine: ExtractionEngine): String = when (engine) {
    ExtractionEngine.LAPTOP -> stringResource(R.string.engine_laptop)
    ExtractionEngine.NVIDIA -> stringResource(R.string.engine_nvidia)
    ExtractionEngine.GEMINI -> stringResource(R.string.engine_gemini)
}

private fun engineEmoji(engine: ExtractionEngine): String = when (engine) {
    ExtractionEngine.LAPTOP -> "🖥️"
    ExtractionEngine.NVIDIA -> "🟣"
    ExtractionEngine.GEMINI -> "🔹"
}

@Composable
fun ReviewScreen(
    vokabeln: List<Vokabel>,
    setName: String?,
    isExtracting: Boolean = false,
    fromScan: Boolean = false,
    onVokabelnChanged: (List<Vokabel>) -> Unit,
    onStartLearning: (() -> Unit)? = null,
    onSave: () -> Unit,
    onBack: () -> Unit,
    checkExist: Boolean = true,
    onCancelExtraction: (() -> Unit)? = null,
    paddingValues: PaddingValues
) {
    var currentVokabeln by remember { mutableStateOf(vokabeln) }
    var initVocabs by remember(vokabeln) {
        mutableStateOf(vokabeln)
    }

    LaunchedEffect(vokabeln) {
        val newItems = vokabeln.filter { new -> currentVokabeln.none { it.id == new.id } }
        if (newItems.isNotEmpty()) {
            currentVokabeln = currentVokabeln + newItems
        }
    }

    LaunchedEffect(isExtracting) {
        if (!isExtracting) {
            onVokabelnChanged(currentVokabeln)
            initVocabs = currentVokabeln.toList()
        }
    }

    fun calculateChanges(original: List<Vokabel>, current: List<Vokabel>): Int {
        var changeCount = 0
        if (isExtracting || fromScan) return 0

        current.forEach { currVokabel ->
            val origVokabel = original.firstOrNull { it.id == currVokabel.id }
            if (origVokabel != null) {
                if (origVokabel.latein != currVokabel.latein || origVokabel.deutsch != currVokabel.deutsch) {
                    changeCount++
                }
            } else {
                changeCount++
            }
        }

        return changeCount
    }

    val changes =
        if (isExtracting || fromScan) 0
        else calculateChanges(initVocabs, currentVokabeln)

    var showCancelDialog by remember { mutableStateOf(false) }
    var vokabelToDelete by remember { mutableStateOf<Vokabel?>(null) }
    var editingId by remember { mutableStateOf<Int?>(null) }

    if (vokabelToDelete != null) {
        AlertDialogTabslify(
            title = stringResource(R.string.vokabel_loeschen),
            text = stringResource(R.string.wird_geloscht, vokabelToDelete!!.latein),
            onConfirm = {
                val rest = currentVokabeln.filter { it.id != vokabelToDelete!!.id }
                currentVokabeln = rest
                vokabelToDelete = null
                onVokabelnChanged(rest)
            },
            onDismiss = { vokabelToDelete = null }
        )
    }

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            containerColor = BgSurface,
            title = {
                Text(
                    stringResource(R.string.erkennung_abbrechen),
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    stringResource(R.string.die_ki_erkennt_noch_vokabeln),
                    color = TextSecondary
                )
            },
            confirmButton = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFB71C1C))
                        .clickable { showCancelDialog = false; onCancelExtraction?.invoke() }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) { Text(stringResource(R.string.abbrechen), color = TextPrimary, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(BgCard)
                        .clickable { showCancelDialog = false }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) { Text(stringResource(R.string.weiter), color = TextSecondary) }
            }
        )
    }

    BackHandler {
        if (isExtracting) showCancelDialog = true else onBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp, end = 16.dp)
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = TextPrimary
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    setName ?: stringResource(R.string.neue_vokabeln),
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(pluralStringResource(R.plurals.vokabeln_2, currentVokabeln.size, currentVokabeln.size), color = TextTertiary, fontSize = 12.sp)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (currentVokabeln.isNotEmpty() && !isExtracting) SolidColor(MaterialTheme.colorScheme.primary)
                        else Brush.horizontalGradient(listOf(BgCard, BgCard))
                    )
                    .clickable(enabled = currentVokabeln.isNotEmpty() && !isExtracting) {
                        if (changes > 0) onVokabelnChanged(currentVokabeln)
                        else {
                            onVokabelnChanged(currentVokabeln); onSave()
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    if (changes > 0) stringResource(R.string.bestatigen, changes) else if (checkExist && !fromScan) stringResource(R.string.umbenennen) else stringResource(R.string.speichern_2),
                    color = if (currentVokabeln.isNotEmpty()) TextPrimary else TextTertiary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (onStartLearning != null) {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (currentVokabeln.isNotEmpty() && !isExtracting) SolidColor(
                                MaterialTheme.colorScheme.primary
                            )
                            else Brush.horizontalGradient(listOf(BgCard, BgCard))
                        )
                        .clickable(enabled = currentVokabeln.isNotEmpty() && !isExtracting) { onStartLearning() }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        stringResource(R.string.lernen),
                        color = if (currentVokabeln.isNotEmpty()) TextPrimary else TextTertiary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        if (isExtracting) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AccentViolet.copy(alpha = 0.15f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = AccentViolet
                )
                Text(
                    stringResource(R.string.ki_erkennt_vokabeln_bisher, vokabeln.size),
                    color = AccentViolet,
                    fontSize = 13.sp
                )
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
                    items(currentVokabeln, key = { it.id }) { vokabel ->
                val editMode = editingId == vokabel.id
                var editLatein by remember(vokabel.latein) { mutableStateOf(vokabel.latein) }
                var editDeutsch by remember(vokabel.deutsch) { mutableStateOf(vokabel.deutsch) }

                BackHandler(editMode) {
                    editingId = null
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(BgSurface)
                        .clickable(enabled = !isExtracting) {
                            editingId = if (editingId == vokabel.id) null else vokabel.id
                        }
                ) {
                    if (editMode) {
                        Column(
                            Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                editLatein, { editLatein = it },
                                label = { Text(stringResource(R.string.latein)) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            OutlinedTextField(
                                editDeutsch, { editDeutsch = it },
                                label = { Text(stringResource(R.string.deutsch)) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFFB71C1C).copy(alpha = 0.15f))
                                        .clickable(enabled = !isExtracting) { vokabelToDelete = vokabel }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) { Text(stringResource(R.string.loschen), color = Color(0xFFEF9A9A), fontSize = 13.sp) }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.primary)
                                        .clickable {
                                            val idx =
                                                currentVokabeln.indexOfFirst { it.id == vokabel.id }
                                            if (idx >= 0) {
                                                currentVokabeln =
                                                    currentVokabeln.toMutableList().also {
                                                        it[idx] = Vokabel(
                                                            editLatein.trim(),
                                                            editDeutsch.trim(),
                                                            vokabel.id
                                                        )
                                                    }
                                            }
                                            editingId = null
                                        }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        stringResource(R.string.speichern),
                                        color = TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                vokabel.latein,
                                modifier = Modifier.weight(1f),
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(18.dp)
                                    .background(BgCard)
                            )
                            Text(
                                vokabel.deutsch,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.End,
                                color = TextSecondary,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@SuppressLint("UnusedContentLambdaTargetStateParameter")
@Composable
fun LearnScreen(
    karten: List<LernKarte>,
    prefs: SharedPreferences,
    sessionKey: Long,
    onBack: () -> Unit,
    setName: String?,
    onVokabelnUpdated: ((List<Vokabel>) -> Unit)? = null,
    onRenameRequest: () -> Unit = {},
    paddingValues: PaddingValues
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var setToReview by remember { mutableStateOf(false) }
    var pendingShuffle by remember { mutableStateOf<Boolean?>(null) }
    var shuffleOrder by remember { mutableStateOf(prefs.getBoolean(SHUFFLE_KEY, true)) }
    var basis by remember { mutableStateOf(karten) }
    val savedSession = remember { if (sessionKey > 0L) loadSessionState(prefs, sessionKey) else null }

    var queue by remember {
        mutableStateOf(
            savedSession?.cards
                ?.mapNotNull { verweis ->
                    karten.firstOrNull {
                        it.setId == verweis.setId && it.vokabel.id == verweis.id
                    }?.copy(richtung = verweis.richtung)
                }
                ?.takeIf { it.size >= 3 }
                ?: ordneKarten(karten, shuffleOrder)
        )
    }
    var index by remember { mutableIntStateOf(savedSession?.currentIndex ?: 0) }
    var richtung by remember { mutableStateOf(savedSession?.richtung ?: false) }
    var modus by remember { mutableStateOf(ladeLernModus(prefs)) }
    var ergebnisse by remember { mutableStateOf(emptyList<LernErgebnis>()) }
    var falscheKarten by remember { mutableStateOf(savedSession?.falscheKarten ?: emptyList()) }
    var flushMarke by remember { mutableIntStateOf(0) }
    var startZeit by remember { mutableStateOf(System.currentTimeMillis()) }
    var endeZeit by remember { mutableStateOf(0L) }
    var aufgedeckt by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<AntwortFeedback?>(null) }
    var tipEingabe by remember { mutableStateOf("") }
    var hinweise by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var hinweisLaden by remember { mutableStateOf(false) }
    val wiederholungen = remember { mutableMapOf<String, Int>() }

    val karte = queue.getOrNull(index)
    val erledigt = index >= queue.size
    val richtigAnzahl = ergebnisse.count { it.richtig }
    val falschAnzahl = ergebnisse.size - richtigAnzahl

    val flipped by animateFloatAsState(
        targetValue = if (modus == LernModus.AUFECKEN && aufgedeckt) 180f else 0f,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "flip"
    )

    val optionen = remember(karte, queue, karten) {
        val aktuell = karte ?: return@remember emptyList()
        val korrekt = aktuell.antwort
        val pool = queue.map { it.vokabel } + karten.map { it.vokabel }
        val distraktoren = pool
            .map { if (aktuell.richtung) it.latein else it.deutsch }
            .filter { it.isNotBlank() && it != korrekt }
            .distinct()
            .shuffled()
            .take(3)
        (distraktoren + korrekt).shuffled()
    }

    fun flushNeu() {
        if (ergebnisse.size > flushMarke) {
            uebernehmeErgebnisse(prefs, ergebnisse.drop(flushMarke))
            flushMarke = ergebnisse.size
        }
    }

    fun speichereSitzung() {
        if (sessionKey <= 0L) return
        saveSessionState(
            prefs, sessionKey,
            SessionState(
                cards = queue.map { KartenVerweis(it.setId, it.vokabel.id, it.richtung) },
                currentIndex = index,
                richtig = richtigAnzahl,
                falsch = falschAnzahl,
                falscheKarten = falscheKarten,
                richtung = richtung,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    fun beantworte(aktuelle: LernKarte, richtig: Boolean) {
        ergebnisse = ergebnisse + LernErgebnis(
            aktuelle.setId, aktuelle.vokabel, richtig, aktuelle.richtung
        )
        val schonGelistet = falscheKarten.any {
            it.setId == aktuelle.setId && it.vokabel.id == aktuelle.vokabel.id
        }
        falscheKarten = when {
            richtig -> falscheKarten.filterNot {
                it.setId == aktuelle.setId && it.vokabel.id == aktuelle.vokabel.id
            }
            schonGelistet -> falscheKarten
            else -> falscheKarten + aktuelle
        }
        if (!richtig) {
            val versuch = (wiederholungen[aktuelle.verweis] ?: 0) + 1
            wiederholungen[aktuelle.verweis] = versuch
            val offen = queue.size - index - 1
            if (versuch <= 2 && offen >= 3) {
                queue = queue.toMutableList().also {
                    it.add(index + 4, aktuelle.copy(richtung = !aktuelle.richtung))
                }
            }
        }
        index++
        aufgedeckt = false
        tipEingabe = ""
    }

    fun waehleOption(optionIndex: Int) {
        val aktuell = karte ?: return
        val gewaehlt = optionen.getOrNull(optionIndex) ?: return
        feedback = AntwortFeedback(aktuell, gewaehlt, gewaehlt == aktuell.antwort)
        beantworte(aktuell, gewaehlt == aktuell.antwort)
    }

    fun pruefeEingabe() {
        val aktuell = karte ?: return
        if (tipEingabe.isBlank()) return
        val korrekt = vergleicheAntwort(tipEingabe, aktuell.antwort)
        feedback = AntwortFeedback(aktuell, tipEingabe.trim(), korrekt)
        beantworte(aktuell, korrekt)
    }

    fun weiter() {
        feedback = null
        aufgedeckt = false
        tipEingabe = ""
    }

    fun setzeRichtung(neu: Boolean) {
        richtung = neu
        queue = queue.mapIndexed { i, c -> if (i >= index) c.copy(richtung = neu) else c }
        aufgedeckt = false
        feedback = null
        tipEingabe = ""
    }

    fun setzeModus(neu: LernModus) {
        modus = neu
        speichereLernModus(prefs, neu)
        tipEingabe = ""
    }

    fun holeHinweis(aktuelle: LernKarte) {
        val verweis = aktuelle.verweis
        ladeHinweis(prefs, verweis)?.let {
            hinweise = hinweise + (verweis to it)
            return
        }
        if (hinweisLaden) return
        hinweisLaden = true
        scope.launch {
            val text = try {
                sendAiRequest(
                    context = context,
                    userMessage = "Vokabel: ${aktuelle.vokabel.latein} bedeutet " +
                        "${aktuelle.vokabel.deutsch}. Antworte auf Deutsch in höchstens 30 Wörtern: " +
                        "ein kurzer Beispielsatz mit der Vokabel und eine grammatische Kurzangabe " +
                        "zu Genus und Form, falls erkennbar. Keine Anrede, nur die Angaben.",
                    target = AiTarget.VocabHint,
                    serviceKey = "vocab"
                )?.trim().orEmpty()
            } catch (_: Exception) {
                ""
            }
            hinweisLaden = false
            if (text.isNotEmpty()) {
                hinweise = hinweise + (verweis to text)
                speichereHinweis(prefs, verweis, text)
            }
        }
    }

    val handleBack = {
        flushNeu()
        if (erledigt) clearSessionState(prefs, sessionKey) else speichereSitzung()
        onBack()
    }

    val applyShuffleOrder = { value: Boolean ->
        shuffleOrder = value
        pendingShuffle = null
        prefs.edit { putBoolean(SHUFFLE_KEY, value) }
        clearSessionState(prefs, sessionKey)
        queue = ordneKarten(basis, value)
        index = 0
        ergebnisse = emptyList()
        falscheKarten = emptyList()
        flushMarke = 0
        aufgedeckt = false
        feedback = null
        startZeit = System.currentTimeMillis()
    }

    val toggleShuffleOrder = {
        val value = !shuffleOrder
        if (!erledigt && index > 0) pendingShuffle = value else applyShuffleOrder(value)
    }

    LaunchedEffect(feedback) {
        val stand = feedback ?: return@LaunchedEffect
        if (stand.korrekt) {
            delay(1100)
            feedback = null
        }
    }

    BackHandler {
        handleBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .alpha(if (setToReview) 0f else 1f)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp, end = 16.dp)
        ) {
            IconButton(onClick = handleBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextPrimary)
            }
            Text(
                setName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.alle_faellig),
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(BgSurface)
                    .clickable { setzeRichtung(!richtung) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    if (richtung) stringResource(R.string.de_la) else stringResource(R.string.la_de),
                    color = AccentViolet,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(BgSurface)
                    .clickable {
                        setzeModus(
                            when (modus) {
                                LernModus.AUSWAHL -> LernModus.TIPPEN
                                LernModus.TIPPEN -> LernModus.AUFECKEN
                                LernModus.AUFECKEN -> LernModus.AUSWAHL
                            }
                        )
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    when (modus) {
                        LernModus.AUSWAHL -> stringResource(R.string.modus_auswahl)
                        LernModus.TIPPEN -> stringResource(R.string.modus_tippen)
                        LernModus.AUFECKEN -> stringResource(R.string.modus_aufdecken)
                    },
                    color = AccentViolet,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (shuffleOrder) AccentViolet.copy(alpha = 0.15f) else BgSurface)
                    .clickable { toggleShuffleOrder() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(
                    Icons.Default.Shuffle,
                    stringResource(R.string.zufaellige_reihenfolge),
                    tint = if (shuffleOrder) AccentViolet else TextTertiary
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(BgSurface)
                    .clickable { setToReview = true }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(
                    Icons.Default.Edit,
                    stringResource(R.string.navigate_to_review_screen),
                    tint = AccentViolet
                )
            }
        }

        if (karte != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Transparent)
            ) {
                if (index > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(index.toFloat() / queue.size)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.primary)
                            .clip(RoundedCornerShape(2.dp))
                    )
                }
            }
            Text(
                "${if (feedback != null) index else index + 1}/${queue.size}  ✓ $richtigAnzahl  ✗ $falschAnzahl",
                color = TextTertiary, fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 4.dp)
            )

            Spacer(Modifier.height(16.dp))

            val stand = feedback

            if (modus == LernModus.AUFECKEN && stand == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 20.dp, vertical = 40.dp)
                        .graphicsLayer { rotationY = flipped; cameraDistance = 12f * density },
                    contentAlignment = Alignment.Center
                ) {
                    val vorderseite = karte.frage
                    val rueckseite = karte.antwort
                    if (flipped <= 90f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .fillMaxHeight(0.6f)
                                .clip(RoundedCornerShape(24.dp))
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable { aufgedeckt = true }
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    vorderseite,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    color = TextPrimary,
                                    modifier = Modifier.padding(horizontal = 24.dp)
                                )
                                if (index == 0) {
                                    Spacer(Modifier.height(20.dp))
                                    Text(
                                        stringResource(R.string.tippe_zum_aufdecken),
                                        color = TextPrimary.copy(0.4f),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .fillMaxHeight(0.6f)
                                .clip(RoundedCornerShape(24.dp))
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable { aufgedeckt = false }
                                .graphicsLayer { rotationY = 180f },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    rueckseite,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    color = TextPrimary,
                                    modifier = Modifier.padding(horizontal = 24.dp)
                                )
                                HinweisBereich(
                                    text = hinweise[karte.verweis],
                                    laedt = hinweisLaden,
                                    onLaden = { holeHinweis(karte) }
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                AnimatedContent(
                    targetState = aufgedeckt,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "btns",
                    modifier = Modifier.padding(bottom = 50.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFFB71C1C).copy(alpha = 0.2f))
                                .clickable { beantworte(karte, false) }
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    "✗",
                                    color = Color(0xFFEF5350),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    stringResource(R.string.falsch),
                                    color = Color(0xFFEF5350),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable { beantworte(karte, true) }
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    "✓",
                                    color = TextPrimary,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    stringResource(R.string.richtig),
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 20.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(vertical = 26.dp, horizontal = 18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stand?.karte?.frage ?: karte.frage,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = TextPrimary
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    if (modus == LernModus.TIPPEN && stand == null) {
                        OutlinedTextField(
                            value = tipEingabe,
                            onValueChange = { tipEingabe = it },
                            label = { Text(stringResource(R.string.tipp_eingabe)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                autoCorrectEnabled = false,
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = { pruefeEingabe() }),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable(enabled = tipEingabe.isNotBlank()) { pruefeEingabe() }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                stringResource(R.string.pruefen),
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    } else if (stand == null) {
                        optionen.forEachIndexed { optionIndex, optionText ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(BgSurface)
                                    .clickable { waehleOption(optionIndex) }
                                    .padding(horizontal = 16.dp, vertical = 14.dp)
                            ) {
                                Text(
                                    optionText,
                                    color = TextPrimary,
                                    fontSize = 15.sp
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    if (stand != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (stand.korrekt) Color(0xFF2E7D32).copy(alpha = 0.45f)
                                    else Color(0xFFB71C1C).copy(alpha = 0.35f)
                                )
                                .padding(14.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    if (stand.korrekt) stringResource(R.string.richtig)
                                    else stringResource(R.string.richtig_waere, stand.karte.antwort),
                                    color = TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (!stand.korrekt) {
                                    HinweisBereich(
                                        text = hinweise[stand.karte.verweis],
                                        laedt = hinweisLaden,
                                        onLaden = { holeHinweis(stand.karte) }
                                    )
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.primary)
                                            .clickable { weiter() }
                                            .padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            stringResource(R.string.weiter),
                                            color = TextPrimary,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        } else {
            LaunchedEffect(erledigt) {
                if (erledigt) {
                    endeZeit = System.currentTimeMillis()
                    flushNeu()
                }
            }
            val versucheNachLatein = ergebnisse.count { !it.richtung }
            val versucheNachDeutsch = ergebnisse.count { it.richtung }
            val trefferNachLatein = ergebnisse.count { !it.richtung && it.richtig }
            val trefferNachDeutsch = ergebnisse.count { it.richtung && it.richtig }
            val quoteNachLatein = if (versucheNachLatein == 0) 0
            else trefferNachLatein * 100 / versucheNachLatein
            val quoteNachDeutsch = if (versucheNachDeutsch == 0) 0
            else trefferNachDeutsch * 100 / versucheNachDeutsch
            val dauerMinuten =
                maxOf(1, ((endeZeit - startZeit).coerceAtLeast(0L) / 60000L).toInt())
            val sitztListe = remember(erledigt) {
                ergebnisse.map { it.setId }.distinct().filter { it > 0L }.flatMap { setId ->
                    val fortschritt = loadSetProgress(prefs, setId)
                    queue.map { it.vokabel }.distinctBy { it.latein }.filter {
                        (fortschritt.vokabelProgress[it.id]?.streak ?: 0) >= SITZT_STREAK
                    }.map { it.latein }
                }
            }
            val nochUebung = falscheKarten.map { it.vokabel.latein }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(BgSurface)
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("🎉", fontSize = 44.sp)
                    Text(
                        stringResource(R.string.fertig_ausruf),
                        color = TextPrimary,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${pluralStringResource(R.plurals.vokabeln_abgefragt, queue.size, queue.size)}  ·  " +
                            stringResource(R.string.lernzeit, dauerMinuten),
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    if (versucheNachLatein > 0 || versucheNachDeutsch > 0) {
                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            if (versucheNachLatein > 0) {
                                Text(
                                    "${stringResource(R.string.la_de)} $quoteNachLatein%",
                                    color = TextTertiary,
                                    fontSize = 12.sp
                                )
                            }
                            if (versucheNachDeutsch > 0) {
                                Text(
                                    "${stringResource(R.string.de_la)} $quoteNachDeutsch%",
                                    color = TextTertiary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "$richtigAnzahl",
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentViolet
                            )
                            Text(stringResource(R.string.richtig), color = TextTertiary, fontSize = 12.sp)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "$falschAnzahl",
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFEF5350)
                            )
                            Text(stringResource(R.string.falsch), color = TextTertiary, fontSize = 12.sp)
                        }
                    }
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            stringResource(R.string.das_kannst_du),
                            color = AccentViolet,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (sitztListe.isEmpty()) stringResource(R.string.noch_nichts_sitzt)
                            else sitztListe.take(8).joinToString(", ") +
                                    if (sitztListe.size > 8) " +${sitztListe.size - 8}" else "",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                    if (nochUebung.isNotEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                stringResource(R.string.braucht_nochmal),
                                color = Color(0xFFEF5350),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                nochUebung.take(8).joinToString(", ") +
                                        if (nochUebung.size > 8) " +${nochUebung.size - 8}" else "",
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable {
                                clearSessionState(prefs, sessionKey)
                                queue = ordneKarten(basis, shuffleOrder)
                                index = 0
                                ergebnisse = emptyList()
                                falscheKarten = emptyList()
                                flushMarke = 0
                                aufgedeckt = false
                                feedback = null
                                startZeit = System.currentTimeMillis()
                                endeZeit = 0L
                            }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stringResource(R.string.nochmal),
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (falscheKarten.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFFB71C1C).copy(alpha = 0.25f))
                                .clickable {
                                    queue = ordneKarten(falscheKarten, shuffleOrder)
                                    index = 0
                                    ergebnisse = emptyList()
                                    falscheKarten = emptyList()
                                    flushMarke = 0
                                    aufgedeckt = false
                                    feedback = null
                                    startZeit = System.currentTimeMillis()
                                    endeZeit = 0L
                                }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                stringResource(R.string.falsche_wiederholen, falscheKarten.size),
                                color = Color(0xFFEF5350),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(BgCard)
                            .clickable { handleBack() }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stringResource(R.string.zuruck_zur_ubersicht),
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }

    if (setToReview) {
        Box(Modifier.fillMaxSize()) {
            ReviewScreen(
                vokabeln = basis.map { it.vokabel },
                onBack = { setToReview = false },
                onVokabelnChanged = { neu ->
                    val alteSetIds = basis.map { it.setId }
                    val alteRichtungen = basis.map { it.richtung }
                    basis = neu.mapIndexed { i, v ->
                        LernKarte(
                            vokabel = v,
                            setId = alteSetIds.getOrNull(i) ?: 0L,
                            richtung = alteRichtungen.getOrNull(i) ?: richtung
                        )
                    }
                    queue = queue.mapNotNull { c ->
                        neu.firstOrNull { it.latein == c.vokabel.latein }?.let { c.copy(vokabel = it) }
                    }
                    index = index.coerceAtMost(queue.size)
                    onVokabelnUpdated?.invoke(neu)
                },
                onSave = { onRenameRequest() },
                setName = setName,
                checkExist = true,
                paddingValues = paddingValues
            )
        }
    }

    pendingShuffle?.let { value ->
        AlertDialogTabslify(
            title = stringResource(R.string.reihenfolge_wechseln),
            text = stringResource(R.string.reihenfolge_wechseln_text),
            confirmText = stringResource(R.string.neu_starten),
            onConfirm = { applyShuffleOrder(value) },
            onDismiss = { pendingShuffle = null }
        )
    }
}

@Composable
private fun HinweisBereich(
    text: String?,
    laedt: Boolean,
    onLaden: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(BgCard)
                .clickable(enabled = !laedt) { onLaden() }
                .padding(horizontal = 12.dp, vertical = 7.dp)
        ) {
            Text(
                if (laedt) stringResource(R.string.ki_denkt)
                else if (text == null) stringResource(R.string.hinweis_holen)
                else stringResource(R.string.hinweis_erneut),
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
        if (text != null) {
            Text(text, color = TextSecondary, fontSize = 13.sp)
        }
    }
}

@Composable
fun SaveSetDialog(initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgSurface,
        title = { Text(stringResource(R.string.set_speichern), color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.name), color = TextTertiary) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (name.isNotBlank())
                            SolidColor(MaterialTheme.colorScheme.primary)
                        else
                            Brush.horizontalGradient(listOf(BgCard, BgCard))
                    )
                    .clickable(enabled = name.isNotBlank()) { onConfirm(name) }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    stringResource(R.string.speichern),
                    color = if (name.isNotBlank()) TextPrimary else TextTertiary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(BgCard)
                    .clickable { onDismiss() }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) { Text(stringResource(R.string.abbrechen), color = TextSecondary) }
        }
    )
}

@Composable
fun MergeVocabSetsDialog(
    prefs: SharedPreferences,
    allSets: List<VokabelSet>,
    onDismiss: () -> Unit,
    onMergeComplete: (VokabelSet) -> Unit
) {
    var selectedSets by remember { mutableStateOf(setOf<Long>()) }
    val defaultMergeName = stringResource(R.string.gemischtes_set, System.currentTimeMillis() % 1000)
    var mergeName by remember {
        mutableStateOf(defaultMergeName)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BgSurface,
        title = {
            Text(
                stringResource(R.string.vokabel_sets_mischen),
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
            ) {
                OutlinedTextField(
                    value = mergeName,
                    onValueChange = { mergeName = it },
                    label = { Text(stringResource(R.string.name), color = TextTertiary) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                )

                Text(
                    stringResource(R.string.sets_auswahlen_min_2),
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(allSets, key = { it.createdAt }) { set ->
                        val isSelected = selectedSets.contains(set.createdAt)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isSelected) AccentViolet.copy(alpha = 0.2f)
                                    else BgCard
                                )
                                .clickable {
                                    selectedSets = if (isSelected) {
                                        selectedSets - set.createdAt
                                    } else {
                                        selectedSets + set.createdAt
                                    }
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (isSelected) AccentViolet else BgSurface
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Text("✓", color = TextPrimary, fontSize = 12.sp)
                                }
                            }

                            Spacer(Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    set.name,
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    pluralStringResource(R.plurals.vokabeln_2, set.vokabeln.size, set.vokabeln.size),
                                    color = TextTertiary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (selectedSets.size >= 2 && mergeName.isNotBlank())
                            SolidColor(AccentViolet)
                        else
                            Brush.horizontalGradient(listOf(BgCard, BgCard))
                    )
                    .clickable(enabled = selectedSets.size >= 2 && mergeName.isNotBlank()) {
                        val mergedSet = createMergedVocabSet(
                            prefs = prefs,
                            name = mergeName.trim(),
                            selectedCreatedAts = selectedSets.toList(),
                            allSets = allSets
                        )
                        onMergeComplete(mergedSet)
                    }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    stringResource(R.string.mischen, selectedSets.size),
                    color = if (selectedSets.size >= 2 && mergeName.isNotBlank())
                        TextPrimary else TextTertiary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(BgCard)
                    .clickable { onDismiss() }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(stringResource(R.string.abbrechen), color = TextSecondary)
            }
        }
    )
}

fun createMergedVocabSet(
    prefs: SharedPreferences,
    name: String,
    selectedCreatedAts: List<Long>,
    allSets: List<VokabelSet>
): VokabelSet {
    val mergedVokabeln = mutableListOf<Vokabel>()
    var nextId = 0

    selectedCreatedAts.forEach { createdAt ->
        val set = allSets.firstOrNull { it.createdAt == createdAt }
        set?.vokabeln?.forEach { vokabel ->
            mergedVokabeln.add(
                Vokabel(
                    latein = vokabel.latein,
                    deutsch = vokabel.deutsch,
                    id = nextId++
                )
            )
        }
    }

    mergedVokabeln.shuffle()

    val mergedSet = VokabelSet(
        name = name,
        vokabeln = mergedVokabeln,
        createdAt = System.currentTimeMillis()
    )

    prefs.edit {
        putString(
            "merged_sources_${mergedSet.createdAt}",
            selectedCreatedAts.joinToString(",")
        )
    }

    return mergedSet
}

private const val SETS_KEY = "vocab_sets"
private const val SHUFFLE_KEY = "shuffle_order"

private fun ordneKarten(source: List<LernKarte>, shuffle: Boolean): List<LernKarte> =
    if (shuffle) source.shuffled() else source

fun saveVokabelSet(prefs: SharedPreferences, set: VokabelSet): List<VokabelSet> {
    val existing = loadVokabelSets(prefs).toMutableList()
    val nachId = existing.indexOfFirst { it.createdAt == set.createdAt }
    val idx = if (nachId >= 0) nachId else existing.indexOfFirst { it.name == set.name }
    if (idx >= 0) existing[idx] = set else existing.add(0, set)
    existing.sortByDescending { it.lastUsed }
    val json = JSONArray().also { arr ->
        existing.forEach { s ->
            arr.put(JSONObject().apply {
                put("name", s.name)
                put("createdAt", s.createdAt)
                put("lastUsed", s.lastUsed)
                put("vokabeln", JSONArray().also { va ->
                    s.vokabeln.forEach { v ->
                        va.put(
                            JSONObject()
                                .apply {
                                    put("latein", v.latein); put(
                                    "deutsch",
                                    v.deutsch
                                ); put("id", v.id)
                                })
                    }
                })
            })
        }
    }.toString()
    prefs.edit { putString(SETS_KEY, json) }
    return existing
}

fun loadVokabelSets(prefs: SharedPreferences): List<VokabelSet> {
    val raw = prefs.getString(SETS_KEY, null) ?: return emptyList()
    return try {
        val arr = JSONArray(raw)
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            val va = o.getJSONArray("vokabeln")
            VokabelSet(
                name = o.getString("name"),
                createdAt = o.getLong("createdAt"),
                lastUsed = if (o.has("lastUsed")) o.getLong("lastUsed") else o.getLong("createdAt"),
                vokabeln = (0 until va.length()).map { i ->
                    val v = va.getJSONObject(i)
                    Vokabel(v.getString("latein"), v.getString("deutsch"), v.getInt("id"))
                }
            )
        }
    } catch (_: Exception) {
        emptyList()
    }
}

fun deleteVokabelSet(prefs: SharedPreferences, set: VokabelSet): List<VokabelSet> {
    val updated = loadVokabelSets(prefs).filter { it.createdAt != set.createdAt }
    val hinweisPraefix = "hinweis_${set.createdAt}:"
    val hinweisKeys = prefs.all.keys.filter { it.startsWith(hinweisPraefix) }
    val json = JSONArray().also { arr ->
        updated.forEach { s ->
            arr.put(JSONObject().apply {
                put("name", s.name)
                put("createdAt", s.createdAt)
                put("lastUsed", s.lastUsed)
                put("vokabeln", JSONArray().also { va ->
                    s.vokabeln.forEach { v ->
                        va.put(
                            JSONObject()
                                .apply {
                                    put("latein", v.latein); put(
                                    "deutsch",
                                    v.deutsch
                                ); put("id", v.id)
                                })
                    }
                })
            })
        }
    }.toString()
    prefs.edit {
        putString(SETS_KEY, json)
        remove("progress_${set.createdAt}")
        remove("weak_${set.createdAt}")
        hinweisKeys.forEach { remove(it) }
    }
    return updated
}

private const val MAX_UPLOAD_IMAGE_DIMENSION = 2048

fun uriToBitmap(context: Context, uri: Uri): Bitmap? {
    return try {
        ImageDecoder.decodeBitmap(
            ImageDecoder.createSource(
                context.contentResolver,
                uri
            )
        ) { decoder, info, _ ->
            val maxDimension = maxOf(info.size.width, info.size.height)
            if (maxDimension > MAX_UPLOAD_IMAGE_DIMENSION) {
                val scale = MAX_UPLOAD_IMAGE_DIMENSION.toFloat() / maxDimension
                decoder.setTargetSize(
                    (info.size.width * scale).toInt(),
                    (info.size.height * scale).toInt()
                )
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } catch (_: Exception) {
        null
    }
}

fun loadWeakVokabeln(prefs: SharedPreferences, setCreatedAt: Long): List<Vokabel> {
    val raw = prefs.getString("weak_$setCreatedAt", null) ?: return emptyList()
    return try {
        val arr = JSONArray(raw)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Vokabel(o.getString("latein"), o.getString("deutsch"), o.optInt("id", i))
        }
    } catch (_: Exception) {
        emptyList()
    }
}

fun saveWeakVokabeln(
    prefs: SharedPreferences,
    setCreatedAt: Long,
    list: List<Vokabel>
) {
    val json = JSONArray().also { arr ->
        list.forEach { v ->
            arr.put(
                JSONObject().apply {
                    put("latein", v.latein); put("deutsch", v.deutsch); put("id", v.id)
                })
        }
    }.toString()
    prefs.edit { putString("weak_$setCreatedAt", json) }
}

fun updateWeakVokabeln(
    prefs: SharedPreferences,
    setCreatedAt: Long,
    correct: List<Vokabel>,
    wrong: List<Vokabel>
) {
    val current = loadWeakVokabeln(prefs, setCreatedAt).toMutableList()
    correct.forEach { v -> current.removeAll { it.latein == v.latein } }
    wrong.forEach { v -> if (current.none { it.latein == v.latein }) current.add(v) }
    saveWeakVokabeln(prefs, setCreatedAt, current)
}

fun loadSetProgress(prefs: SharedPreferences, setCreatedAt: Long): SetProgress {
    val raw = prefs.getString("progress_$setCreatedAt", null) ?: return SetProgress(setCreatedAt)
    return try {
        val obj = JSONObject(raw)
        val progressMap = mutableMapOf<Int, VokabelProgress>()
        val vokabelProgressJson = obj.getJSONObject("vokabelProgress")
        vokabelProgressJson.keys().forEach { key ->
            val vp = vokabelProgressJson.getJSONObject(key)
            progressMap[key.toInt()] = VokabelProgress(
                vokabelId = vp.getInt("vokabelId"),
                correctCount = vp.getInt("correctCount"),
                wrongCount = vp.getInt("wrongCount"),
                lastPracticed = vp.getLong("lastPracticed"),
                streak = vp.optInt("streak", 0)
            )
        }
        SetProgress(
            setCreatedAt = obj.getLong("setCreatedAt"),
            vokabelProgress = progressMap,
            totalSessions = obj.getInt("totalSessions"),
            lastSession = obj.getLong("lastSession")
        )
    } catch (_: Exception) {
        SetProgress(setCreatedAt)
    }
}

fun saveSetProgress(prefs: SharedPreferences, progress: SetProgress) {
    val json = JSONObject().apply {
        put("setCreatedAt", progress.setCreatedAt)
        put("totalSessions", progress.totalSessions)
        put("lastSession", progress.lastSession)
        put("vokabelProgress", JSONObject().also { vpObj ->
            progress.vokabelProgress.forEach { (id, vp) ->
                vpObj.put(id.toString(), JSONObject().apply {
                    put("vokabelId", vp.vokabelId)
                    put("correctCount", vp.correctCount)
                    put("wrongCount", vp.wrongCount)
                    put("lastPracticed", vp.lastPracticed)
                    put("streak", vp.streak)
                })
            }
        })
    }.toString()
    prefs.edit { putString("progress_${progress.setCreatedAt}", json) }
}

fun updateSetProgress(
    prefs: SharedPreferences,
    setCreatedAt: Long,
    correct: List<Vokabel>,
    wrong: List<Vokabel>
) {
    val current = loadSetProgress(prefs, setCreatedAt)
    val updatedMap = current.vokabelProgress.toMutableMap()
    val now = System.currentTimeMillis()

    correct.forEach { vokabel ->
        val existing = updatedMap[vokabel.id] ?: VokabelProgress(vokabel.id)
        updatedMap[vokabel.id] = existing.copy(
            correctCount = existing.correctCount + 1,
            streak = existing.streak + 1,
            lastPracticed = now
        )
    }

    wrong.forEach { vokabel ->
        val existing = updatedMap[vokabel.id] ?: VokabelProgress(vokabel.id)
        updatedMap[vokabel.id] = existing.copy(
            wrongCount = existing.wrongCount + 1,
            streak = 0,
            lastPracticed = now
        )
    }

    val updatedProgress = current.copy(
        vokabelProgress = updatedMap,
        totalSessions = current.totalSessions + 1,
        lastSession = now
    )

    saveSetProgress(prefs, updatedProgress)
}

fun uebernehmeErgebnisse(prefs: SharedPreferences, ergebnisse: List<LernErgebnis>) {
    if (ergebnisse.isEmpty()) return
    val letzterStand = ergebnisse.associateBy { it.setId to it.vokabel.id }
    letzterStand.values.groupBy { it.setId }.forEach { (setId, eintraege) ->
        if (setId == 0L) return@forEach
        val richtige = eintraege.filter { it.richtig }.map { it.vokabel }
        val falsche = eintraege.filter { !it.richtig }.map { it.vokabel }
        updateWeakVokabeln(prefs, setId, richtige, falsche)
        updateSetProgress(prefs, setId, richtige, falsche)
    }
}

private fun lernKarteZuJson(karte: LernKarte): JSONObject = JSONObject().apply {
    put("setId", karte.setId)
    put("id", karte.vokabel.id)
    put("richtung", karte.richtung)
    put("latein", karte.vokabel.latein)
    put("deutsch", karte.vokabel.deutsch)
}

private fun jsonZuLernKarte(o: JSONObject) = LernKarte(
    vokabel = Vokabel(o.getString("latein"), o.getString("deutsch"), o.getInt("id")),
    setId = o.optLong("setId", 0L),
    richtung = o.optBoolean("richtung", false)
)

fun saveSessionState(prefs: SharedPreferences, setCreatedAt: Long, state: SessionState) {
    if (setCreatedAt <= 0L) return
    val json = JSONObject().apply {
        put("cards", JSONArray().also { arr ->
            state.cards.forEach { arr.put(
                JSONObject().apply {
                    put("setId", it.setId); put("id", it.id); put("richtung", it.richtung)
                }
            ) }
        })
        put("currentIndex", state.currentIndex)
        put("richtig", state.richtig)
        put("falsch", state.falsch)
        put("falscheKarten", JSONArray().also { arr ->
            state.falscheKarten.forEach { arr.put(lernKarteZuJson(it)) }
        })
        put("richtung", state.richtung)
        put("timestamp", state.timestamp)
    }.toString()
    prefs.edit { putString("session_$setCreatedAt", json) }
}

fun loadSessionState(prefs: SharedPreferences, setCreatedAt: Long): SessionState? {
    if (setCreatedAt <= 0L) return null
    val raw = prefs.getString("session_$setCreatedAt", null) ?: return null
    return try {
        val obj = JSONObject(raw)
        val timestamp = if (obj.has("timestamp")) obj.getLong("timestamp") else 0L
        if (System.currentTimeMillis() - timestamp > 20 * 60 * 1000) {
            clearSessionState(prefs, setCreatedAt)
            return null
        }
        val cardsArr = obj.getJSONArray("cards")
        val cards = (0 until cardsArr.length()).map { i ->
            val o = cardsArr.getJSONObject(i)
            KartenVerweis(
                setId = o.optLong("setId", 0L),
                id = o.getInt("id"),
                richtung = o.optBoolean("richtung", false)
            )
        }
        if (cards.isEmpty()) {
            clearSessionState(prefs, setCreatedAt)
            return null
        }
        val falschArr = obj.getJSONArray("falscheKarten")
        SessionState(
            cards = cards,
            currentIndex = obj.getInt("currentIndex"),
            richtig = obj.getInt("richtig"),
            falsch = obj.getInt("falsch"),
            falscheKarten = (0 until falschArr.length()).map { i ->
                jsonZuLernKarte(falschArr.getJSONObject(i))
            },
            richtung = obj.optBoolean("richtung", false),
            timestamp = timestamp
        )
    } catch (_: Exception) {
        null
    }
}

fun clearSessionState(prefs: SharedPreferences, setCreatedAt: Long) {
    if (setCreatedAt <= 0L) return
    prefs.edit { remove("session_$setCreatedAt") }
}