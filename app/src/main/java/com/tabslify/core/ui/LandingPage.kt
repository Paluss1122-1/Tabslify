package com.tabslify.core.ui

import android.Manifest
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Paint
import android.net.TrafficStats
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tabslify.BuildConfig
import com.tabslify.R
import com.tabslify.core.functions.canNotify
import com.tabslify.core.objects.BackupEntry
import com.tabslify.core.objects.BackupOutcome
import com.tabslify.core.objects.Config
import com.tabslify.core.objects.Config.client
import com.tabslify.core.objects.Config.realDevice
import com.tabslify.core.objects.PasswordStorage
import com.tabslify.core.objects.PrefsBackup
import com.tabslify.core.objects.prvt
import com.tabslify.core.objects.toast
import com.tabslify.quicksettingsfunctions.ChargingTrackerService
import com.tabslify.quicksettingsfunctions.startBatteryWorker
import com.tabslify.quicksettingsfunctions.stopBatteryWorker
import com.tabslify.services.QuietHoursNotificationService
import com.tabslify.services.WhatsAppNotificationListener
import com.tabslify.tabs.focusguard.FocusGuardService
import com.tabslify.tabs.focusguard.monitoring.cancelFocusGuardWorkers
import com.tabslify.tabs.focusguard.monitoring.scheduleFocusGuardDailySummary
import com.tabslify.tabs.focusguard.monitoring.scheduleFocusGuardWorkers
import com.tabslify.tabs.mediaplayer.MediaAnalyticsManager
import com.tabslify.tabs.virustotal.VirusTotalScanBar
import com.tabslify.tabs.virustotal.pendingVirusTotalReport
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.storage.Storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds
import androidx.compose.ui.platform.LocalLocale

val inAppNotifications = mutableStateListOf<String>()
private const val MAX_IN_APP_NOTIFICATIONS = 20

@Suppress("unused")
fun sendInAppNotification(message: String) {
    inAppNotifications.add(0, message)
    if (inAppNotifications.size > MAX_IN_APP_NOTIFICATIONS) {
        inAppNotifications.removeRange(MAX_IN_APP_NOTIFICATIONS, inAppNotifications.size)
    }
}

fun saveRecentTab(context: Context, menuItem: MenuItem) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val recentTabsString = prefs.getString(KEY_RECENT_TABS, "") ?: ""
    val recentTabs = recentTabsString.split(",").filter { it.isNotEmpty() }.toMutableList()

    recentTabs.remove(menuItem.name)
    recentTabs.add(0, menuItem.name)
    val trimmedTabs = recentTabs.take(MAX_RECENT_TABS)

    prefs.edit {
        putString(KEY_RECENT_TABS, trimmedTabs.joinToString(","))
    }
}

fun loadRecentTabs(context: Context): List<MenuItem> {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val recentTabsString = prefs.getString(KEY_RECENT_TABS, "") ?: ""
    return recentTabsString.split(",")
        .filter { it.isNotEmpty() }
        .mapNotNull { name ->
            try {
                MenuItem.valueOf(name)
            } catch (_: Exception) {
                null
            }
        }
}

fun getDeviceName(): String {
    val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercaseChar() }
    val model = Build.MODEL
    return if (model.startsWith(manufacturer, ignoreCase = true)) {
        model.replaceFirstChar { it.uppercaseChar() }
    } else {
        "$manufacturer $model"
    }
}

@Composable
fun LandingPageOrApp(storage: Storage, startTarget: String?) {
    val context = LocalContext.current
    var hasLoadedApp by rememberSaveable { mutableStateOf(startTarget != null) }
    var selectedMenuItem by rememberSaveable { mutableStateOf<MenuItem?>(null) }
    var masterPw by remember { mutableStateOf(PasswordStorage.loadPassword(context)) }
    val landingListState = rememberSaveable(saver = LazyListState.Saver) {
        LazyListState()
    }
    var reloadKey by remember { mutableIntStateOf(0) }
    realDevice = !getDeviceName().trim().contains("sdk_gphone", ignoreCase = true)
    var landingReloadTrigger by remember { mutableIntStateOf(0) }

    val appPrefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    var showFirstStartPermissionInfo by remember {
        mutableStateOf(!appPrefs.getBoolean("has_seen_permission_info", false))
    }
    var onboardingExiting by remember { mutableStateOf(false) }

    if (realDevice && prvt()) {
        if (masterPw == null || Config.masterPassword.isEmpty()) {
            MasterPasswordSetupScreen(modifier = Modifier.zIndex(10f)) { pw ->
                PasswordStorage.savePassword(context, pw)
                Config.masterPassword = pw
            }
            return
        }
        Config.masterPassword = masterPw!!

        val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
        var hasCoords by remember { mutableStateOf(prefs.getBoolean("has_coordinates", false)) }
        if (!hasCoords) {
            CoordinatesSetupScreen(modifier = Modifier.zIndex(10f)) { lat, lon ->
                prefs.edit {
                    putLong("lat_key_d", java.lang.Double.doubleToRawLongBits(lat))
                    putLong("lon_key_d", java.lang.Double.doubleToRawLongBits(lon))
                    putBoolean("has_coordinates", true)
                }
                Config.LAT = lat
                Config.LON = lon
                hasCoords = true
            }
            return
        }
    }

    AnimatedVisibility(
        visible = showFirstStartPermissionInfo,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = Modifier.zIndex(10f)
    ) {
        WelcomeOnboardingScreen(
            onFinished = {
                appPrefs.edit { putBoolean("has_seen_permission_info", true) }
                showFirstStartPermissionInfo = false
            },
            onExitStart = { onboardingExiting = true },
            initialPage = appPrefs.getInt("onboarding_page", 0),
            onPageChanged = { page -> appPrefs.edit { putInt("onboarding_page", page) } }
        )
    }

    if (showFirstStartPermissionInfo && !onboardingExiting) {
        return
    }

    val sessionStatus by client.auth.sessionStatus.collectAsState()

    if (prvt()) {
        when (sessionStatus) {
            is SessionStatus.NotAuthenticated -> {
                SupabaseLoginScreen { }
                return
            }

            is SessionStatus.Authenticated -> Unit

            SessionStatus.Initializing,
            is SessionStatus.RefreshFailure -> {
                if (client.auth.currentSessionOrNull() == null) {
                    SupabaseLoadingScreen()
                    return
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { }
    }

    LaunchedEffect(startTarget) {
        if (startTarget != null && selectedMenuItem == null) {
            selectedMenuItem = when (startTarget) {
                "weather" -> MenuItem.WEATHER
                "aitab" -> MenuItem.AITAB
                "apkm" -> MenuItem.APKM_INSTALLER
                "gmail" -> MenuItem.GMAIL
                "virustotal" -> MenuItem.VIRUSTOTAL
                else -> null
            }
            if (selectedMenuItem != null && selectedMenuItem != MenuItem.APKM_INSTALLER) {
                saveRecentTab(context, selectedMenuItem!!)
            }
        }
    }

    val landingOffsetX = remember { Animatable(if (!hasLoadedApp) 0f else -1f) }
    val scope = rememberCoroutineScope()

    fun openLanding() {
        landingReloadTrigger++
        scope.launch {
            landingOffsetX.animateTo(0f, tween(360, easing = FastOutSlowInEasing))
        }
    }

    fun closeLanding(force: Boolean = false, then: (() -> Unit)? = null) {
        scope.launch {
            landingOffsetX.animateTo(
                -1f,
                tween(if (force) 1 else 300, easing = FastOutSlowInEasing)
            )
            then?.invoke()
            landingOffsetX.snapTo(-1f)
        }
    }

    var pendingOverlayItem by remember { mutableStateOf<MenuItem?>(null) }
    val overlayScale = remember { Animatable(0f) }
    val overlayAlpha = remember { Animatable(0f) }

    Box(modifier = Modifier.fillMaxSize()) {

        if (hasLoadedApp) {
            val targetMenuItem = selectedMenuItem ?: startTarget?.let { target ->
                when (target) {
                    "weather" -> MenuItem.WEATHER
                    "apkm" -> MenuItem.APKM_INSTALLER
                    "gmail" -> MenuItem.GMAIL
                    "virustotal" -> MenuItem.VIRUSTOTAL
                    else -> null
                }
            }

            key(selectedMenuItem, reloadKey) {
                Box(Modifier.fillMaxSize()) {
                    if (targetMenuItem != null) {
                        PrivateTabslifyApp(
                            storage = storage,
                            startTarget = null,
                            initialMenuItem = targetMenuItem,
                            onMenuClick = { openLanding() }
                        )
                    }
                }
            }

            VirusTotalScanBar(
                onOpenReport = { id ->
                    pendingVirusTotalReport = id
                    selectedMenuItem = MenuItem.VIRUSTOTAL
                    saveRecentTab(context, MenuItem.VIRUSTOTAL)
                    if (!hasLoadedApp) hasLoadedApp = true
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = landingOffsetX.value * size.width
                }
        ) {
            LandingPage(
                showCloseButton = hasLoadedApp,
                onClose = { closeLanding() },
                onTabSelected = { menuItem -> pendingOverlayItem = menuItem },
                state = landingListState,
                reloadTrigger = landingReloadTrigger
            )
        }

        if (pendingOverlayItem != null) {
            val item = pendingOverlayItem!!
            val graphicsLayer = rememberGraphicsLayer()
            var previewBitmap by remember(item) { mutableStateOf<ImageBitmap?>(null) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationY = 10000f }
                    .drawWithContent {
                        graphicsLayer.record { this@drawWithContent.drawContent() }
                        drawLayer(graphicsLayer)
                    }
                    .background(APP_COLOR)
            ) {
                PrivateTabslifyApp(
                    storage = storage,
                    startTarget = null,
                    initialMenuItem = item,
                    onMenuClick = null
                )
            }

            LaunchedEffect(item) {
                repeat(5) { withFrameNanos { } }
                val captured = graphicsLayer.toImageBitmap()
                previewBitmap = captured

                snapshotFlow { previewBitmap }.filterNotNull().first()

                overlayScale.snapTo(0.05f)
                overlayAlpha.snapTo(1f)

                overlayScale.animateTo(
                    1f, tween(durationMillis = 320, easing = FastOutSlowInEasing)
                )

                closeLanding(true)

                selectedMenuItem = item
                saveRecentTab(context, item)

                reloadKey++

                if (!hasLoadedApp) {
                    hasLoadedApp = true
                }

                delay(80.milliseconds)
                overlayAlpha.animateTo(0f, tween(durationMillis = 200))
                previewBitmap = null
                overlayScale.snapTo(0f)
                pendingOverlayItem = null
            }

            if (previewBitmap != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = overlayScale.value
                            scaleY = overlayScale.value
                            alpha = overlayAlpha.value
                            transformOrigin = TransformOrigin(0.5f, 0.5f)
                        }
                ) {
                    Image(
                        bitmap = previewBitmap!!,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillBounds
                    )
                }
            }
        }
    }
}

@Composable
fun MasterPasswordSetupScreen(modifier: Modifier, onPasswordSaved: (String) -> Unit) {
    var input by remember { mutableStateOf("") }
    var confirmed by remember { mutableStateOf("") }
    val isValid = input.length >= 20 && input == confirmed

    Box(
        modifier
            .fillMaxSize()
            .background(Color(0xFF17171C)), contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                stringResource(R.string.master_passwort_einrichten),
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                stringResource(R.string.wird_einmalig_gesetzt_mindestens_20),
                color = Color(0xFF8A8A9F),
                fontSize = 13.sp
            )

            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text(stringResource(R.string.passwort)) },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = confirmed,
                onValueChange = { confirmed = it },
                label = { Text(stringResource(R.string.wiederholen)) },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )

            if (input.isNotEmpty() && input != confirmed)
                Text(
                    stringResource(R.string.passworter_stimmen_nicht_uberein),
                    color = Color(0xFFE74C3C),
                    fontSize = 12.sp
                )
            if (input.isNotEmpty() && input.length < 20)
                Text(
                    stringResource(R.string.mindestens_20_zeichen),
                    color = Color(0xFFE74C3C),
                    fontSize = 12.sp
                )

            Button(
                onClick = { onPasswordSaved(input) },
                enabled = isValid,
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.speichern_starten)) }
        }
    }
}

