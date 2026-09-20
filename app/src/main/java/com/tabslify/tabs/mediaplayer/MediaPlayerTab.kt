package com.tabslify.tabs.mediaplayer

import android.Manifest
import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.tabslify.R
import com.tabslify.core.ui.NeonBox
import com.tabslify.quiethoursnotificationhelper.AiResponseEntry
import com.tabslify.quiethoursnotificationhelper.aiResponseFlow
import com.tabslify.quiethoursnotificationhelper.deleteAiResponse
import com.tabslify.quiethoursnotificationhelper.loadAllAiResponses
import com.tabslify.quiethoursnotificationhelper.loadTodayOrYesterdayEntry
import com.tabslify.services.MediaPlayerService
import com.tabslify.tabs.audiorecordertab.AudioRecorderTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

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

data class NowPlayingState(
    val isActive: Boolean = false,
    val mode: String = "music",
    val title: String = "",
    val subtitle: String = "",
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val progress: Float = 0f
)

fun formatMsMTB(ms: Long): String {
    val m = (ms / 60_000)
    val s = (ms % 60_000) / 1_000
    return "%d:%02d".format(m, s)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaTab(viewModel: MediaViewModel = viewModel(), onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> viewModel.onPermissionResult(granted) }

    LaunchedEffect(Unit) {
        viewModel.checkAndRefresh()
    }

    DisposableEffect(Unit) {
        viewModel.startNowPlayingPoller()
        onDispose { viewModel.stopNowPlayingPoller() }
    }

    var analyticsTarget by remember { mutableStateOf<Any?>(null) }

    var showFullscreenPlayer by remember { mutableStateOf(false) }
    val nowPlaying by viewModel.nowPlaying.collectAsState()

    BackHandler {
        if (state.currentTab == MediaTab.DOWNLOADER || state.currentTab == MediaTab.RECORDER) {
            viewModel.setTab(MediaTab.HOME)
            return@BackHandler
        }
        onBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                Column {
                    NowPlayingBar(
                        nowPlaying = nowPlaying,
                        onOpenFullscreen = { showFullscreenPlayer = true },
                        onPlayPause = {
                            viewModel.playerAction(
                                context,
                                MediaViewModel.PlayerAction.PLAY_PAUSE
                            )
                        }
                    )
                    MediaBottomBar(
                        currentTab = state.currentTab,
                        onTabSelected = { viewModel.setTab(it) }
                    )
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 30.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.media_player_2),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 26.sp
                        )

                        Row {
                            IconButton(onClick = {
                                if (state.currentTab != MediaTab.DOWNLOADER) viewModel.setTab(
                                    MediaTab.DOWNLOADER
                                )
                                else viewModel.setTab(MediaTab.HOME)
                            }) {
                                val color by animateColorAsState(
                                    targetValue = if (state.currentTab == MediaTab.DOWNLOADER) MaterialTheme.colorScheme.primary else Color.White,
                                    animationSpec = tween(durationMillis = 300),
                                    label = "containerColor"
                                )
                                Icon(
                                    Icons.Default.Download,
                                    contentDescription = stringResource(R.string.open_downloader),
                                    tint = color
                                )
                            }

                            IconButton(onClick = {
                                if (state.currentTab != MediaTab.RECORDER) viewModel.setTab(
                                    MediaTab.RECORDER
                                )
                                else viewModel.setTab(MediaTab.HOME)
                            }) {
                                val color by animateColorAsState(
                                    targetValue = if (state.currentTab == MediaTab.RECORDER) MaterialTheme.colorScheme.primary else Color.White,
                                    animationSpec = tween(durationMillis = 300),
                                    label = "containerColor"
                                )
                                Icon(
                                    Icons.Default.Mic,
                                    contentDescription = stringResource(R.string.open_recorder),
                                    tint = color
                                )
                            }
                        }
                    }
                }

                if (state.permissionGranted == false) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = innerPadding.calculateBottomPadding()),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 32.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(BgCard)
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("🔒", fontSize = 40.sp)
                            Text(
                                stringResource(R.string.medienzugriff_benotigt),
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Text(
                                stringResource(R.string.um_musik_und_podcasts_zu),
                                color = TextSecondary,
                                fontSize = 14.sp
                            )
                            Button(
                                onClick = {
                                    val perm =
                                        Manifest.permission.READ_MEDIA_AUDIO
                                    permissionLauncher.launch(perm)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.berechtigung_erteilen))
                            }
                        }
                    }
                } else {
                    Crossfade(
                        targetState = state.currentTab,
                        label = "tab_transition",
                        modifier = Modifier.fillMaxSize()
                    ) { tab ->
                        when (tab) {
                            MediaTab.HOME -> HomeTab(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(bottom = innerPadding.calculateBottomPadding()),
                                state = state,
                                onSongClick = { song -> playSong(context, song) },
                                onSongLongClick = { analyticsTarget = it },
                                onEpisodeClick = { ep -> playEpisode(context, ep) },
                                onRefresh = { viewModel.refresh() }
                            )

                            MediaTab.SEARCH -> SearchTab(
                                modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
                                query = state.searchQuery,
                                results = searchResults,
                                onQueryChange = { viewModel.setSearchQuery(it) },
                                onSongClick = { song -> playSong(context, song) },
                                onSongLongClick = { analyticsTarget = it },
                                onEpisodeClick = { ep -> playEpisode(context, ep) },
                                onShowClick = { viewModel.setTab(MediaTab.PODCASTS) }
                            )

                            MediaTab.PODCASTS -> LibraryTab(
                                modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
                                state = state,
                                onEpisodeClick = { ep -> playEpisode(context, ep) },
                                onRefresh = { viewModel.refresh() }
                            )

                            MediaTab.MUSIC -> MusicTab(
                                modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
                                state = state,
                                onSongClick = { song -> playSong(context, song) },
                                onPlaylistCreated = {
                                    viewModel.viewModelScope.launch {
                                        delay(600.milliseconds)
                                        viewModel.refresh()
                                    }
                                },
                                viewModel = viewModel
                            )

                            MediaTab.DOWNLOADER -> {
                                PodcastTab()
                            }

                            MediaTab.RECORDER -> {
                                AudioRecorderTab()
                            }
                        }
                    }
                } // end permission check
            }

            analyticsTarget?.let { target ->
                AnalyticsBottomSheet(
                    target = target,
                    songAnalytics = state.songAnalytics,
                    podcastAnalytics = state.podcastAnalytics,
                    onDismiss = { analyticsTarget = null }
                )
            }

            if (showFullscreenPlayer) {
                var offsetY by remember { mutableFloatStateOf(0f) }
                val dismissThreshold = 200f

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(10f)
                        .offset { IntOffset(0, offsetY.toInt().coerceAtLeast(0)) }
                        .draggable(
                            orientation = Orientation.Vertical,
                            state = rememberDraggableState { delta ->
                                if (offsetY + delta >= 0f) offsetY += delta
                            },
                            onDragStopped = {
                                if (offsetY > dismissThreshold) {
                                    showFullscreenPlayer = false
                                }
                                offsetY = 0f
                            }
                        )
                ) {
                    FullscreenPlayerContent(
                        nowPlaying = nowPlaying,
                        onDismiss = { showFullscreenPlayer = false },
                        onAction = { action -> viewModel.playerAction(context, action) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnalyticsBottomSheet(
    target: Any,
    songAnalytics: Map<String, SongAnalytics>,
    podcastAnalytics: Map<String, PodcastAnalytics>,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BgSurface
    ) {
        when (target) {
            is MediaPlayerService.Song -> {
                songAnalytics[target.path]
                SongAnalyticsContent(song = target)
            }

            is PodcastEpisode -> {
                podcastAnalytics[target.path]
                PodcastAnalyticsContent(episode = target)
            }
        }
    }
}

@Composable
private fun MediaBottomBar(currentTab: MediaTab, onTabSelected: (MediaTab) -> Unit) {
    BottomAppBar(
        containerColor = BgSurface,
        contentColor = TextSecondary,
        tonalElevation = 0.dp,
        windowInsets = WindowInsets(0, 10, 0, 20)
    ) {
        val tabs = listOf(
            Triple(MediaTab.HOME, stringResource(R.string.home), "🏠"),
            Triple(MediaTab.SEARCH, stringResource(R.string.suche), "🔍"),
            Triple(MediaTab.MUSIC, stringResource(R.string.musik), "🎵"),
            Triple(MediaTab.PODCASTS, stringResource(R.string.podcasts), "📚")
        )
        tabs.forEach { (tab, label, icon) ->
            val selected = currentTab == tab
            NavigationBarItem(
                selected = selected,
                onClick = { onTabSelected(tab) },
                icon = {
                    Text(icon, fontSize = 20.sp)
                },
                label = {
                    Text(
                        label,
                        fontSize = 11.sp,
                        color = if (selected) AccentViolet else TextTertiary
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = AccentViolet,
                    indicatorColor = AccentVioletDim.copy(alpha = 0.3f)
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTab(
    modifier: Modifier = Modifier,
    state: MediaUiState,
    onSongClick: (MediaPlayerService.Song) -> Unit,
    onSongLongClick: (MediaPlayerService.Song) -> Unit,
    onEpisodeClick: (PodcastEpisode) -> Unit,
    onRefresh: () -> Unit
) {
    var showStatsSheet by remember { mutableStateOf(false) }
    var detailPlaylist by remember {
        mutableStateOf<Pair<PlaylistSource, List<MediaPlayerService.Song>>?>(
            null
        )
    }
    // Cache for algorithmic playlist data
    var cachedDetailPlaylistData by remember {
        mutableStateOf<Pair<PlaylistSource, List<MediaPlayerService.Song>>?>(
            null
        )
    }

    val activePodcasts = remember(state.episodes) {
        state.episodes.filter { !it.isCompleted && it.savedPositionMs > 0 }
    }

    var detailUserPlaylist by remember { mutableStateOf<MediaPlayerService.Playlist?>(null) }
    // Cache for user playlist data
    var cachedDetailUserPlaylistData by remember {
        mutableStateOf<Pair<MediaPlayerService.Playlist, List<MediaPlayerService.Song>>?>(
            null
        )
    }

    val recentSongs = remember(state.songs) {
        val sessions = MediaAnalyticsManager.getSessions()
            .filter { it.type == "music" }
        val lastPlayedMap = sessions
            .groupBy { it.label }
            .mapValues { (_, s) -> s.maxOf { it.startedAt } }
        state.songs
            .filter { lastPlayedMap.containsKey(it.name) }
            .sortedByDescending { lastPlayedMap[it.name] ?: 0L }
            .take(10)
    }

    AnimatedVisibility(
        visible = showStatsSheet,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut()
    ) {
        AllStatsBottomSheet(onDismiss = { showStatsSheet = false })
    }

    val context = LocalContext.current

    context.getSharedPreferences("media_analytics", Context.MODE_PRIVATE)
    val aiEntry by aiResponseFlow.collectAsState()
    var showAiHistory by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (aiEntry == null) {
            val saved = loadTodayOrYesterdayEntry(context)
            if (saved != null) aiResponseFlow.emit(saved)
        }
    }

    // Periodic refresh every second, even if sheets are open
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000.milliseconds)
            onRefresh()
        }
    }

    if (showAiHistory) {
        AiResponseHistorySheet(
            context = context,
            onDismiss = { showAiHistory = false }
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(BgSurface)
                    .padding(16.dp)
            ) {
                val stats = state.globalStats
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem("⏰", formatDuration(stats.totalListenedMs), stringResource(R.string.gesamt))
                    StatDivider()
                    StatItem(
                        "🔥", "${stats.listeningStreakDays}d", stringResource(R.string.streak),
                        dimmed = !stats.playedToday
                    )
                    StatDivider()
                    StatItem("🎵", "${stats.totalSongsPlayed}", stringResource(R.string.songs_gespielt))
                }

                Spacer(Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(AccentViolet.copy(alpha = 0.15f))
                        .clickable { showStatsSheet = true }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("📊", fontSize = 14.sp)
                        Text(
                            stringResource(R.string.alle_statistiken),
                            color = AccentViolet,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        aiEntry?.let { entry ->
            item {
                AiResponseCard(
                    entry = entry,
                    onShowHistory = { showAiHistory = true }
                )
            }
        }

        if (state.algorithmicPlaylists.isNotEmpty()) {
            item {
                SectionHeader(stringResource(R.string.fur_dich))
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.algorithmicPlaylists) { (source, songs) ->
                        AlgorithmicPlaylistCard(
                            source = source,
                            songCount = songs.size,
                            onClick = {
                                if (source.id == "favorites") {
                                    MediaPlayerService.toggleFavoritesMode(context)
                                } else {
                                    cachedDetailPlaylistData = source to songs
                                    detailPlaylist = source to songs
                                }
                            }
                        )
                    }
                }
            }
        }

        if (state.userPlaylists.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.meine_playlists)) }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.userPlaylists) { playlist ->
                        val songCount = playlist.items.size
                        val currentSongs = state.songs.filter { playlist.items.contains(it.path) }
                        Box(
                            modifier = Modifier
                                .size(160.dp, 200.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            AccentViolet,
                                            AccentVioletDim
                                        )
                                    )
                                )
                                .clickable {
                                    cachedDetailUserPlaylistData = playlist to currentSongs
                                    detailUserPlaylist = playlist
                                }
                                .padding(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("▶️", fontSize = 36.sp)
                                Column {
                                    Text(
                                        playlist.name,
                                        color = TextPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        pluralStringResource(R.plurals.songs_2, songCount, songCount),
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (activePodcasts.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.weiter_horen)) }
            items(activePodcasts.take(5)) { ep ->
                PodcastEpisodeRow(
                    episode = ep,
                    showName = state.shows.find { it.id == ep.showId }?.name ?: "Sonstige",
                    onClick = { onEpisodeClick(ep) },
                    onLongClick = {}
                )
            }
        }
        if (recentSongs.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.zuletzt_gehort)) }
            items(recentSongs) { song ->
                SongRow(
                    song = song,
                    onClick = { onSongClick(song) },
                    onLongClick = { onSongLongClick(song) }
                )
            }
        }

        if (state.songs.isEmpty() && state.episodes.isEmpty() && !state.isLoading) {
            item {
                EmptyState(
                    icon = "🎵",
                    title = stringResource(R.string.keine_medien_gefunden),
                    subtitle = stringResource(R.string.lege_musik_in_cloud_ab)
                )
            }
        }
    }

    (detailPlaylist ?: cachedDetailPlaylistData)?.let { (source, _) ->
        if (detailPlaylist != null) {
            val currentData = state.algorithmicPlaylists.find { it.first.id == source.id }
            if (currentData != null) {
                cachedDetailPlaylistData = currentData
            }
        }
        PlaylistDetailSheet(
            title = stringResource(source.nameRes),
            icon = source.icon,
            songs = (detailPlaylist ?: cachedDetailPlaylistData)!!.second,
            onStart = { onSongClick(it) },
            onDismiss = {
                detailPlaylist = null
                cachedDetailPlaylistData = null
            },
            algorithmicSourceId = source.id
        )
    }

    // For user playlist: use cached data if available, otherwise new data
    (detailUserPlaylist?.let { playlist ->
        val currentSongs = state.songs.filter { playlist.items.contains(it.path) }
        playlist to currentSongs
    } ?: cachedDetailUserPlaylistData)?.let { (playlist, songs) ->
        // Update cache with current state data
        if (detailUserPlaylist != null) {
            val currentSongs = state.songs.filter { playlist.items.contains(it.path) }
            cachedDetailUserPlaylistData = playlist to currentSongs
        }
        PlaylistDetailSheet(
            title = playlist.name,
            icon = "🎵",
            songs = songs,
            onStart = { onSongClick(it) },
            onDismiss = {
                detailUserPlaylist = null
                cachedDetailUserPlaylistData = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AllStatsBottomSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val allSessions = remember { MediaAnalyticsManager.getSessions() }

    val musicGroups = remember(allSessions) {
        allSessions.filter { it.type == "music" }
            .groupBy { it.label }
            .map { (label, sessions) ->
                Triple(
                    label,
                    sessions.sumOf { it.listenedMs },
                    sessions.size
                )
            }
            .sortedByDescending { it.second }
    }

    val podcastGroups = remember(allSessions) {
        allSessions.filter { it.type == "podcast" }
            .groupBy { it.label }
            .map { (label, sessions) ->
                Triple(
                    label,
                    sessions.sumOf { it.listenedMs },
                    sessions.size
                )
            }
            .sortedByDescending { it.second }
    }

    val spotifyGroups = remember(allSessions) {
        allSessions.filter { it.source == "spotify" || it.source == "spotify_pc" }
            .groupBy { it.label }
            .map { (label, sessions) ->
                Triple(
                    label,
                    sessions.sumOf { it.listenedMs },
                    sessions.size
                )
            }
            .sortedByDescending { it.second }
    }

    val totalMusicMs = remember(musicGroups) { musicGroups.sumOf { it.second } }
    val totalPodcastMs = remember(podcastGroups) { podcastGroups.sumOf { it.second } }
    val totalSpotifyMs = remember(spotifyGroups) { spotifyGroups.sumOf { it.second } }
    val maxMusicMs = remember(musicGroups) { musicGroups.firstOrNull()?.second ?: 1L }
    val maxPodcastMs = remember(podcastGroups) { podcastGroups.firstOrNull()?.second ?: 1L }
    val maxSpotifyMs = remember(spotifyGroups) { spotifyGroups.firstOrNull()?.second ?: 1L }

    var selectedTab by remember { mutableIntStateOf(0) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BgSurface,
        contentWindowInsets = { WindowInsets(0) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.statistiken),
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    pluralStringResource(R.plurals.sessions, allSessions.size, allSessions.size),
                    color = TextTertiary,
                    fontSize = 12.sp
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(BgCard)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem("🎵", formatDuration(totalMusicMs), stringResource(R.string.musik_gesamt))
                StatDivider()
                StatItem("🎙️", formatDuration(totalPodcastMs), stringResource(R.string.podcast_gesamt))
                StatDivider()
                StatItem("🟢", formatDuration(totalSpotifyMs), "davon Spotify")
                StatDivider()
                StatItem(
                    "⏰",
                    formatDuration(totalMusicMs + totalPodcastMs),
                    stringResource(R.string.alles)
                )
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(BgCard)
                    .padding(4.dp)
            ) {
                listOf(stringResource(R.string.musik_2), stringResource(R.string.podcasts_3), "Spotify").forEachIndexed { i, label ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selectedTab == i) AccentViolet else Color.Transparent)
                            .clickable { selectedTab = i }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            color = if (selectedTab == i) TextPrimary else TextTertiary,
                            fontSize = 13.sp,
                            fontWeight = if (selectedTab == i) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            val items = when (selectedTab) {
                0 -> musicGroups
                1 -> podcastGroups
                else -> spotifyGroups
            }
            val maxMs = when (selectedTab) {
                0 -> maxMusicMs
                1 -> maxPodcastMs
                else -> maxSpotifyMs
            }

            if (items.isEmpty()) {
                EmptyState(
                    icon = when (selectedTab) {
                        0 -> "🎵"
                        1 -> "🎙️"
                        else -> "🟢"
                    },
                    title = stringResource(R.string.noch_keine_daten),
                    subtitle = stringResource(R.string.spiele_erst_etwas_ab)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp),
                    contentPadding = PaddingValues(
                        horizontal = 20.dp,
                        vertical = 4.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(items) { (label, totalMs, sessionCount) ->
                        StatsRow(
                            label = label,
                            totalMs = totalMs,
                            sessionCount = sessionCount,
                            barFraction = (totalMs.toFloat() / maxMs).coerceIn(0f, 1f),
                            isMusic = selectedTab == 0,
                            icon = if (selectedTab == 2) "🟢" else null
                        )
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
        }
    }
}

@Composable
private fun StatsRow(
    label: String,
    totalMs: Long,
    sessionCount: Int,
    barFraction: Float,
    isMusic: Boolean,
    icon: String? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isMusic) generateAccentColor(label)
                        else generateShowGradient(label.hashCode())
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    icon ?: if (isMusic) label.take(1).uppercase() else "🎙️",
                    fontSize = if (icon == null && isMusic) 15.sp else 14.sp,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    pluralStringResource(R.plurals.sessions, sessionCount, sessionCount),
                    color = TextTertiary,
                    fontSize = 11.sp
                )
            }
            Text(
                formatDuration(totalMs),
                color = AccentViolet,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(CircleShape)
                .background(BgSurface)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(barFraction)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(
                        Brush.horizontalGradient(
                            listOf(AccentViolet, AccentViolet.copy(alpha = 0.5f))
                        )
                    )
            )
        }
    }
}

