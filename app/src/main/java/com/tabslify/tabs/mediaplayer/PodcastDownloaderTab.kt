package com.tabslify.tabs.mediaplayer

import android.app.DownloadManager
import android.content.Context
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tabslify.R
import com.tabslify.core.functions.errorInsert
import com.tabslify.core.functions.fetchPodcastFeed
import com.tabslify.core.functions.isUnseenPodcastEpisode
import com.tabslify.core.functions.loadCachedNewEpisodes
import com.tabslify.core.functions.loadPodcastSeenAt
import com.tabslify.core.functions.markPodcastEpisodeDownloaded
import com.tabslify.core.functions.markPodcastSeen
import com.tabslify.core.functions.podcastCheckIsFresh
import com.tabslify.core.functions.podcastDownloadPrefs
import com.tabslify.core.functions.podcastFavPrefs
import com.tabslify.core.functions.readPodcastEpisodes
import com.tabslify.core.functions.runPodcastCheck
import com.tabslify.core.objects.Config
import com.tabslify.core.ui.AlertDialogTabslify
import com.tabslify.core.ui.FeedCard
import com.tabslify.services.MediaPlayerService
import com.tabslify.spotifydownloader_own.data.DownloadRepositoryImpl
import com.tabslify.spotifydownloader_own.domain.DownloadState
import com.tabslify.spotifydownloader_own.ui.DownloadViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.time.Instant

data class PodcastFeed(
    val title: String,
    val author: String,
    val image: String,
    val feedUrl: String,
)

data class Episode(
    val title: String,
    val audioUrl: String,
    val publishDate: Long = 0L,
)