@Composable
fun CoordinatesSetupScreen(modifier: Modifier, onCoordinatesSaved: (Double, Double) -> Unit) {
    var latInput by remember { mutableStateOf("") }
    var lonInput by remember { mutableStateOf("") }
    val latDouble = latInput.toDoubleOrNull()
    val lonDouble = lonInput.toDoubleOrNull()
    val isValid = latDouble != null && lonDouble != null

    Box(
        modifier
            .fillMaxSize()
            .background(Color(0xFF17171C)), contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                stringResource(R.string.koordinaten_einrichten),
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                stringResource(R.string.bitte_gib_deine_standard_koordinaten),
                color = Color(0xFF8A8A9F),
                fontSize = 13.sp
            )

            OutlinedTextField(
                value = latInput,
                onValueChange = { latInput = it },
                label = { Text(stringResource(R.string.breitengrad_latitude)) },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = lonInput,
                onValueChange = { lonInput = it },
                label = { Text(stringResource(R.string.langengrad_longitude)) },
                modifier = Modifier.fillMaxWidth()
            )

            if (latInput.isNotEmpty() && latDouble == null)
                Text(
                    stringResource(R.string.ungultiger_breitengrad),
                    color = Color(0xFFE74C3C),
                    fontSize = 12.sp
                )
            if (lonInput.isNotEmpty() && lonDouble == null)
                Text(
                    stringResource(R.string.ungultiger_langengrad),
                    color = Color(0xFFE74C3C),
                    fontSize = 12.sp
                )

            Button(
                onClick = { onCoordinatesSaved(latDouble!!, lonDouble!!) },
                enabled = isValid,
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.speichern_starten)) }
        }
    }
}

@Composable
fun SupabaseLoadingScreen() {
    AppBackground(scrim = AppBgScrim.LIGHT) {
        CircularProgressIndicator(
            color = Color.White,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
fun SupabaseLoginScreen(onLoggedIn: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AppBackground(scrim = AppBgScrim.LIGHT) {
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    stringResource(R.string.tabslify_login),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(Modifier.height(32.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(stringResource(R.string.e_mail)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedPlaceholderColor = Color.White,
                        unfocusedPlaceholderColor = Color.White,
                        focusedLabelColor = Color.White,
                        unfocusedLabelColor = Color.White,
                    )
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.passwort)) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedPlaceholderColor = Color.White,
                        unfocusedPlaceholderColor = Color.White,
                        focusedLabelColor = Color.White,
                        unfocusedLabelColor = Color.White,
                    )
                )
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .padding(top = 8.dp)
                ) {
                    error?.let {
                        val formattedError = when {
                            it.contains("invalid_credentials") -> stringResource(R.string.invalid_credentials)
                            else -> it
                        }
                        Text(formattedError, color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Button(onClick = {
                    loading = true
                    scope.launch {
                        try {
                            client.auth.signInWith(Email) {
                                this.email = email; this.password = password
                            }
                            onLoggedIn()
                        } catch (e: Exception) {
                            error = e.message
                        } finally {
                            loading = false
                        }
                    }
                }, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                    Text(if (loading) "..." else stringResource(R.string.anmelden))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LandingPage(
    onTabSelected: (MenuItem) -> Unit,
    showCloseButton: Boolean = false,
    onClose: () -> Unit = {},
    state: LazyListState = rememberLazyListState(),
    reloadTrigger: Int = 0
) {
    val context = LocalContext.current
    var recentTabs by remember { mutableStateOf(loadRecentTabs(context)) }
    var showSettings by remember { mutableStateOf(false) }

    LaunchedEffect(reloadTrigger) {
        recentTabs = loadRecentTabs(context)
    }
    val titleMap = MenuItem.entries.associateWith { stringResource(it.titleRes) }

    val allTabsSorted = remember(titleMap) {
        MenuItem.entries.filter {
            it != MenuItem.APKM_INSTALLER &&
                    (prvt() || (it != MenuItem.GMAIL && it != MenuItem.PRIVATE_CLOUD && it != MenuItem.REMOTEDESKTOP && it != MenuItem.PC_MANAGER && it != MenuItem.FOCUSGUARD && it != MenuItem.HEISE_NEWS))
        }.sortedBy { titleMap[it] }
    }
    val currentHour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val neonOrange = c()
    val neonGlow = when (currentHour) {
        in 11..16 -> Color(0xFF2C2C2C)
        else -> Color(0xFF00177E)
    }

    val txtcolors = Color.White

    AppBackground(scrim = AppBgScrim.MEDIUM) {
        CompositionLocalProvider(LocalContentColor provides txtcolors) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp, top = 40.dp)
                ) {
                    if (showCloseButton) {
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.align(Alignment.CenterStart)
                        ) {
                            Text(
                                "✕",
                                color = txtcolors,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.tabslify),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Center),
                        textAlign = TextAlign.Center
                    )

                    var showNotifications by remember { mutableStateOf(false) }

                    Row(
                        modifier = Modifier.align(Alignment.CenterEnd),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (prvt()) {
                            IconButton(onClick = { showNotifications = true }) {
                                Icon(
                                    Icons.Default.Notifications,
                                    contentDescription = stringResource(R.string.open_notification_frame),
                                    tint = Color.White
                                )
                            }
                        }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = stringResource(R.string.open_settings_frame),
                                tint = Color.White
                            )
                        }
                    }

                    Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                        DropdownMenu(
                            expanded = showNotifications,
                            onDismissRequest = { showNotifications = false },
                            containerColor = Color.Black
                        ) {
                            if (inAppNotifications.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.keine_benachrichtigungen)) },
                                    onClick = { showNotifications = false }
                                )
                            } else {
                                inAppNotifications.forEach { notif ->
                                    DropdownMenuItem(
                                        text = { Text(notif) },
                                        onClick = { showNotifications = false }
                                    )
                                }
                            }
                        }
                    }
                }

                LazyColumn(
                    state = state,
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    if (recentTabs.isNotEmpty()) {
                        item(key = "recent_header") {
                            Text(
                                text = stringResource(R.string.zuletzt_verwendet),
                                style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Default),
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                        }
                        items(items = recentTabs, key = { "recent_${it.ordinal}" }) { menuItem ->
                            TabCard(
                                menuItem = menuItem,
                                onClick = { onTabSelected(menuItem) },
                                neonGlow,
                                neonOrange
                            )
                        }
                        item(key = "divider") {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 16.dp),
                                thickness = 1.dp,
                                color = Color.White.copy(alpha = 0.3f)
                            )
                            Text(
                                text = stringResource(R.string.alle_tabs),
                                style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Default),
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                        }
                    }
                    items(items = allTabsSorted, key = { "all_${it.ordinal}" }) { menuItem ->
                        TabCard(
                            menuItem = menuItem,
                            onClick = { onTabSelected(menuItem) },
                            neonGlow,
                            neonOrange
                        )
                    }
                }
            }
        }
    }

    SettingsFrame(
        visible = showSettings,
        onClose = { showSettings = false }
    )
}

