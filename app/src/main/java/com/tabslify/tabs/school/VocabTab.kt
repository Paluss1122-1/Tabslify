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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutCubic
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
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.tabslify.quiethoursnotificationhelper.callNvidiaVisionApi
import com.tabslify.quiethoursnotificationhelper.flashcardVokabelnFlow
import com.tabslify.quiethoursnotificationhelper.trySendImageToLaptop
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import kotlin.time.Duration.Companion.milliseconds


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
    val lastPracticed: Long = 0L
)

data class SetProgress(
    val setCreatedAt: Long,
    val vokabelProgress: Map<Int, VokabelProgress> = emptyMap(),
    val totalSessions: Int = 0,
    val lastSession: Long = 0L
)

data class SessionState(
    val shuffledIds: List<Int>,
    val currentIndex: Int,
    val correctIds: List<Int>,
    val wrongIds: List<Int>,
    val showDeutsch: Boolean,
    val timestamp: Long
)

data class WidthState(
    val id: Int,
    val value: Int
)

enum class VokabelTabScreen { DASHBOARD, HOME, UPLOAD, REVIEW, LEARN, MATERIALIEN }

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
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var savedSets by remember { mutableStateOf(loadVokabelSets(prefs)) }
    var activeSet by remember { mutableStateOf<VokabelSet?>(null) }
    var cachedSetName by remember { mutableStateOf<String?>(null) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var saveNameInput by remember { mutableStateOf("") }
    var lastWidths by remember { mutableStateOf<List<WidthState>>(emptyList()) }
    var currentWidths by remember { mutableStateOf<List<WidthState>>(emptyList()) }
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

    fun openSetAndUpdateLastUsed(set: VokabelSet) {
        val updatedSet = set.copy(lastUsed = System.currentTimeMillis())
        savedSets = saveVokabelSet(prefs, updatedSet)
        activeSet = updatedSet
        cachedSetName = set.name
        vokabeln = updatedSet.vokabeln
        screen = VokabelTabScreen.LEARN
    }

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
                activeSet = mergedSet
                vokabeln = mergedSet.vokabeln
                screen = VokabelTabScreen.LEARN
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
                screen = VokabelTabScreen.UPLOAD
            }
        }

    if (showSaveDialog) {
        SaveSetDialog(
            initial = saveNameInput,
            onConfirm = { name ->
                scope.launch {
                    val set = VokabelSet(name.trim(), vokabeln)
                    savedSets = saveVokabelSet(prefs, set)
                    cachedSetName = name.trim()
                    showSaveDialog = false
                    saveNameInput = ""
                    comingFromScan = false
                    activeSet?.let { oldSet ->
                        val oldId = oldSet.createdAt.toInt()
                        val newId = set.createdAt.toInt()
                        val weakVokabeln = loadWeakVokabeln(prefs, oldSet.createdAt)
                        saveWeakVokabeln(prefs, set.createdAt, weakVokabeln)
                        lastWidths =
                            lastWidths.map { if (it.id == oldId) it.copy(id = newId) else it }
                        currentWidths =
                            currentWidths.map { if (it.id == oldId) it.copy(id = newId) else it }
                        deleteVokabelSet(prefs, oldSet)
                    }
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
                onVocabClick = { screen = VokabelTabScreen.HOME },
                onMaterialClick = {
                    selectedMaterialSubject = null
                    selectedMaterialFile = null
                    screen = VokabelTabScreen.MATERIALIEN
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
                onNewSet = { screen = VokabelTabScreen.UPLOAD },
                onOpenSet = { set -> openSetAndUpdateLastUsed(set) },
                onLearnWeak = { set ->
                    openSetAndUpdateLastUsed(
                        set.copy(
                            vokabeln = loadWeakVokabeln(
                                prefs,
                                set.createdAt
                            )
                        )
                    )
                },
                onLearnWithMix = { set ->
                    val otherVokabeln = savedSets
                        .filter { it.createdAt != set.createdAt }
                        .flatMap { it.vokabeln }
                        .shuffled()
                    val mixCount = (5..10).random().coerceAtMost(otherVokabeln.size)
                    val mixed = (set.vokabeln + otherVokabeln.take(mixCount)).shuffled()
                    val reindexed = mixed.mapIndexed { i, v -> v.copy(id = i) }
                    activeSet = set
                    vokabeln = reindexed
                    screen = VokabelTabScreen.LEARN
                },
                onDeleteSet = { set ->
                    saveWeakVokabeln(prefs, set.createdAt, emptyList())
                    savedSets = deleteVokabelSet(prefs, set)
                },
                onUpdate = { id, value ->
                    currentWidths = currentWidths.filter { it.id != id } + WidthState(id, value)
                },
                lastWidths = lastWidths,
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
                                    val result = flashcardVokabelnFlow.first { it != null }
                                    vokabeln = result ?: emptyList()
                                    if (vokabeln.isNotEmpty()) screen = VokabelTabScreen.REVIEW
                                    else {
                                        errorMessage = lokaleExtraktionLeer
                                    }
                                } else {
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
                paddingValues = paddingValues
            )

            VokabelTabScreen.REVIEW -> ReviewScreen(
                vokabeln = vokabeln,
                setName = activeSet?.name ?: cachedSetName,
                isExtracting = isExtracting,
                fromScan = comingFromScan,
                onVokabelnChanged = { vokabeln = it },
                onStartLearning = { screen = VokabelTabScreen.LEARN },
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
                vokabeln = vokabeln,
                prefs = prefs,
                setCreatedAt = activeSet?.createdAt ?: 0L,
                onBack = {
                    lastWidths = currentWidths
                    screen =
                        if (activeSet != null) VokabelTabScreen.HOME else VokabelTabScreen.REVIEW
                    activeSet = null
                },
                setName = activeSet?.name ?: cachedSetName,
                onVokabelnUpdated = { updatedVokabeln ->
                    vokabeln = updatedVokabeln
                    activeSet = activeSet?.copy(vokabeln = updatedVokabeln)
                    savedSets = saveVokabelSet(prefs, activeSet!!)
                },
                onRenameRequest = {
                    saveNameInput = activeSet?.name ?: " "
                    showSaveDialog = true
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
        }
    }
}

@Composable
fun SchoolDashboard(
    savedSets: List<VokabelSet>,
    onVocabClick: () -> Unit,
    onMaterialClick: () -> Unit,
    onOpenSet: (VokabelSet) -> Unit,
    paddingValues: PaddingValues,
    recentMaterials: List<RecentMaterial> = emptyList(),
    onOpenMaterial: (RecentMaterial) -> Unit = {}
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
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(BgSurface)
                .clickable { }
                .padding(horizontal = 20.dp, vertical = 18.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("🤖", fontSize = 22.sp)
                Column {
                    Text(
                        stringResource(R.string.ai_chat),
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        stringResource(R.string.unterstutzung_beim_lernen),
                        color = TextTertiary,
                        fontSize = 12.sp
                    )
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
    onUpdate: (Int, Int) -> Unit,
    lastWidths: List<WidthState>,
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
                        val progressFloat by remember(session) {
                            mutableFloatStateOf(
                                if (session != null && set.vokabeln.isNotEmpty())
                                    session.currentIndex.toFloat() / set.vokabeln.size
                                else 0f
                            )
                        }

                        val setId = set.createdAt.toInt()
                        val lastWidth = remember {
                            lastWidths.firstOrNull { it.id == setId }?.value?.toFloat() ?: 0f
                        }

                        val progressPercentFloat = remember { Animatable(lastWidth) }
                        LaunchedEffect(progressFloat) {
                            delay(200.milliseconds)
                            progressPercentFloat.animateTo(
                                progressFloat,
                                animationSpec = tween(400, easing = EaseInOutCubic)
                            )
                            if (progressPercentFloat.value != 0f) onUpdate(
                                setId,
                                progressPercentFloat.value.toInt()
                            )
                        }

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
                                    }
                                    val weakCount = loadWeakVokabeln(prefs, set.createdAt).size
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
                                        if (progressPercentFloat.value > 0f) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth(progressPercentFloat.value)
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

                                    val hasSession =
                                        remember { loadSessionState(prefs, set.createdAt) != null }
                                    if (hasSession) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(AccentViolet.copy(alpha = 0.2f))
                                                .clickable { onOpenSet(set) }
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                "▶ ${
                                                    loadSessionState(
                                                        prefs,
                                                        set.createdAt
                                                    )?.currentIndex ?: 0
                                                }/${set.vokabeln.size}",
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

        original.forEach { origVokabel ->
            if (current.none { it.id == origVokabel.id }) {
                changeCount++
            }
        }

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
                var editMode by remember { mutableStateOf(false) }
                var editLatein by remember(vokabel.latein) { mutableStateOf(vokabel.latein) }
                var editDeutsch by remember(vokabel.deutsch) { mutableStateOf(vokabel.deutsch) }

                BackHandler(editMode) {
                    editMode = false
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(BgSurface)
                        .clickable { editMode = !editMode }
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
                                        .clickable {
                                            currentVokabeln =
                                                currentVokabeln.filter { it.id != vokabel.id }
                                            editMode = false
                                        }
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
                                            editMode = false
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
    vokabeln: List<Vokabel>,
    prefs: SharedPreferences,
    setCreatedAt: Long,
    onBack: () -> Unit,
    setName: String?,
    onVokabelnUpdated: ((List<Vokabel>) -> Unit)? = null,
    onRenameRequest: () -> Unit = {},
    paddingValues: PaddingValues
) {
    var setToReview by remember { mutableStateOf(false) }
    var vokabeln by remember { mutableStateOf(vokabeln) }
    val savedSession = remember { loadSessionState(prefs, setCreatedAt) }
    val allVokabeln = vokabeln
    var shuffled by remember {
        mutableStateOf(
            savedSession?.shuffledIds?.mapNotNull { id -> allVokabeln.firstOrNull { it.id == id } }
                ?: vokabeln.shuffled()
        )
    }
    var currentIndex by remember { mutableIntStateOf(savedSession?.currentIndex ?: 0) }
    var showDeutsch by remember { mutableStateOf(savedSession?.showDeutsch ?: false) }
    var showAnswer by remember { mutableStateOf(false) }
    var correctVokabeln by remember {
        mutableStateOf(
            (savedSession?.correctIds ?: emptyList()).mapNotNull { id ->
                allVokabeln.firstOrNull { it.id == id }
            }
        )
    }
    var wrongVokabeln by remember {
        mutableStateOf(
            (savedSession?.wrongIds ?: emptyList()).mapNotNull { id ->
                allVokabeln.firstOrNull { it.id == id }
            }
        )
    }
    var correct by remember { mutableIntStateOf(savedSession?.correctIds?.size ?: 0) }
    var wrong by remember { mutableIntStateOf(savedSession?.wrongIds?.size ?: 0) }
    val flipped by animateFloatAsState(
        targetValue = if (showAnswer) 180f else 0f,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "flip"
    )
    val done = currentIndex >= shuffled.size
    LaunchedEffect(vokabeln) {
        shuffled = vokabeln.shuffled()
        showAnswer = showDeutsch
    }

    val handleBack = {
        if (done) clearSessionState(prefs, setCreatedAt)
        if (!done) {
            saveSessionState(
                prefs, setCreatedAt,
                SessionState(
                    shuffledIds = shuffled.map { it.id },
                    currentIndex = currentIndex,
                    correctIds = correctVokabeln.map { it.id },
                    wrongIds = wrongVokabeln.map { it.id },
                    showDeutsch = showDeutsch,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
        onBack()
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
                "$setName ",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(BgSurface)
                    .clickable { showDeutsch = !showDeutsch; showAnswer = false }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    if (showDeutsch) stringResource(R.string.de_la) else stringResource(R.string.la_de),
                    color = AccentViolet,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(BgSurface)
                    .clickable { setToReview = true }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(
                    Icons.Default.Edit,
                    stringResource(R.string.navigate_to_review_screen),
                    tint = AccentViolet
                )
            }
        }

        if (!done) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Transparent)
            ) {
                if (currentIndex > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(currentIndex.toFloat() / shuffled.size)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.primary)
                            .clip(RoundedCornerShape(2.dp))
                    )
                }
            }
            Text(
                "${currentIndex + 1}/${shuffled.size}  ✓ $correct  ✗ $wrong",
                color = TextTertiary, fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 4.dp)
            )

            Spacer(Modifier.height(24.dp))

            val vokabel = shuffled[currentIndex]
            val frontText = if (showDeutsch) vokabel.deutsch else vokabel.latein
            val backText = if (showDeutsch) vokabel.latein else vokabel.deutsch

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 20.dp, vertical = 80.dp)
                    .graphicsLayer { rotationY = flipped; cameraDistance = 12f * density },
                contentAlignment = Alignment.Center
            ) {
                if (flipped <= 90f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .fillMaxHeight(0.6f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable { showAnswer = !showAnswer }
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                frontText,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                color = TextPrimary,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                            if (currentIndex == 0) {
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
                            .clickable { showAnswer = !showAnswer }
                            .graphicsLayer { rotationY = 180f },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                backText,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                color = TextPrimary,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            AnimatedContent(
                targetState = showAnswer,
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
                            .clickable {
                                wrong++
                                wrongVokabeln = wrongVokabeln + shuffled[currentIndex]
                                currentIndex++
                                showAnswer = false
                            }
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
                            .clickable {
                                correct++
                                correctVokabeln = correctVokabeln + shuffled[currentIndex]
                                currentIndex++
                                showAnswer = false
                            }
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
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LaunchedEffect(true) {
                    updateWeakVokabeln(prefs, setCreatedAt, correctVokabeln, wrongVokabeln)
                    updateSetProgress(prefs, setCreatedAt, correctVokabeln, wrongVokabeln)
                }
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(BgSurface)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("🎉", fontSize = 48.sp)
                    Text(
                        stringResource(R.string.fertig_ausruf),
                        color = TextPrimary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        pluralStringResource(R.plurals.vokabeln_abgefragt, shuffled.size, shuffled.size),
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "$correct",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentViolet
                            )
                            Text(stringResource(R.string.richtig), color = TextTertiary, fontSize = 12.sp)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "$wrong",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFEF5350)
                            )
                            Text(stringResource(R.string.falsch), color = TextTertiary, fontSize = 12.sp)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable {
                                clearSessionState(prefs, setCreatedAt)
                                shuffled = vokabeln.shuffled()
                                currentIndex = 0; correct = 0; wrong = 0
                                correctVokabeln = emptyList(); wrongVokabeln = emptyList()
                                showAnswer = false
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
                    if (wrongVokabeln.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFFB71C1C).copy(alpha = 0.25f))
                                .clickable {
                                    shuffled = wrongVokabeln.shuffled()
                                    wrongVokabeln = emptyList()
                                    currentIndex = 0; correct = 0; wrong = 0; showAnswer = false
                                }
                                .padding(vertical = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                stringResource(R.string.falsche_wiederholen, wrongVokabeln.size),
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
                    ) { Text(stringResource(R.string.zuruck_zur_ubersicht), color = TextSecondary, fontSize = 14.sp) }
                }
            }
        }
    }

    if (setToReview) {
        Box(Modifier.fillMaxSize()) {
            ReviewScreen(
                vokabeln = vokabeln,
                onBack = { setToReview = false },
                onVokabelnChanged = { new ->
                    vokabeln = new
                    shuffled = new.shuffled()
                    onVokabelnUpdated?.invoke(new)
                },
                onSave = { onRenameRequest() },
                setName = setName,
                checkExist = true,
                paddingValues = paddingValues
            )
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

fun saveVokabelSet(prefs: SharedPreferences, set: VokabelSet): List<VokabelSet> {
    val existing = loadVokabelSets(prefs).toMutableList()
    val idx = existing.indexOfFirst { it.name == set.name }
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
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Vokabel(o.getString("latein"), o.getString("deutsch"), o.getInt("id"))
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
                JSONObject().apply { put("latein", v.latein); put("deutsch", v.deutsch) })
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
                lastPracticed = vp.getLong("lastPracticed")
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
            lastPracticed = now
        )
    }

    wrong.forEach { vokabel ->
        val existing = updatedMap[vokabel.id] ?: VokabelProgress(vokabel.id)
        updatedMap[vokabel.id] = existing.copy(
            wrongCount = existing.wrongCount + 1,
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

fun saveSessionState(prefs: SharedPreferences, setCreatedAt: Long, state: SessionState) {
    val json = JSONObject().apply {
        put("shuffledIds", JSONArray(state.shuffledIds))
        put("currentIndex", state.currentIndex)
        put("correctIds", JSONArray(state.correctIds))
        put("wrongIds", JSONArray(state.wrongIds))
        put("showDeutsch", state.showDeutsch)
        put("timestamp", state.timestamp)
    }.toString()
    prefs.edit { putString("session_$setCreatedAt", json) }
}

fun loadSessionState(prefs: SharedPreferences, setCreatedAt: Long): SessionState? {
    val raw = prefs.getString("session_$setCreatedAt", null) ?: return null
    return try {
        val obj = JSONObject(raw)
        val timestamp = if (obj.has("timestamp")) obj.getLong("timestamp") else 0L
        if (System.currentTimeMillis() - timestamp > 20 * 60 * 1000) {
            clearSessionState(prefs, setCreatedAt)
            return null
        }
        fun JSONArray.toIntList() = (0 until length()).map { getInt(it) }
        SessionState(
            shuffledIds = obj.getJSONArray("shuffledIds").toIntList(),
            currentIndex = obj.getInt("currentIndex"),
            correctIds = obj.getJSONArray("correctIds").toIntList(),
            wrongIds = obj.getJSONArray("wrongIds").toIntList(),
            showDeutsch = obj.getBoolean("showDeutsch"),
            timestamp = timestamp
        )
    } catch (_: Exception) {
        null
    }
}

fun clearSessionState(prefs: SharedPreferences, setCreatedAt: Long) {
    prefs.edit { remove("session_$setCreatedAt") }
}