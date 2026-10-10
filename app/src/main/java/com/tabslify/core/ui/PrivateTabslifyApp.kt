package com.tabslify.core.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Activity.RESULT_OK
import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.BatteryManager
import android.os.Environment
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsetsController
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.Black
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.tabslify.R
import com.tabslify.core.TabNavigationViewModel
import com.tabslify.core.objects.Config
import com.tabslify.core.objects.Config.cms
import com.tabslify.core.objects.FavoriteManager
import com.tabslify.core.objects.prvt
import com.tabslify.core.objects.tNotify
import com.tabslify.privatetabslifyapp.FileIcon
import com.tabslify.privatetabslifyapp.FullscreenImageDialog
import com.tabslify.privatetabslifyapp.fileExistsInDCIM
import com.tabslify.privatetabslifyapp.fileExistsLocallyWithSameSize
import com.tabslify.privatetabslifyapp.getFileNameFromUri
import com.tabslify.privatetabslifyapp.getLocalFileWithPath
import com.tabslify.privatetabslifyapp.getMimeType
import com.tabslify.privatetabslifyapp.isImageFile
import com.tabslify.privatetabslifyapp.isOnline
import com.tabslify.remote.DevRemoteDesktopTabContent
import com.tabslify.tabs.ApkmInstallerTabContent
import com.tabslify.tabs.BrowserTabContent
import com.tabslify.tabs.CalendarTabContent
import com.tabslify.tabs.ContactsRepository
import com.tabslify.tabs.ContactsTabContent
import com.tabslify.tabs.ContactsViewModel
import com.tabslify.tabs.DateCalculatorContent
import com.tabslify.tabs.GalleryTab
import com.tabslify.tabs.GmailTabContent
import com.tabslify.tabs.HeiseNewsTabContent
import com.tabslify.tabs.MovieDiscoveryTabContent
import com.tabslify.tabs.NotizenApp
import com.tabslify.tabs.OtherBucketViewer
import com.tabslify.tabs.PCManagerTab
import com.tabslify.tabs.QuickSettingsTabContent
import com.tabslify.tabs.WeatherTabContent
import com.tabslify.tabs.aitab.AITabContent
import com.tabslify.tabs.audiorecordertab.AudioRecorderTab
import com.tabslify.tabs.authenticator.AuthenticatorTab
import com.tabslify.tabs.exploretab.ExploreTabContent
import com.tabslify.tabs.fitnesstab.FitnessTabContent
import com.tabslify.tabs.focusguard.FocusGuardTabContent
import com.tabslify.tabs.mediaplayer.AiResponseHistorySheet
import com.tabslify.tabs.mediaplayer.MediaAnalyticsManager
import com.tabslify.tabs.mediaplayer.MediaTab
import com.tabslify.tabs.pendingApkmUri
import com.tabslify.tabs.school.VocabTab
import com.tabslify.tabs.virustotal.VirusTotalTabContent
import io.github.jan.supabase.storage.Storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

const val KEY_RECENT_TABS = "recent_tabs"
const val MAX_RECENT_TABS = 5