@Composable
fun LanguageSelectionDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val current = remember { Config.currentAppLanguage(context) }
    val options = listOf(
        "" to stringResource(R.string.language_system),
        "de" to stringResource(R.string.language_german),
        "en" to stringResource(R.string.language_english)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF2A2A2A),
        title = {
            Text(
                text = stringResource(R.string.language_title),
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                options.forEach { (tag, label) ->
                    val selected = tag == current
                    TextButton(
                        onClick = {
                            Config.setAppLanguage(context, tag)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (selected) "✓ $label" else label,
                            color = if (selected) Color(0xFF4CAF50) else Color.White,
                            fontSize = 16.sp,
                            textAlign = TextAlign.Start,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.abbrechen), color = Color.Gray)
            }
        }
    )
}

@Composable
fun TabCard(
    menuItem: MenuItem,
    onClick: () -> Unit,
    neonGlow: Color,
    neonOrange: Color
) {
    val alpha = remember { Animatable(0f) }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    var isClicked by remember { mutableStateOf(false) }
    val glowAlphaState = animateFloatAsState(
        targetValue = when {
            isClicked -> 0f
            isPressed -> 0.4f
            else -> 1f
        },
        animationSpec = tween(if (isClicked) 200 else if (isPressed) 80 else 250)
    )

    val glowPaint = remember {
        Paint().apply {
            isAntiAlias = true
            maskFilter = android.graphics.BlurMaskFilter(
                18f, android.graphics.BlurMaskFilter.Blur.OUTER
            )
        }
    }
    val orangePaint = remember {
        Paint().apply {
            isAntiAlias = true
            maskFilter = android.graphics.BlurMaskFilter(
                10f, android.graphics.BlurMaskFilter.Blur.OUTER
            )
        }
    }

    LaunchedEffect(Unit) {
        alpha.animateTo(1f, animationSpec = tween(300))
    }

    Box(
        Modifier
            .graphicsLayer { this.alpha = alpha.value }
            .drawBehind {
                val a = glowAlphaState.value
                glowPaint.color = neonGlow.copy(alpha = 0.6f * a).toArgb()
                orangePaint.color = neonOrange.copy(alpha = 0.75f * a).toArgb()

                val canvasSize = size
                val cornerRadius = 8.dp.toPx()

                drawContext.canvas.nativeCanvas.apply {
                    drawRoundRect(
                        0f, 0f, canvasSize.width, canvasSize.height,
                        cornerRadius, cornerRadius,
                        glowPaint
                    )
                    drawRoundRect(
                        0f, 0f, canvasSize.width, canvasSize.height,
                        cornerRadius, cornerRadius,
                        orangePaint
                    )
                }
            }
            .border(
                width = 1.5.dp,
                color = neonOrange.copy(alpha = glowAlphaState.value),
                shape = RoundedCornerShape(8.dp)
            )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primary.copy(
                    alpha = 0.6f
                )
            ),
            shape = RoundedCornerShape(8.dp),
            onClick = {
                isClicked = true
                onClick()
            },
            interactionSource = interactionSource
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(menuItem.titleRes).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Left
                )
                Text(
                    text = menuItem.icon,
                    fontSize = 32.sp,
                    modifier = Modifier.padding(end = 16.dp)
                )
            }
        }
    }
}

private fun isUsageStatsGranted(context: Context): Boolean {
    val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val now = System.currentTimeMillis()
    val stats = try {
        usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 60_000, now)
    } catch (_: SecurityException) {
        return false
    }
    return stats != null && stats.isNotEmpty()
}

fun isPermissionGranted(context: Context, key: String): Boolean {
    fun granted(p: String) =
        ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
    return when (key) {
        "READ_MEDIA_AUDIO" ->
            granted(Manifest.permission.READ_MEDIA_AUDIO)

        "POST_NOTIFICATIONS" -> canNotify(context)

        "ACCESS_COARSE_LOCATION / ACCESS_FINE_LOCATION" ->
            granted(Manifest.permission.ACCESS_COARSE_LOCATION) ||
                    granted(Manifest.permission.ACCESS_FINE_LOCATION)

        "ACCESS_BACKGROUND_LOCATION" -> granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)

        "ACTIVITY_RECOGNITION" -> granted(Manifest.permission.ACTIVITY_RECOGNITION)

        "SYSTEM_ALERT_WINDOW" -> Settings.canDrawOverlays(context)

        "PACKAGE_USAGE_STATS" -> isUsageStatsGranted(context)

        "FOREGROUND_SERVICE" -> granted(Manifest.permission.FOREGROUND_SERVICE)

        "READ_MEDIA_IMAGES / READ_MEDIA_VIDEO" ->
            granted(Manifest.permission.READ_MEDIA_IMAGES) ||
                    granted(Manifest.permission.READ_MEDIA_VIDEO) ||
                    granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)

        "READ_CONTACTS / WRITE_CONTACTS" ->
            granted(Manifest.permission.READ_CONTACTS) &&
                    granted(Manifest.permission.WRITE_CONTACTS)

        "CAMERA" -> granted(Manifest.permission.CAMERA)

        "RECEIVE_BOOT_COMPLETED" -> granted(Manifest.permission.RECEIVE_BOOT_COMPLETED)

        "RECORD_AUDIO" -> granted(Manifest.permission.RECORD_AUDIO)

        "REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" ->
            (context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager)
                .isIgnoringBatteryOptimizations(context.packageName)

        "BLUETOOTH_CONNECT" -> granted(Manifest.permission.BLUETOOTH_CONNECT)

        "READ_PHONE_STATE / READ_BASIC_PHONE_STATE" ->
            granted(Manifest.permission.READ_PHONE_STATE) ||
                    granted(Manifest.permission.READ_BASIC_PHONE_STATE)

        "READ_SMS" -> granted(Manifest.permission.READ_SMS)

        "MANAGE_EXTERNAL_STORAGE" -> Environment.isExternalStorageManager()

        "ACCESS_NOTIFICATION_POLICY" ->
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager)
                .isNotificationPolicyAccessGranted

        "ACCESS_SUPERUSER" -> granted("android.permission.ACCESS_SUPERUSER")

        "SET_ALARM" -> granted("com.android.alarm.permission.SET_ALARM")

        "ACCESS_WIFI_STATE" -> granted(Manifest.permission.ACCESS_WIFI_STATE)

        "ACCESS_NETWORK_STATE" -> granted(Manifest.permission.ACCESS_NETWORK_STATE)

        else -> false
    }
}