data class SearchResult(
    val feed: PodcastFeed
)


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastTab() {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val appSignatureInvalidMsg = stringResource(R.string.app_signatur_konnte_nicht_validiert)
    val apiErrorMsg = stringResource(R.string.api_fehler_code)
    val noTitleMsg = stringResource(R.string.ohne_titel)
    val fileExistsMsg = stringResource(R.string.datei_existiert_bereits)
    val podcastDownloadingMsg = stringResource(R.string.podcast_wird_heruntergeladen)
    val downloadStartedMsg = stringResource(R.string.download_gestartet)

    val httpClient = remember {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        prettyPrint = true
                    }
                )
            }
        }
    }

    val engineInUse = remember { arrayOfNulls<HttpClient>(1) }

    val factory = remember(context, httpClient) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val repo = DownloadRepositoryImpl(httpClient, context.applicationContext as Context)
                engineInUse[0] = httpClient
                return DownloadViewModel(repo, context.applicationContext as Context, httpClient) as T
            }
        }
    }

    val vm: DownloadViewModel = viewModel(factory = factory)
    val downloadState by vm.downloadState.collectAsState()

    DisposableEffect(Unit) {
        onDispose {
            if (engineInUse[0] !== httpClient) {
                runCatching { httpClient.close() }
            }
        }
    }

    var query by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var hasSearched by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var expandedFeedUrl by remember { mutableStateOf<String?>(null) }
    var episodes by remember { mutableStateOf<Map<String, List<Episode>>>(emptyMap()) }
    var loadingEpisodes by remember { mutableStateOf<String?>(null) }
    var feedToUnfav by remember { mutableStateOf<PodcastFeed?>(null) }
    var newEpisodesState by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var seenAtState by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }

    LaunchedEffect(Unit) {
        val appContext = context.applicationContext
        val snapshot = withContext(Dispatchers.IO) {
            Triple(
                loadCachedNewEpisodes(appContext),
                loadPodcastSeenAt(appContext),
                podcastCheckIsFresh(appContext)
            )
        }
        newEpisodesState = snapshot.first
        seenAtState = snapshot.second
        if (snapshot.third) return@LaunchedEffect
        val found = try {
            withContext(Dispatchers.IO) { runPodcastCheck(appContext) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            errorInsert(
                "podcastCheckTab",
                "Hintergrundpruefung fehlgeschlagen: ${e.message}",
                Instant.now().toString(),
                "ERROR"
            )
            return@LaunchedEffect
        }
        newEpisodesState = found
    }

    val isUrl = remember(query) {
        android.util.Patterns.WEB_URL.matcher(query).matches() || query.startsWith("http")
    }

    suspend fun search(q: String) {
        if (q.isBlank()) return
        if (isUrl) {
            vm.startDownload(q)
            return
        }
        isSearching = true
        hasSearched = true
        error = null
        results = emptyList()
        try {
            val requestBody = JSONObject().apply {
                put("action", "podcastindex")
                put("payload", JSONObject().apply {
                    put(
                        "url",
                        "https://api.podcastindex.org/api/1.0/search/byterm?q=${
                            java.net.URLEncoder.encode(
                                q,
                                "UTF-8"
                            )
                        }"
                    )
                })
                put("apiKey", Config.userApiKey(context, "podcastindex"))
                put("apiSecret", Config.userApiKey(context, "podcastindex_secret"))
            }.toString()

            val conn = Config.openApiProxyConnection(context) ?: run {
                error = appSignatureInvalidMsg
                return
            }
            withContext(Dispatchers.IO) {
                conn.outputStream.use { it.write(requestBody.toByteArray(Charsets.UTF_8)) }
            }
            val responseCode = withContext(Dispatchers.IO) { conn.responseCode }
            if (responseCode != 200) {
                val errorText = withContext(Dispatchers.IO) {
                    conn.errorStream?.bufferedReader()?.readText() ?: "No error body"
                }
                android.util.Log.e(
                    "PodcastDownloaderTab",
                    "Podcast Index proxy failed: Code $responseCode, Body: $errorText"
                )
                error = apiErrorMsg.format(responseCode)
                return
            }
            val json = withContext(Dispatchers.IO) { conn.inputStream.bufferedReader().readText() }
            val arr = JSONObject(json).getJSONArray("feeds")
            val podcastResults = (0 until arr.length()).map { i ->
                val f = arr.getJSONObject(i)
                SearchResult(
                    PodcastFeed(
                        title = f.optString("title"),
                        author = f.optString("author").ifEmpty { f.optString("ownerName") },
                        image = f.optString("image"),
                        feedUrl = f.optString("url"),
                    )
                )
            }
            results = podcastResults
        } catch (e: Exception) {
            error = e.message
        } finally {
            isSearching = false
        }
    }

    suspend fun loadEpisodes(feedUrl: String) {
        if (episodes.containsKey(feedUrl)) return
        loadingEpisodes = feedUrl
        try {
            val list = readPodcastEpisodes(fetchPodcastFeed(feedUrl), 50)
                .map { Episode(it.title.ifEmpty { noTitleMsg }, it.audioUrl, it.publishedAt) }
                .sortedByDescending { it.publishDate }
            episodes = episodes + (feedUrl to list)
        } catch (e: Exception) {
            episodes = episodes + (feedUrl to emptyList())
            errorInsert(
                "loadPodcastEpisodes",
                "Fehler beim Laden der Episoden für $feedUrl: ${e.message}",
                Instant.now().toString(),
                "ERROR"
            )
        } finally {
            loadingEpisodes = null
        }
    }

    fun downloadEpisode(audioUrl: String, title: String, showName: String) {
        val safeTitle = title.replace(Regex("[/\\\\:*?\"<>|]"), "_")
        val filename = "$safeTitle.mp3"
        val subPath = "Tabslify/$filename"

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        val alreadyDone = dm.query(
            DownloadManager.Query().setFilterByStatus(DownloadManager.STATUS_SUCCESSFUL)
        )?.use { cursor ->
            val col = cursor.getColumnIndex(DownloadManager.COLUMN_TITLE)
            while (cursor.moveToNext()) {
                if (cursor.getString(col) == filename) return@use true
            }
            false
        } ?: false

        if (alreadyDone) {
            Toast.makeText(context, fileExistsMsg, Toast.LENGTH_SHORT).show()
            markPodcastEpisodeDownloaded(context.applicationContext, audioUrl)
            return
        }

        val request = DownloadManager.Request(audioUrl.toUri()).apply {
            setTitle(filename)
            setDescription(podcastDownloadingMsg)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_PODCASTS, subPath)
            setAllowedOverMetered(true)
            addRequestHeader("User-Agent", "Mozilla/5.0")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
        }
        val downloadId = dm.enqueue(request)

        podcastDownloadPrefs(context).edit {
            putString("pending_$downloadId", JSONObject().apply {
                put("safeTitle", safeTitle)
                put("showName", showName)
                put("audioUrl", audioUrl)
            }.toString())
        }

        Toast.makeText(context, downloadStartedMsg, Toast.LENGTH_SHORT).show()
    }

    fun streamEpisode(audioUrl: String) {
        MediaPlayerService.streamRemote(context, audioUrl)
    }

    val scope = rememberCoroutineScope()
    val prefs = podcastFavPrefs(context)

    fun loadFavs(): Map<String, PodcastFeed> {
        val raw = prefs.getString("favs", null) ?: return emptyMap()
        return try {
            val arr = org.json.JSONArray(raw)
            (0 until arr.length()).associate { i ->
                val o = arr.getJSONObject(i)
                val f = PodcastFeed(
                    o.getString("title"),
                    o.getString("author"),
                    o.getString("image"),
                    o.getString("feedUrl")
                )
                f.feedUrl to f
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun saveFavs(favs: Map<String, PodcastFeed>) {
        val arr = org.json.JSONArray()
        favs.values.forEach { f ->
            arr.put(JSONObject().apply {
                put("title", f.title); put("author", f.author)
                put("image", f.image); put("feedUrl", f.feedUrl)
            })
        }
        prefs.edit { putString("favs", arr.toString()) }
    }

    var favorites by remember { mutableStateOf(loadFavs()) }

    feedToUnfav?.let { feed ->
        AlertDialogTabslify(
            onConfirm = {
                favorites = favorites - feed.feedUrl
                saveFavs(favorites)
                feedToUnfav = null
            },
            onDismiss = { feedToUnfav = null },
            title = stringResource(R.string.aus_favoriten_entfernen),
            text = stringResource(R.string.wird_aus_deinen_lieblings_podcasts, feed.title),
            confirmText = stringResource(R.string.entfernen)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it; if (query.isEmpty()) {
                results = emptyList(); isSearching = false
            }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            placeholder = { Text(stringResource(R.string.podcast_suchen_oder_url_eingeben)) },
            singleLine = true,
            trailingIcon = {
                if (isSearching) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = {
                        keyboard?.hide()
                        scope.launch { search(query) }
                    }) {
                        Icon(Icons.Default.Search, contentDescription = null)
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                keyboard?.hide()
                scope.launch { search(query) }
            }),
            shape = RoundedCornerShape(12.dp),
        )

        error?.let {
            if (!hasSearched && !query.isNotBlank()) {
                Text(
                    stringResource(R.string.fehler_msg, it),
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }

        if (isUrl) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Button(
                    onClick = { vm.startDownload(query) },
                    enabled = downloadState is DownloadState.Idle || downloadState is DownloadState.Success || downloadState is DownloadState.Error
                ) {
                    Text(stringResource(R.string.download))
                }

                Spacer(modifier = Modifier.height(32.dp))

                when (val state = downloadState) {
                    is DownloadState.Idle -> Text(stringResource(R.string.url_eingeben_und_download_starten))
                    is DownloadState.Searching -> CircularProgressIndicator()
                    is DownloadState.Downloading -> {
                        val progress = state.progress
                        LinearProgressIndicator(progress = { progress / 100f })
                        Text(stringResource(R.string.downloading, progress))
                    }

                    is DownloadState.Converting -> {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.converting_to_mp3))
                    }

                    is DownloadState.Success -> Text(stringResource(R.string.download_complete))
                    is DownloadState.Error -> Text(
                        stringResource(R.string.fehler_msg, state.message),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        } else if (results.isEmpty() && !isSearching) {
            if (hasSearched && query.isNotBlank()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🔍", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stringResource(R.string.keine_podcast_shows_gefunden), color = Color.White.copy(0.5f))
                        Text(
                            stringResource(R.string.versuche_es_mit_einem_anderen),
                            fontSize = 12.sp,
                            color = Color.White.copy(0.3f)
                        )
                    }
                }
            } else if (favorites.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.podcast_suchen_oder_url_zum),
                        color = Color.White.copy(0.5f),
                        modifier = Modifier.fillMaxSize(),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Text(
                            stringResource(R.string.lieblings_podcasts), fontSize = 13.sp, color = Color(0xFF7A7880),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    items(favorites.values.toList()) { feed ->
                        val isExpanded = expandedFeedUrl == feed.feedUrl
                        val feedEpisodes = episodes[feed.feedUrl]
                        val seenAt = seenAtState[feed.feedUrl] ?: 0L
                        val newAudioUrls = remember(newEpisodesState, feed.title, seenAt) {
                            newEpisodesState
                                .filter { it.optString("showName") == feed.title }
                                .filter { isUnseenPodcastEpisode(it, seenAt) }
                                .map { it.optString("audioUrl") }.toSet()
                        }
                        val hasNew = newAudioUrls.isNotEmpty() && !isExpanded

                        Box {
                            FeedCard(
                                feed = feed, isExpanded = isExpanded, feedEpisodes = feedEpisodes,
                                loadingEpisodes = loadingEpisodes, isFavorite = true,
                                onToggleExpand = {
                                    if (isExpanded) expandedFeedUrl = null
                                    else {
                                        expandedFeedUrl = feed.feedUrl
                                        scope.launch { loadEpisodes(feed.feedUrl) }
                                        val seenMark = newEpisodesState
                                            .filter { it.optString("showName") == feed.title }
                                            .maxOfOrNull { it.optLong("publishedAt") }
                                            ?: System.currentTimeMillis()
                                        markPodcastSeen(
                                            context.applicationContext,
                                            feed.feedUrl,
                                            seenMark
                                        )
                                        seenAtState = seenAtState + (feed.feedUrl to seenMark)
                                    }
                                },
                                onToggleFav = { feedToUnfav = feed },
                                onDownload = { url, title ->
                                    downloadEpisode(
                                        url,
                                        title,
                                        feed.title
                                    )
                                },
                                onStream = { url -> streamEpisode(url) },
                                newAudioUrls = newAudioUrls,
                            )
                            if (hasNew) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp)
                                        .size(12.dp)
                                        .background(
                                            Color.Red,
                                            androidx.compose.foundation.shape.CircleShape
                                        )
                                )
                            }
                        }
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(results) { result ->
                    val feed = result.feed
                    val isExpanded = expandedFeedUrl == feed.feedUrl
                    val feedEpisodes = episodes[feed.feedUrl]
                    FeedCard(
                        feed = feed,
                        isExpanded = isExpanded,
                        feedEpisodes = feedEpisodes,
                        loadingEpisodes = loadingEpisodes,
                        isFavorite = favorites.containsKey(feed.feedUrl),
                        onToggleExpand = {
                            if (isExpanded) expandedFeedUrl = null
                            else {
                                expandedFeedUrl =
                                    feed.feedUrl; scope.launch { loadEpisodes(feed.feedUrl) }
                            }
                        },
                        onToggleFav = {
                            if (favorites.containsKey(feed.feedUrl)) feedToUnfav = feed
                            else {
                                favorites = favorites + (feed.feedUrl to feed)
                                saveFavs(favorites)
                            }
                        },
                        onDownload = { url, title -> downloadEpisode(url, title, feed.title) },
                        onStream = { url -> streamEpisode(url) }
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}