enum class MenuItem(
    @StringRes val titleRes: Int,
    val icon: String,
    val content: @Composable (setGesturesEnabled: (Boolean) -> Unit) -> Unit
) {
    PRIVATE_CLOUD(
        R.string.private_cloud,
        "☁️",
        {}
    ),
    AITAB(
        R.string.ai_tab,
        "☁️",
        { AITabContent() }
    ),
    BROWSER(
        R.string.browser,
        "🌐",
        {}
    ),
    QUICK(
        R.string.schnellzugriff,
        "⚡",
        { QuickSettingsTabContent() }
    ),
    GALLERY(
        R.string.gallerie,
        "🖼️",
        { setGesturesEnabled ->
            LaunchedEffect(Unit) { setGesturesEnabled(true) }
            GalleryTab()
        }
    ),
    AUTHENTICATOR(
        R.string.authenticator,
        "🔒",
        { }
    ),
    WEATHER(
        R.string.wetter,
        "🌡️",
        { }
    ),
    CONTACTS(
        R.string.kontakte,
        "🧍",
        {
            val context = LocalContext.current
            val repository = remember { ContactsRepository(context) }
            val viewModel = remember { ContactsViewModel(repository) }
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                if (permissions.all { it.value }) {
                    viewModel.loadContacts()
                }
            }

            LaunchedEffect(Unit) {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.READ_CONTACTS,
                        Manifest.permission.WRITE_CONTACTS
                    )
                )
            }

            ContactsTabContent(
                state = viewModel.state,
                onLoadContacts = { viewModel.loadContacts() },
                onSaveContact = { contact -> viewModel.saveContact(contact) },
                onDeleteContact = { id -> viewModel.deleteContact(id) }
            )
        }
    ),
    RECORDER(
        R.string.recorder,
        "🎙️",
        { AudioRecorderTab() }
    ),
    DATECALCULATOR(
        R.string.date_calculator,
        "📅",
        { DateCalculatorContent() }
    ),
    MOVIEDISCOVER(
        R.string.filme_discovery,
        "📺",
        { MovieDiscoveryTabContent() }
    ),
    NOTES(
        R.string.notizen,
        "📖",
        { NotizenApp() }
    ),
    MEDIAPLAYERTAB(
        R.string.media_player,
        "️️🎶",
        {}
    ),
    GMAIL(
        R.string.gmail,
        "️️✉️",
        { GmailTabContent() }
    ),
    Vocabs(
        R.string.vokabeln,
        "️️🏫️",
        {}
    ),
    EXPLORE(
        R.string.explore,
        "🗺️",
        { setGesturesEnabled ->
            ExploreTabContent(setGesturesEnabled)
        }
    ),
    CALENDAR(
        R.string.kalender,
        "📅",
        { CalendarTabContent() }
    ),
    REMOTEDESKTOP(
        R.string.remote_desktop,
        "🖥️",
        { DevRemoteDesktopTabContent() }
    ),
    HEISE_NEWS(
        R.string.heise_news,
        "📰",
        {}
    ),
    PC_MANAGER(
        R.string.pc_manager,
        "💻",
        { PCManagerTab() }
    ),
    APKM_INSTALLER(
        R.string.apkm_installer,
        "📦",
        {}
    ),
    VIRUSTOTAL(
        R.string.virustotal,
        "🛡️",
        { VirusTotalTabContent() }
    ),
    PUSHUPS(
        R.string.push_ups,
        "💪",
        { FitnessTabContent() }
    ),
    FOCUSGUARD(
        R.string.focusguard_tab,
        "🛡️",
        { FocusGuardTabContent() }
    ),
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivateTabslifyApp(
    storage: Storage,
    startTarget: String?,
    initialMenuItem: MenuItem,
    onMenuClick: (() -> Unit)? = null,
    viewModel: TabNavigationViewModel = viewModel(),
    svm: SharedViewModel = viewModel()
) {
    val context = LocalContext.current
    var selectedMenuItem by rememberSaveable {
        mutableStateOf(initialMenuItem)
    }
    var isFullScreen by rememberSaveable { mutableStateOf(false) }
    var isHelpOpen by rememberSaveable { mutableStateOf(false) }
    var webViewUrl by rememberSaveable { mutableStateOf("https://www.google.com") }
    var currentUrl by rememberSaveable { mutableStateOf(webViewUrl) }
    var webViewState by remember { mutableStateOf<WebView?>(null) }
    var isDesktopMode by rememberSaveable { mutableStateOf(false) }
    val navigationState by viewModel.navigationState.collectAsState()
    var gesturesEnabled by rememberSaveable { mutableStateOf(true) }
    var showBox by remember { mutableStateOf(false) }
    val overlayAlpha = animateFloatAsState(
        targetValue = if (showBox) 1f else 0f,
        animationSpec = tween(400),
        label = "overlay"
    )

    val wirdHeruntergeladenMsg = stringResource(R.string.wird_heruntergeladen)
    val downloadGestartMsg = stringResource(R.string.download_gestartet)

    LaunchedEffect(Unit) {
        svm.uiEvent.collect { value ->
            showBox = value
        }
    }

    val setGesturesEnabled: (Boolean) -> Unit = { enabled ->
        gesturesEnabled = enabled
    }

    LaunchedEffect(startTarget) {
        if (startTarget == "weather") {
            selectedMenuItem = MenuItem.WEATHER
        }
        if (startTarget == "focusguard") {
            selectedMenuItem = MenuItem.FOCUSGUARD
        }
    }

    val activity = context as? Activity
    LaunchedEffect(isFullScreen) {
        val controller = activity?.window?.insetsController ?: return@LaunchedEffect
        if (isFullScreen) {
            controller.hide(android.view.WindowInsets.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            (activity as? AppCompatActivity)?.supportActionBar?.hide()
        } else {
            controller.show(android.view.WindowInsets.Type.systemBars())
        }
    }

    LaunchedEffect(selectedMenuItem) {
        isHelpOpen = false
        gesturesEnabled = when (selectedMenuItem) {
            MenuItem.EXPLORE -> false
            MenuItem.REMOTEDESKTOP -> false
            MenuItem.GALLERY -> false
            else -> true
        }
    }

    LaunchedEffect(currentUrl) {
        saveLastUrl(context, currentUrl)
    }

    if (isFullScreen) {
        Box(modifier = Modifier.fillMaxSize()) {
            when (selectedMenuItem) {
                MenuItem.WEATHER -> WeatherTabContent(viewModel = viewModel)
                MenuItem.HEISE_NEWS -> HeiseNewsTabContent(viewModel = viewModel)
                MenuItem.BROWSER -> Unit

                MenuItem.PRIVATE_CLOUD -> {
                    MainTabslifyScreen(storage = storage)
                }

                MenuItem.AITAB -> {
                    AITabContent(svm = svm)
                }

                MenuItem.APKM_INSTALLER -> {
                    pendingApkmUri?.let { uri ->
                        ApkmInstallerTabContent(uri = uri, onDone = { pendingApkmUri = null; onMenuClick?.invoke() })
                    }
                }

                else -> selectedMenuItem.content(setGesturesEnabled)
            }
        }

        if (selectedMenuItem == MenuItem.BROWSER) {
            BackHandler(enabled = true) {
                if (webViewState?.canGoBack() == true) {
                    webViewState?.goBack()
                } else {
                    isFullScreen = false
                }
            }
        }
    } else {
        AppBackground(
            modifier = Modifier
                .then(
                    if (gesturesEnabled) {
                        Modifier.pointerInput(navigationState.canNavigateBack) {
                            var gestureHandled = false
                            detectHorizontalDragGestures(
                                onDragStart = { gestureHandled = false },
                                onHorizontalDrag = { _, dragAmount ->
                                    if (!gestureHandled && dragAmount > 20f) {
                                        gestureHandled = true
                                        if (navigationState.canNavigateBack) {
                                            viewModel.triggerBack()
                                        } else {
                                            onMenuClick?.invoke()
                                        }
                                    }
                                },
                                onDragEnd = { gestureHandled = false },
                                onDragCancel = { gestureHandled = false }
                            )
                        }
                    } else {
                        Modifier
                    }
                ),
            scrim = AppBgScrim.STRONG
        ) {
            Scaffold(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (selectedMenuItem == MenuItem.Vocabs) {
                            Modifier.background(
                                Brush.verticalGradient(
                                    listOf(BgDeep, Color.Transparent),
                                    endY = 800f
                                )
                            )
                        } else {
                            Modifier
                        }
                    ),
                topBar = {
                    if (selectedMenuItem != MenuItem.MEDIAPLAYERTAB) {
                        TopAppBar(
                            title = {
                                Text(
                                    text = "${selectedMenuItem.icon} ${stringResource(selectedMenuItem.titleRes)}",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            actions = {
                                IconButton(onClick = { isHelpOpen = true }) {
                                    Icon(
                                        imageVector = Icons.Default.QuestionMark,
                                        contentDescription = stringResource(R.string.hilfe_offnen),
                                        tint = Color.White
                                    )
                                }
                            },

                            navigationIcon = {
                                if (onMenuClick != null) {
                                    IconButton(onClick = { onMenuClick() }) {
                                        Icon(
                                            imageVector = Icons.Default.Menu,
                                            contentDescription = stringResource(R.string.menu_offnen),
                                            tint = Color.White
                                        )
                                    }
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Menu,
                                        contentDescription = stringResource(R.string.menu_offnen),
                                        tint = Color.White,
                                        modifier = Modifier
                                            .alpha(0f)
                                            .size(48.dp)
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = Color.Transparent,
                                scrolledContainerColor = Color.Transparent
                            ),
                            windowInsets = WindowInsets.statusBars
                        )
                        Box(
                            Modifier
                                .alpha(overlayAlpha.value)
                                .matchParentSize()
                                .background(Black.copy(0.6f))
                                .zIndex(10f)
                        )
                    }
                },
                containerColor = Color.Transparent
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .padding(
                            when (selectedMenuItem) {
                                MenuItem.Vocabs, MenuItem.MEDIAPLAYERTAB, MenuItem.HEISE_NEWS, MenuItem.AITAB -> PaddingValues(0.dp)
                                MenuItem.AUTHENTICATOR -> PaddingValues(top = paddingValues.calculateTopPadding())
                                else -> paddingValues
                            }
                        )
                        .zIndex(1000000f)
                ) {
                    when (selectedMenuItem) {
                        MenuItem.WEATHER -> WeatherTabContent(viewModel)
                        MenuItem.HEISE_NEWS -> HeiseNewsTabContent(
                            viewModel = viewModel,
                            paddingValues = paddingValues
                        )
                        MenuItem.AITAB -> AITabContent(
                            paddingValues = paddingValues
                        )
                        MenuItem.BROWSER -> BrowserTabContent(
                            url = webViewUrl,
                            onUrlChange = { webViewUrl = it },
                            onEnterFullScreen = { isFullScreen = true }
                        )

                        MenuItem.PRIVATE_CLOUD -> MainTabslifyScreen(storage = storage)

                        MenuItem.Vocabs -> VocabTab(paddingValues)

                        MenuItem.MEDIAPLAYERTAB -> MediaTab(onBack = { onMenuClick?.invoke() })

                        MenuItem.AUTHENTICATOR -> AuthenticatorTab()

                        MenuItem.APKM_INSTALLER -> pendingApkmUri?.let { uri ->
                            ApkmInstallerTabContent(uri = uri, onDone = { pendingApkmUri = null; onMenuClick?.invoke() })
                        }

                        else -> selectedMenuItem.content(setGesturesEnabled)
                    }
                    AnimatedVisibility(
                        visible = isHelpOpen,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                    ) {
                        HelpFrame(
                            selectedMenuItem = selectedMenuItem,
                            onDismiss = { isHelpOpen = false }
                        )
                    }
                }
            }
        }
    }

    if (isFullScreen) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            val downloadReceivers = remember { mutableSetOf<BroadcastReceiver>() }
            val webView = remember {
                WebView(context).apply {
                    webChromeClient = object : WebChromeClient() {
                        private var customView: View? = null
                        private var customViewCallback: CustomViewCallback? = null

                        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                            (context as? Activity)?.let { activity ->
                                val decor = activity.window.decorView as FrameLayout
                                decor.addView(
                                    view,
                                    FrameLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                )
                                activity.requestedOrientation =
                                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                customView = view
                                customViewCallback = callback

                                activity.window.insetsController?.apply {
                                    hide(android.view.WindowInsets.Type.systemBars())
                                    systemBarsBehavior =
                                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                                }
                            }
                        }

                        override fun onHideCustomView() {
                            (context as? Activity)?.let { activity ->
                                val decor = activity.window.decorView as FrameLayout
                                decor.removeView(customView)
                                customView = null
                                customViewCallback?.onCustomViewHidden()
                                customViewCallback = null
                                isFullScreen = false
                                activity.window.insetsController?.show(
                                    android.view.WindowInsets.Type.systemBars()
                                )
                                activity.requestedOrientation =
                                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                            }
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean = false

                        override fun onPageFinished(view: WebView?, url: String?) {
                            if (url != null) {
                                currentUrl = url
                                WebViewCookieBackup.saveCookies(context, url)
                            }
                        }
                    }

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true

                        loadsImagesAutomatically = true
                        blockNetworkLoads = false

                        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

                        useWideViewPort = true
                        loadWithOverviewMode = true

                        builtInZoomControls = true
                        displayZoomControls = false
                        setSupportZoom(true)

                        javaScriptCanOpenWindowsAutomatically = true
                        setSupportMultipleWindows(true)

                        cacheMode = WebSettings.LOAD_DEFAULT
                        mediaPlaybackRequiresUserGesture = false

                        userAgentString =
                            "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                    }

                    setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_BOUND, true)

                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(this, true)
                    cookieManager.flush()

                    WebViewCookieBackup.restoreCookies(context, webViewUrl)

                    setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->

                        val filename = URLUtil.guessFileName(url, contentDisposition, mimetype)

                        val request = DownloadManager.Request(url.toUri()).apply {
                            setMimeType(mimetype)
                            setTitle(filename)
                            setDescription(wirdHeruntergeladenMsg)
                            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                            setDestinationInExternalPublicDir(
                                Environment.DIRECTORY_DOWNLOADS,
                                filename
                            )
                            addRequestHeader("User-Agent", userAgent)
                            addRequestHeader(
                                "Cookie",
                                CookieManager.getInstance().getCookie(url) ?: ""
                            )
                        }

                        val dm =
                            context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                        val downloadId = dm.enqueue(request)

                        // BroadcastReceiver: wird gefeuert wenn Download fertig
                        val receiver = object : BroadcastReceiver() {
                            override fun onReceive(ctx: Context, intent: Intent) {
                                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                                if (id != downloadId) return

                                // Receiver sofort wieder abmelden
                                ctx.unregisterReceiver(this)
                                downloadReceivers.remove(this)

                                val query = DownloadManager.Query().setFilterById(downloadId)
                                val cursor = dm.query(query)

                                cursor?.use {
                                    if (it.moveToFirst()) {
                                        val statusCol =
                                            it.getColumnIndex(DownloadManager.COLUMN_STATUS)
                                        val status = it.getInt(statusCol)

                                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                                            val src = File(
                                                Environment.getExternalStoragePublicDirectory(
                                                    Environment.DIRECTORY_DOWNLOADS
                                                ),
                                                filename
                                            )
                                            val destDir = File(
                                                Environment.getExternalStoragePublicDirectory(
                                                    Environment.DIRECTORY_DOWNLOADS
                                                ),
                                                "tabslify/podcasts"
                                            )
                                            destDir.mkdirs()
                                            val dest = File(destDir, filename)

                                            val moved = src.renameTo(dest)

                                            Toast.makeText(
                                                ctx,
                                                if (moved) ctx.getString(R.string.gespeichert_in_tabslify_podcasts) else ctx.getString(R.string.download_ok_verschieben_fehlgeschlagen),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
                            }
                        }

                        context.registerReceiver(
                            receiver,
                            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                            Context.RECEIVER_NOT_EXPORTED
                        )
                        downloadReceivers += receiver

                        Toast.makeText(context, downloadGestartMsg, Toast.LENGTH_SHORT).show()
                    }

                    loadUrl(webViewUrl)
                }
            }
            DisposableEffect(webView, activity) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_STOP -> {
                            webView.onPause()
                            webView.pauseTimers()
                        }
                        Lifecycle.Event.ON_START -> {
                            webView.resumeTimers()
                            webView.onResume()
                        }
                        else -> Unit
                    }
                }
                (activity as? ComponentActivity)?.lifecycle?.addObserver(observer)
                onDispose {
                    (activity as? ComponentActivity)?.lifecycle?.removeObserver(observer)
                }
            }

            DisposableEffect(isFullScreen) {
                onDispose {
                    if (!isFullScreen) {
                        downloadReceivers.forEach { receiver ->
                            runCatching { context.unregisterReceiver(receiver) }
                        }
                        downloadReceivers.clear()
                        webView.stopLoading()
                        webView.onPause()
                        webView.destroy()
                        webViewState = null
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { webView },
                    update = { webViewState = it },
                    modifier = Modifier.fillMaxSize()
                )

                Button(
                    onClick = {
                        isDesktopMode = !isDesktopMode
                        val currentUrl =
                            webView.url ?: return@Button  // falls keine URL geladen ist

                        webView.settings.apply {
                            if (isDesktopMode) {
                                // Desktop-Modus (Windows 11 PC)
                                userAgentString =
                                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36"
                                useWideViewPort = true
                                loadWithOverviewMode = true
                            } else {
                                // Mobile-Modus (wie vorher)
                                userAgentString =
                                    "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                                useWideViewPort = true
                                loadWithOverviewMode = true
                            }
                        }

                        val desktopUA =
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36"

                        webView.evaluateJavascript(
                            """
            Object.defineProperty(navigator, 'userAgent', {
                value: '${if (isDesktopMode) desktopUA else webView.settings.userAgentString}',
                configurable: true
            });
            Object.defineProperty(navigator, 'platform', {
                value: '${if (isDesktopMode) "Win32" else "Linux armv8l"}',
                configurable: true
            });
        """.trimIndent(), null
                        )

                        webView.loadUrl(currentUrl)
                    },
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 16.dp)
                ) {
                    Icon(
                        imageVector = if (isDesktopMode) Icons.Filled.Laptop else Icons.Filled.Phone,
                        contentDescription = if (isDesktopMode) stringResource(R.string.desktop_modus) else stringResource(R.string.mobile_modus),
                        tint = Color.White
                    )
                }
            }

            BackHandler(enabled = true) {
                if (webViewState?.canGoBack() == true) {
                    webViewState?.goBack()
                } else {
                    isFullScreen = false
                }
            }
        }

        return
    }
}