@Composable
private fun PermissionButton(
    txt: String,
    isSelected: Boolean,
    permRefreshKey: Int,
    usageStatsGranted: Boolean?,
    buttonShape: RoundedCornerShape,
    borderBrush: Brush,
    onShowDetails: () -> Unit,
    onGrant: () -> Unit
) {
    val context = LocalContext.current
    val syncGranted = remember(permRefreshKey, txt) {
        if (txt == "PACKAGE_USAGE_STATS") null else isPermissionGranted(context, txt)
    }
    val granted = syncGranted ?: usageStatsGranted ?: false
    val requestable = remember(txt) { Config.isPermissionRequestable(txt) }

    Button(
        onClick = onShowDetails,
        shape = buttonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) APP_COLOR.copy(
                alpha = 0.3f
            ) else Color.Transparent
        ),
        modifier = Modifier
            .padding(vertical = 5.dp)
            .fillMaxWidth()
            .clip(buttonShape)
            .background(if (isSelected) APP_COLOR.copy(alpha = 0.3f) else APP_COLOR)
            .animateContentSize()
            .border(
                BorderStroke(1.dp, borderBrush),
                buttonShape
            )
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(txt, fontSize = 13.sp, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100))
                        .background(
                            if (granted) Color(0xFF00C853).copy(alpha = 0.22f)
                            else Color.White.copy(alpha = 0.10f)
                        )
                        .padding(horizontal = 9.dp, vertical = 3.dp)
                ) {
                    Text(
                        if (granted) "● Erteilt" else "○ Nicht erteilt",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (granted) Color(0xFF69F0AE)
                        else Color.White.copy(alpha = 0.55f)
                    )
                }
                Spacer(Modifier.weight(1f))
                if (!granted && requestable) {
                    TextButton(
                        onClick = onGrant,
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Text(
                            stringResource(R.string.erteilen),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = stringResource(R.string.open_information),
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
fun PermissionInfoScreen(onboarding: Boolean = false, onClose: (() -> Unit)? = null) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var permRefreshKey by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permRefreshKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permRefreshKey++ }
    var selectedPermission by remember { mutableStateOf<String?>(null) }
    var titleDialog by remember { mutableStateOf("") }
    var usagesDialog by remember { mutableStateOf<List<String>>(emptyList()) }

    val readMediaAudio1 = stringResource(R.string.mediaplayerservice_tab_songs_podcasts_abspielen)
    val readMediaAudio2 = stringResource(R.string.spotifydownloader_speichern_von_audiodateien_im)
    val postNotifications1 =
        stringResource(R.string.quiethoursnotificationservice_anzeigen_von_quiet_hours)
    val postNotifications2 = stringResource(R.string.mediaplayerservice_wiedergabenotifikationen)
    val postNotifications3 = stringResource(R.string.status_updates_fur_laufende_downloads)
    val postNotifications4 = stringResource(R.string.chargingtrackerservice_ladeinformationen)
    val postNotifications5 = stringResource(R.string.networkinfo_netzwerkinfos)
    val postNotifications6 = stringResource(R.string.batteryinfo_batterieinformationen)
    val location1 = stringResource(R.string.standortbasierte_wettervorhersage_im_weathertab)
    val location2 =
        stringResource(R.string.exploretab_explorelocationtracker_standortverfolgung_und_geofencing)
    val location3 = stringResource(R.string.shownetwerkinfo_anzeige_der_wlan_ssid)
    val activityRecognition1 = stringResource(R.string.exploretab_activityrecognition_erkennung_des_verkehrsmittels)
    val systemAlertWindow1 =
        stringResource(R.string.quiethoursnotificationservice_test_overlay_fur_youtube)
    val foregroundService1 =
        stringResource(R.string.mediaplayerservice_medienwiedergabe_im_vordergrund)
    val foregroundService2 =
        stringResource(R.string.quiethoursnotificationservice_dauerhafte_benachrichtigungen)
    val foregroundService3 = stringResource(R.string.chargingtrackerservice_ladeuberwachung)
    val foregroundService4 = stringResource(R.string.audioforegroundservice_audioaufnahmen)
    val readMediaImagesVideo1 = stringResource(R.string.gallerytab_anzeige_von_bildern_und)
    val contacts1 = stringResource(R.string.contactstab_laden_speichern_und_loschen)
    val bootCompleted1 =
        stringResource(R.string.bootreceiver_starten_von_quiethoursnotificationservice_beim)
    val recordAudio1 =
        stringResource(R.string.audiorecordertab_audioforegroundservice_audioaufnahmen_mit_mikrofon)
    val ignoreBattery1 =
        stringResource(R.string.quiethoursnotificationservice_anfrage_zum_ignorieren_von)
    val camera1 = stringResource(R.string.authenticator_scannen_von_qr_codes)
    val camera2 = stringResource(R.string.fitnesstab_zahlen_von_push_ups)
    val keineAngabenHinterlegt = stringResource(R.string.keine_angaben_hinterlegt)
    val bluetoothConnect1 =
        stringResource(R.string.reportdeviceinformation_bluetooth_verbindungsstatus)
    val phoneState1 =
        stringResource(R.string.reportdeviceinformation_ismobiledataactive_mobilfunk_datenstatus)
    val readSms1 = stringResource(R.string.startsmslistener_abruf_des_sms_posteingangs)
    val setAlarm1 = stringResource(R.string.wifi_direct_set_alarm_befehl_vom_notebook)
    val wifiState1 = stringResource(R.string.reportdeviceinformation_wlan_status_hotspot)
    val networkState1 = stringResource(R.string.reportdeviceinformation_netzwerkstatus_konnektivitat)
    val focusguardUsageDesc = stringResource(R.string.focusguard_perm_usage_desc)
    val focusguardOverlayDesc = stringResource(R.string.focusguard_perm_overlay_desc)
    val focusguardNotifDesc = stringResource(R.string.focusguard_perm_notifications_desc)

    val permissionUsages = remember(
        readMediaAudio1,
        readMediaAudio2,
        postNotifications1,
        postNotifications2,
        postNotifications3,
        postNotifications4,
        postNotifications5,
        postNotifications6,
        location1,
        location2,
        location3,
        activityRecognition1,
        systemAlertWindow1,
        foregroundService1,
        foregroundService2,
        foregroundService3,
        foregroundService4,
        readMediaImagesVideo1,
        contacts1,
        bootCompleted1,
        recordAudio1,
        ignoreBattery1,
        camera1,
        camera2,
        bluetoothConnect1,
        phoneState1,
        readSms1,
        setAlarm1,
        wifiState1,
        networkState1,
        focusguardUsageDesc,
        focusguardOverlayDesc,
        focusguardNotifDesc
    ) {
        mapOf(
            "READ_MEDIA_AUDIO" to listOf(
                readMediaAudio1,
                readMediaAudio2
            ),
            "POST_NOTIFICATIONS" to listOf(
                postNotifications1,
                postNotifications2,
                postNotifications3,
                postNotifications4,
                postNotifications5,
                postNotifications6,
                focusguardNotifDesc
            ),
            "ACCESS_COARSE_LOCATION / ACCESS_FINE_LOCATION" to listOf(
                location1,
                location2,
                location3
            ),
            "ACCESS_BACKGROUND_LOCATION" to listOf(
                location2
            ),
            "ACTIVITY_RECOGNITION" to listOf(
                activityRecognition1
            ),
            "SYSTEM_ALERT_WINDOW" to listOf(
                systemAlertWindow1,
                focusguardOverlayDesc
            ),
            "PACKAGE_USAGE_STATS" to listOf(
                focusguardUsageDesc
            ),
            "FOREGROUND_SERVICE" to listOf(
                foregroundService1,
                foregroundService2,
                foregroundService3,
                foregroundService4
            ),
            "READ_MEDIA_IMAGES / READ_MEDIA_VIDEO" to listOf(
                readMediaImagesVideo1
            ),
            "READ_CONTACTS / WRITE_CONTACTS" to listOf(
                contacts1
            ),
            "RECEIVE_BOOT_COMPLETED" to listOf(
                bootCompleted1
            ),
            "RECORD_AUDIO" to listOf(
                recordAudio1
            ),
            "REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" to listOf(
                ignoreBattery1
            ),
            "CAMERA" to listOf(
                camera1,
                camera2
            ),
            "BLUETOOTH_CONNECT" to listOf(
                bluetoothConnect1
            ),
            "READ_PHONE_STATE / READ_BASIC_PHONE_STATE" to listOf(
                phoneState1
            ),
            "READ_SMS" to listOf(
                readSms1
            ),
            "SET_ALARM" to listOf(
                setAlarm1
            ),
            "ACCESS_WIFI_STATE" to listOf(
                wifiState1
            ),
            "ACCESS_NETWORK_STATE" to listOf(
                networkState1
            )
        )
    }

    Box(
        Modifier
            .fillMaxSize()
            .then(
                if (onboarding) Modifier.systemGestureExclusion()
                else Modifier.background(Color(0xFF0C1017))
            )
    ) {
        val hazeState = remember { HazeState() }
        var headerHeightDp by remember { mutableStateOf(0.dp) }
        val density = LocalDensity.current

        val usageStatsCache = remember { mutableStateOf<Boolean?>(null) }
        val usageStatsGranted by produceState<Boolean?>(initialValue = usageStatsCache.value, permRefreshKey) {
            value = withContext(Dispatchers.IO) { isUsageStatsGranted(context.applicationContext) }
            usageStatsCache.value = value
        }
        val permissionButtonShape = remember { RoundedCornerShape(22.dp) }
        val permissionBorderBrush = remember {
            Brush.linearGradient(colors = listOf(Color(0xFFFF368A), Color(0xFF7C4DFF)))
        }
        val allPermissionKeys = remember {
            buildList {
                add("READ_MEDIA_AUDIO")
                add("POST_NOTIFICATIONS")
                add("ACCESS_COARSE_LOCATION / ACCESS_FINE_LOCATION")
                add("ACCESS_BACKGROUND_LOCATION")
                add("ACTIVITY_RECOGNITION")
                add("SYSTEM_ALERT_WINDOW")
                add("PACKAGE_USAGE_STATS")
                add("FOREGROUND_SERVICE")
                add("READ_MEDIA_IMAGES / READ_MEDIA_VIDEO")
                add("READ_CONTACTS / WRITE_CONTACTS")
                add("CAMERA")
                add("RECEIVE_BOOT_COMPLETED")
                add("RECORD_AUDIO")
                add("REQUEST_IGNORE_BATTERY_OPTIMIZATIONS")
                if (prvt()) {
                    add("BLUETOOTH_CONNECT")
                    add("READ_PHONE_STATE / READ_BASIC_PHONE_STATE")
                    add("READ_SMS")
                    add("MANAGE_EXTERNAL_STORAGE")
                    add("ACCESS_NOTIFICATION_POLICY")
                    add("ACCESS_SUPERUSER")
                    add("SET_ALARM")
                    add("ACCESS_WIFI_STATE")
                    add("ACCESS_NETWORK_STATE")
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState)
                .verticalScroll(rememberScrollState())
                .then(
                    if (!onboarding) Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                    else Modifier
                )
                .padding(horizontal = 15.dp)
        ) {
            Spacer(Modifier.height(headerHeightDp))

            Button(
                onClick = {
                    Config.requestAllRuntimePermissions(allPermissionKeys, permissionLauncher)
                },
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.buttonColors(containerColor = APP_COLOR),
                modifier = Modifier
                    .padding(bottom = 10.dp)
                    .fillMaxWidth()
            ) {
                Text(stringResource(R.string.alle_berechtigungen_anfragen), fontWeight = FontWeight.Bold)
            }

            allPermissionKeys.forEach { permissionKey ->
                key(permissionKey) {
                    PermissionButton(
                        txt = permissionKey,
                        isSelected = selectedPermission == permissionKey,
                        permRefreshKey = permRefreshKey,
                        usageStatsGranted = usageStatsGranted,
                        buttonShape = permissionButtonShape,
                        borderBrush = permissionBorderBrush,
                        onShowDetails = {
                            selectedPermission = permissionKey
                            titleDialog = permissionKey
                            usagesDialog = permissionUsages[permissionKey] ?: listOf(keineAngabenHinterlegt)
                        },
                        onGrant = { Config.requestPermissionForKey(context, permissionKey, permissionLauncher) }
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned {
                    val newHeight = with(density) { it.size.height.toDp() }
                    if (newHeight != headerHeightDp) headerHeightDp = newHeight
                }
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .hazeEffect(
                        state = hazeState,
                        style = HazeStyle(
                            backgroundColor = Color(0xFF0C1017),
                            tint = HazeTint(Color(0xFF0C1017).copy(alpha = 0.7f)),
                            blurRadius = 60.dp,
                            noiseFactor = 0f
                        )
                    ) {
                        progressive = HazeProgressive.verticalGradient(
                            startIntensity = 1f,
                            endIntensity = 0f,
                            preferPerformance = true
                        )
                    }
                    .drawWithContent {
                        drawContent()
                        val fadePx = 24.dp.toPx()
                        val bottomStop = (1f - fadePx / size.height).coerceIn(0f, 1f)
                        drawRect(
                            brush = Brush.verticalGradient(
                                0f to Color.Black,
                                bottomStop to Color.Black,
                                1f to Color.Transparent
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    }
            )

            Column(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 15.dp)
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.tabslify_berechtigungen),
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 5.dp),
                        fontSize = 22.sp
                    )
                    if (onClose != null) {
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.background(
                                Color.White.copy(alpha = 0.1f),
                                CircleShape
                            )
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.close),
                                tint = Color.White
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                val introTabslifyIst = stringResource(R.string.tabslify_ist)
                val introNichtNurApp = stringResource(R.string.nicht_nur_irgendeine_app)
                val introSieIstEin = stringResource(R.string.sie_ist_ein)
                val introMixAusVielen = stringResource(R.string.mix_aus_ganz_vielen)
                val introAppsUnterAnderem = stringResource(R.string.apps_unter_anderem_fur_die)
                val introVieleBerechtigungen = stringResource(R.string.jeweils_viele_berechtigungen)
                val introBenutzererlebnis = stringResource(R.string.um_das_benutzererlebnis)
                val introUnkompliziert = stringResource(R.string.moglichst_unkompliziert)
                val introZuHalten = stringResource(R.string.zu_halten)
                Text(
                    text = buildAnnotatedString {
                        append(introTabslifyIst)
                        withStyle(
                            SpanStyle(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFFFF368A),
                                        Color(0xFF7C4DFF)
                                    )
                                )
                            )
                        ) {
                            append(introNichtNurApp)
                        }
                        append(introSieIstEin)
                        withStyle(
                            SpanStyle(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFFFF368A),
                                        Color(0xFF7C4DFF)
                                    )
                                )
                            )
                        ) {
                            append(introMixAusVielen)
                        }
                        append(introAppsUnterAnderem)
                        withStyle(
                            SpanStyle(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFFFF368A),
                                        Color(0xFF7C4DFF)
                                    )
                                )
                            )
                        ) {
                            append(introVieleBerechtigungen)
                        }
                        append(introBenutzererlebnis)
                        withStyle(
                            SpanStyle(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFFFF368A),
                                        Color(0xFF7C4DFF)
                                    )
                                )
                            )
                        ) {
                            append(introUnkompliziert)
                        }
                        append(introZuHalten)
                    },
                    color = APP_COLOR.copy(
                        red = APP_COLOR.red + .6f,
                        green = APP_COLOR.green + .6f,
                        blue = APP_COLOR.blue + .6f
                    ),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    fontFamily = FontFamily.Default
                )
            }
        }

        AnimatedVisibility(
            visible = selectedPermission != null,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            DialogTabslify(
                onConfirm = { selectedPermission = null },
                onDismiss = { selectedPermission = null },
                title = titleDialog,
                text = usagesDialog.joinToString("\n\n") { "•  $it" },
                confirmText = stringResource(R.string.schliesen),
                oneButton = true,
                modifier = Modifier.fillMaxWidth(0.9f)
            )
        }
    }
}