@Composable
private fun SearchTab(
    modifier: Modifier,
    query: String,
    results: SearchResults,
    onQueryChange: (String) -> Unit,
    onSongClick: (MediaPlayerService.Song) -> Unit,
    onSongLongClick: (MediaPlayerService.Song) -> Unit,
    onEpisodeClick: (PodcastEpisode) -> Unit,
    onShowClick: (PodcastShow) -> Unit
) {
    Column(modifier = modifier.fillMaxSize()) {
        SearchBar(query = query, onQueryChange = onQueryChange)

        val hasResults =
            results.songs.isNotEmpty() || results.episodes.isNotEmpty() || results.shows.isNotEmpty()

        if (query.length < 2) {
            EmptyState(
                icon = "🔍",
                title = stringResource(R.string.suche_starten),
                subtitle = stringResource(R.string.gib_mindestens_2_zeichen_ein)
            )
        } else if (!hasResults) {
            EmptyState(
                icon = "😕",
                title = stringResource(R.string.keine_ergebnisse),
                subtitle = stringResource(R.string.nichts_fur_gefunden, query)
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (results.songs.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.songs_3, results.songs.size)) }
                    items(results.songs) { song ->
                        SongRow(
                            song = song,
                            onClick = { onSongClick(song) },
                            onLongClick = { onSongLongClick(song) })
                    }
                }
                if (results.episodes.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.podcast_folgen, results.episodes.size)) }
                    items(results.episodes) { ep ->
                        PodcastEpisodeRow(
                            ep,
                            "",
                            onClick = { onEpisodeClick(ep) },
                            onLongClick = {})
                    }
                }
                if (results.shows.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.shows_2, results.shows.size)) }
                    items(results.shows) { show ->
                        ShowCard(
                            show = show,
                            episodeCount = 0,
                            unheard = 0,
                            completionFraction = 0f,
                            expanded = false,
                            episodes = emptyList(),
                            onToggle = { onShowClick(show) },
                            onEpisodeClick = {})
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryTab(
    modifier: Modifier = Modifier,
    state: MediaUiState,
    onEpisodeClick: (PodcastEpisode) -> Unit,
    onRefresh: () -> Unit
) {
    var expandedShowId by remember { mutableStateOf<String?>(null) }
    var showAssignTarget by remember { mutableStateOf<PodcastEpisode?>(null) }
    val grouped = remember(state.episodes, state.shows) {
        PodcastShowManager.groupEpisodesIntoShows(state.episodes)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Row {
                SectionHeader(stringResource(R.string.podcast_shows_3))
                Button(onClick = { PodcastShowManager.resetToDefault() }) {
                    Text(stringResource(R.string.reset_2))
                }
            }
        }
        items(state.shows) { show ->
            val eps = grouped[show] ?: emptyList()
            val stats = PodcastShowManager.getShowStats(show.id, eps)
            val totalListenedMs = remember(show.name) {
                MediaAnalyticsManager.getTotalListenedMsForShow(show.name)
            }
            ShowCard(
                show = show,
                episodeCount = stats.totalEpisodes,
                unheard = stats.unheardEpisodes,
                completionFraction = stats.completionFraction,
                totalListenedMs = totalListenedMs,
                expanded = expandedShowId == show.id,
                episodes = eps,
                onToggle = {
                    expandedShowId = if (expandedShowId == show.id) null else show.id
                },
                onEpisodeClick = { ep ->
                    onEpisodeClick(ep)
                },
                onEpisodeLongClick = { ep -> showAssignTarget = ep }
            )
        }
    }
    showAssignTarget?.let { ep ->
        ShowAssignBottomSheet(
            episode = ep,
            shows = state.shows,
            onAssign = { show ->
                PodcastShowManager.assignPattern(ep.title, show.name)
                showAssignTarget = null
                onRefresh()
            },
            onCreateShow = { newName ->
                val newShow = PodcastShowManager.createShow(newName)
                PodcastShowManager.assignPattern(ep.title, newShow.name)
                showAssignTarget = null
                onRefresh()
            },
            onDismiss = { showAssignTarget = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MusicTab(
    modifier: Modifier,
    state: MediaUiState,
    onSongClick: (MediaPlayerService.Song) -> Unit,
    onPlaylistCreated: () -> Unit,
    viewModel: MediaViewModel
) {
    var showCreateSheet by remember { mutableStateOf(false) }
    var selectedSongs by remember { mutableStateOf<Set<String>>(emptySet()) }
    val context = LocalContext.current
    var detailPlaylist by remember {
        mutableStateOf<Pair<PlaylistSource, List<MediaPlayerService.Song>>?>(
            null
        )
    }
    var detailUserPlaylist by remember { mutableStateOf<MediaPlayerService.Playlist?>(null) }
    var playlistToDelete by remember { mutableStateOf<MediaPlayerService.Playlist?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            if (state.algorithmicPlaylists.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.fur_dich)) }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.algorithmicPlaylists) { (source, songs) ->
                            AlgorithmicPlaylistCard(
                                source = source,
                                songCount = songs.size,
                                onClick = {
                                    if (source.id == "favorites") {
                                        MediaPlayerService.toggleFavoritesMode(context)
                                    } else {
                                        detailPlaylist = source to songs
                                    }
                                }
                            )
                        }
                    }
                }
            }

            if (state.userPlaylists.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.meine_playlists_2, state.userPlaylists.size)) }
                items(state.userPlaylists) { playlist ->
                    val songCount = playlist.items.size
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { detailUserPlaylist = playlist }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    Brush.linearGradient(listOf(AccentViolet, AccentVioletDim))
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🎵", fontSize = 20.sp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                playlist.name,
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                pluralStringResource(R.plurals.songs_2, songCount, songCount),
                                color = TextTertiary,
                                fontSize = 11.sp
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(BgCard)
                                .clickable { playlistToDelete = playlist },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🗑", fontSize = 14.sp)
                        }
                    }
                }
            }

            if (state.songs.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.alle_songs_3, state.songs.size)) }
                items(state.songs) { song ->
                    val isSelected = selectedSongs.contains(song.path)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isSelected) AccentViolet.copy(alpha = 0.15f)
                                else Color.Transparent
                            )
                            .combinedClickable(
                                onClick = {
                                    if (selectedSongs.isNotEmpty()) {
                                        selectedSongs = if (isSelected)
                                            selectedSongs - song.path
                                        else
                                            selectedSongs + song.path
                                    } else {
                                        MediaPlayerService.playFromAllSongs(context, song.path)
                                    }
                                },
                                onLongClick = {
                                    selectedSongs = if (isSelected)
                                        selectedSongs - song.path
                                    else
                                        selectedSongs + song.path
                                }
                            )
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSelected) Brush.linearGradient(
                                        listOf(AccentViolet, AccentVioletDim)
                                    ) else generateAccentColor(song.name)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (isSelected) "✓" else song.name.take(1).uppercase(),
                                color = TextPrimary,
                                fontSize = if (isSelected) 20.sp else 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                song.name, color = TextPrimary, fontSize = 14.sp,
                                fontWeight = FontWeight.Medium, maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 20.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AnimatedVisibility(
                visible = selectedSongs.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(BgCard)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        pluralStringResource(R.plurals.ausgewahlt, selectedSongs.size, selectedSongs.size),
                        color = AccentViolet,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AnimatedVisibility(
                    visible = selectedSongs.isNotEmpty(),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(BgCard)
                            .clickable { selectedSongs = emptySet() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✕", fontSize = 18.sp, color = TextSecondary)
                    }
                }

                Box(
                    modifier = Modifier
                        .height(56.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(
                            Brush.horizontalGradient(listOf(AccentViolet, AccentVioletDim))
                        )
                        .clickable { showCreateSheet = true }
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "＋",
                            fontSize = 20.sp,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (selectedSongs.isNotEmpty()) stringResource(R.string.playlist_erstellen_2) else stringResource(R.string.neue_playlist),
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }

    detailPlaylist?.let { (source, songs) ->
        PlaylistDetailSheet(
            title = stringResource(source.nameRes),
            icon = source.icon,
            songs = songs,
            onStart = { onSongClick(it) },
            onDismiss = { detailPlaylist = null },
            algorithmicSourceId = source.id
        )
    }

    detailUserPlaylist?.let { playlist ->
        val songs = state.songs.filter { playlist.items.contains(it.path) }
        PlaylistDetailSheet(
            title = playlist.name,
            icon = "🎵",
            songs = songs,
            onStart = { onSongClick(it) },
            onDismiss = { detailUserPlaylist = null }
        )
    }

    playlistToDelete?.let { playlist ->
        AlertDialog(
            onDismissRequest = { playlistToDelete = null },
            containerColor = BgSurface,
            title = {
                Text(stringResource(R.string.playlist_loschen_2), color = TextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    stringResource(R.string.wird_unwiderruflich_geloscht, playlist.name),
                    color = TextSecondary
                )
            },
            confirmButton = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFB71C1C))
                        .clickable {
                            MediaPlayerService.deletePlaylist(context, playlist.id)
                            playlistToDelete = null
                            onPlaylistCreated()
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(stringResource(R.string.loschen), color = TextPrimary, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(BgCard)
                        .clickable { playlistToDelete = null }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(stringResource(R.string.abbrechen), color = TextSecondary)
                }
            }
        )
    }

    if (showCreateSheet) {
        CreatePlaylistBottomSheet(
            allSongs = state.songs,
            preselectedPaths = selectedSongs,
            onConfirm = { name, paths ->
                MediaPlayerService.createPlaylistWithSongs(context, name, paths.toList())
                selectedSongs = emptySet()
                showCreateSheet = false
                viewModel.viewModelScope.launch {
                    delay(500.milliseconds)
                    onPlaylistCreated()
                }
            },
            onDismiss = { showCreateSheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun CreatePlaylistBottomSheet(
    allSongs: List<MediaPlayerService.Song>,
    preselectedPaths: Set<String>,
    onConfirm: (name: String, paths: Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var playlistName by remember { mutableStateOf("") }
    var selectedPaths by remember { mutableStateOf(preselectedPaths) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredSongs = remember(allSongs, searchQuery) {
        if (searchQuery.length < 2) allSongs
        else allSongs.filter { it.name.lowercase().contains(searchQuery.lowercase()) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BgSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.playlist_erstellen_3),
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(pluralStringResource(R.plurals.songs_2, selectedPaths.size, selectedPaths.size), color = AccentViolet, fontSize = 13.sp)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(BgCard)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                if (playlistName.isEmpty()) {
                    Text(stringResource(R.string.playlist_name_platzhalter), color = TextTertiary, fontSize = 15.sp)
                }
                BasicTextField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(color = TextPrimary, fontSize = 15.sp),
                    singleLine = true
                )
            }

            Spacer(Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(BgCard)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                if (searchQuery.isEmpty()) {
                    Text(stringResource(R.string.songs_suchen), color = TextTertiary, fontSize = 14.sp)
                }
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp),
                    singleLine = true
                )
            }

            Spacer(Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(filteredSongs, key = { it.path }) { song ->
                    val isSelected = selectedPaths.contains(song.path)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isSelected) AccentViolet.copy(alpha = 0.15f) else BgCard
                            )
                            .clickable {
                                selectedPaths = if (isSelected)
                                    selectedPaths - song.path
                                else
                                    selectedPaths + song.path
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .animateItem(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) Brush.linearGradient(
                                        listOf(AccentViolet, AccentVioletDim)
                                    ) else generateAccentColor(song.name)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (isSelected) "✓" else song.name.take(1).uppercase(),
                                color = TextPrimary,
                                fontSize = if (isSelected) 14.sp else 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            song.name,
                            color = TextPrimary,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (playlistName.isNotBlank() && selectedPaths.isNotEmpty())
                            Brush.horizontalGradient(listOf(AccentViolet, AccentVioletDim))
                        else
                            Brush.horizontalGradient(listOf(BgCard, BgCard))
                    )
                    .clickable {
                        if (playlistName.isNotBlank() && selectedPaths.isNotEmpty()) {
                            onConfirm(playlistName.trim(), selectedPaths)
                        }
                    }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    pluralStringResource(R.plurals.playlist_erstellen_songs, selectedPaths.size, selectedPaths.size),
                    color = if (playlistName.isNotBlank() && selectedPaths.isNotEmpty())
                        TextPrimary else TextTertiary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShowAssignBottomSheet(
    episode: PodcastEpisode,
    shows: List<PodcastShow>,
    onAssign: (PodcastShow) -> Unit,
    onCreateShow: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var newShowName by remember { mutableStateOf("") }
    var showCreateField by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BgSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding()
        ) {
            Text(
                stringResource(R.string.show_zuweisen),
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                episode.title,
                color = TextSecondary,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(16.dp))

            shows.filter { it.id != "unassigned" }.forEach { show ->
                val isCurrentShow = show.id == episode.showId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isCurrentShow) AccentVioletDim.copy(alpha = 0.4f) else BgCard)
                        .clickable { onAssign(show) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(generateShowGradient(show.accentColorSeed)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🎙️", fontSize = 16.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        show.name,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    if (isCurrentShow) {
                        Text(
                            "✓",
                            color = AccentViolet,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
            }

            Spacer(Modifier.height(8.dp))

            AnimatedVisibility(
                visible = !showCreateField,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(AccentViolet.copy(alpha = 0.15f))
                        .clickable { showCreateField = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "＋",
                        color = AccentViolet,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.neue_show_erstellen),
                        color = AccentViolet,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            AnimatedVisibility(
                visible = showCreateField,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(BgCard)
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        if (newShowName.isEmpty()) {
                            Text(stringResource(R.string.show_name_platzhalter), color = TextTertiary, fontSize = 15.sp)
                        }
                        BasicTextField(
                            value = newShowName,
                            onValueChange = { newShowName = it },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(color = TextPrimary, fontSize = 15.sp),
                            singleLine = true
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(BgCard)
                                .clickable { showCreateField = false; newShowName = "" }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(stringResource(R.string.abbrechen), color = TextSecondary, fontSize = 14.sp)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (newShowName.isNotBlank()) AccentViolet else BgCard)
                                .clickable {
                                    if (newShowName.isNotBlank()) onCreateShow(newShowName.trim())
                                }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                stringResource(R.string.erstellen_zuweisen),
                                color = if (newShowName.isNotBlank()) TextPrimary else TextTertiary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StatItem(icon: String, value: String, label: String, dimmed: Boolean = false) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.alpha(if (dimmed) 0.4f else 1f)
    ) {
        Text(icon, fontSize = 20.sp)
        Text(value, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(label, color = TextTertiary, fontSize = 11.sp)
    }
}

@Composable
private fun StatDivider() {
    Box(
        modifier = Modifier
            .height(40.dp)
            .width(1.dp)
            .background(BgCard)
    )
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = TextPrimary,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
private fun AlgorithmicPlaylistCard(
    source: PlaylistSource,
    songCount: Int,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(160.dp, 200.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    listOf(source.accentColor.copy(alpha = 0.8f), BgCard)
                )
            )
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(source.icon, fontSize = 36.sp)
            Column {
                Text(
                    stringResource(source.nameRes),
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(pluralStringResource(R.plurals.songs_2, songCount, songCount), color = TextSecondary, fontSize = 12.sp)
                Text(
                    stringResource(source.descriptionRes), color = TextTertiary, fontSize = 11.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongRow(
    song: MediaPlayerService.Song,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val sessionCount = remember(song.name) {
        MediaAnalyticsManager.getSessionsForLabel(song.name).size
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(generateAccentColor(song.name)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                song.name.take(1).uppercase(),
                color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                song.name, color = TextPrimary, fontSize = 14.sp,
                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            if (sessionCount > 0) {
                Text(stringResource(R.string.gehort, sessionCount), color = TextTertiary, fontSize = 11.sp)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PodcastEpisodeRow(
    episode: PodcastEpisode,
    showName: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(generateAccentColor(showName)),
                contentAlignment = Alignment.Center
            ) {
                Text("🎙️", fontSize = 20.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    episode.title,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row {
                    if (showName.isNotEmpty()) {
                        Text("$showName · ", color = TextTertiary, fontSize = 11.sp)
                    }
                    if (episode.durationMs > 0) {
                        Text(
                            formatDuration(episode.durationMs),
                            color = TextTertiary,
                            fontSize = 11.sp
                        )
                    }
                }
            }
            if (episode.isCompleted) Text("✓", color = Color(0xFF43A047), fontSize = 14.sp)
        }
        if (episode.savedPositionMs > 0 && episode.durationMs > 0 && !episode.isCompleted) {
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 56.dp)
            ) {
                LinearProgressIndicator(
                    progress = {
                        (episode.savedPositionMs.toFloat() / episode.durationMs).coerceIn(
                            0f,
                            1f
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(CircleShape),
                    color = AccentViolet,
                    trackColor = BgCard
                )
            }
        }
    }
}

@Composable
private fun ShowCard(
    show: PodcastShow,
    episodeCount: Int,
    unheard: Int,
    completionFraction: Float,
    totalListenedMs: Long = 0L,
    expanded: Boolean,
    episodes: List<PodcastEpisode>,
    onToggle: () -> Unit,
    onEpisodeClick: (PodcastEpisode) -> Unit,
    onEpisodeLongClick: ((PodcastEpisode) -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(BgSurface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(generateShowGradient(show.accentColorSeed)),
                contentAlignment = Alignment.Center
            ) {
                Text("🎙️", fontSize = 24.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    show.name,
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    pluralStringResource(
                        R.plurals.folgen,
                        episodeCount,
                        episodeCount,
                        (if (unheard > 0) stringResource(R.string.neu_suffix, unheard) else "") +
                                (if (totalListenedMs > 0) stringResource(
                                    R.string.gehort_suffix,
                                    formatDuration(totalListenedMs)
                                ) else "")
                    ),
                    color = TextSecondary, fontSize = 12.sp
                )
            }

            CompletionRing(fraction = completionFraction, size = 36.dp, color = AccentViolet)
            Spacer(Modifier.width(8.dp))
            Text(if (expanded) "▲" else "▼", color = TextTertiary, fontSize = 12.sp)
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(modifier = Modifier.animateContentSize()) {
                episodes.forEach { ep ->
                    PodcastEpisodeRow(
                        episode = ep,
                        showName = "",
                        onClick = { onEpisodeClick(ep) },
                        onLongClick = { onEpisodeLongClick?.invoke(ep) }
                    )
                }
                if (episodes.isEmpty()) {
                    Text(
                        stringResource(R.string.keine_folgen),
                        color = TextTertiary, fontSize = 13.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CompletionRing(fraction: Float, size: Dp, color: Color) {
    Canvas(modifier = Modifier.size(size)) {
        val strokeWidth = 4.dp.toPx()
        val radius = (this.size.minDimension - strokeWidth) / 2f
        val center = Offset(this.size.width / 2, this.size.height / 2)
        drawCircle(
            color = Color.White.copy(alpha = 0.1f), radius = radius, center = center,
            style = Stroke(strokeWidth)
        )
        if (fraction > 0f) {
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * fraction.coerceIn(0f, 1f),
                useCenter = false,
                style = Stroke(strokeWidth, cap = StrokeCap.Round),
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2)
            )
        }
    }
}

@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(BgSurface)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        if (query.isEmpty()) {
            Text(stringResource(R.string.songs_podcasts_shows_suchen), color = TextTertiary, fontSize = 15.sp)
        }
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(color = TextPrimary, fontSize = 15.sp),
            singleLine = true
        )
    }
}

@Composable
private fun EmptyState(icon: String, title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(icon, fontSize = 64.sp)
        Spacer(Modifier.height(16.dp))
        Text(title, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, color = TextTertiary, fontSize = 14.sp)
    }
}

@Composable
private fun SongAnalyticsContent(song: MediaPlayerService.Song) {
    val sessions = remember(song.path) {
        MediaAnalyticsManager.getSessionsForLabel(song.name)
            .sortedByDescending { it.startedAt }
            .take(20)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text(song.name, color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))

        if (sessions.isEmpty()) {
            Text(stringResource(R.string.noch_nicht_gespielt), color = TextTertiary, fontSize = 14.sp)
            Spacer(Modifier.height(32.dp))
            return@Column
        }

        val longestRepeatSession = sessions.filter { it.repeatCount > 1 }
            .maxByOrNull { it.listenedMs }

        longestRepeatSession?.let {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(NeonGreen.copy(alpha = 0.07f))
                    .border(1.dp, NeonGreen.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        stringResource(R.string.langste_dauerschleife),
                        color = NeonGreen.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(stringResource(R.string.am_stuck, it.repeatCount), color = TextTertiary, fontSize = 11.sp)
                }
                Text(
                    formatDuration(it.listenedMs),
                    color = NeonGreen,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        sessions.forEach { session ->
            val date = SimpleDateFormat("dd.MM · HH:mm", Locale.GERMANY)
                .format(Date(session.startedAt))
            val repeatStr =
                if (session.repeatCount > 1) stringResource(R.string.wiederholt, session.repeatCount) else ""
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(BgCard)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(date, color = TextSecondary, fontSize = 12.sp)
                Text(
                    formatDuration(session.listenedMs) + repeatStr,
                    color = TextTertiary, fontSize = 12.sp
                )
            }
            Spacer(Modifier.height(4.dp))
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun PodcastAnalyticsContent(episode: PodcastEpisode) {
    val showName = remember(episode.showId) {
        PodcastShowManager.getShow(episode.showId)?.name ?: "Sonstige"
    }
    val showTotalMs = remember(showName) {
        MediaAnalyticsManager.getTotalListenedMsForShow(showName)
    }
    val showSessions = remember(showName) {
        MediaAnalyticsManager.getSessions()
            .filter { it.type == "podcast" && it.label == showName }
            .sortedByDescending { it.startedAt }
            .take(10)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text(
            episode.title, color = TextPrimary, fontSize = 20.sp,
            fontWeight = FontWeight.Bold, maxLines = 2
        )
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.show_prefix, showName), color = TextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))

        if (episode.isCompleted) {
            Text(
                stringResource(R.string.vollstandig_angehort), color = Color(0xFF43A047),
                fontSize = 14.sp, fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(8.dp))
        }

        if (showTotalMs > 0) {
            Text(
                stringResource(R.string.show_insgesamt_gehort, formatDuration(showTotalMs)),
                color = TextSecondary, fontSize = 14.sp
            )
            Spacer(Modifier.height(16.dp))
        }

        if (showSessions.isNotEmpty()) {
            Text(
                stringResource(R.string.letzte_show_sessions), color = TextSecondary,
                fontSize = 14.sp, fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(8.dp))
            showSessions.forEach { session ->
                val date = SimpleDateFormat("dd.MM · HH:mm", Locale.GERMANY)
                    .format(Date(session.startedAt))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(BgCard)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(date, color = TextSecondary, fontSize = 12.sp)
                    Text(
                        formatDuration(session.listenedMs),
                        color = TextTertiary,
                        fontSize = 12.sp
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
        } else {
            Text(stringResource(R.string.noch_nicht_angehort), color = TextTertiary, fontSize = 14.sp)
        }
        Spacer(Modifier.height(32.dp))
    }
}

private fun playSong(
    context: Context,
    song: MediaPlayerService.Song
) {
    MediaPlayerService.playFromAllSongs(context, song.path)
}

private fun playEpisode(context: Context, ep: PodcastEpisode) {
    val intent = Intent(context, MediaPlayerService::class.java).apply {
        action = "SELECT_${ep.path.hashCode()}"
    }
    context.startService(intent)
}

private fun generateAccentColor(seed: String): Brush {
    val hash = seed.hashCode()
    val hue = ((hash and 0xFFFFFF) % 360).toFloat()
    val c1 = lerp(Color.hsl(hue, 0.6f, 0.25f), Color.Black, 0.2f)
    val c2 = Color.hsl((hue + 30) % 360, 0.5f, 0.35f)
    return Brush.linearGradient(listOf(c1, c2))
}

private fun generateShowGradient(seed: Int): Brush {
    val hue = ((seed and 0xFFFFFF) % 360).toFloat()
    return Brush.linearGradient(
        listOf(
            Color.hsl(hue, 0.7f, 0.3f),
            Color.hsl((hue + 60) % 360, 0.6f, 0.2f)
        )
    )
}

private fun formatDuration(ms: Long): String {
    val h = TimeUnit.MILLISECONDS.toHours(ms)
    val m = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    val s = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return if (h > 0) "${h}h ${m}m" else if (m > 0) "${m}m ${s}s" else "${s}s"
}

@Composable
fun NowPlayingBar(
    nowPlaying: NowPlayingState,
    onOpenFullscreen: () -> Unit,
    onPlayPause: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!nowPlaying.isActive) return

    val accent = generateBarAccentColor(nowPlaying.title)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .clickable { onOpenFullscreen() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(listOf(accent.copy(alpha = 0.25f), BgSurface))
                )
        )

        Box(
            modifier = Modifier
                .fillMaxWidth(nowPlaying.progress)
                .height(2.dp)
                .background(accent)
                .align(Alignment.TopStart)
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.4f)))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (nowPlaying.mode == "podcast") "🎙️" else nowPlaying.title.take(1)
                        .uppercase(),
                    fontSize = if (nowPlaying.mode == "podcast") 22.sp else 18.sp,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                MarqueeText(
                    text = nowPlaying.title,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    nowPlaying.subtitle,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.15f))
                    .clickable(onClick = onPlayPause),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (nowPlaying.isPlaying) "⏸️" else "▶️",
                    fontSize = 18.sp
                )
            }
        }
    }
}

@Composable
private fun FullscreenPlayerContent(
    nowPlaying: NowPlayingState,
    onDismiss: () -> Unit,
    onAction: (MediaViewModel.PlayerAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = generateBarAccentColor(nowPlaying.title)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        accent.copy(alpha = 0.95f),
                        BgDeep,
                        BgDeep
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(Modifier.height(0.1f.dp))
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(TextTertiary)
                    .clickable { onDismiss() }
            )

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(BgDeep.copy(alpha = 0.4f))
                    .padding(horizontal = 12.dp, vertical = 12.dp)
            ) {
                Text(
                    if (nowPlaying.mode == "podcast") stringResource(R.string.podcast_2) else stringResource(R.string.musik_3),
                    color = accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
            }

            Spacer(Modifier.height(40.dp))

            Box(
                modifier = Modifier
                    .size(260.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                accent,
                                accent.copy(alpha = 0.5f),
                                BgCard
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (nowPlaying.mode == "podcast") {
                    Text("🎙️", fontSize = 80.sp)
                } else {
                    Text(
                        nowPlaying.title.take(1).uppercase(),
                        fontSize = 96.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary.copy(alpha = 0.85f)
                    )
                }
            }

            Spacer(Modifier.height(40.dp))

            MarqueeText(
                text = nowPlaying.title,
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            Text(
                nowPlaying.subtitle,
                color = TextSecondary,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(32.dp))

            PlayerProgressRow(nowPlaying = nowPlaying)

            Spacer(Modifier.height(32.dp))

            if (nowPlaying.mode == "podcast") {
                PodcastControls(
                    isPlaying = nowPlaying.isPlaying,
                    accentColor = accent,
                    onAction = onAction
                )
            } else {
                MusicControls(
                    isPlaying = nowPlaying.isPlaying,
                    accentColor = accent,
                    onAction = onAction
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PlayerProgressRow(nowPlaying: NowPlayingState) {
    val posText = formatMsMTB(nowPlaying.positionMs)
    val durText = if (nowPlaying.durationMs > 0) formatMsMTB(nowPlaying.durationMs) else "--:--"

    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape)
                .background(BgCard)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(nowPlaying.progress)
                    .fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(
                            listOf(AccentViolet, AccentViolet.copy(alpha = 0.7f))
                        )
                    )
                    .clip(CircleShape)
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(posText, color = TextTertiary, fontSize = 12.sp)
            Text(durText, color = TextTertiary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun PodcastControls(
    isPlaying: Boolean,
    accentColor: Color,
    onAction: (MediaViewModel.PlayerAction) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ControlButton(
            label = "-15",
            size = 80.dp,
            bgColor = BgCard,
            onClick = { onAction(MediaViewModel.PlayerAction.REWIND_15) }
        )

        ControlButton(
            label = if (isPlaying) "⏸" else "▶",
            size = 80.dp,
            fontSize = 30.sp,
            bgColor = accentColor,
            onClick = { onAction(MediaViewModel.PlayerAction.PLAY_PAUSE) }
        )

        ControlButton(
            label = "+15",
            size = 80.dp,
            bgColor = BgCard,
            onClick = { onAction(MediaViewModel.PlayerAction.FORWARD_15) }
        )
    }
}

@Composable
private fun MusicControls(
    isPlaying: Boolean,
    accentColor: Color,
    onAction: (MediaViewModel.PlayerAction) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ControlButton(
            "⏮",
            size = 56.dp,
            fontSize = 22.sp,
            bgColor = BgCard
        ) { onAction(MediaViewModel.PlayerAction.PREVIOUS) }
        ControlButton(
            if (isPlaying) "⏸" else "▶",
            size = 80.dp, fontSize = 30.sp, bgColor = accentColor
        ) {
            onAction(MediaViewModel.PlayerAction.PLAY_PAUSE)
        }
        ControlButton(
            "⏭",
            size = 56.dp,
            fontSize = 22.sp,
            bgColor = BgCard
        ) { onAction(MediaViewModel.PlayerAction.NEXT) }
    }
}

@Composable
private fun ControlButton(
    label: String,
    sublabel: String? = null,
    size: Dp,
    fontSize: TextUnit = 16.sp,
    bgColor: Color,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(bgColor)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(label, fontSize = fontSize, color = TextPrimary, fontWeight = FontWeight.Bold)
        }
        if (sublabel != null) {
            Spacer(Modifier.height(4.dp))
            Text(sublabel, color = TextTertiary, fontSize = 11.sp)
        }
    }
}

@Composable
private fun MarqueeText(
    modifier: Modifier = Modifier,
    text: String,
    color: Color,
    fontSize: TextUnit,
    fontWeight: FontWeight = FontWeight.Normal
) {
    val scrollX = remember { Animatable(0f) }
    LaunchedEffect(text) {
        while (true) {
            scrollX.animateTo(
                -400f, animationSpec = tween(6000, easing = LinearEasing, delayMillis = 1500)
            )
            scrollX.snapTo(0f)
            delay(500.milliseconds)
        }
    }
    Box(modifier = modifier.fillMaxWidth()) {
        Text(
            text = text,
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistDetailSheet(
    title: String,
    icon: String,
    songs: List<MediaPlayerService.Song>,
    onStart: (MediaPlayerService.Song) -> Unit,
    onDismiss: () -> Unit,
    algorithmicSourceId: String? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BgSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(icon, fontSize = 28.sp)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(pluralStringResource(R.plurals.songs_2, songs.size, songs.size), color = TextTertiary, fontSize = 12.sp)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.horizontalGradient(listOf(AccentViolet, AccentVioletDim)))
                    .clickable {
                        if (algorithmicSourceId != null) {
                            MediaPlayerService.activateAlgorithmicPlaylist(
                                context,
                                algorithmicSourceId,
                                0
                            )
                        } else {
                            songs.firstOrNull()?.let { onStart(it) }
                        }
                        onDismiss()
                    }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "▶",
                        fontSize = 16.sp,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        stringResource(R.string.alle_abspielen),
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(songs) { song ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(BgCard)
                            .clickable {
                                if (algorithmicSourceId != null) {
                                    val idx = songs.indexOf(song)
                                    MediaPlayerService.activateAlgorithmicPlaylist(
                                        context,
                                        algorithmicSourceId,
                                        idx
                                    )
                                } else {
                                    onStart(song)
                                }
                                onDismiss()
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(generateAccentColor(song.name)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                song.name.take(1).uppercase(),
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            song.name, color = TextPrimary, fontSize = 13.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

private fun generateBarAccentColor(seed: String): Color {
    val hash = seed.hashCode()
    val hue = ((hash and 0xFFFFFF) % 360).toFloat()
    return Color.hsl(hue, 0.7f, 0.55f)
}

data class ListenSession(
    val label: String,
    val type: String,
    val listenedMs: Long,
    val startedAt: Long,
    val repeatCount: Int = 1,
    val source: String = "local"
)

data class SongAnalytics(val path: String)
data class PodcastAnalytics(val path: String, val showId: String)

object MediaAnalyticsManager {

    private const val PREFS_NAME = "media_analytics"
    const val KEY_SESSIONS = "sessions"
    private const val KEY_ANALYTICS_ENABLED = "analytics_enabled"

    val gson: Gson = GsonBuilder().create()
    var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        dedupExistingSessions()
    }

    fun isAnalyticsEnabled(): Boolean {
        return prefs?.getBoolean(KEY_ANALYTICS_ENABLED, true) ?: true
    }

    fun setAnalyticsEnabled(enabled: Boolean) {
        prefs?.edit { putBoolean(KEY_ANALYTICS_ENABLED, enabled) }
    }

    fun clearAllSessions() {
        prefs?.edit { remove(KEY_SESSIONS) }
    }

    fun addSession(session: ListenSession) {
        insertDeduped(session)
    }

    fun getSessions(): List<ListenSession> {
        val json = prefs?.getString(KEY_SESSIONS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<ListenSession>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun clearAll() {
        prefs?.edit { remove(KEY_SESSIONS) }
    }

    data class GlobalStats(
        val totalListenedMs: Long = 0,
        val listeningStreakDays: Int = 0,
        val totalSongsPlayed: Int = 0,
        val playedToday: Boolean = false
    )

    fun getGlobalStats(): GlobalStats {
        val sessions = getSessions()
        val totalMs = sessions.sumOf { it.listenedMs }
        val streak = calculateStreak(sessions)
        val songCount = sessions.count { it.type == "music" }
        val today = System.currentTimeMillis() / (24 * 60 * 60 * 1000L)
        val playedToday = sessions.any { it.startedAt / (24 * 60 * 60 * 1000L) == today }
        return GlobalStats(totalMs, streak, songCount, playedToday)
    }

    private fun calculateStreak(sessions: List<ListenSession>): Int {
        if (sessions.isEmpty()) return 0
        val playDays = sessions.map { it.startedAt / (24 * 60 * 60 * 1000L) }.toSet()
        val today = System.currentTimeMillis() / (24 * 60 * 60 * 1000L)
        val startDay = if (playDays.contains(today)) today else today - 1
        if (!playDays.contains(startDay)) return 0
        var streak = 0
        var day = startDay
        while (playDays.contains(day)) {
            streak++; day--
        }
        return streak
    }

    fun getSessionsForLabel(label: String): List<ListenSession> =
        getSessions().filter { it.label == label }

    fun getTotalListenedMsForShow(showName: String): Long =
        getSessions().filter { it.type == "podcast" && it.label == showName }
            .sumOf { it.listenedMs }

    fun rebuildSessions(sessions: List<ListenSession>) {
        prefs?.edit { putString(KEY_SESSIONS, gson.toJson(sessions)) }
    }

    fun mergeSessions(incoming: List<ListenSession>): Int {
        if (!isAnalyticsEnabled() || incoming.isEmpty()) return 0
        val existing = getSessions().toMutableList()
        val existingKeys = existing.mapTo(HashSet()) { Triple(it.source, it.label, it.startedAt) }
        var added = 0
        var changed = false
        for (session in incoming) {
            val key = Triple(session.source, session.label, session.startedAt)
            if (!existingKeys.add(key)) continue
            val dupIndex = existing.indexOfFirst { isSamePlayback(it, session) }
            if (dupIndex >= 0) {
                existing[dupIndex] = mergedPlayback(existing[dupIndex], session)
                changed = true
            } else {
                existing.add(session)
                added++
                changed = true
            }
        }
        if (changed) {
            prefs?.edit { putString(KEY_SESSIONS, gson.toJson(existing)) }
        }
        return added
    }

    const val SAME_PLAYBACK_WINDOW_MS = 60_000L

    fun normalizeLabel(label: String): String = label.trim().lowercase()

    fun isSpotifySource(source: String): Boolean =
        source == "spotify" || source.startsWith("spotify_pc")

    fun isSamePlayback(a: ListenSession, b: ListenSession): Boolean {
        if (!isSpotifySource(a.source) || !isSpotifySource(b.source)) return false
        if (normalizeLabel(a.label) != normalizeLabel(b.label)) return false
        return kotlin.math.abs(a.startedAt - b.startedAt) < SAME_PLAYBACK_WINDOW_MS
    }

    fun mergedPlayback(existing: ListenSession, incoming: ListenSession): ListenSession {
        return if (incoming.listenedMs >= existing.listenedMs) {
            incoming.copy(repeatCount = maxOf(existing.repeatCount, incoming.repeatCount))
        } else {
            existing.copy(repeatCount = maxOf(existing.repeatCount, incoming.repeatCount))
        }
    }

    fun insertDeduped(session: ListenSession): Boolean {
        if (!isAnalyticsEnabled()) return false
        val existing = getSessions().toMutableList()
        for (i in existing.indices) {
            if (isSamePlayback(existing[i], session)) {
                existing[i] = mergedPlayback(existing[i], session)
                prefs?.edit { putString(KEY_SESSIONS, gson.toJson(existing)) }
                return false
            }
        }
        existing.add(session)
        prefs?.edit { putString(KEY_SESSIONS, gson.toJson(existing)) }
        return true
    }

    fun dedupForStats(sessions: List<ListenSession>): List<ListenSession> {
        val result = mutableListOf<ListenSession>()
        for (session in sessions) {
            val dupIndex = result.indexOfFirst { isSamePlayback(it, session) }
            if (dupIndex >= 0) {
                result[dupIndex] = mergedPlayback(result[dupIndex], session)
            } else {
                result.add(session)
            }
        }
        return result
    }

    fun dedupExistingSessions(): Int {
        val existing = getSessions()
        if (existing.isEmpty()) return 0
        val deduped = dedupForStats(existing)
        if (deduped.size == existing.size) return 0
        prefs?.edit { putString(KEY_SESSIONS, gson.toJson(deduped)) }
        return existing.size - deduped.size
    }
}

data class MediaUiState(
    val songs: List<MediaPlayerService.Song> = emptyList(),
    val episodes: List<PodcastEpisode> = emptyList(),
    val shows: List<PodcastShow> = emptyList(),

    val songAnalytics: Map<String, SongAnalytics> = emptyMap(),
    val podcastAnalytics: Map<String, PodcastAnalytics> = emptyMap(),

    val userPlaylists: List<MediaPlayerService.Playlist> = emptyList(),
    val algorithmicPlaylists: List<Pair<PlaylistSource, List<MediaPlayerService.Song>>> = emptyList(),
    val globalStats: MediaAnalyticsManager.GlobalStats = MediaAnalyticsManager.GlobalStats(),
    val isLoading: Boolean = false,
    val searchQuery: String = "",
    val currentTab: MediaTab = MediaTab.HOME,
    val permissionGranted: Boolean? = null // null = noch nicht geprüft
)

enum class MediaTab { HOME, SEARCH, PODCASTS, MUSIC, DOWNLOADER, RECORDER }

data class SearchResults(
    val songs: List<MediaPlayerService.Song> = emptyList(),
    val episodes: List<PodcastEpisode> = emptyList(),
    val shows: List<PodcastShow> = emptyList()
)

class MediaViewModel(app: Application) : AndroidViewModel(app) {

    private var refreshJob: Job? = null
    private val _uiState = MutableStateFlow(MediaUiState(isLoading = true))
    val uiState: StateFlow<MediaUiState> = _uiState.asStateFlow()

    private val _nowPlaying = MutableStateFlow(NowPlayingState())
    val nowPlaying: StateFlow<NowPlayingState> = _nowPlaying.asStateFlow()

    val searchResults: StateFlow<SearchResults> = _uiState.map { state ->
        val q = state.searchQuery.lowercase().trim()
        if (q.length < 2) return@map SearchResults()
        SearchResults(
            songs = state.songs.filter { it.name.lowercase().contains(q) },
            episodes = state.episodes.filter {
                it.title.lowercase().contains(q) || it.path.lowercase().contains(q)
            },
            shows = state.shows.filter { it.name.lowercase().contains(q) }
        )
    }.stateIn(viewModelScope, SharingStarted.Lazily, SearchResults())

    init {
        MediaAnalyticsManager.init(app)
        PodcastShowManager.init(app)
        FavoritesPlaylist.setContext(app)
        // refresh() wird erst nach Permission-Check aus dem Composable aufgerufen
        // Poller startet über DisposableEffect in MediaTab (Sichtbarkeits-gekoppelt)
    }

    private fun hasAudioPermission(): Boolean {
        val ctx = getApplication<Application>()
        return ContextCompat.checkSelfPermission(
            ctx,
            Manifest.permission.READ_MEDIA_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun onPermissionResult(granted: Boolean) {
        _uiState.value = _uiState.value.copy(permissionGranted = granted)
        if (granted) refresh()
        else _uiState.value = _uiState.value.copy(isLoading = false)
    }

    fun checkAndRefresh() {
        val granted = hasAudioPermission()
        _uiState.value = _uiState.value.copy(permissionGranted = granted)
        if (granted) refresh()
        else _uiState.value = _uiState.value.copy(isLoading = false)
    }

    private var nowPlayingPollerJob: Job? = null

    fun startNowPlayingPoller() {
        if (nowPlayingPollerJob?.isActive == true) return
        nowPlayingPollerJob = viewModelScope.launch {
            while (isActive) {
                _nowPlaying.value = readNowPlayingFromPrefs()
                delay(1000.milliseconds)
            }
        }
    }

    fun stopNowPlayingPoller() {
        nowPlayingPollerJob?.cancel()
        nowPlayingPollerJob = null
    }

    override fun onCleared() {
        stopNowPlayingPoller()
    }

    fun refresh() {
        if (!hasAudioPermission()) {
            _uiState.value = _uiState.value.copy(permissionGranted = false, isLoading = false)
            return
        }
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val songs = withContext(Dispatchers.IO) { loadSongsFromMediaStore() }
            val rawEpisodes = withContext(Dispatchers.IO) { loadEpisodesFromMediaStore() }
            val shows = PodcastShowManager.getShows()
            val globalStats =
                withContext(Dispatchers.IO) { MediaAnalyticsManager.getGlobalStats() }

            val userPlaylists = withContext(Dispatchers.IO) { loadUserPlaylistsFromPrefs() }

            val episodes = rawEpisodes.map { ep ->
                val showId = PodcastShowManager.resolveShowForEpisode(ep.path, ep.title)
                ep.copy(showId = showId)
            }

            val algPlaylists = AlgorithmicPlaylistRegistry.all.map { source ->
                source to source.getSongs(songs)
            }.filter { (_, songs) -> songs.isNotEmpty() }

            _uiState.value = _uiState.value.copy(
                songs = songs,
                episodes = episodes,
                shows = shows,
                songAnalytics = emptyMap(),
                podcastAnalytics = emptyMap(),
                algorithmicPlaylists = algPlaylists,
                globalStats = globalStats,
                userPlaylists = userPlaylists,
                isLoading = false
            )
        }
    }

    fun setSearchQuery(q: String) {
        _uiState.value = _uiState.value.copy(searchQuery = q)
    }

    fun setTab(tab: MediaTab) {
        _uiState.value = _uiState.value.copy(currentTab = tab)
    }


    private fun loadSongsFromMediaStore(): List<MediaPlayerService.Song> {
        val songs = mutableListOf<MediaPlayerService.Song>()
        val cr: ContentResolver = getApplication<Application>().contentResolver
        val proj = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.TITLE
        )
        cr.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj, null, null,
            "${MediaStore.Audio.Media.DISPLAY_NAME} ASC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: continue
                val data = cursor.getString(dataCol) ?: continue
                val title = cursor.getString(titleCol)
                val norm = try {
                    URLDecoder.decode(data, "UTF-8").replace("\\", "/").lowercase()
                } catch (_: Exception) {
                    data.lowercase()
                }
                val inTabslify = norm.contains("/music/tabslify/", ignoreCase = true)
                if (inTabslify && !norm.contains("/podcasts/tabslify/")) {
                    val contentUri = Uri.withAppendedPath(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id.toString()
                    )
                    val displayName =
                        if (!title.isNullOrBlank() && title != "<unknown>") title else name.substringBeforeLast(
                            '.'
                        )
                    songs.add(MediaPlayerService.Song(contentUri, displayName, data))
                }
            }
        }
        return songs.sortedBy { it.name.lowercase() }
    }

    private fun loadEpisodesFromMediaStore(): List<PodcastEpisode> {
        val episodes = mutableListOf<PodcastEpisode>()
        val cr: ContentResolver = getApplication<Application>().contentResolver
        val podcastPrefs = getApplication<Application>().getSharedPreferences(
            "podcast_player_prefs",
            Context.MODE_PRIVATE
        )
        val proj = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DURATION
        )
        cr.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj, null, null,
            "${MediaStore.Audio.Media.DISPLAY_NAME} ASC"
        )?.use { cursor ->
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val durCol = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameCol) ?: continue
                val data = cursor.getString(dataCol) ?: continue
                val title = cursor.getString(titleCol)
                val duration = if (durCol >= 0) cursor.getLong(durCol) else 0L
                val norm = try {
                    URLDecoder.decode(data, "UTF-8").replace("\\", "/").lowercase()
                } catch (_: Exception) {
                    data.lowercase()
                }
                val inPodcasts = norm.contains("/podcasts/tabslify/")
                if (inPodcasts && (name.endsWith(".mp3") || name.endsWith(".m4a"))) {
                    val displayName =
                        if (!title.isNullOrBlank() && title != "<unknown>") title else name.substringBeforeLast(
                            '.'
                        )
                    val savedPos =
                        podcastPrefs.getLong("podcast_position_${data.hashCode()}", 0L)
                    val isCompleted =
                        podcastPrefs.getBoolean("podcast_completed_${data.hashCode()}", false)
                    episodes.add(
                        PodcastEpisode(
                            data,
                            displayName,
                            "unassigned",
                            null,
                            duration,
                            savedPos,
                            isCompleted
                        )
                    )
                }
            }
        }
        return episodes.sortedBy { it.title.lowercase() }
    }

    private fun readNowPlayingFromPrefs(): NowPlayingState {
        val app = getApplication<Application>()
        if (!MediaPlayerService.isServiceActive()) return NowPlayingState()

        val musicPrefs = app.getSharedPreferences("music_player_prefs", Context.MODE_PRIVATE)
        val podcastPrefs =
            app.getSharedPreferences("podcast_player_prefs", Context.MODE_PRIVATE)
        val mode = musicPrefs.getString("current_mode", "music") ?: "music"

        return if (mode == "music") {
            val songName = musicPrefs.getString("current_song_name", "") ?: ""
            val posMs = musicPrefs.getLong("music_position_ms", 0L)
            val durMs = musicPrefs.getLong("music_duration_ms", 0L)
            val progress = if (durMs > 0) (posMs.toFloat() / durMs).coerceIn(0f, 1f) else 0f
            NowPlayingState(
                isActive = songName.isNotEmpty(),
                mode = "music",
                title = songName,
                subtitle = app.getString(R.string.musik),
                isPlaying = musicPrefs.getBoolean("is_playing", false),
                positionMs = posMs,
                durationMs = durMs,
                progress = progress
            )
        } else {
            val path = podcastPrefs.getString("current_podcast_path", null)
            val pos = if (path != null) podcastPrefs.getLong(
                "podcast_position_${path.hashCode()}",
                0L
            ) else 0L
            val ep = _uiState.value.episodes.find { it.path == path }
            val show = _uiState.value.shows.find { it.id == ep?.showId }
            val progress =
                if (ep != null && ep.durationMs > 0) (pos.toFloat() / ep.durationMs).coerceIn(
                    0f,
                    1f
                ) else 0f
            NowPlayingState(
                isActive = ep != null,
                mode = "podcast",
                title = ep?.title ?: "",
                subtitle = show?.name ?: app.getString(R.string.podcast),
                isPlaying = musicPrefs.getBoolean("is_playing", false),
                positionMs = pos,
                durationMs = ep?.durationMs ?: 0L,
                progress = progress
            )
        }
    }

    fun playerAction(context: Context, action: PlayerAction) {
        when (action) {
            PlayerAction.PLAY_PAUSE -> {
                val np = _nowPlaying.value
                if (np.mode == "music") {
                    if (np.isPlaying) context.startService(
                        Intent(context, MediaPlayerService::class.java).apply {
                            this.action = "com.cloud.ACTION_MUSIC_PAUSE"
                        }
                    ) else MediaPlayerService.sendMusicPlayAction(context)
                } else {
                    if (np.isPlaying) context.startService(
                        Intent(context, MediaPlayerService::class.java).apply {
                            this.action = "com.cloud.ACTION_PODCAST_PAUSE"
                        }
                    ) else MediaPlayerService.sendPodcastPlayAction(context)
                }
            }

            PlayerAction.NEXT -> context.startService(
                Intent(context, MediaPlayerService::class.java).apply {
                    this.action = "com.cloud.ACTION_MUSIC_NEXT"
                }
            )

            PlayerAction.PREVIOUS -> context.startService(
                Intent(context, MediaPlayerService::class.java).apply {
                    this.action = "com.cloud.ACTION_MUSIC_PREVIOUS"
                }
            )

            PlayerAction.REWIND_15 -> MediaPlayerService.sendPodcastForwardAction(
                context,
                -15_000
            )

            PlayerAction.FORWARD_15 -> MediaPlayerService.sendPodcastForwardAction(
                context,
                15_000
            )
        }
    }

    private fun loadUserPlaylistsFromPrefs(): List<MediaPlayerService.Playlist> {
        val prefs = getApplication<Application>().getSharedPreferences(
            "music_player_prefs",
            Context.MODE_PRIVATE
        )
        val json = prefs.getString("playlists_json", null) ?: return emptyList()
        return try {
            json.split("\n---\n").filter { it.isNotBlank() }.mapNotNull { line ->
                val parts = line.split(":::", limit = 4)
                if (parts.size >= 3) {
                    val id = parts[0]
                    val name = parts[1]
                    val type = MediaPlayerService.PlaylistType.valueOf(parts[2])
                    val items = if (parts.size > 3 && parts[3].isNotEmpty())
                        parts[3].split("|~~|").toMutableList()
                    else mutableListOf()
                    MediaPlayerService.Playlist(id, name, type, items)
                } else null
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    enum class PlayerAction { PLAY_PAUSE, NEXT, PREVIOUS, REWIND_15, FORWARD_15 }
}

enum class PlaylistSourceType {
    ALGORITHMIC,

    @Suppress("unused")
    USER_CREATED
}

interface PlaylistSource {
    val id: String
    val nameRes: Int
    val descriptionRes: Int
    val type: PlaylistSourceType
    val icon: String
    val accentColor: Color
    fun getSongs(
        allSongs: List<MediaPlayerService.Song>
    ): List<MediaPlayerService.Song>
}


object RecentlyPlayedPlaylist : PlaylistSource {
    override val id = "recently_played"
    override val nameRes = R.string.zuletzt_gespielt
    override val descriptionRes = R.string.deine_letzten_20_songs
    override val type = PlaylistSourceType.ALGORITHMIC
    override val icon = "🕐"
    override val accentColor = Color(0xFF7C4DFF)
    override fun getSongs(allSongs: List<MediaPlayerService.Song>): List<MediaPlayerService.Song> {
        val lastPlayedMap = MediaAnalyticsManager.getSessions()
            .filter { it.type == "music" }
            .groupBy { it.label }
            .mapValues { (_, s) -> s.maxOf { it.startedAt } }
        return allSongs
            .filter { lastPlayedMap.containsKey(it.name) }
            .sortedByDescending { lastPlayedMap[it.name] ?: 0L }
            .take(20)
    }
}

object NeverPlayedPlaylist : PlaylistSource {
    override val id = "never_played"
    override val nameRes = R.string.noch_nie_gehort
    override val descriptionRes = R.string.songs_die_auf_ihre_chance
    override val type = PlaylistSourceType.ALGORITHMIC
    override val icon = "🌱"
    override val accentColor = Color(0xFF00BFA5)

    override fun getSongs(
        allSongs: List<MediaPlayerService.Song>
    ): List<MediaPlayerService.Song> {
        val playedLabels = MediaAnalyticsManager.getSessions()
            .filter { it.type == "music" }
            .map { it.label }
            .toSet()
        return allSongs.filter { !playedLabels.contains(it.name) }.shuffled().take(30)
    }
}

object MostPlayedPlaylist : PlaylistSource {
    override val id = "most_played"
    override val nameRes = R.string.meistgespielt
    override val descriptionRes = R.string.deine_absoluten_lieblinge
    override val type = PlaylistSourceType.ALGORITHMIC
    override val icon = "🔥"
    override val accentColor = Color(0xFFE53935)

    override fun getSongs(
        allSongs: List<MediaPlayerService.Song>
    ): List<MediaPlayerService.Song> {
        val playCounts = MediaAnalyticsManager.getSessions()
            .filter { it.type == "music" }
            .groupingBy { it.label }
            .eachCount()
        return allSongs
            .filter { (playCounts[it.name] ?: 0) > 0 }
            .sortedByDescending { playCounts[it.name] ?: 0 }
            .take(25)
    }
}

object RecentlyAddedPlaylist : PlaylistSource {
    override val id = "recently_added"
    override val nameRes = R.string.neu_hinzugefugt
    override val descriptionRes = R.string.in_den_letzten_24_stunden
    override val type = PlaylistSourceType.ALGORITHMIC
    override val icon = "✨"
    override val accentColor = Color(0xFF00BCD4)

    override fun getSongs(
        allSongs: List<MediaPlayerService.Song>
    ): List<MediaPlayerService.Song> {
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        return allSongs
            .filter { File(it.path).lastModified() >= cutoff }
            .sortedByDescending { File(it.path).lastModified() }
    }
}

/*object LateNightPlaylist : PlaylistSource {
    override val id = "late_night"
    override val name = "Nacht-Modus"
    override val description = "Deine Songs für die späten Stunden"
    override val type = PlaylistSourceType.ALGORITHMIC
    override val icon = "🌙"
    override val accentColor = Color(0xFF283593)

    override fun getSongs(
        allSongs: List<MediaPlayerService.Song>,
        analytics: Map<String, SongAnalytics>
    ): List<MediaPlayerService.Song> {
        val nightHours = setOf(22, 23, 0, 1, 2, 3, 4, 5)
        return allSongs
            .filter { song ->
                val a = analytics[song.path] ?: return@filter false
                val nightPlays = nightHours.sumOf { a.hourlyPlayCount[it] ?: 0 }
                nightPlays > 0
            }
            .sortedByDescending { song ->
                val a = analytics[song.path] ?: return@sortedByDescending 0
                nightHours.sumOf { a.hourlyPlayCount[it] ?: 0 }
            }
            .take(25)
    }
}*/

object FavoritesPlaylist : PlaylistSource {
    override val id = "favorites"
    override val nameRes = R.string.favoriten
    override val descriptionRes = R.string.deine_markierten_lieblinge
    override val type = PlaylistSourceType.ALGORITHMIC
    override val icon = "⭐"
    override val accentColor = Color(0xFFFDD835)

    private var _appContext: Context? = null
    fun setContext(ctx: Context) {
        _appContext = ctx.applicationContext
    }

    override fun getSongs(
        allSongs: List<MediaPlayerService.Song>
    ): List<MediaPlayerService.Song> {
        val prefs =
            _appContext?.getSharedPreferences("music_player_prefs", Context.MODE_PRIVATE)
        val favPaths: Set<String> = try {
            prefs?.getStringSet("favorite_songs", emptySet()) ?: emptySet()
        } catch (_: ClassCastException) {
            val raw = prefs?.getString("favorite_songs", null)
            val migrated = if (!raw.isNullOrBlank())
                raw.split("|").filter { it.isNotBlank() }.toSet()
            else
                emptySet()
            prefs?.edit { remove("favorite_songs") }
            prefs?.edit { putStringSet("favorite_songs", migrated) }
            migrated
        }

        if (allSongs.isNotEmpty()) {
            val validPaths = allSongs.mapTo(HashSet()) { it.path }
            val pruned = com.tabslify.core.objects.PrefsCleanup.pruneInvalid(favPaths) {
                validPaths.contains(it)
            }
            if (pruned.size != favPaths.size) {
                prefs?.edit { putStringSet("favorite_songs", pruned) }
                return allSongs.filter { pruned.contains(it.path) }
            }
        }

        return allSongs.filter { favPaths.contains(it.path) }
    }
}


object AlgorithmicPlaylistRegistry {
    val all: List<PlaylistSource> = listOf(
        RecentlyPlayedPlaylist,
        MostPlayedPlaylist,
        FavoritesPlaylist,
        RecentlyAddedPlaylist,
        NeverPlayedPlaylist
    )

    fun findById(id: String): PlaylistSource? = all.find { it.id == id }
}

data class PodcastShow(
    val id: String,
    val name: String,
    val description: String = "",
    val matchPatterns: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val accentColorSeed: Int = name.hashCode()
)

data class PodcastEpisode(
    val path: String,
    val title: String,
    val showId: String,
    val publishedAt: Long? = null,
    val durationMs: Long = 0,
    val savedPositionMs: Long = 0,
    val isCompleted: Boolean = false
)

object PodcastShowManager {

    private const val PREFS_NAME = "podcast_shows"
    private const val KEY_SHOWS = "shows_list"
    private const val KEY_PATTERNS = "pattern_mappings"

    private val defaultShows = listOf(
        PodcastShow(
            id = "unassigned",
            name = "Sonstige",
            description = "Alle nicht zugeordneten Folgen",
            matchPatterns = emptyList(),
            accentColorSeed = "unassigned".hashCode()
        )
    )

    private var prefs: SharedPreferences? = null
    private val shows = mutableListOf<PodcastShow>()

    private val extraPatterns = mutableMapOf<String, String>()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadShows()
        loadPatternMappings()
    }

    fun resetToDefault() {
        shows.clear()
        shows.addAll(defaultShows)

        extraPatterns.clear()

        saveShows()
        savePatternMappings()
    }

    fun getShows(): List<PodcastShow> = shows.toList()

    fun getShow(id: String): PodcastShow? = shows.find { it.id == id }

    fun createShow(name: String): PodcastShow {
        val id = name.lowercase().replace(" ", "_")
            .replace("[^a-z0-9_]".toRegex(), "") + "_${System.currentTimeMillis() % 10000}"
        val show = PodcastShow(id = id, name = name)
        shows.add(show)
        saveShows()
        return show
    }

    fun renameShow(oldName: String, newName: String): Boolean {
        val idx = shows.indexOfFirst { it.name.equals(oldName, ignoreCase = true) }
        if (idx < 0) return false
        shows[idx] = shows[idx].copy(name = newName)
        saveShows()
        return true
    }

    @Suppress("unused")
    fun deleteShow(id: String): Boolean {
        if (id == "unassigned") return false
        val removed = shows.removeIf { it.id == id }
        if (removed) saveShows()
        return removed
    }

    fun assignPattern(pattern: String, showName: String) {
        val show = shows.find { it.name.equals(showName, ignoreCase = true) }
            ?: createShow(showName)
        val pattern = pattern.replace('\u2019', '\'').replace('\u2018', '\'').replace('\u201C', '"')
            .replace('\u201D', '"')
        extraPatterns[pattern.lowercase()] = show.id
        val idx = shows.indexOf(show)
        if (idx >= 0) {
            val updated =
                show.copy(matchPatterns = (show.matchPatterns + pattern.lowercase()).distinct())
            shows[idx] = updated
        }
        saveShows()
        savePatternMappings()
    }

    @Suppress("unused")
    fun removePattern(pattern: String) {
        extraPatterns.remove(pattern.lowercase())
        shows.forEachIndexed { idx, show ->
            if (show.matchPatterns.contains(pattern.lowercase())) {
                shows[idx] = show.copy(matchPatterns = show.matchPatterns - pattern.lowercase())
            }
        }
        saveShows()
        savePatternMappings()
    }

    fun resolveShowForEpisode(path: String, title: String): String {
        val combined = ("$path $title").lowercase().replace('\u2019', '\'').replace('\u2018', '\'')
            .replace('\u201C', '"').replace('\u201D', '"')
        extraPatterns.forEach { (pattern, showId) ->
            if (combined.contains(pattern)) return showId
        }
        for (show in shows) {
            if (show.id == "unassigned") continue
            for (pattern in show.matchPatterns) {
                if (combined.contains(pattern.lowercase())) return show.id
            }
        }
        return "unassigned"
    }

    fun groupEpisodesIntoShows(episodes: List<PodcastEpisode>): Map<PodcastShow, List<PodcastEpisode>> {
        val result = mutableMapOf<PodcastShow, MutableList<PodcastEpisode>>()
        shows.forEach { show -> result[show] = mutableListOf() }
        episodes.forEach { ep ->
            val show = shows.find { it.id == ep.showId } ?: shows.find { it.id == "unassigned" }
            ?: return@forEach
            result[show]?.add(ep)
        }
        return result.filter { (show, eps) -> eps.isNotEmpty() || show.id == "unassigned" }
    }


    data class ShowStats(
        val totalEpisodes: Int,
        val completedEpisodes: Int,
        val unheardEpisodes: Int,
        val totalDurationMs: Long,
        val listenedDurationMs: Long,
        val completionFraction: Float
    )

    fun getShowStats(
        showId: String,
        episodes: List<PodcastEpisode>
    ): ShowStats {
        val showEps = episodes.filter { it.showId == showId }
        val completed = showEps.count { it.isCompleted }

        val unheard = showEps.count { !it.isCompleted && it.savedPositionMs == 0L }
        val totalDur = showEps.sumOf { it.durationMs }
        val fraction = if (showEps.isEmpty()) 0f
        else (completed.toFloat() / showEps.size).coerceIn(0f, 1f)
        return ShowStats(showEps.size, completed, unheard, totalDur, 0L, fraction)
    }


    private fun saveShows() {
        val sb = StringBuilder()
        shows.forEach { show ->
            sb.append("${show.id}|||${show.name}|||${show.description}|||")
            sb.append(show.matchPatterns.joinToString("~~"))
            sb.append("|||${show.createdAt}|||${show.accentColorSeed}")
            sb.append("\n---\n")
        }
        prefs?.edit { putString(KEY_SHOWS, sb.toString()) }
    }

    private fun loadShows() {
        shows.clear()
        val raw = prefs?.getString(KEY_SHOWS, null)
        if (raw.isNullOrEmpty()) {
            shows.addAll(defaultShows)
            saveShows()
            return
        }
        try {
            val entries = raw.split("\n---\n").filter { it.isNotBlank() }
            entries.forEach { entry ->
                val parts = entry.split("|||")
                if (parts.size >= 6) {
                    val patterns = if (parts[3].isBlank()) emptyList()
                    else parts[3].split("~~").filter { it.isNotBlank() }
                    shows.add(
                        PodcastShow(
                            id = parts[0],
                            name = parts[1],
                            description = parts[2],
                            matchPatterns = patterns,
                            createdAt = parts[4].toLongOrNull() ?: System.currentTimeMillis(),
                            accentColorSeed = parts[5].toIntOrNull() ?: parts[1].hashCode()
                        )
                    )
                }
            }
        } catch (_: Exception) {
            shows.clear()
            shows.addAll(defaultShows)
        }
        if (shows.none { it.id == "unassigned" }) {
            shows.add(defaultShows.last())
        }
    }

    private fun savePatternMappings() {
        val encoded = extraPatterns.entries.joinToString("\n") { "${it.key}|||${it.value}" }
        prefs?.edit { putString(KEY_PATTERNS, encoded) }
    }

    private fun loadPatternMappings() {
        extraPatterns.clear()
        val raw = prefs?.getString(KEY_PATTERNS, null) ?: return
        raw.lines().forEach { line ->
            val parts = line.split("|||")
            if (parts.size == 2) extraPatterns[parts[0]] = parts[1]
        }
    }
}

@Composable
fun AiResponseCard(
    entry: AiResponseEntry,
    onShowHistory: () -> Unit
) {
    NeonBox(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        borderWidth = 3.dp,
        onClick = onShowHistory,
        neonColors = listOf(NeonGreen, NeonBlue)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = entry.text,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                style = TextStyle(brush = Brush.linearGradient(listOf(NeonGreen, NeonBlue)))
            )

            Text(
                text = formatTimestamp(entry.timestamp),
                color = TextTertiary,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiResponseHistorySheet(
    context: Context,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var entries by remember { mutableStateOf(loadAllAiResponses(context)) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BgSurface,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        properties = ModalBottomSheetProperties()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.ai_verlauf),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    style = TextStyle(
                        brush = Brush.linearGradient(listOf(NeonGreen, NeonBlue))
                    ),
                    modifier = Modifier.weight(1f)
                )
                Text(
                    pluralStringResource(R.plurals.eintrage, entries.size, entries.size),
                    color = TextTertiary,
                    fontSize = 12.sp
                )
            }

            if (entries.isEmpty()) {
                EmptyState(
                    icon = "🤖",
                    title = stringResource(R.string.keine_eintrage),
                    subtitle = stringResource(R.string.noch_keine_ai_antworten_gespeichert)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 600.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(entries, key = { it.timestamp }) { entry ->
                        var showDeleteConfirm by remember { mutableStateOf(false) }

                        if (showDeleteConfirm) {
                            AlertDialog(
                                onDismissRequest = { showDeleteConfirm = false },
                                containerColor = BgSurface,
                                title = {
                                    Text(
                                        stringResource(R.string.eintrag_loschen),
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                text = {
                                    Text(
                                        entry.text.take(80) + if (entry.text.length > 80) "…" else "",
                                        color = TextSecondary,
                                        fontSize = 13.sp
                                    )
                                },
                                confirmButton = {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(0xFFB71C1C))
                                            .clickable {
                                                deleteAiResponse(context, entry.timestamp)
                                                entries = loadAllAiResponses(context)
                                                showDeleteConfirm = false
                                            }
                                            .padding(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            stringResource(R.string.loschen),
                                            color = TextPrimary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                },
                                dismissButton = {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(BgCard)
                                            .clickable { showDeleteConfirm = false }
                                            .padding(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        Text(stringResource(R.string.abbrechen), color = TextSecondary)
                                    }
                                }
                            )
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            NeonGreen.copy(alpha = 0.05f),
                                            NeonBlue.copy(alpha = 0.05f)
                                        )
                                    )
                                )
                                .border(
                                    width = 1.dp,
                                    brush = Brush.linearGradient(
                                        listOf(
                                            NeonGreen.copy(alpha = 0.4f),
                                            NeonBlue.copy(alpha = 0.4f)
                                        )
                                    ),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    formatTimestamp(entry.timestamp),
                                    color = TextTertiary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFB71C1C).copy(alpha = 0.15f))
                                        .clickable { showDeleteConfirm = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("🗑", fontSize = 13.sp)
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = entry.text,
                                fontSize = 13.sp,
                                lineHeight = 20.sp,
                                style = TextStyle(
                                    brush = Brush.linearGradient(
                                        listOf(
                                            NeonGreen.copy(alpha = 0.9f),
                                            NeonBlue.copy(alpha = 0.9f)
                                        )
                                    )
                                )
                            )
                        }
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
        }
    }
}

private fun formatTimestamp(ts: Long): String {
    val sdf = SimpleDateFormat("dd.MM.yyyy · HH:mm", Locale.GERMANY)
    return sdf.format(Date(ts))
}