@Composable
fun formatFileSize(sizeBytes: Long): String {
    val gb = stringResource(R.string.dateigrose_2f_gb)
    val mb = stringResource(R.string.dateigrose_2f_mb)
    val kb = stringResource(R.string.dateigrose_1f_kb)
    
    return when {
        sizeBytes >= 1_000_000_000 -> gb.format(sizeBytes / 1_000_000_000.0)
        sizeBytes >= 1_000_000 -> mb.format(sizeBytes / 1_000_000.0)
        else -> kb.format(sizeBytes / 1_000.0)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTabslifyScreen(storage: Storage) {
    val context = LocalContext.current
    
    val forbiddenMsg = stringResource(R.string.forbidden)
    val fehlerBeimLadenMsg = stringResource(R.string.fehler_beim_laden)
    val geloeschtMsg = stringResource(R.string.geloscht)
    val fehlerBeimLoeschenMsg = stringResource(R.string.fehler_beim_loschen)
    val hochgeladenMsg = stringResource(R.string.hochgeladen)
    val fehlerBeimUploadMsg = stringResource(R.string.fehler_beim_upload)
    val keineInternetverbindungMsg = stringResource(R.string.keine_internetverbindung)
    val bildGespeichertMsg = stringResource(R.string.bild_gespeichert)
    val fehlerMsgTemplate = stringResource(R.string.fehler_msg)
    val dateiGespeichertMsg = stringResource(R.string.datei_gespeichert)
    val listeAktualisiertMsg = stringResource(R.string.liste_aktualisiert)
    
    if (!prvt()) {
        Toast.makeText(context, forbiddenMsg, Toast.LENGTH_SHORT).show()
        return
    }
    data class TabslifyFileMeta(
        val name: String,
        val updatedAt: String,
        val size: Long,
    )

    var fileList by remember { mutableStateOf<List<TabslifyFileMeta>>(emptyList()) }
    var isUploading by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf<String?>(null) }
    var selectedFilter by remember { mutableStateOf("Alle") }
    var sortOption by remember { mutableStateOf("A-Z") }
    var showUploadProgress by remember { mutableStateOf(false) }
    var showDownloadProgress by remember { mutableStateOf(false) }
    var favoritesClickCount by remember { mutableIntStateOf(0) }
    var showOtherBucket by remember { mutableStateOf(false) }
    var favoriteFiles by remember {
        mutableStateOf(FavoriteManager.loadFavorites(context))
    }
    var expanded by remember { mutableStateOf(false) }
    var fullscreenImageDialogData by remember { mutableStateOf<Triple<String?, String, Long>?>(null) }
    var fileToDelete by remember { mutableStateOf<TabslifyFileMeta?>(null) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var pendingOtherBucket by remember { mutableStateOf(false) }
    val otherBucketLayer = rememberGraphicsLayer()
    var otherBucketBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    val otherBucketScale = remember { Animatable(0f) }
    val otherBucketAlpha = remember { Animatable(0f) }
    var shouldshow by remember { mutableStateOf(false) }

    BackHandler(true) { }

    suspend fun loadFiles() {
        try {
            val files = withContext(Dispatchers.IO) {
                storage.from(Config.SUPABASE_BUCKET).list()
            }

            val groupedFiles: List<TabslifyFileMeta> = files
                .filter { it.name != ".emptyFolderPlaceholder" }
                .groupBy { file ->
                    if (file.name.contains(".part")) {
                        file.name.substringBefore(".part")
                    } else {
                        file.name
                    }
                }
                .map { (baseName, chunks) ->
                    if (chunks.size > 1 || chunks.first().name.contains(".part")) {
                        val totalSize = chunks.sumOf { chunk ->
                            when (chunk.metadata?.get("size")) {
                                else -> 0L
                            }
                        }
                        val latestDate = chunks.mapNotNull { it.updatedAt }.maxOrNull()
                        val updatedAtString = latestDate?.toString() ?: ""

                        TabslifyFileMeta(
                            name = baseName,
                            updatedAt = updatedAtString,
                            size = totalSize,
                        )
                    } else {
                        val file = chunks.first()
                        val localDate = file.updatedAt
                            ?.let {
                                Instant.ofEpochMilli(it.toEpochMilliseconds())
                                    .atZone(ZoneId.systemDefault())
                            }
                            ?.toString() ?: ""

                        TabslifyFileMeta(
                            name = file.name,
                            updatedAt = localDate,
                            size = 0L,
                        )
                    }
                }

            fileList = groupedFiles
            // Favoriten, die auf inzwischen gelöschte Dateien zeigen, aufräumen,
            // damit "favorites" nicht unbegrenzt mit toten Verweisen wächst.
            favoriteFiles = FavoriteManager.pruneMissing(context, groupedFiles.map { it.name })
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, fehlerBeimLadenMsg.format(e.message), Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    suspend fun deleteFile(file: TabslifyFileMeta) {
        try {
            val fileName = file.name
            withContext(Dispatchers.IO) {
                val paths = storage.from(Config.SUPABASE_BUCKET).list()
                    .map { it.name }
                    .filter { it == fileName || it.startsWith("$fileName.part") }
                storage.from(Config.SUPABASE_BUCKET).delete(paths.ifEmpty { listOf(fileName) })
            }
            FavoriteManager.remove(context, fileName)
            favoriteFiles = favoriteFiles - fileName
            withContext(Dispatchers.Main) {
                Toast.makeText(context, geloeschtMsg.format(fileName), Toast.LENGTH_SHORT).show()
            }
            loadFiles()
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, fehlerBeimLoeschenMsg.format(e.message), Toast.LENGTH_LONG)
                    .show()
            }
            e.printStackTrace()
        }
    }

    val uploadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val clip = result.data?.clipData
            val dataUri = result.data?.data
            val uris = mutableListOf<Uri>()
            if (clip != null) {
                for (i in 0 until clip.itemCount) {
                    uris += clip.getItemAt(i).uri
                }
            } else if (dataUri != null) {
                uris += dataUri
            }

            scope.launch {
                isUploading = true
                showUploadProgress = true
                try {
                    for (uri in uris) {
                        val fileName = getFileNameFromUri(uri, context) ?: "unnamed_file"
                        val file = File(context.cacheDir, fileName)

                        context.contentResolver.openInputStream(uri)?.use { input ->
                            file.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }

                        val fileSize = file.length()
                        val maxChunkSize = 20 * 1024 * 1024L

                        if (fileSize <= maxChunkSize) {
                            val bytes = file.readBytes()
                            storage.from(Config.SUPABASE_BUCKET)
                                .upload(fileName, bytes)
                        } else {
                            val inputStream = file.inputStream()
                            var chunkIndex = 0
                            val buffer = ByteArray(maxChunkSize.toInt())
                            var bytesRead: Int

                            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                val chunkData = buffer.copyOf(bytesRead)
                                val chunkFileName = "${fileName}.part${chunkIndex + 1}"
                                storage.from(Config.SUPABASE_BUCKET)
                                    .upload(chunkFileName, chunkData)
                                chunkIndex++
                            }
                            inputStream.close()
                        }

                        file.delete()
                    }

                    Toast.makeText(context, hochgeladenMsg, Toast.LENGTH_SHORT).show()
                    loadFiles()
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            fehlerBeimUploadMsg.format(e.message),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } finally {
                    isUploading = false
                    showUploadProgress = false
                }
            }

        }
    }

    LaunchedEffect(Unit) {
        if (!isOnline(context)) {
            Toast.makeText(context, keineInternetverbindungMsg, Toast.LENGTH_LONG).show()
        } else {
            loadFiles()
        }
    }

    if (!showOtherBucket) {
        val alpha = remember { Animatable(0f) }

        LaunchedEffect(Unit) {
            delay(100.milliseconds)
            alpha.animateTo(
                1f, animationSpec = tween(
                    durationMillis = 150,
                    easing = FastOutSlowInEasing
                )
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(0.dp)
                .background(Color.Transparent)
                .alpha(alpha.value)
        )
        {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp, 0.dp, 16.dp, 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("Alle", "Dateien", "Bilder", "Favoriten").forEach { filter ->
                        val containerColor by animateColorAsState(
                            targetValue = if (selectedFilter == filter) Color(0xFF555555) else Color(
                                0xFF333333
                            ),
                            animationSpec = tween(durationMillis = 300),
                            label = "containerColor"
                        )
                        PloppingButton(
                            onClick = {
                                if (filter == "Favoriten") {
                                    favoritesClickCount++
                                    if (favoritesClickCount >= 5) {
                                        pendingOtherBucket = true
                                        favoritesClickCount = 0
                                    }
                                } else {
                                    favoritesClickCount = 0
                                }
                                selectedFilter = filter
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = containerColor),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = when (filter) {
                                    "Alle" -> stringResource(R.string.alle)
                                    "Dateien" -> stringResource(R.string.dateien_2)
                                    "Bilder" -> stringResource(R.string.bilder)
                                    "Favoriten" -> stringResource(R.string.favoriten)
                                    else -> filter
                                },
                                color = Color.White,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                val preFilteredFileList = remember(fileList, selectedFilter) {
                    when (selectedFilter) {
                        "Bilder" -> fileList.filter { isImageFile(it.name) }
                        "Dateien" -> fileList.filter { !isImageFile(it.name) }
                        else -> fileList
                    }
                }

                var searchQuery by remember { mutableStateOf("") }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    stringResource(R.string.suche),
                                    color = Color.White
                                )
                            },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )

                        val sortOptions = listOf("A-Z", "Größte Datei", "Zuletzt hochgeladen")
                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = it },
                            modifier = Modifier.width(150.dp)
                        ) {
                            OutlinedTextField(
                                readOnly = true,
                                value = when (sortOption) {
                                    "Größte Datei" -> stringResource(R.string.groste_datei)
                                    "Zuletzt hochgeladen" -> stringResource(R.string.zuletzt_hochgeladen)
                                    else -> sortOption
                                },
                                onValueChange = {},
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.ArrowDropDown,
                                        contentDescription = stringResource(R.string.sortierung_offnen),
                                        tint = Color.White
                                    )
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedContainerColor = Color.DarkGray,
                                    focusedContainerColor = Color.DarkGray,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                ),
                                modifier = Modifier
                                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, true)
                                    .fillMaxWidth()
                            )

                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                                modifier = Modifier.background(Color.DarkGray)
                            ) {
                                sortOptions.forEach { option ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                when (option) {
                                                    "Größte Datei" -> stringResource(R.string.groste_datei)
                                                    "Zuletzt hochgeladen" -> stringResource(R.string.zuletzt_hochgeladen)
                                                    else -> option
                                                },
                                                color = Color.White
                                            )
                                        },
                                        onClick = {
                                            sortOption = option
                                            expanded = false
                                        },
                                        contentPadding = PaddingValues(16.dp),
                                        colors = MenuDefaults.itemColors(
                                            textColor = Color.White
                                        )
                                    )
                                }
                            }
                        }
                    }

                    val filteredFileList = remember(
                        fileList,
                        selectedFilter,
                        searchQuery,
                        sortOption,
                        favoriteFiles
                    ) {
                        val filtered = fileList.filter { file ->
                            val fileName = file.name
                            val matchesFilter = when (selectedFilter) {
                                "Bilder" -> isImageFile(fileName)
                                "Dateien" -> !isImageFile(fileName)
                                "Favoriten" -> favoriteFiles.contains(fileName)
                                else -> true
                            }
                            val matchesSearch = fileName.contains(searchQuery, ignoreCase = true)
                            matchesFilter && matchesSearch
                        }

                        when (sortOption) {
                            "A-Z" -> filtered.sortedBy { it.name.lowercase() }
                            "Größte Datei" -> filtered.sortedByDescending {
                                it.size
                            }

                            "Zuletzt hochgeladen" -> filtered.sortedByDescending {
                                it.updatedAt
                            }

                            else -> filtered
                        }
                    }

                    if (preFilteredFileList.isEmpty()) {
                        val emptyFilterLabel = when (selectedFilter) {
                            "Alle", "Dateien" -> stringResource(R.string.dateien_2)
                            "Bilder" -> stringResource(R.string.bilder)
                            "Favoriten" -> stringResource(R.string.favoriten)
                            else -> selectedFilter
                        }
                        Text(
                            stringResource(R.string.keine, emptyFilterLabel),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White
                        )
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (selectedFilter == "Bilder") {
                            items(filteredFileList.chunked(2)) { rowFiles ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowFiles.forEach { file ->
                                        val fileName = file.name
                                        val fileDate =
                                            file.updatedAt.replace("T", " ")
                                                .substringBefore(".")
                                                .ifEmpty { stringResource(R.string.unbekannt) }
                                        val sizeBytes = file.size
                                        var publicUrl by remember(fileName) {
                                            mutableStateOf<String?>(
                                                null
                                            )
                                        }
                                        Card(
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(1f),
                                            onClick = {
                                                if (publicUrl != null) {
                                                    fullscreenImageDialogData =
                                                        Triple(publicUrl, fileName, sizeBytes)
                                                }
                                            }
                                        ) {
                                            Box(modifier = Modifier.fillMaxSize()) {
                                                LaunchedEffect(fileName) {
                                                    try {
                                                        val signed = withContext(Dispatchers.IO) {
                                                            storage.from(Config.SUPABASE_BUCKET)
                                                                .createSignedUrl(
                                                                    fileName,
                                                                    600.seconds
                                                                )
                                                        }
                                                        publicUrl = signed
                                                    } catch (e: Exception) {
                                                        e.printStackTrace()
                                                    }
                                                }

                                                if (publicUrl != null) {
                                                    Image(
                                                        painter = rememberAsyncImagePainter(
                                                            publicUrl
                                                        ),
                                                        contentDescription = fileName,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                } else {
                                                    Box(
                                                        modifier = Modifier.fillMaxSize(),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        CircularProgressIndicator(
                                                            modifier = Modifier.size(24.dp),
                                                            color = Color.White
                                                        )
                                                    }
                                                }

                                                Column(
                                                    modifier = Modifier
                                                        .align(Alignment.BottomCenter)
                                                        .fillMaxWidth()
                                                        .background(Black.copy(alpha = 0.7f))
                                                        .padding(4.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        val imageAlreadyDownloaded =
                                                            sizeBytes >= 0 && fileExistsLocallyWithSameSize(
                                                                fileName,
                                                                sizeBytes
                                                            )
                                                        val isFavorite =
                                                            favoriteFiles.contains(fileName)

                                                        val sizeText = formatFileSize(sizeBytes)
                                                        Text(
                                                            text = "$fileName\n $fileDate\n $sizeText",
                                                            style = MaterialTheme.typography.bodySmall.copy(
                                                                fontSize = 9.sp
                                                            ),
                                                            color = Color.White,
                                                            maxLines = 3
                                                        )
                                                        IconButton(
                                                            onClick = {
                                                                favoriteFiles = if (isFavorite) {
                                                                    favoriteFiles - fileName
                                                                } else {
                                                                    favoriteFiles + fileName
                                                                }
                                                                FavoriteManager.saveFavorites(
                                                                    context,
                                                                    favoriteFiles
                                                                )
                                                                haptic.performHapticFeedback(
                                                                    HapticFeedbackType.LongPress
                                                                )
                                                            },
                                                            modifier = Modifier.size(32.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                                                                contentDescription = stringResource(R.string.favorit),
                                                                tint = if (isFavorite) Color.Yellow else Color.White
                                                            )
                                                        }
                                                        if (!imageAlreadyDownloaded) {
                                                            IconButton(
                                                                onClick = {
                                                                    isDownloading = fileName
                                                                    scope.launch {
                                                                        showDownloadProgress = true
                                                                        try {
                                                                            val data =
                                                                                withContext(
                                                                                    Dispatchers.IO
                                                                                ) {
                                                                                    storage.from(
                                                                                        Config.SUPABASE_BUCKET
                                                                                    )
                                                                                        .downloadAuthenticated(
                                                                                            fileName
                                                                                        )
                                                                                }

                                                                            val dcimDir =
                                                                                Environment.getExternalStoragePublicDirectory(
                                                                                    Environment.DIRECTORY_DCIM
                                                                                )
                                                                            val appFolder =
                                                                                File(
                                                                                    dcimDir,
                                                                                    "Tabslify"
                                                                                )
                                                                            if (!appFolder.exists()) {
                                                                                appFolder.mkdirs()
                                                                            }

                                                                            val outputFile = File(
                                                                                appFolder,
                                                                                fileName
                                                                            )

                                                                            withContext(Dispatchers.IO) {
                                                                                FileOutputStream(
                                                                                    outputFile
                                                                                ).use { fos ->
                                                                                    fos.write(data)
                                                                                }
                                                                            }

                                                                            MediaScannerConnection.scanFile(
                                                                                context,
                                                                                arrayOf(outputFile.absolutePath),
                                                                                null,
                                                                                null
                                                                            )

                                                                            Toast.makeText(
                                                                                context,
                                                                                bildGespeichertMsg,
                                                                                Toast.LENGTH_SHORT
                                                                            ).show()

                                                                            haptic.performHapticFeedback(
                                                                                HapticFeedbackType.LongPress
                                                                            )
                                                                        } catch (e: Exception) {
                                                                            Toast.makeText(
                                                                                context,
                                                                                fehlerMsgTemplate.format(e.message),
                                                                                Toast.LENGTH_LONG
                                                                            ).show()
                                                                        } finally {
                                                                            isDownloading = null
                                                                            delay(500.milliseconds)
                                                                            showDownloadProgress =
                                                                                false
                                                                        }
                                                                    }
                                                                },
                                                                enabled = isDownloading != fileName,
                                                                modifier = Modifier.size(32.dp)
                                                            ) {
                                                                if (isDownloading == fileName) {
                                                                    CircularProgressIndicator(
                                                                        modifier = Modifier.size(16.dp),
                                                                        color = Color.White
                                                                    )
                                                                } else {
                                                                    Icon(
                                                                        imageVector = Icons.Filled.ArrowDropDown,
                                                                        contentDescription = stringResource(R.string.download),
                                                                        tint = Black
                                                                    )
                                                                }
                                                            }
                                                        } else {
                                                            IconButton(
                                                                onClick = {
                                                                    val localImageFile =
                                                                        fileExistsInDCIM(fileName)!!
                                                                    val fileUri =
                                                                        FileProvider.getUriForFile(
                                                                            context,
                                                                            "${context.packageName}.fileprovider",
                                                                            localImageFile
                                                                        )
                                                                    val intent =
                                                                        Intent(Intent.ACTION_VIEW).apply {
                                                                            setDataAndType(
                                                                                fileUri,
                                                                                "image/*"
                                                                            )
                                                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                                        }
                                                                    context.startActivity(intent)
                                                                    haptic.performHapticFeedback(
                                                                        HapticFeedbackType.LongPress
                                                                    )
                                                                },
                                                                modifier = Modifier.size(32.dp)
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                                                    contentDescription = stringResource(R.string.offnen),
                                                                    tint = Color.White
                                                                )
                                                            }
                                                        }

                                                        IconButton(
                                                            onClick = {
                                                                fileToDelete = file
                                                                haptic.performHapticFeedback(
                                                                    HapticFeedbackType.LongPress
                                                                )
                                                            },
                                                            modifier = Modifier.size(32.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Delete,
                                                                contentDescription = stringResource(R.string.loschen),
                                                                tint = Color.Red
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    if (rowFiles.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        } else {
                            items(filteredFileList) { file ->
                                val fileName = file.name
                                val fileDate =
                                    file.updatedAt
                                        .replace("T", " ")
                                        .substringBefore(".")
                                        .ifEmpty { stringResource(R.string.unbekannt) }
                                val sizeBytes = file.size
                                val showOpenButton =
                                    sizeBytes >= 0 && fileExistsLocallyWithSameSize(
                                        fileName,
                                        sizeBytes
                                    )

                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color.DarkGray)
                                            .padding(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            FileIcon(
                                                fileName = fileName,
                                                storage = storage,
                                                modifier = Modifier
                                                    .size(56.dp)
                                                    .padding(end = 12.dp)
                                            )

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = fileName,
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        fontSize = 16.sp
                                                    ),
                                                    color = Color.White,
                                                    maxLines = 1
                                                )

                                                Text(
                                                    text = stringResource(R.string.hochgeladen_datum, fileDate),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color.LightGray,
                                                    fontSize = 10.sp
                                                )

                                                val sizeText = when {
                                                    sizeBytes >= 1_000_000_000 -> "%.2f GB".format(
                                                        sizeBytes / 1_000_000_000.0
                                                    )

                                                    sizeBytes >= 1_000_000 -> "%.2f MB".format(
                                                        sizeBytes / 1_000_000.0
                                                    )

                                                    else -> "%.1f KB".format(sizeBytes / 1_000.0)
                                                }

                                                Text(
                                                    text = sizeText,
                                                    fontSize = 10.sp,
                                                    color = Color.White.copy(alpha = 0.6f)
                                                )
                                            }

                                            val isFavorite = favoriteFiles.contains(fileName)
                                            IconButton(
                                                onClick = {
                                                    favoriteFiles = if (isFavorite) {
                                                        favoriteFiles - fileName
                                                    } else {
                                                        favoriteFiles + fileName
                                                    }
                                                    FavoriteManager.saveFavorites(
                                                        context,
                                                        favoriteFiles
                                                    )
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                                                    contentDescription = stringResource(R.string.favorit),
                                                    tint = if (isFavorite) Color.Yellow else Color.White
                                                )
                                            }

                                            if (showOpenButton && !isImageFile(fileName)) {
                                                val localFile =
                                                    getLocalFileWithPath(fileName, sizeBytes)
                                                if (localFile != null) {
                                                    IconButton(onClick = {
                                                        val fileUri = FileProvider.getUriForFile(
                                                            context,
                                                            "${context.packageName}.fileprovider",
                                                            localFile
                                                        )
                                                        val mimeType =
                                                            context.contentResolver.getType(fileUri)
                                                                ?: getMimeType(fileName)
                                                        val intent =
                                                            Intent(Intent.ACTION_VIEW).apply {
                                                                setDataAndType(fileUri, mimeType)
                                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                            }
                                                        context.startActivity(intent)
                                                        haptic.performHapticFeedback(
                                                            HapticFeedbackType.LongPress
                                                        )
                                                    }) {
                                                        Icon(
                                                            Icons.AutoMirrored.Filled.OpenInNew,
                                                            contentDescription = stringResource(R.string.offnen),
                                                            tint = Color.White
                                                        )
                                                    }
                                                } else {
                                                    Spacer(modifier = Modifier.size(32.dp))
                                                }
                                            } else if (isImageFile(fileName) && fileExistsInDCIM(
                                                    fileName
                                                ) != null
                                            ) {
                                                IconButton(onClick = {
                                                    val imageFile = fileExistsInDCIM(fileName)!!
                                                    val fileUri = FileProvider.getUriForFile(
                                                        context,
                                                        "${context.packageName}.fileprovider",
                                                        imageFile
                                                    )
                                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                                        setDataAndType(fileUri, "image/*")
                                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    }
                                                    context.startActivity(intent)
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                }) {
                                                    Icon(
                                                        Icons.AutoMirrored.Filled.OpenInNew,
                                                        contentDescription = stringResource(R.string.offnen),
                                                        tint = Color.White
                                                    )
                                                }
                                            } else {
                                                IconButton(
                                                    onClick = {
                                                        isDownloading = fileName
                                                        scope.launch {
                                                            showDownloadProgress = true
                                                            try {
                                                                val allFiles =
                                                                    withContext(Dispatchers.IO) {
                                                                        storage.from(Config.SUPABASE_BUCKET)
                                                                            .list()
                                                                    }

                                                                val chunks = allFiles.filter {
                                                                    it.name.startsWith(fileName) && it.name.contains(
                                                                        ".part"
                                                                    )
                                                                }.sortedBy {
                                                                    val partMatch =
                                                                        Regex("part(\\d+)of").find(
                                                                            it.name
                                                                        )
                                                                    partMatch?.groupValues?.get(1)
                                                                        ?.toIntOrNull() ?: 0
                                                                }
                                                                val targetBaseFolder =
                                                                    if (isImageFile(fileName) || fileName.endsWith(
                                                                            ".mp4",
                                                                            ignoreCase = true
                                                                        )
                                                                    ) {
                                                                        Environment.getExternalStoragePublicDirectory(
                                                                            Environment.DIRECTORY_DCIM
                                                                        )
                                                                    } else {
                                                                        Environment.getExternalStoragePublicDirectory(
                                                                            Environment.DIRECTORY_DOWNLOADS
                                                                        )
                                                                    }

                                                                val appFolder =
                                                                    File(
                                                                        targetBaseFolder,
                                                                        "Tabslify"
                                                                    )
                                                                if (!appFolder.exists()) {
                                                                    appFolder.mkdirs()
                                                                }

                                                                val cleanFileName =
                                                                    fileName.substringBefore(".part")
                                                                val outputFile =
                                                                    File(appFolder, cleanFileName)

                                                                withContext(Dispatchers.IO) {
                                                                    FileOutputStream(outputFile).use { fos ->
                                                                        if (chunks.isNotEmpty()) {
                                                                            for ((name) in chunks) {
                                                                                val chunkData =
                                                                                    storage.from(
                                                                                        Config.SUPABASE_BUCKET
                                                                                    )
                                                                                        .downloadAuthenticated(
                                                                                            name
                                                                                        )

                                                                                fos.write(
                                                                                    chunkData
                                                                                )
                                                                            }
                                                                        } else {
                                                                            val downloadedData =
                                                                                storage.from(
                                                                                    Config.SUPABASE_BUCKET
                                                                                )
                                                                                    .downloadAuthenticated(
                                                                                        fileName
                                                                                    )

                                                                            fos.write(downloadedData)
                                                                        }
                                                                    }
                                                                }

                                                                if (isImageFile(fileName) || fileName.endsWith(
                                                                        ".mp4",
                                                                        ignoreCase = true
                                                                    )
                                                                ) {
                                                                    MediaScannerConnection.scanFile(
                                                                        context,
                                                                        arrayOf(outputFile.absolutePath),
                                                                        null,
                                                                        null
                                                                    )
                                                                }

                                                                Toast.makeText(
                                                                    context,
                                                                    dateiGespeichertMsg,
                                                                    Toast.LENGTH_SHORT
                                                                ).show()

                                                                haptic.performHapticFeedback(
                                                                    HapticFeedbackType.LongPress
                                                                )
                                                            } catch (e: Exception) {
                                                                e.printStackTrace()
                                                                Toast.makeText(
                                                                    context,
                                                                    String.format(fehlerMsgTemplate, e.message),
                                                                    Toast.LENGTH_LONG
                                                                ).show()
                                                            } finally {
                                                                isDownloading = null
                                                                delay(500.milliseconds)
                                                                showDownloadProgress = false
                                                            }
                                                        }
                                                    },
                                                    enabled = isDownloading != fileName,
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    if (isDownloading == fileName) {
                                                        CircularProgressIndicator(
                                                            modifier = Modifier.size(16.dp),
                                                            color = Color.White
                                                        )
                                                    } else {
                                                        Icon(
                                                            imageVector = Icons.Filled.ArrowDropDown,
                                                            contentDescription = stringResource(R.string.download),
                                                            tint = Black
                                                        )
                                                    }
                                                }
                                            }

                                            IconButton(
                                                onClick = {
                                                    fileToDelete = file
                                                    haptic.performHapticFeedback(
                                                        HapticFeedbackType.LongPress
                                                    )
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = stringResource(R.string.loschen),
                                                    tint = Color.Red
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                                    type = "*/*"
                                    addCategory(Intent.CATEGORY_OPENABLE)
                                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                                }
                                uploadLauncher.launch(intent)
                            },
                            enabled = !isUploading,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                stringResource(R.string.datei_auswahlen_hochladen),
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }

                        Button(
                            onClick = {
                                scope.launch {
                                    if (!isOnline(context)) {
                                        Toast.makeText(
                                            context,
                                            keineInternetverbindungMsg,
                                            Toast.LENGTH_LONG
                                        ).show()
                                    } else {
                                        loadFiles()
                                        Toast.makeText(
                                            context,
                                            listeAktualisiertMsg,
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            },
                            modifier = Modifier.weight(0.64f)
                        ) {
                            Text(
                                stringResource(R.string.aktualisieren),
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            if (showUploadProgress) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Black.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .padding(16.dp)
                            .size(50.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                color = Black,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                }
            }

            if (showDownloadProgress) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Black.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .padding(16.dp)
                            .size(50.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                color = Black,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    fileToDelete?.let { file ->
        AlertDialogTabslify(
            onConfirm = {
                fileToDelete = null
                scope.launch { deleteFile(file) }
            },
            onDismiss = { fileToDelete = null },
            title = stringResource(R.string.datei_wirklich_loeschen),
            text = stringResource(R.string.datei_wird_geloescht, file.name),
            confirmText = stringResource(R.string.loschen)
        )
    }

    fullscreenImageDialogData?.let { (url, name, size) ->
        val imageAlreadyDownloaded = fileExistsLocallyWithSameSize(name, size)
        FullscreenImageDialog(
            imageUrl = url,
            fileName = name,
            isDownloaded = imageAlreadyDownloaded,
            onDismiss = {
                fullscreenImageDialogData = null
            },
            onDownload = {
                isDownloading = name
                scope.launch {
                    showDownloadProgress = true
                    try {
                        val data = withContext(Dispatchers.IO) {
                            storage.from(Config.SUPABASE_BUCKET)
                                .downloadAuthenticated(name)
                        }
                        val dcimDir =
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
                        val appFolder = File(dcimDir, "Tabslify")
                        if (!appFolder.exists()) appFolder.mkdirs()
                        val outputFile = File(appFolder, name)
                        withContext(Dispatchers.IO) {
                            FileOutputStream(outputFile).use { fos -> fos.write(data) }
                        }
                        MediaScannerConnection.scanFile(
                            context,
                            arrayOf(outputFile.absolutePath),
                            null,
                            null
                        )
                        Toast.makeText(context, bildGespeichertMsg, Toast.LENGTH_SHORT).show()
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    } catch (e: Exception) {
                        Toast.makeText(context, fehlerMsgTemplate.format(e.message), Toast.LENGTH_LONG).show()
                    } finally {
                        isDownloading = null
                        delay(500.milliseconds)
                        showDownloadProgress = false
                        fullscreenImageDialogData = null
                    }
                }
            },
            onOpenInGallery = {
                val localImageFile = fileExistsInDCIM(name)!!
                val fileUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    localImageFile
                )
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(fileUri, "image/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                fullscreenImageDialogData = null
            }
        )
    }
    if (pendingOtherBucket || showOtherBucket) {
        if (pendingOtherBucket) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationY = 10000f }
                    .drawWithContent {
                        otherBucketLayer.record { this@drawWithContent.drawContent() }
                        drawLayer(otherBucketLayer)
                    }
            ) {
                OtherBucketViewer(onBackPressed = {
                    showOtherBucket = false
                })
            }

            LaunchedEffect(Unit) {
                repeat(5) { withFrameNanos { } }

                val captured = otherBucketLayer.toImageBitmap()
                otherBucketBitmap = captured

                snapshotFlow { otherBucketBitmap }
                    .filterNotNull()
                    .first()

                showOtherBucket = true
                otherBucketScale.snapTo(0.05f)
                otherBucketAlpha.snapTo(1f)

                otherBucketScale.animateTo(
                    1f,
                    tween(durationMillis = 320, easing = FastOutSlowInEasing)
                )

                pendingOtherBucket = false
                shouldshow = true

                delay(80.milliseconds)
                otherBucketAlpha.animateTo(0f, tween(durationMillis = 200))
                otherBucketScale.snapTo(0f)
                otherBucketBitmap = null
            }

            if (otherBucketBitmap != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = otherBucketScale.value
                            scaleY = otherBucketScale.value
                            alpha = otherBucketAlpha.value
                            transformOrigin = TransformOrigin(0.5f, 0.5f)
                        }
                ) {
                    Image(
                        bitmap = otherBucketBitmap!!,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillBounds
                    )
                }
            }
        }

        if (shouldshow) {
            OtherBucketViewer(
                onBackPressed = {
                    shouldshow = false
                    showOtherBucket = false
                }
            )
        }
    }
}

fun showBatteryInfo(context: Context) {
    val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

    if (batteryIntent != null) {
        val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val percentage = (level / scale.toFloat() * 100).toInt()

        val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val plugged = batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
        val chargingType = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> context.getString(R.string.netzteil_ac)
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> context.getString(R.string.wireless)
            else -> context.getString(R.string.nicht_angeschlossen)
        }

        val temperature = batteryIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) / 10.0
        val voltage = batteryIntent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) / 1000.0

        val health = batteryIntent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)
        val healthText = when (health) {
            BatteryManager.BATTERY_HEALTH_GOOD -> context.getString(R.string.gut)
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> context.getString(R.string.uberhitzt)
            BatteryManager.BATTERY_HEALTH_DEAD -> context.getString(R.string.defekt)
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> context.getString(R.string.uberspannung)
            BatteryManager.BATTERY_HEALTH_COLD -> context.getString(R.string.zu_kalt)
            else -> context.getString(R.string.unbekannt)
        }

        val channelId = "battery_info_channel"
        val channel = NotificationChannel(
            channelId,
            context.getString(R.string.batterie_informationen),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.zeigt_detaillierte_batterie_info)
        }
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(context.getString(R.string.batterie_info))
            .setContentText(context.getString(R.string.ladezustand, percentage))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(
                        context.getString(
                            R.string.batterie_details,
                            percentage,
                            if (isCharging) context.getString(R.string.ladt) else context.getString(R.string.entladt),
                            chargingType,
                            temperature,
                            voltage,
                            healthText
                        )
                    )
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            tNotify(context, cms(), builder)
        } else {
            Toast.makeText(
                context,
                context.getString(R.string.ladezustand_temp, percentage, temperature),
                Toast.LENGTH_LONG
            ).show()
        }
    } else {
        Toast.makeText(context, context.getString(R.string.batterie_info_nicht_verfugbar), Toast.LENGTH_SHORT).show()
    }
}