private fun formatBytes(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1f -> String.format(Locale.US, "%.2f GB", gb)
        mb >= 1f -> String.format(Locale.US, "%.2f MB", mb)
        kb >= 1f -> String.format(Locale.US, "%.2f KB", kb)
        else -> "$bytes B"
    }
}

@Composable
fun LiveTrafficPanel(modifier: Modifier = Modifier) {
    var rxRate by remember { mutableFloatStateOf(0f) }
    var txRate by remember { mutableFloatStateOf(0f) }
    var rxTotal by remember { mutableLongStateOf(TrafficStats.getTotalRxBytes()) }
    var txTotal by remember { mutableLongStateOf(TrafficStats.getTotalTxBytes()) }

    LaunchedEffect(Unit) {
        var lastRx = TrafficStats.getTotalRxBytes()
        var lastTx = TrafficStats.getTotalTxBytes()
        while (true) {
            delay(1000.milliseconds)
            val curRx = TrafficStats.getTotalRxBytes()
            val curTx = TrafficStats.getTotalTxBytes()
            rxRate = (curRx - lastRx).coerceAtLeast(0).toFloat()
            txRate = (curTx - lastTx).coerceAtLeast(0).toFloat()
            rxTotal = curRx
            txTotal = curTx
            lastRx = curRx
            lastTx = curTx
        }
    }

    NeonBox(
        modifier = modifier.fillMaxWidth(),
        neonColors = listOf(Color(0xFFFF8A00), Color(0xFFFF0066)),
        backgroundAlpha = 0.15f
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Text(
                "📶 Live Traffic (Test)",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Starte einen Download - unten sollte Rx/s hochschießen.",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("⬇️ Rx", color = Color(0xFF4CAF50), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("${formatBytes(rxRate.toLong())}/s", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("total ${formatBytes(rxTotal)}", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("⬆️ Tx", color = Color(0xFF2196F3), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("${formatBytes(txRate.toLong())}/s", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("total ${formatBytes(txTotal)}", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun SettingsFrame(
    visible: Boolean,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var directedToSettings by remember { mutableStateOf(false) }
    val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var showPermissionInfo by remember { mutableStateOf(false) }
    var animatePermissionInfo by remember { mutableStateOf(false) }
    var showCoordinatesEdit by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    var backupLastMs by remember { mutableLongStateOf(prefs.getLong("last_prefs_backup_ms", 0L)) }
    var backupBusy by remember { mutableStateOf(false) }
    var showRestoreList by remember { mutableStateOf(false) }
    var backupList by remember { mutableStateOf<List<BackupEntry>>(emptyList()) }
    var restoreTarget by remember { mutableStateOf<BackupEntry?>(null) }

    val backupDoneMsg = stringResource(R.string.cloud_backup_done)
    val backupNoMasterMsg = stringResource(R.string.cloud_backup_no_master)
    val backupShrinkMsg = stringResource(R.string.cloud_backup_shrink_warn)
    val backupWrongPwMsg = stringResource(R.string.cloud_backup_wrong_pw)
    val backupFailedMsg = stringResource(R.string.cloud_backup_failed)
    val backupRestoredMsg = stringResource(R.string.cloud_backup_restored)

    fun outcomeMessage(outcome: BackupOutcome): String = when (outcome) {
        BackupOutcome.DONE -> backupDoneMsg
        BackupOutcome.NO_MASTER -> backupNoMasterMsg
        BackupOutcome.SHRINK_BLOCKED -> backupShrinkMsg
        BackupOutcome.WRONG_PASSWORD -> backupWrongPwMsg
        BackupOutcome.CORRUPT -> backupWrongPwMsg
        BackupOutcome.EMPTY -> backupFailedMsg
        BackupOutcome.FAILED -> backupFailedMsg
    }

    BackHandler {
        if (showCoordinatesEdit) {
            showCoordinatesEdit = false
        } else if (showPermissionInfo) {
            showPermissionInfo = false
        } else {
            onClose()
        }
    }

    var masterEnabled by remember { mutableStateOf(prefs.getBoolean("services_master", true)) }
    var servicesExpanded by remember { mutableStateOf(false) }

    var serviceQhns by remember { mutableStateOf(prefs.getBoolean("service_qhns", true)) }
    var serviceWh by remember { mutableStateOf(prefs.getBoolean("service_wh", true)) }
    var serviceCharge by remember { mutableStateOf(prefs.getBoolean("service_charge", true)) }
    var serviceBattery by remember { mutableStateOf(prefs.getBoolean("service_battery", true)) }
    var serviceFocusguard by remember { mutableStateOf(prefs.getBoolean("service_focusguard", true)) }

    var aiPrefGlobal by remember {
        mutableStateOf(
            prefs.getString("ai_pref_global", "gemini") ?: "gemini"
        )
    }
    var aiPrefChat by remember {
        mutableStateOf(
            prefs.getString("ai_pref_service_chat", "default") ?: "default"
        )
    }
    var aiPrefMusicSummary by remember {
        mutableStateOf(
            prefs.getString(
                "ai_pref_service_music_summary",
                "default"
            ) ?: "default"
        )
    }
    var aiPrefVision by remember {
        mutableStateOf(
            prefs.getString(
                "ai_pref_service_vision",
                "default"
            ) ?: "default"
        )
    }
    var aiPrefReplies by remember {
        mutableStateOf(
            prefs.getString(
                "ai_pref_service_replies",
                "default"
            ) ?: "default"
        )
    }
    var aiSettingsExpanded by remember { mutableStateOf(false) }

    val apiKeyDefs = remember {
        listOf(
            "NVIDIA API-Key" to "api_key_nvidia",
            "TMDB API-Key" to "api_key_tmdb",
            "WeatherAPI-Key" to "api_key_weatherapi",
            "RapidAPI-Key (Spotify)" to "api_key_rapidapi",
            "PodcastIndex-Key" to "api_key_podcastindex",
            "PodcastIndex-Secret" to "api_key_podcastindex_secret",
            "DB Client-ID (Bahn)" to "api_key_db_client_id",
            "DB API-Key (Bahn)" to "api_key_db_api_key"
        )
    }
    val apiKeyValues = remember {
        mutableStateMapOf<String, String>().apply {
            apiKeyDefs.forEach { (_, key) -> put(key, prefs.getString(key, "") ?: "") }
        }
    }
    var apiKeysExpanded by remember { mutableStateOf(false) }

    var hasNotificationPermission by remember { mutableStateOf(true) }
    var intendedToEnableQhns by remember { mutableStateOf(false) }

    var showMediaAnalyticsDialog by remember { mutableStateOf(false) }
    var mediaAnalyticsEnabled by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        MediaAnalyticsManager.init(context.applicationContext)
        mediaAnalyticsEnabled = MediaAnalyticsManager.isAnalyticsEnabled()
    }

    fun applyService(cls: Class<*>, enabled: Boolean) {
        if (cls == WhatsAppNotificationListener::class.java) return

        val intent = Intent(context, cls)
        val permissionOk =
            if (cls == QuietHoursNotificationService::class.java ||
                cls == FocusGuardService::class.java
            ) hasNotificationPermission else true

        if (enabled && masterEnabled && permissionOk) {
            when (cls) {
                QuietHoursNotificationService::class.java,
                ChargingTrackerService::class.java,
                FocusGuardService::class.java -> ContextCompat.startForegroundService(
                    context,
                    intent
                )

                else -> context.startService(intent)
            }
        } else {
            context.stopService(intent)
        }
    }

    fun save() {
        prefs.edit {
            putBoolean("services_master", masterEnabled)
                .putBoolean("service_qhns", serviceQhns)
                .putBoolean("service_wh", serviceWh)
                .putBoolean("service_charge", serviceCharge)
                .putBoolean("service_battery", serviceBattery)
                .putBoolean("service_focusguard", serviceFocusguard)
                .putString("ai_pref_global", aiPrefGlobal)
                .putString("ai_pref_service_chat", aiPrefChat)
                .putString("ai_pref_service_music_summary", aiPrefMusicSummary)
                .putString("ai_pref_service_vision", aiPrefVision)
                .putString("ai_pref_service_replies", aiPrefReplies)
        }

        applyService(QuietHoursNotificationService::class.java, serviceQhns)
        applyService(ChargingTrackerService::class.java, serviceCharge)
        applyService(FocusGuardService::class.java, serviceFocusguard)

        if (masterEnabled && serviceBattery) {
            startBatteryWorker(context)
        } else {
            stopBatteryWorker(context)
        }

        if (masterEnabled && serviceFocusguard) {
            scheduleFocusGuardWorkers(context)
            scheduleFocusGuardDailySummary(context)
        } else {
            cancelFocusGuardWorkers(context)
        }
    }

    fun checkPermissionsAndSyncServices() {
        val permitted = canNotify(context)
        hasNotificationPermission = permitted

        masterEnabled = prefs.getBoolean("services_master", true)
        serviceWh = prefs.getBoolean("service_wh", true)
        serviceCharge = prefs.getBoolean("service_charge", true)
        serviceBattery = prefs.getBoolean("service_battery", true)

        if (permitted) {
            serviceQhns = if (intendedToEnableQhns) true else prefs.getBoolean("service_qhns", true)
            serviceFocusguard = prefs.getBoolean("service_focusguard", true)
            intendedToEnableQhns = false
        } else {
            serviceQhns = false
            serviceFocusguard = false
            intendedToEnableQhns = false
        }
        save()
    }

    val manualPerm = {
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            val intent =
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }

            context.startActivity(intent)
            directedToSettings = true
            intendedToEnableQhns = true
        }
    }

    DisposableEffect(lifecycleOwner) {
        var isFirstResume = true
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                println("YEP22")
                if (isFirstResume) {
                    println("YEP")
                    isFirstResume = false
                    return@LifecycleEventObserver
                }
                checkPermissionsAndSyncServices()
                directedToSettings = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(visible) {
        if (visible) {
            checkPermissionsAndSyncServices()
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.einstellungen),
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.background(Color.White.copy(alpha = 0.1f), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.close),
                            tint = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                NeonBox(
                    modifier = Modifier.fillMaxWidth(),
                    neonColors = listOf(Color(0xFF00FFAA), Color(0xFF00CCFF)),
                    backgroundAlpha = 0.15f
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showLanguageDialog = true }
                                .padding(20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.settings_language),
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = stringResource(R.string.language_title),
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 12.sp
                                )
                            }

                            Box {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForwardIos,
                                    contentDescription = stringResource(R.string.koordinaten_andern),
                                    tint = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                if (Config.masterPassword.isBlank()) {
                    Spacer(modifier = Modifier.height(20.dp))

                    NeonBox(
                        modifier = Modifier.fillMaxWidth(),
                        neonColors = listOf(Color(0xFF00FFAA), Color(0xFFFFB300)),
                        backgroundAlpha = 0.15f
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                            Text(
                                text = stringResource(R.string.cloud_backup_title),
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = when {
                                    Config.masterPassword.isBlank() ->
                                        stringResource(R.string.cloud_backup_no_master)
                                    backupLastMs > 0L ->
                                        stringResource(R.string.cloud_backup_last) + " " +
                                                android.text.format.DateUtils
                                                    .getRelativeTimeSpanString(backupLastMs)
                                    else -> stringResource(R.string.cloud_backup_never)
                                },
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        if (backupBusy) return@Button
                                        backupBusy = true
                                        scope.launch {
                                            val outcome =
                                                PrefsBackup.backupNow(context, force = true)
                                            if (outcome == BackupOutcome.DONE) {
                                                backupLastMs =
                                                    prefs.getLong("last_prefs_backup_ms", 0L)
                                            }
                                            toast(context, outcomeMessage(outcome))
                                            backupBusy = false
                                        }
                                    },
                                    enabled = !backupBusy && Config.masterPassword.isNotBlank(),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(stringResource(R.string.cloud_backup_now))
                                }
                                Button(
                                    onClick = {
                                        if (backupBusy) return@Button
                                        backupBusy = true
                                        scope.launch {
                                            backupList = PrefsBackup.listBackups()
                                            backupBusy = false
                                            showRestoreList = true
                                        }
                                    },
                                    enabled = !backupBusy && Config.masterPassword.isNotBlank(),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(stringResource(R.string.cloud_backup_restore))
                                }
                            }
                        }
                    }
                }

                if (showRestoreList) {
                    AlertDialog(
                        onDismissRequest = { showRestoreList = false },
                        title = { Text(stringResource(R.string.cloud_backup_restore)) },
                        text = {
                            if (backupList.isEmpty()) {
                                Text(stringResource(R.string.cloud_backup_never))
                            } else {
                                Column(
                                    modifier = Modifier.verticalScroll(rememberScrollState())
                                ) {
                                    val fmt = java.text.SimpleDateFormat(
                                        "dd.MM.yyyy HH:mm",
                                        LocalLocale.current.platformLocale
                                    )
                                    backupList.forEach { entry ->
                                        Text(
                                            text = fmt.format(java.util.Date(entry.createdAt)),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    restoreTarget = entry
                                                    showRestoreList = false
                                                }
                                                .padding(vertical = 12.dp)
                                        )
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showRestoreList = false }) {
                                Text(stringResource(R.string.close))
                            }
                        }
                    )
                }

                restoreTarget?.let { entry ->
                    AlertDialog(
                        onDismissRequest = { restoreTarget = null },
                        title = { Text(stringResource(R.string.cloud_backup_restore)) },
                        text = { Text(stringResource(R.string.cloud_backup_restore_confirm)) },
                        confirmButton = {
                            TextButton(onClick = {
                                restoreTarget = null
                                if (backupBusy) return@TextButton
                                backupBusy = true
                                scope.launch {
                                    val outcome = PrefsBackup.restore(context, entry.fileName)
                                    if (outcome == BackupOutcome.DONE) {
                                        backupLastMs =
                                            prefs.getLong("last_prefs_backup_ms", 0L)
                                    }
                                    toast(
                                        context,
                                        if (outcome == BackupOutcome.DONE)
                                            backupRestoredMsg
                                        else outcomeMessage(outcome)
                                    )
                                    backupBusy = false
                                }
                            }) {
                                Text(stringResource(R.string.cloud_backup_restore))
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { restoreTarget = null }) {
                                Text(stringResource(R.string.close))
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                NeonBox(
                    modifier = Modifier.fillMaxWidth(),
                    neonColors = listOf(Color(0xFF00E5FF), Color(0xFF7C4DFF)),
                    backgroundAlpha = 0.15f
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.hintergrunddienste),
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    stringResource(R.string.alle_hintergrundaktivitaten),
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 12.sp
                                )
                            }
                            Switch(
                                checked = masterEnabled,
                                onCheckedChange = { masterEnabled = it; save() },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                                    uncheckedThumbColor = Color.Gray,
                                    uncheckedTrackColor = Color.DarkGray,
                                    disabledUncheckedTrackColor = Color.DarkGray.copy(alpha = 0.4f)
                                )
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { servicesExpanded = !servicesExpanded }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(R.string.einzelne_dienste),
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                if (servicesExpanded) "▲" else "▼",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 12.sp
                            )
                        }

                        AnimatedVisibility(visible = servicesExpanded) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White.copy(alpha = 0.05f))
                                    .padding(horizontal = 20.dp, vertical = 8.dp)
                            ) {
                                val services = listOfNotNull(
                                    Triple(
                                        "QuietHoursNotificationService",
                                        stringResource(R.string.alles_rund_um_commands),
                                        serviceQhns
                                    ),
                                    if (prvt()) Triple(
                                        "WhatsAppNotificationListener",
                                        stringResource(R.string.verarbeitet_eingehende_whatsapps),
                                        serviceWh
                                    ) else null,
                                    Triple(
                                        "ChargingTracker",
                                        stringResource(R.string.verfolgt_ladevorgange_fur_ladedauer_schatzungen),
                                        serviceCharge
                                    ),
                                    Triple(
                                        "BatterySamplingWorker",
                                        stringResource(R.string.hintergrund_aufzeichnung_der_batteriedaten),
                                        serviceBattery
                                    ),
                                    Triple(
                                        "FocusGuardService",
                                        stringResource(R.string.focusguard_service_subtitle),
                                        serviceFocusguard
                                    )
                                )

                                services.forEachIndexed { index, (title, subtitle, checked) ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(title, color = Color.White, fontSize = 14.sp)
                                            Text(
                                                subtitle,
                                                color = Color.White.copy(alpha = 0.5f),
                                                fontSize = 11.sp
                                            )
                                            if (index == 0 && !hasNotificationPermission) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = stringResource(R.string.keine_benachrichtigungsberechtigung),
                                                    color = Color(0xFFFF5252),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        Switch(
                                            checked = checked && masterEnabled,
                                            onCheckedChange = { v ->
                                                when (title) {
                                                    "QuietHoursNotificationService" -> {
                                                        if (hasNotificationPermission) {
                                                            serviceQhns = v
                                                        } else {
                                                            manualPerm()
                                                        }
                                                    }

                                                    "WhatsAppNotificationListener" -> {
                                                        serviceWh = v
                                                    }

                                                    "ChargingTracker" -> {
                                                        serviceCharge = v
                                                    }

                                                    "BatterySamplingWorker" -> {
                                                        serviceBattery = v
                                                    }

                                                    "FocusGuardService" -> {
                                                        serviceFocusguard = v
                                                    }
                                                }
                                                save()
                                            },
                                            enabled = masterEnabled,
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = Color.White,
                                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                                uncheckedThumbColor = Color.Gray,
                                                uncheckedTrackColor = Color.DarkGray,
                                                disabledCheckedTrackColor = Color.DarkGray,
                                                disabledCheckedThumbColor = Color.Gray
                                            )
                                        )
                                    }
                                    if (index < services.lastIndex) {
                                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                NeonBox(
                    modifier = Modifier.fillMaxWidth(),
                    neonColors = listOf(Color(0xFF00FF00), Color(0xFF00CC00)),
                    backgroundAlpha = 0.15f
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.media_analytics),
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    stringResource(R.string.tracke_horgewohnheiten_fur_statistiken),
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 12.sp
                                )
                            }
                            Switch(
                                checked = mediaAnalyticsEnabled,
                                onCheckedChange = { newValue ->
                                    if (newValue) {
                                        // Just enable it immediately
                                        mediaAnalyticsEnabled = true
                                        MediaAnalyticsManager.setAnalyticsEnabled(true)
                                    } else {
                                        // Show confirmation dialog
                                        showMediaAnalyticsDialog = true
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                                    uncheckedThumbColor = Color.Gray,
                                    uncheckedTrackColor = Color.DarkGray,
                                    disabledUncheckedTrackColor = Color.DarkGray.copy(alpha = 0.4f)
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                LiveTrafficPanel()

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Spacer(
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                            .drawBehind {
                                drawIntoCanvas { canvas ->
                                    canvas.nativeCanvas.drawRect(
                                        0f, 0f, size.width, size.height,
                                        Paint().apply {
                                            color = Color(0xFFBEBEBE).copy(alpha = 0.3f).toArgb()
                                            isAntiAlias = true
                                            maskFilter = android.graphics.BlurMaskFilter(
                                                25f,
                                                android.graphics.BlurMaskFilter.Blur.OUTER
                                            )
                                        }
                                    )
                                }
                            }
                            .clip(RoundedCornerShape(5.dp))
                            .height(5.dp)
                            .background(Color(0xFFBEBEBE))
                    )
                    Text(
                        stringResource(R.string.transparenz),
                        modifier = Modifier.weight(2f),
                        color = Color(0xFFBEBEBE),
                        fontSize = 17.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                            .drawBehind {
                                drawIntoCanvas { canvas ->
                                    canvas.nativeCanvas.drawRect(
                                        0f, 0f, size.width, size.height,
                                        Paint().apply {
                                            color = Color(0xFFBEBEBE).copy(alpha = 0.3f).toArgb()
                                            isAntiAlias = true
                                            maskFilter = android.graphics.BlurMaskFilter(
                                                25f,
                                                android.graphics.BlurMaskFilter.Blur.OUTER
                                            )
                                        }
                                    )
                                }
                            }
                            .clip(RoundedCornerShape(5.dp))
                            .height(5.dp)
                            .background(Color(0xFFBEBEBE))
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                NeonBox(
                    modifier = Modifier.fillMaxWidth(),
                    neonColors = listOf(Color(0xFFE8B92A), Color(0xFFFF0000)),
                    backgroundAlpha = 0.15f
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showPermissionInfo = true }
                                .padding(20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.warum_fordere_ich_so_viele),
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Box {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForwardIos,
                                    contentDescription = stringResource(R.string.open_information),
                                    tint = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                if (prvt()) {
                    Spacer(modifier = Modifier.height(16.dp))

                    NeonBox(
                        modifier = Modifier.fillMaxWidth(),
                        neonColors = listOf(Color(0xFF00FFAA), Color(0xFF00CCFF)),
                        backgroundAlpha = 0.15f
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showCoordinatesEdit = true }
                                    .padding(20.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        stringResource(R.string.zuhause_koordinaten_andern),
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Box {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowForwardIos,
                                        contentDescription = stringResource(R.string.koordinaten_andern),
                                        tint = Color.White.copy(alpha = 0.8f),
                                        modifier = Modifier
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                if (prvt()) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Spacer(
                            Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                                .drawBehind {
                                    drawIntoCanvas { canvas ->
                                        canvas.nativeCanvas.drawRect(
                                            0f, 0f, size.width, size.height,
                                            Paint().apply {
                                                color =
                                                    Color(0xFFBEBEBE).copy(alpha = 0.3f).toArgb()
                                                isAntiAlias = true
                                                maskFilter = android.graphics.BlurMaskFilter(
                                                    25f,
                                                    android.graphics.BlurMaskFilter.Blur.OUTER
                                                )
                                            }
                                        )
                                    }
                                }
                                .clip(RoundedCornerShape(5.dp))
                                .height(5.dp)
                                .background(Color(0xFFBEBEBE))
                        )
                        Text(
                            stringResource(R.string.erweitert),
                            modifier = Modifier.weight(1f),
                            color = Color(0xFFBEBEBE),
                            fontSize = 17.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(
                            Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                                .drawBehind {
                                    drawIntoCanvas { canvas ->
                                        canvas.nativeCanvas.drawRect(
                                            0f, 0f, size.width, size.height,
                                            Paint().apply {
                                                color =
                                                    Color(0xFFBEBEBE).copy(alpha = 0.3f).toArgb()
                                                isAntiAlias = true
                                                maskFilter = android.graphics.BlurMaskFilter(
                                                    25f,
                                                    android.graphics.BlurMaskFilter.Blur.OUTER
                                                )
                                            }
                                        )
                                    }
                                }
                                .clip(RoundedCornerShape(5.dp))
                                .height(5.dp)
                                .background(Color(0xFFBEBEBE))
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    NeonBox(
                        modifier = Modifier.fillMaxWidth(),
                        neonColors = listOf(Color(0xFF00E5FF), Color(0xFFE8622A)),
                        backgroundAlpha = 0.15f
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        stringResource(R.string.bevorzugte_ai_global),
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        stringResource(R.string.standard_anbieter_fur_alle_dienste),
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontSize = 12.sp
                                    )
                                }

                                var showGlobalDropdown by remember { mutableStateOf(false) }
                                Box {
                                    Text(
                                        text = aiPrefGlobal.uppercase(),
                                        color = Color(0xFF00E5FF),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .clickable { showGlobalDropdown = true }
                                            .border(
                                                1.dp,
                                                Color.White.copy(alpha = 0.2f),
                                                RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                    DropdownMenu(
                                        expanded = showGlobalDropdown,
                                        onDismissRequest = { showGlobalDropdown = false },
                                        containerColor = Color.Black
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Gemini", color = Color.White) },
                                            onClick = {
                                                aiPrefGlobal = "gemini"; showGlobalDropdown =
                                                false; save()
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("NVIDIA", color = Color.White) },
                                            onClick = {
                                                aiPrefGlobal = "nvidia"; showGlobalDropdown =
                                                false; save()
                                            }
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { aiSettingsExpanded = !aiSettingsExpanded }
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    stringResource(R.string.einzelne_dienste_anpassen),
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    if (aiSettingsExpanded) "▲" else "▼",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 12.sp
                                )
                            }

                            AnimatedVisibility(visible = aiSettingsExpanded) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.White.copy(alpha = 0.05f))
                                        .padding(horizontal = 20.dp, vertical = 8.dp)
                                ) {
                                    val aiServices = listOf(
                                        Triple(
                                            stringResource(R.string.befehl_ai_chat),
                                            aiPrefChat
                                        ) { v: String -> aiPrefChat = v },
                                        Triple(
                                            stringResource(R.string.musik_zusammenfassung),
                                            aiPrefMusicSummary
                                        ) { v: String -> aiPrefMusicSummary = v },
                                        Triple(
                                            stringResource(R.string.vokabel_vision),
                                            aiPrefVision
                                        ) { v: String -> aiPrefVision = v },
                                        Triple(
                                            stringResource(R.string.nachrichten_antworten),
                                            aiPrefReplies
                                        ) { v: String -> aiPrefReplies = v }
                                    )

                                    aiServices.forEachIndexed { index, (title, currentVal, setter) ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                title,
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                modifier = Modifier.weight(1f)
                                            )

                                            var showServiceDropdown by remember {
                                                mutableStateOf(
                                                    false
                                                )
                                            }
                                            Box {
                                                Text(
                                                    text = when (currentVal) {
                                                        "default" -> stringResource(R.string.standard)
                                                        "gemini" -> "Gemini"
                                                        "nvidia" -> "NVIDIA"
                                                        else -> stringResource(R.string.standard)
                                                    },
                                                    color = Color.White.copy(alpha = 0.8f),
                                                    fontSize = 12.sp,
                                                    modifier = Modifier
                                                        .clickable { showServiceDropdown = true }
                                                        .border(
                                                            1.dp,
                                                            Color.White.copy(alpha = 0.15f),
                                                            RoundedCornerShape(4.dp)
                                                        )
                                                        .padding(
                                                            horizontal = 10.dp,
                                                            vertical = 4.dp
                                                        )
                                                )
                                                DropdownMenu(
                                                    expanded = showServiceDropdown,
                                                    onDismissRequest = {
                                                        showServiceDropdown = false
                                                    },
                                                    containerColor = Color.Black
                                                ) {
                                                    DropdownMenuItem(
                                                        text = {
                                                            Text(
                                                                stringResource(R.string.standard_global),
                                                                color = Color.White
                                                            )
                                                        },
                                                        onClick = {
                                                            setter("default"); showServiceDropdown =
                                                            false; save()
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = {
                                                            Text(
                                                                "Gemini",
                                                                color = Color.White
                                                            )
                                                        },
                                                        onClick = {
                                                            setter("gemini"); showServiceDropdown =
                                                            false; save()
                                                        }
                                                    )
                                                    DropdownMenuItem(
                                                        text = {
                                                            Text(
                                                                "NVIDIA",
                                                                color = Color.White
                                                            )
                                                        },
                                                        onClick = {
                                                            setter("nvidia"); showServiceDropdown =
                                                            false; save()
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                        if (index < aiServices.lastIndex) {
                                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    NeonBox(
                        modifier = Modifier.fillMaxWidth(),
                        neonColors = listOf(Color(0xFFFFC107), Color(0xFF7C4DFF)),
                        backgroundAlpha = 0.15f
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { apiKeysExpanded = !apiKeysExpanded }
                                    .padding(20.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        stringResource(R.string.eigene_api_schlussel),
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        stringResource(R.string.leer_lassen_standard_schlussel_des),
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontSize = 12.sp
                                    )
                                }
                                Text(
                                    if (apiKeysExpanded) "▲" else "▼",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 12.sp
                                )
                            }

                            AnimatedVisibility(visible = apiKeysExpanded) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.White.copy(alpha = 0.05f))
                                        .padding(horizontal = 20.dp, vertical = 8.dp)
                                ) {
                                    apiKeyDefs.forEach { (label, key) ->
                                        OutlinedTextField(
                                            value = apiKeyValues[key] ?: "",
                                            onValueChange = { v ->
                                                apiKeyValues[key] = v
                                                prefs.edit { putString(key, v.trim()) }
                                            },
                                            label = { Text(label) },
                                            singleLine = true,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = BuildConfig.VERSION_NAME,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp),
                    textAlign = TextAlign.Center,
                    color = Color.White.copy(alpha = 0.3f),
                    fontSize = 12.sp
                )
            }
        }

        LaunchedEffect(showPermissionInfo) {
            if (showPermissionInfo) {
                delay(50.milliseconds)
                animatePermissionInfo = true
            } else {
                animatePermissionInfo = false
            }
        }

        AnimatedVisibility(
            visible = showPermissionInfo && animatePermissionInfo,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
        ) {
            PermissionInfoScreen(onClose = { showPermissionInfo = false })
        }

        // Media Analytics Confirmation Dialog
        if (showMediaAnalyticsDialog) {
            AlertDialog(
                onDismissRequest = { showMediaAnalyticsDialog = false },
                title = {
                    Text(
                        stringResource(R.string.media_analytics_deaktivieren),
                        color = Color.White
                    )
                },
                text = {
                    Column {
                        Text(
                            stringResource(R.string.mochtest_du_media_analytics_wirklich),
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.option_1_deaktivieren_alle_daten),
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        Text(
                            stringResource(R.string.option_2_nur_deaktivieren_daten),
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                },
                confirmButton = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                // Option 1: Disable and clear all data
                                mediaAnalyticsEnabled = false
                                MediaAnalyticsManager.setAnalyticsEnabled(false)
                                MediaAnalyticsManager.clearAllSessions()
                                showMediaAnalyticsDialog = false
                            }
                        ) {
                            Text(stringResource(R.string.deaktivieren_daten_loschen))
                        }
                        Button(
                            onClick = {
                                // Option 2: Just disable, keep data
                                mediaAnalyticsEnabled = false
                                MediaAnalyticsManager.setAnalyticsEnabled(false)
                                showMediaAnalyticsDialog = false
                            }
                        ) {
                            Text(stringResource(R.string.nur_deaktivieren))
                        }
                    }
                },
                dismissButton = {
                    Button(onClick = { showMediaAnalyticsDialog = false }) {
                        Text(stringResource(R.string.abbrechen))
                    }
                },
                containerColor = Color(0xFF121212)
            )
        }

        AnimatedVisibility(
            visible = showCoordinatesEdit && prvt(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            val coordPrefs =
                remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
            var latInput by remember {
                mutableStateOf(
                    if (coordPrefs.contains("lat_key_d"))
                        java.lang.Double.longBitsToDouble(coordPrefs.getLong("lat_key_d", 0L))
                            .toString()
                    else
                        coordPrefs.getFloat("lat_key", 0f).toString()
                )
            }
            var lonInput by remember {
                mutableStateOf(
                    if (coordPrefs.contains("lon_key_d"))
                        java.lang.Double.longBitsToDouble(coordPrefs.getLong("lon_key_d", 0L))
                            .toString()
                    else
                        coordPrefs.getFloat("lon_key", 0f).toString()
                )
            }
            var savedHint by remember { mutableStateOf(false) }
            val latDouble = latInput.toDoubleOrNull()
            val lonDouble = lonInput.toDoubleOrNull()
            val isValid = latDouble != null && lonDouble != null

            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0C1017))
                    .windowInsetsPadding(WindowInsets.systemBars)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 15.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.koordinaten_andern),
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 5.dp),
                            fontSize = 22.sp
                        )
                        IconButton(
                            onClick = { showCoordinatesEdit = false },
                            modifier = Modifier.background(
                                Color.White.copy(alpha = 0.1f),
                                CircleShape
                            )
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.close),
                                tint = Color.White
                            )
                        }
                    }

                    Text(
                        stringResource(R.string.diese_koordinaten_gelten_als_zuhause),
                        color = APP_COLOR.copy(
                            red = APP_COLOR.red + .6f,
                            green = APP_COLOR.green + .6f,
                            blue = APP_COLOR.blue + .6f
                        ),
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 8.dp)
                    )

                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = latInput,
                        onValueChange = { latInput = it; savedHint = false },
                        label = { Text(stringResource(R.string.breitengrad_latitude)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = lonInput,
                        onValueChange = { lonInput = it; savedHint = false },
                        label = { Text(stringResource(R.string.langengrad_longitude)) },
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (latInput.isNotEmpty() && latDouble == null)
                        Text(
                            stringResource(R.string.ungultiger_breitengrad),
                            color = Color(0xFFE74C3C),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    if (lonInput.isNotEmpty() && lonDouble == null)
                        Text(
                            stringResource(R.string.ungultiger_langengrad),
                            color = Color(0xFFE74C3C),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = {
                            coordPrefs.edit {
                                putLong(
                                    "lat_key_d",
                                    java.lang.Double.doubleToRawLongBits(latDouble!!)
                                )
                                putLong(
                                    "lon_key_d",
                                    java.lang.Double.doubleToRawLongBits(lonDouble!!)
                                )
                                putBoolean("has_coordinates", true)
                            }
                            Config.LAT = latDouble!!
                            Config.LON = lonDouble!!
                            savedHint = true
                        },
                        enabled = isValid,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.speichern)) }

                    if (savedHint) {
                        Text(
                            stringResource(R.string.gespeichert_punkt),
                            color = Color(0xFF00FFAA),
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    Spacer(Modifier.height(32.dp))
                }
            }
        }

        AnimatedVisibility(
            visible = showLanguageDialog,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            LanguageSelectionDialog { showLanguageDialog = false }
        }
    }
}