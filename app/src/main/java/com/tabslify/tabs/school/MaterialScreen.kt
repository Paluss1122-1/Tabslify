package com.tabslify.tabs.school

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.core.graphics.scale
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.tabslify.R
import com.tabslify.core.objects.Config
import com.tabslify.core.objects.Config.MAX_GEMINI
import com.tabslify.core.objects.prvt
import com.tabslify.core.ui.AccentViolet
import com.tabslify.core.ui.AlertDialogTabslify
import com.tabslify.core.ui.BgSurface
import com.tabslify.core.ui.TextPrimary
import com.tabslify.core.ui.TextSecondary
import com.tabslify.core.ui.TextTertiary
import com.tabslify.core.ui.calloutAwareMarkdownComponents
import com.tabslify.quiethoursnotificationhelper.AiProvider
import com.tabslify.quiethoursnotificationhelper.sendAiRequest
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.time.Duration.Companion.minutes

data class MaterialUploadState(
    val fileName: String,
    val status: UploadStatus
)

enum class UploadStatus { PENDING, UPLOADING, DONE, ERROR }

data class AiSummaryState(
    val summary: String? = null,
    val loading: Boolean = false,
    val analysisStarted: Boolean = false
)

@SuppressLint("AutoboxingStateCreation")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialienScreen(
    onBack: () -> Unit,
    onOpenSet: (VokabelSet) -> Unit = {},
    paddingValues: PaddingValues,
    initialSubject: String? = null,
    initialFile: String? = null
) {
    val context = LocalContext.current
    if (!prvt()) {
        Toast.makeText(context, stringResource(R.string.forbidden), Toast.LENGTH_SHORT).show()
        return
    }
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("material_cache", Context.MODE_PRIVATE) }

    var showSubjectDialog by remember { mutableStateOf(false) }
    var subjectNameInput by remember { mutableStateOf("") }
    var uploads by remember { mutableStateOf<List<MaterialUploadState>>(emptyList()) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var selectedSubject by remember { mutableStateOf(initialSubject) }
    var selectedFile by remember { mutableStateOf(initialFile) }
    var folders by remember { mutableStateOf<List<String>>(emptyList()) }
    var foldersLoading by remember { mutableStateOf(true) }
    var folderFiles by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    var folderFilesLoading by remember { mutableStateOf(false) }
    var sheetExpanded by remember { mutableStateOf(false) }
    var showFullscreenSummary by remember { mutableStateOf(false) }
    var rawRecentMaterials by remember {
        mutableStateOf(
            prefs.getString("recent_materials", null)
                ?.split("\u001e")
                ?.filter { it.isNotBlank() }
                ?: emptyList()
        )
    }
    var showAiSummaryToRefresh by remember { mutableStateOf(false) }

    var recentMaterialPreviews by remember { mutableStateOf<List<RecentMaterial>>(emptyList()) }

    val dateiNichtLesbar = stringResource(R.string.datei_nicht_lesbar_2)
    val dateiNichtErreichbar = stringResource(R.string.datei_nicht_erreichbar)
    val ocrFehlgeschlagen = stringResource(R.string.ocr_fehlgeschlagen)
    val summaryFehlgeschlagen = stringResource(R.string.summary_fehlgeschlagen)

    var aiSummaryStates by remember { mutableStateOf<Map<String, AiSummaryState>>(emptyMap()) }

    fun persistRecentMaterial(subject: String, fileName: String? = null) {
        if (fileName == null) return
        val newItem = RecentMaterial(
            subject = subject,
            fileName = fileName,
            lastUsed = System.currentTimeMillis()
        )
        val existing = rawRecentMaterials.mapNotNull(::parseRecentMaterial)
            .filter { it.subject != subject || it.fileName != fileName }
        val normalized = (listOf(newItem) + existing).take(3)
        rawRecentMaterials = normalized.map(::serializeRecentMaterial)
        prefs.edit { putString("recent_materials", rawRecentMaterials.joinToString("\u001e")) }
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

    LaunchedEffect(selectedFile) {
        if (selectedFile == null) {
            sheetExpanded = false
            showFullscreenSummary = false
        }
    }

    LaunchedEffect(Unit) {
        try {
            val files = Config.client.storage.from("school").list()
            val fetchedFolders = files.map { it.name }.filter { it.isNotBlank() }.sortedDescending()
            folders = fetchedFolders

            scope.launch(Dispatchers.IO) {
                try {
                    val allKeys = prefs.all.keys.filter { it.startsWith("cache_") }
                    if (allKeys.isNotEmpty()) {
                        fetchedFolders.forEach { subject ->
                            val subjectPrefix = "cache_${subject}_"
                            val keysForSubject = allKeys.filter { it.startsWith(subjectPrefix) }
                            if (keysForSubject.isNotEmpty()) {
                                val existingFiles = Config.client.storage.from("school").list(subject).map { it.name }.toSet()
                                val toRemove = keysForSubject.filter { key ->
                                    val fileName = key.removePrefix(subjectPrefix).removeSuffix("_ocr").removeSuffix("_summary")
                                    !existingFiles.contains(fileName)
                                }
                                if (toRemove.isNotEmpty()) {
                                    prefs.edit { toRemove.forEach { remove(it) } }
                                }
                            }
                        }

                        val orphanedKeys = allKeys.filter { key ->
                            fetchedFolders.none { subject -> key.startsWith("cache_${subject}_") }
                        }
                        if (orphanedKeys.isNotEmpty()) {
                            prefs.edit { orphanedKeys.forEach { remove(it) } }
                        }
                    }
                } catch (_: Exception) {
                }
            }
        } catch (e: Exception) {
            val cachedFolders = prefs.all.keys
                .filter { it.startsWith("files_") }
                .map { it.removePrefix("files_") }
                .sortedDescending()
            if (cachedFolders.isNotEmpty()) {
                folders = cachedFolders
                cachedFolders.forEach { subj ->
                    val cached = prefs.getString("files_$subj", null)
                        ?.split("\u001e")?.filter { it.isNotBlank() } ?: emptyList()
                    folderFiles = folderFiles + (subj to cached)
                }
            } else {
                errorMsg = e.localizedMessage
            }
        } finally {
            foldersLoading = false
        }
    }
    LaunchedEffect(selectedSubject) {
        val subject = selectedSubject ?: return@LaunchedEffect
        if (folderFiles.containsKey(subject)) return@LaunchedEffect
        folderFilesLoading = true
        try {
            val files = Config.client.storage.from("school").list(subject)
            folderFiles = folderFiles + (subject to files.map { it.name })
            val fileNames = files.map { it.name }
            folderFiles = folderFiles + (subject to fileNames)
            prefs.edit {
                putString("files_$subject", fileNames.joinToString("\u001e"))
            }
        } catch (e: Exception) {
            val cached = prefs.getString("files_$subject", null)
                ?.split("\u001e")?.filter { it.isNotBlank() }
            if (cached != null) {
                folderFiles = folderFiles + (subject to cached)
            } else {
                errorMsg = e.localizedMessage
            }
        } finally {
            folderFilesLoading = false
        }
    }

    BackHandler {
        when {
            showFullscreenSummary -> showFullscreenSummary = false
            selectedFile != null -> selectedFile = null
            selectedSubject != null -> selectedSubject = null
            else -> onBack()
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val newUploads = uris.map { uri ->
            MaterialUploadState(
                fileName = getFileName(context, uri),
                status = UploadStatus.PENDING
            )
        }
        uploads = uploads + newUploads

        uris.forEachIndexed { i, uri ->
            val state = newUploads[i]
            scope.launch {
                uploads = uploads.map {
                    if (it.fileName == state.fileName) it.copy(status = UploadStatus.UPLOADING) else it
                }
                if (selectedSubject != null && !folders.contains(selectedSubject)) {
                    folders = (listOf(selectedSubject!!) + folders).sortedDescending()
                }
                try {
                    val rawBytes =
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            ?: throw Exception(dateiNichtLesbar)
                    val (uploadBytes, uploadName) = compressToJpgIfImage(rawBytes, state.fileName)
                    val path = "$selectedSubject/$uploadName"
                    Config.client.storage.from("school").upload(path, uploadBytes) { upsert = true }

                    selectedSubject?.let { subject ->
                        val currentFiles = folderFiles[subject] ?: emptyList()
                        if (!currentFiles.contains(uploadName)) {
                            folderFiles = folderFiles + (subject to (currentFiles + uploadName))
                        }
                    }
                    uploads = uploads.filter { it.fileName != state.fileName }

                } catch (e: Exception) {
                    errorMsg = e.localizedMessage
                    uploads = uploads.map {
                        if (it.fileName == state.fileName) it.copy(status = UploadStatus.ERROR) else it
                    }
                }
            }
        }
    }

    suspend fun runOcrAndSummary(
        fileKey: String,
        subject: String,
        fileName: String,
        forceRefresh: Boolean = false
    ) {
        val cacheKeyBase = "cache_${subject}_${fileName}"

        aiSummaryStates =
            aiSummaryStates + (fileKey to AiSummaryState(loading = true, analysisStarted = true))

        if (!forceRefresh) {
            val cachedOcr = prefs.getString("${cacheKeyBase}_ocr", null)
            val cachedSummary = prefs.getString("${cacheKeyBase}_summary", null)

            if (cachedOcr != null && cachedSummary != null) {
                aiSummaryStates = aiSummaryStates + (fileKey to AiSummaryState(
                    summary = cachedSummary,
                    loading = false,
                    analysisStarted = true
                ))
                return
            }
        }

        try {
            val localUri = resolveFileUrl(context, subject, fileName)
                ?: throw Exception(dateiNichtErreichbar)

            val imgBytes = withContext(Dispatchers.IO) {
                if (localUri.startsWith("file:")) {
                    java.io.File(java.net.URI(localUri)).readBytes()
                } else {
                    java.net.URL(localUri).readBytes()
                }
            }

            val base64 = android.util.Base64.encodeToString(
                imgBytes,
                android.util.Base64.NO_WRAP
            )

            val rawText = sendAiRequest(
                context = context,
                history = emptyList(),
                userMessage = """
                    Du bist ein hochpräzises System zur strukturierten Inhaltsextraktion für nachfolgende LLM-Verarbeitung. Analysiere das bereitgestellte Bild und generiere eine semantisch perfekt aufbereitete Textrekonstruktion. Halte dich strikt an diese Struktur:

                    # [TITEL/ÜBERSCHRIFT DES MATERIALS]

                    ## 1. TEXTINHALT (WORTLAUT & REKONSTRUKTION)
                    - Extrahiere JEDEN sichtbaren Text vollständig, buchstabengetreu und in der logischen Lesereihenfolge.
                    - Nutze sauberes Markdown für Formatierungen (#, ##, **, *).
                    - Wichtig: Markiere visuelle Hervorhebungen (Textmarker, Unterstreichungen, auffällige Farben) explizit mit einem Inline-Tag, z. B. [HERVORHEBUNG: text].

                    ## 2. STRUKTURELLE & VISUELLE ELEMENTE
                    - Beschreibe Diagramme, Tabellen, Infografiken, Formeln oder Bilder präzise im Kontext des Materials.
                    - Erkläre die räumliche Zuordnung (z.B. "In der Infografik rechts neben dem Text wird X dargestellt...").
                    - Wandle Tabellen in valide Markdown-Tabellen um.

                    ## 3. METADATEN & BEZÜGE
                    - Notiere Fußnoten, Randnotizen oder Quellenangaben.
                    - Falls vorhanden: Benenne die logische Beziehung zwischen Textabschnitten und Grafiken.

                    Ziel: Ein kompromisslos strukturierter, semantisch reicher Text, den ein anderes KI-Modell ohne das Originalbild fehlerfrei interpretieren und weiterverarbeiten kann.
                """.trimIndent(),
                pic = base64,
                model = MAX_GEMINI,
                provider = AiProvider.GEMINI
            ) ?: throw Exception(ocrFehlgeschlagen)

            val summary = sendAiRequest(
                context = context,
                history = emptyList(),
                userMessage = """
                    Du bist ein erfahrener, empathischer Pädagoge und Experte für Didaktik. Deine Aufgabe ist es, den folgenden extrahierten Inhalt eines Lernmaterials so aufzubereiten, dass Schüler oder Studierende das Thema intuitiv, tiefgründig und nachhaltig verstehen. 

                    HIER IST DAS ROHMATERIAL:
                    $rawText

                    BITTE ERSTELLE EINE DIDAKTISCH WERTVOLLE ERKLÄRUNG NACH FOLGENDEM AUFBAU:

                    1. **Der Anker (Einstieg & Relevanz)**
                       - Starte mit einer einfachen, greifbaren Analogie oder einem praktischen Alltagsbeispiel, das das Kernthema sofort greifbar macht. Warum ist dieses Thema überhaupt spannend oder wichtig?

                    2. **Die Kernbotschaft (Der rote Faden)**
                       - Formuliere das Hauptziel oder die Kernaussage des Materials in einem einzigen, klaren Satz.

                    3. **Schritt-für-Schritt-Erklärung (Didaktischer Aufbau)**
                       - Gliedere das Thema in 2 bis 4 logische Abschnitte (gehe dabei vom Einfachen zum Komplexen über).
                       - Verwende sprechende, motivierende Überschriften.
                       - *Wichtig:* Erkläre jeden Fachbegriff (Fachtermini) sofort bei der ersten Nennung auf einfache und anschauliche Weise.
                       - Falls Grafiken oder Diagramme im Quelltext beschrieben wurden: Erkläre deren didaktische Aussage in eigenen Worten.

                    4. **Spickzettel & Aha-Momente (Kritische Punkte)**
                       - Fasse die 3 bis 5 absolut kritischen Erkenntnisse prägnant zusammen.
                       - **STYLING-VORGABE:** Nutze für besonders einprägsame Sachen, typische Klausur-Stolperfallen, Definitionen oder extrem wichtige Merksätze sehr gerne Markdown-Callouts wie `> [!warning]` oder `[!warning] Text` (bzw. passende Varianten wie `[!info]`), damit diese optisch sofort aus dem Text hervorstechen.

                    5. **Selbstcheck (Aktivierung des Gelernten)**
                       - Beende den Text mit 2 kurzen, reflexiven Fragen (ohne direkt die Antwort zu verraten), mit denen die Lernenden selbst prüfen können, ob sie den Kern der Sache verstanden haben.

                    Tonfall: Motivierend, klar, verständlich, auf Augenhöhe, fehlerfrei auf Deutsch. Vermeide verschachtelte Sätze und kognitive Überlastung.
                """.trimIndent(),
                provider = AiProvider.GEMINI
            ) ?: throw Exception(summaryFehlgeschlagen)

            aiSummaryStates = aiSummaryStates + (fileKey to AiSummaryState(
                summary = summary,
                loading = false,
                analysisStarted = true
            ))

            prefs.edit {
                putString("${cacheKeyBase}_ocr", rawText)
                putString("${cacheKeyBase}_summary", summary)
            }
        } catch (e: Exception) {
            errorMsg = e.localizedMessage
            aiSummaryStates = aiSummaryStates + (fileKey to AiSummaryState(
                summary = null,
                loading = false,
                analysisStarted = true
            ))
        }
    }

    if (showAiSummaryToRefresh) {
        AlertDialogTabslify(
            onConfirm = {
                scope.launch {
                    runOcrAndSummary(
                        "${selectedSubject}_${selectedFile}",
                        selectedSubject!!,
                        selectedFile!!,
                        forceRefresh = true
                    )
                }
            },
            onDismiss = { showAiSummaryToRefresh = false },
            title = stringResource(R.string.mochtest_du_wirklich_deine_ai),
            text = stringResource(R.string.die_jetzige_geht_dabei_verloren)
        )
    }

    if (showSubjectDialog) {
        AlertDialog(
            onDismissRequest = { showSubjectDialog = false },
            title = { Text(stringResource(R.string.fach_erstellen)) },
            text = {
                OutlinedTextField(
                    value = subjectNameInput,
                    onValueChange = { subjectNameInput = it },
                    label = { Text(stringResource(R.string.fachname)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = subjectNameInput.trim()
                    if (name.isNotBlank()) {
                        selectedSubject = name
                        if (!folders.contains(name)) {
                            folders = (listOf(name) + folders).sortedDescending()
                        }
                        folderFiles = folderFiles + (name to (folderFiles[name] ?: emptyList()))
                        subjectNameInput = ""
                    }
                    showSubjectDialog = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSubjectDialog = false }) { Text(stringResource(R.string.abbrechen)) }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SchoolHeader(
            title = stringResource(R.string.materialien),
            subtitle = if (selectedSubject != null) stringResource(R.string.school, selectedSubject.orEmpty()) else stringResource(R.string.school_2),
            onBack = {
                when {
                    showFullscreenSummary -> showFullscreenSummary = false
                    selectedFile != null -> selectedFile = null
                    selectedSubject != null -> selectedSubject = null
                    else -> onBack()
                }
            },
            savedSets = emptyList(),
            onOpenSet = onOpenSet,
            recentMaterials = recentMaterialPreviews.ifEmpty {
                folders.take(3).map { RecentMaterial(subject = it) }
            },
            onOpenMaterial = {
                if (it.fileName != null) {
                    selectedSubject = it.subject
                    selectedFile = it.fileName
                    persistRecentMaterial(it.subject, it.fileName)
                } else {
                    selectedSubject = it.subject
                }
            },
            showDashboard = selectedSubject == null,
            paddingValues = paddingValues,
            actions = {
                if (selectedSubject != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(BgSurface)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            selectedSubject!!,
                            color = AccentViolet,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            },
            drawGradient = false
        )

        AnimatedVisibility(errorMsg != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFB71C1C).copy(alpha = 0.15f))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("⚠️", fontSize = 14.sp)
                Text(
                    errorMsg ?: "",
                    color = Color(0xFFEF9A9A),
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
                Text("✕", color = TextTertiary, modifier = Modifier.clickable { errorMsg = null })
            }
        }

        if (selectedSubject == null) {
            if (foldersLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentViolet)
                }
            } else if (folders.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("🗂️", fontSize = 48.sp)
                        Text(
                            stringResource(R.string.keine_ordner_vorhanden),
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            stringResource(R.string.erstelle_ein_neues_fach_via),
                            color = TextTertiary,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(folders) { folder ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1.1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(BgSurface)
                                .clickable {
                                    selectedSubject = folder
                                    uploads = emptyList()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("📁", fontSize = 42.sp)
                                Text(
                                    folder,
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
            FloatingActionButton(
                onClick = { showSubjectDialog = true },
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(20.dp),
                containerColor = AccentViolet,
                contentColor = TextPrimary
            ) {
                Icon(Icons.Default.Add, stringResource(R.string.ordner_erstellen))
            }
        } else {
            if (folderFilesLoading) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = AccentViolet
                    )
                }
            }
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (uploads.isEmpty() && (folderFiles[selectedSubject]
                        ?: emptyList()).isEmpty() && !folderFilesLoading
                ) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 60.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("📂", fontSize = 48.sp)
                                Text(
                                    stringResource(R.string.noch_keine_dateien),
                                    color = TextPrimary,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    stringResource(R.string.tippe_um_dateien_hochzuladen),
                                    color = TextTertiary,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
                val allExisting = (folderFiles[selectedSubject] ?: emptyList())
                    .filter { name -> uploads.none { it.fileName == name } }
                val imageFiles = allExisting.filter { isImageFile(it) }
                val otherFiles = allExisting.filter { !isImageFile(it) }

                if (imageFiles.isNotEmpty()) {
                    item {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(((imageFiles.size + 1) / 2 * 164).dp),
                            userScrollEnabled = false
                        ) {
                            items(imageFiles) { fileName ->
                                var url by remember(fileName) { mutableStateOf<String?>(null) }

                                LaunchedEffect(fileName) {
                                    url = resolveFileUrl(context, selectedSubject!!, fileName)
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(156.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(BgSurface)
                                        .clickable {
                                            selectedFile = fileName
                                            selectedSubject?.let {
                                                persistRecentMaterial(
                                                    it,
                                                    fileName
                                                )
                                            }
                                        }
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(url)
                                            .scale(Scale.FILL)
                                            .precision(Precision.EXACT)
                                            .build(),
                                        contentDescription = fileName,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }
                }

                items(otherFiles) { fileName ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(BgSurface)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(fileEmoji(fileName), fontSize = 22.sp)
                        Text(
                            fileName,
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "✓",
                            color = Color(0xFF66BB6A),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                items(uploads) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(BgSurface)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(fileEmoji(item.fileName), fontSize = 22.sp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                item.fileName,
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                when (item.status) {
                                    UploadStatus.PENDING -> stringResource(R.string.warte_platzhalter)
                                    UploadStatus.UPLOADING -> stringResource(R.string.hochladen_platzhalter)
                                    UploadStatus.DONE -> stringResource(R.string.fertig_3)
                                    UploadStatus.ERROR -> stringResource(R.string.fehler)
                                },
                                color = when (item.status) {
                                    UploadStatus.DONE -> Color(0xFF66BB6A)
                                    UploadStatus.ERROR -> Color(0xFFEF5350)
                                    else -> TextTertiary
                                },
                                fontSize = 12.sp
                            )
                        }
                        when (item.status) {
                            UploadStatus.UPLOADING -> CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = AccentViolet
                            )

                            UploadStatus.DONE -> Icon(
                                Icons.Default.CheckCircle, null,
                                tint = Color(0xFF66BB6A), modifier = Modifier.size(20.dp)
                            )

                            else -> {}
                        }
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
            FloatingActionButton(
                onClick = { filePicker.launch("*/*") },
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(20.dp),
                containerColor = AccentViolet,
                contentColor = TextPrimary
            ) {
                Icon(Icons.Default.Add, stringResource(R.string.datei_hinzufugen))
            }
        }
    }

    if (selectedFile != null && selectedSubject != null) {
        val fileKey = remember(selectedFile, selectedSubject) {
            "${selectedSubject}_${selectedFile}"
        }

        var fileUrl by remember(fileKey) { mutableStateOf<String?>(null) }

        val currentState = aiSummaryStates[fileKey] ?: AiSummaryState()
        val aiSummary = currentState.summary
        val summaryLoading = currentState.loading
        val analysisStarted = currentState.analysisStarted

        var scale by remember { mutableFloatStateOf(1f) }
        var offsetX by remember { mutableFloatStateOf(0f) }
        var offsetY by remember { mutableStateOf(0f) }

        val transformableState = rememberTransformableState { _, zoomChange, panChange, _ ->
            scale = (scale * zoomChange).coerceIn(1f, 4f)
            offsetX += panChange.x
            offsetY += panChange.y
        }

        LaunchedEffect(fileKey) {
            fileUrl = resolveFileUrl(context, selectedSubject!!, selectedFile!!)

            if (!analysisStarted && isImageFile(selectedFile!!)) {
                runOcrAndSummary(
                    fileKey,
                    selectedSubject!!,
                    selectedFile!!,
                    forceRefresh = false
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.97f))
        ) {
            Column(modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(
                        start = 4.dp,
                        top = 8.dp,
                        end = 16.dp,
                        bottom = 4.dp
                    )
                ) {
                    IconButton(onClick = { selectedFile = null }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            selectedFile!!,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            selectedSubject!!,
                            color = Color.White.copy(alpha = 0.45f),
                            fontSize = 12.sp
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .transformable(transformableState)
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(fileUrl)
                            .scale(Scale.FIT)
                            .precision(Precision.EXACT)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offsetX
                                translationY = offsetY
                            }
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                        .background(BgSurface)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { sheetExpanded = !sheetExpanded }
                        .animateContentSize(animationSpec = tween(400))
                        .padding(bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 10.dp, bottom = 4.dp)
                            .width(36.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(TextTertiary.copy(alpha = 0.4f))
                            .align(Alignment.CenterHorizontally)
                    )

                    AnimatedVisibility(
                        visible = !sheetExpanded,
                        enter = fadeIn(animationSpec = tween(400)) + expandVertically(
                            animationSpec = tween(
                                400
                            )
                        ),
                        exit = fadeOut(animationSpec = tween(400)) + shrinkVertically(
                            animationSpec = tween(
                                400
                            )
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("🤖", fontSize = 18.sp)
                                Text(
                                    if (aiSummary != null) stringResource(R.string.zusammenfassung_bereit) else stringResource(R.string.ki_analyse),
                                    color = TextSecondary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Text(
                                stringResource(R.string.tippe_zum_offnen),
                                color = TextTertiary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = sheetExpanded,
                        enter = fadeIn(animationSpec = tween(400)) + expandVertically(
                            animationSpec = tween(
                                400
                            )
                        ),
                        exit = fadeOut(animationSpec = tween(400)) + shrinkVertically(
                            animationSpec = tween(
                                400
                            )
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("📅", fontSize = 16.sp)
                                Text(
                                    stringResource(R.string.fach_prefix, selectedSubject.orEmpty()),
                                    color = TextPrimary,
                                    fontSize = 14.sp
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.White.copy(alpha = 0.05f))
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text("🤖", fontSize = 22.sp)

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            stringResource(R.string.ai_summary),
                                            color = TextPrimary,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )

                                        if (aiSummary != null) {
                                            IconButton(
                                                onClick = { showFullscreenSummary = true },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.OpenInFull,
                                                    contentDescription = stringResource(R.string.vollbild),
                                                    tint = TextTertiary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }

                                    when {
                                        summaryLoading -> Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(14.dp),
                                                strokeWidth = 2.dp,
                                                color = AccentViolet
                                            )
                                            Text(
                                                stringResource(R.string.analysiere_platzhalter),
                                                color = TextTertiary,
                                                fontSize = 12.sp
                                            )
                                        }

                                        aiSummary != null -> {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .heightIn(max = 240.dp)
                                                    .verticalScroll(rememberScrollState())
                                            ) {
                                                Markdown(
                                                    content = aiSummary,
                                                    colors = markdownColor(text = TextPrimary),
                                                    typography = markdownTypography(
                                                        h1 = TextStyle(
                                                            fontSize = 20.sp,
                                                            lineHeight = 22.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = TextPrimary,
                                                            textDecoration = TextDecoration.Underline
                                                        ),
                                                        h2 = TextStyle(
                                                            fontSize = 18.sp,
                                                            lineHeight = 20.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = TextPrimary,
                                                            textDecoration = TextDecoration.Underline
                                                        ),
                                                        h3 = TextStyle(
                                                            fontSize = 17.sp,
                                                            lineHeight = 19.sp,
                                                            fontWeight = FontWeight.Medium,
                                                            color = TextPrimary,
                                                            textDecoration = TextDecoration.Underline
                                                        ),
                                                        text = TextStyle(
                                                            fontSize = 11.sp,
                                                            lineHeight = 15.sp,
                                                            color = TextPrimary
                                                        ),
                                                        paragraph = TextStyle(
                                                            fontSize = 11.sp,
                                                            lineHeight = 15.sp,
                                                            color = TextPrimary
                                                        ),
                                                        list = TextStyle(
                                                            fontSize = 11.sp,
                                                            lineHeight = 15.sp,
                                                            color = TextPrimary
                                                        ),
                                                        ordered = TextStyle(
                                                            fontSize = 11.sp,
                                                            lineHeight = 15.sp,
                                                            color = TextPrimary
                                                        )
                                                    ),
                                                    components = calloutAwareMarkdownComponents()
                                                )
                                            }
                                        }

                                        else -> Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                stringResource(R.string.nicht_verfugbar),
                                                color = TextTertiary,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showFullscreenSummary,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BgSurface)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            IconButton(onClick = { showFullscreenSummary = false }) {
                                Icon(Icons.Default.Close, null, tint = TextPrimary)
                            }
                            Text(
                                stringResource(R.string.ai_zusammenfassung),
                                color = TextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (aiSummary != null) {
                                IconButton(
                                    onClick = {
                                        val clipboard =
                                            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val label = context.getString(R.string.ai_zusammenfassung)
                                        clipboard.setPrimaryClip(ClipData.newPlainText(label, aiSummary))
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.kopiert, label),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = stringResource(R.string.kopieren),
                                        tint = TextPrimary
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(AccentViolet.copy(alpha = 0.15f))
                                    .clickable {
                                        showAiSummaryToRefresh = true
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    stringResource(R.string.aktualisieren_2),
                                    color = AccentViolet,
                                    fontSize = 7.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = stringResource(R.string.reanalyze_picture),
                                    tint = Color.White
                                )
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp)
                    ) {
                        if (aiSummary != null) {
                            SelectionContainer {
                                Markdown(
                                    content = aiSummary,
                                    colors = markdownColor(text = TextPrimary),
                                    typography = markdownTypography(
                                        h1 = TextStyle(
                                            fontSize = 24.sp,
                                            lineHeight = 22.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TextPrimary,
                                            textDecoration = TextDecoration.Underline
                                        ),
                                        h2 = TextStyle(
                                            fontSize = 22.sp,
                                            lineHeight = 20.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = TextPrimary,
                                            textDecoration = TextDecoration.Underline
                                        ),
                                        h3 = TextStyle(
                                            fontSize = 21.sp,
                                            lineHeight = 19.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = TextPrimary,
                                            textDecoration = TextDecoration.Underline
                                        ),
                                        text = TextStyle(
                                            fontSize = 13.sp,
                                            lineHeight = 15.sp,
                                            color = TextPrimary
                                        ),
                                        paragraph = TextStyle(
                                            fontSize = 13.sp,
                                            lineHeight = 15.sp,
                                            color = TextPrimary
                                        ),
                                        list = TextStyle(
                                            fontSize = 13.sp,
                                            lineHeight = 15.sp,
                                            color = TextPrimary
                                        ),
                                        ordered = TextStyle(
                                            fontSize = 13.sp,
                                            lineHeight = 15.sp,
                                            color = TextPrimary
                                        )
                                    ),
                                    components = calloutAwareMarkdownComponents()
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = AccentViolet)
                            }
                        }

                        Spacer(Modifier.height(40.dp))
                    }
                }
            }
        }
    }
}

private fun getFileName(context: Context, uri: Uri): String {
    var name = "datei_${System.currentTimeMillis()}"
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst() && idx >= 0) name = cursor.getString(idx)
    }
    return name
}

private fun isImageFile(name: String) =
    name.lowercase().let {
        it.endsWith(".jpg") || it.endsWith(".jpeg") || it.endsWith(".png") || it.endsWith(".webp")
    }

private fun fileEmoji(name: String) = when {
    name.endsWith(".pdf") -> "📄"
    name.endsWith(".jpg") || name.endsWith(".png") || name.endsWith(".jpeg") -> "🖼️"
    name.endsWith(".mp4") || name.endsWith(".mov") -> "🎬"
    name.endsWith(".mp3") || name.endsWith(".m4a") -> "🎵"
    name.endsWith(".docx") || name.endsWith(".doc") -> "📝"
    name.endsWith(".pptx") || name.endsWith(".ppt") -> "📊"
    else -> "📎"
}

private fun compressToJpgIfImage(
    bytes: ByteArray,
    fileName: String,
    quality: Int = 70,
    maxSize: Int = 2048
): Pair<ByteArray, String> {
    val lower = fileName.lowercase()
    val isImage = lower.endsWith(".png") || lower.endsWith(".jpg") ||
            lower.endsWith(".jpeg") || lower.endsWith(".webp") || lower.endsWith(".bmp")
    if (!isImage) return bytes to fileName

    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)

    var sampleSize = 1
    var w = opts.outWidth
    var h = opts.outHeight
    while (w > maxSize * 2 || h > maxSize * 2) {
        sampleSize *= 2; w /= 2; h /= 2
    }

    val decoded = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.RGB_565
    }
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decoded)
        ?: return bytes to fileName

    val scaled = if (bitmap.width > maxSize || bitmap.height > maxSize) {
        val ratio = maxSize.toFloat() / maxOf(bitmap.width, bitmap.height)
        bitmap.scale((bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
    } else bitmap

    val out = ByteArrayOutputStream()
    try {
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
    } finally {
        if (scaled !== bitmap) runCatching { scaled.recycle() }
        bitmap.recycle()
    }

    val jpgName = fileName.substringBeforeLast(".") + ".jpg"
    return out.toByteArray() to jpgName
}

fun localCacheFile(context: Context, subject: String, fileName: String): java.io.File {
    val dir = java.io.File(context.cacheDir, "material_cache/$subject").also { it.mkdirs() }
    return java.io.File(dir, fileName)
}

suspend fun resolveFileUrl(
    context: Context,
    subject: String,
    fileName: String
): String? {
    val local = localCacheFile(context, subject, fileName)
    if (local.exists()) return local.toURI().toString()

    return try {
        val signed = Config.client.storage.from("school")
            .createSignedUrl("$subject/$fileName", 10.minutes)

        withContext(Dispatchers.IO) {
            val bytes = java.net.URL(signed).readBytes()
            local.writeBytes(bytes)
        }
        local.toURI().toString()
    } catch (_: Exception) {
        null
    }
}

fun serializeRecentMaterial(material: RecentMaterial): String {
    return listOf(material.subject, material.fileName.orEmpty(), material.lastUsed.toString())
        .joinToString("\u001f")
}

private fun parseRecentMaterial(serialized: String): RecentMaterial? {
    val parts = serialized.split("\u001f")
    if (parts.size != 3) return null
    val subject = parts[0]
    val fileName = parts[1].ifBlank { null }
    val lastUsed = parts[2].toLongOrNull() ?: return null
    return RecentMaterial(subject = subject, fileName = fileName, lastUsed = lastUsed)
}