const val PREFS_NAME = "tabslify_app_prefs"

private const val KEY_LAST_URL = "last_browser_url"

fun saveLastUrl(context: Context, url: String) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit {
            putString(KEY_LAST_URL, url)
        }
}

fun loadLastUrl(context: Context): String {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getString(KEY_LAST_URL, "https://www.google.com") ?: "https://www.google.com"
}

object WebViewCookieBackup {
    private const val PREFS_NAME = "webview_cookies"
    private const val KEY_ORDER = "cookie_key_order"
    private const val MAX_ENTRIES = 150

    fun saveCookies(context: Context, url: String) {
        val cookies = CookieManager.getInstance().getCookie(url) ?: return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = "cookies_${url.hashCode()}"

        // LRU-Reihenfolge der zuletzt gespeicherten Cookie-Keys, damit alte
        // Einträge entfernt werden können, statt die Prefs-Datei unbegrenzt
        // mit Cookies längst nicht mehr besuchter Seiten wachsen zu lassen.
        val order = prefs.getString(KEY_ORDER, "")
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?.toMutableList() ?: mutableListOf()
        order.remove(key)
        order.add(key)

        prefs.edit {
            putString(key, cookies)
            while (order.size > MAX_ENTRIES) {
                val stale = order.removeAt(0)
                remove(stale)
            }
            putString(KEY_ORDER, order.joinToString(","))
        }
    }

    fun restoreCookies(context: Context, url: String) {
        val cookies = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString("cookies_${url.hashCode()}", null) ?: return
        val cm = CookieManager.getInstance()
        cookies.split(";").forEach { cookie ->
            cm.setCookie(url, cookie.trim())
        }
        cm.flush()
    }
}

@Suppress("unused")
@Composable
fun GoodNightScreen(ai: String) {
    val context = LocalContext.current
    MediaAnalyticsManager.init(context)
    var showStats by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.night),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        val spaceMono = FontFamily(
            Font(R.font.smb, FontWeight.Normal),
            Font(R.font.smb, FontWeight.Bold)
        )
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(Modifier.fillMaxWidth(0.8f)) {
                Row {
                    Text(
                        stringResource(R.string.guck_wie_du),
                        color = Color.White,
                        fontFamily = spaceMono
                    )
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    Modifier
                        .clip(RoundedCornerShape(20))
                        .background(APP_COLOR)
                        .padding(5.dp)
                        .clickable(onClick = {
                            showStats = true
                        })
                ) {
                    Text(
                        "$ai...",
                        fontFamily = spaceMono,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
    if (showStats) {
        AiResponseHistorySheet(context = context, onDismiss = { showStats = false })
    }
}

private val HelpBgTop = Color(0xFF1A1330)
private val HelpBgBottom = Color(0xFF060509)
private val HelpChipBg = Color(0x0DFFFFFF)
private val HelpTextPrimary = Color(0xFFF7F5FB)
private val HelpTextSecondary = Color(0xD1FFFFFF)
private val HelpAccentBrush = Brush.linearGradient(
    listOf(Color(0xFFFF8A4C), Color(0xFFB45CFC), Color(0xFF6B4CFC))
)

//@Preview
@Composable
fun HelpFrame(
    selectedMenuItem: MenuItem = MenuItem.GMAIL,
    onDismiss: () -> Unit = {}
) {
    val helpText = Config.helpFrameEntries[selectedMenuItem]?.let { stringResource(it) }
        ?: stringResource(R.string.fur_diesen_bereich)

    BackHandler(enabled = true, onBack = onDismiss)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(HelpBgTop, HelpBgBottom)))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {}
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(HelpAccentBrush),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = selectedMenuItem.icon, fontSize = 34.sp)
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.hilfe),
                        color = HelpTextSecondary.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(selectedMenuItem.titleRes),
                        color = HelpTextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.hilfe_schliesen),
                        tint = Color.White
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(HelpChipBg)
                    .padding(20.dp)
            ) {
                Text(
                    text = helpText,
                    color = HelpTextSecondary,
                    fontSize = 16.sp,
                    lineHeight = 24.sp
                )
            }

            Spacer(Modifier.height(32.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(HelpAccentBrush)
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.verstanden),
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}