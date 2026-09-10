package com.tabslify.quiethoursnotificationhelper

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Context.WINDOW_SERVICE
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.scale
import androidx.core.net.toUri
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.google.android.gms.location.LocationServices
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import com.tabslify.core.activities.Tabslify.Companion.coroutineExceptionHandler
import com.tabslify.core.functions.errorInsert
import com.tabslify.core.functions.showSimpleNotificationExtern
import com.tabslify.core.objects.Config
import com.tabslify.core.objects.Config.FLASHCARD_RECEIVE_PORT
import com.tabslify.core.objects.Config.INFO_PORT
import com.tabslify.core.objects.Config.SYNC_PORT
import com.tabslify.core.objects.Config.TODOS
import com.tabslify.core.objects.Config.UPDATE_PORT
import com.tabslify.core.objects.prvt
import com.tabslify.core.ui.APP_COLOR
import com.tabslify.services.MediaPlayerService
import com.tabslify.services.MediaPlayerService.Companion.ACTION_TOGGLE_REPEAT
import com.tabslify.services.OverlayLifecycleOwner
import com.tabslify.services.QuietHoursNotificationService.Companion.CHANNEL_ID
import com.tabslify.services.WhatsAppNotificationListener
import com.tabslify.tabs.aitab.ChatMessage
import com.tabslify.tabs.authenticator.PasswordDatabase
import com.tabslify.tabs.authenticator.TotpGenerator.base32Encode
import com.tabslify.tabs.authenticator.TotpGenerator.deriveTotpSecretFromUuid
import com.tabslify.tabs.authenticator.TotpGenerator.generateTOTP
import com.tabslify.tabs.authenticator.TwoFADatabase
import com.tabslify.tabs.mediaplayer.AlgorithmicPlaylistRegistry
import com.tabslify.tabs.mediaplayer.ListenSession
import com.tabslify.tabs.mediaplayer.MediaAnalyticsManager
import com.tabslify.tabs.mediaplayer.MediaAnalyticsManager.getSessions
import com.tabslify.tabs.school.Vokabel
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.BindException
import java.net.ConnectException
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private data class Cache(
    var batteryLevel: String = "0",
    var temperature: String = "0",
    var plugged: Boolean = false,
    var mobileData: Boolean? = null,
    var wifi: Boolean? = null,
    var hotspot: String? = null,
    var bluetoothOn: Boolean? = null,
    var bluetoothConnectedCount: Int? = null,
    var volume: Int? = null
)

private var cache = Cache()
private var reportDebounceJob: Job? = null
private var pendingReportIntent: Intent? = null
private val akkuReceiver = AkkuReceiver()
private val bluetoothReceiver = BluetoothReceiver()
private val volumeChangeReceiver = VolumeChangeReceiver()
private val connectedBluetoothDevices = mutableSetOf<String>()
private var deviceInfoNetworkCallback: ConnectivityManager.NetworkCallback? = null
private var deviceInfoBroadcastReceiver: BroadcastReceiver? = null
private var mobileDataObserver: ContentObserver? = null

val bluetoothIntentFilter: IntentFilter = IntentFilter().apply {
    addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
    addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
    addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
}
val volumeChangeIntentFilter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")

fun handleBluetoothBroadcast(context: Context, intent: Intent) {
    try {
        when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED ->
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    ?.address?.let { connectedBluetoothDevices.add(it) }

            BluetoothDevice.ACTION_ACL_DISCONNECTED ->
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    ?.address?.let { connectedBluetoothDevices.remove(it) }

            BluetoothAdapter.ACTION_STATE_CHANGED -> {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)
                if (state == BluetoothAdapter.STATE_OFF || state == BluetoothAdapter.STATE_TURNING_OFF) {
                    connectedBluetoothDevices.clear()
                }
            }
        }
    } catch (_: SecurityException) {
    }
    reportDeviceInformation(context, intent)
}

private fun isBluetoothOn(context: Context): Boolean {
    return try {
        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bm?.adapter?.isEnabled == true
    } catch (_: Exception) {
        false
    }
}

@SuppressLint("MissingPermission")
fun connectBluetoothDevice(
    context: Context,
    nameOrAddress: String,
    onResult: ((Boolean, String) -> Unit)? = null
) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
        != PackageManager.PERMISSION_GRANTED
    ) {
        onResult?.invoke(false, "Keine BLUETOOTH_CONNECT-Berechtigung")
        return
    }

    val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val adapter = bm?.adapter
    if (adapter == null) {
        onResult?.invoke(false, "Kein Bluetooth-Adapter verfügbar")
        return
    }

    if (!adapter.isEnabled) {
        @Suppress("DEPRECATION")
        val enabled = try {
            adapter.enable()
        } catch (_: SecurityException) {
            false
        }
        if (!enabled) {
            onResult?.invoke(false, "Bluetooth konnte nicht eingeschaltet werden")
            return
        }
        var waited = 0L
        while (!adapter.isEnabled && waited < 10000) {
            Thread.sleep(200)
            waited += 200
        }
        if (!adapter.isEnabled) {
            onResult?.invoke(false, "Bluetooth wurde nicht rechtzeitig eingeschaltet")
            return
        }
    }

    val target = try {
        adapter.bondedDevices.firstOrNull {
            it.address.equals(nameOrAddress, ignoreCase = true) ||
                it.name?.equals(nameOrAddress, ignoreCase = true) == true
        }
    } catch (e: SecurityException) {
        Log.e("CLOUDSA", "[BT] Zugriff auf gekoppelte Geräte verweigert", e)
        null
    }

    if (target == null) {
        onResult?.invoke(false, "Gerät \"$nameOrAddress\" nicht gekoppelt")
        return
    }

    if (connectedBluetoothDevices.contains(target.address)) {
        onResult?.invoke(true, "\"${target.name}\" ist bereits verbunden")
        return
    }

    var handled = false
    val profiles = intArrayOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET)
    val remaining = profiles.toMutableList()

    val listener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            try {
                val method = proxy.javaClass.getMethod("connect", BluetoothDevice::class.java)
                method.isAccessible = true
                val started = method.invoke(proxy, target) as? Boolean == true
                if (started && !handled) {
                    handled = true
                    onResult?.invoke(true, "Verbindung zu \"${target.name}\" gestartet")
                }
            } catch (e: Exception) {
                Log.e("CLOUDSA", "[BT] connect() fehlgeschlagen für Profil $profile", e)
            } finally {
                adapter.closeProfileProxy(profile, proxy)
                remaining.remove(profile)
                if (remaining.isEmpty() && !handled) {
                    handled = true
                    onResult?.invoke(false, "Verbindung zu \"${target.name}\" fehlgeschlagen")
                }
            }
        }

        override fun onServiceDisconnected(profile: Int) {}
    }

    for (profile in profiles) {
        if (!adapter.getProfileProxy(context, listener, profile)) {
            remaining.remove(profile)
        }
    }

    if (remaining.isEmpty() && !handled) {
        onResult?.invoke(false, "Kein passendes Bluetooth-Profil verfügbar")
    }
}

@SuppressLint("MissingPermission")
fun disconnectBluetoothDevice(
    context: Context,
    nameOrAddress: String,
    onResult: ((Boolean, String) -> Unit)? = null
) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
        != PackageManager.PERMISSION_GRANTED
    ) {
        onResult?.invoke(false, "Keine BLUETOOTH_CONNECT-Berechtigung")
        return
    }

    val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val adapter = bm?.adapter
    if (adapter == null || !adapter.isEnabled) {
        onResult?.invoke(false, "Bluetooth ist ausgeschaltet")
        return
    }

    val target = try {
        adapter.bondedDevices.firstOrNull {
            it.address.equals(nameOrAddress, ignoreCase = true) ||
                it.name?.equals(nameOrAddress, ignoreCase = true) == true
        }
    } catch (e: SecurityException) {
        Log.e("CLOUDSA", "[BT] Zugriff auf gekoppelte Geräte verweigert", e)
        null
    }

    if (target == null) {
        onResult?.invoke(false, "Gerät \"$nameOrAddress\" nicht gekoppelt")
        return
    }

    if (!connectedBluetoothDevices.contains(target.address)) {
        onResult?.invoke(true, "\"${target.name}\" ist bereits getrennt")
        return
    }

    var handled = false
    val profiles = intArrayOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET)
    val remaining = profiles.toMutableList()

    val listener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            try {
                val method = proxy.javaClass.getMethod("disconnect", BluetoothDevice::class.java)
                method.isAccessible = true
                val started = method.invoke(proxy, target) as? Boolean == true
                if (started && !handled) {
                    handled = true
                    onResult?.invoke(true, "Verbindung zu \"${target.name}\" getrennt")
                }
            } catch (e: Exception) {
                Log.e("CLOUDSA", "[BT] disconnect() fehlgeschlagen für Profil $profile", e)
            } finally {
                adapter.closeProfileProxy(profile, proxy)
                remaining.remove(profile)
                if (remaining.isEmpty() && !handled) {
                    handled = true
                    onResult?.invoke(false, "Trennung von \"${target.name}\" fehlgeschlagen")
                }
            }
        }

        override fun onServiceDisconnected(profile: Int) {}
    }

    for (profile in profiles) {
        if (!adapter.getProfileProxy(context, listener, profile)) {
            remaining.remove(profile)
        }
    }

    if (remaining.isEmpty() && !handled) {
        onResult?.invoke(false, "Kein passendes Bluetooth-Profil verfügbar")
    }
}

val isLaptopConnectedFlow = MutableStateFlow(false)
val aiResponseFlow = MutableStateFlow<AiResponseEntry?>(null)
val flashcardVokabelnFlow = MutableStateFlow<List<Vokabel>?>(null)

private val serverMutexes = mutableMapOf<Int, Mutex>()

private fun getServerMutex(port: Int) = synchronized(serverMutexes) {
    serverMutexes.getOrPut(port) { Mutex() }
}

var isLaptopConnected: Boolean
    get() = isLaptopConnectedFlow.value
    set(value) {
        isLaptopConnectedFlow.value = value
    }

private val syncScope = CoroutineScope(
    Dispatchers.IO + SupervisorJob() + coroutineExceptionHandler
)

private val pcCallFailures = java.util.concurrent.atomic.AtomicInteger(0)
@Volatile
private var reconnectAttempted = false
@Volatile
private var laptopUnreachableNotified = false
@Volatile
private var missingIpNotified = false
private const val MAX_PC_CALL_FAILURES = 3

fun onPcCallSuccess() {
    pcCallFailures.set(0)
    reconnectAttempted = false
    laptopUnreachableNotified = false
    missingIpNotified = false
    resetReconnectBackoff()
}

fun resetReconnectBackoff() {
    reconnectFailures = 0
    reconnectBackoff = RECONNECT_HEARTBEAT_INTERVAL
    reconnectSuspended = false
    lastReconnectHeartbeat = 0L
}

private fun registerReconnectFailure() {
    reconnectFailures++
    reconnectBackoff = (reconnectBackoff * 2).coerceAtMost(RECONNECT_BACKOFF_MAX)
    if (reconnectFailures >= MAX_RECONNECT_FAILURES) {
        reconnectSuspended = true
        Log.w("CLOUDSA", "Reconnect nach $reconnectFailures Fehlversuchen ausgesetzt")
    }
}

private fun notifyLaptopUnreachableOnce(context: Context, e: Exception) {
    if (laptopUnreachableNotified) {
        Log.w("CLOUDSA", "Laptop nicht erreichbar: ${e.message}")
        return
    }
    laptopUnreachableNotified = true
    showSimpleNotificationExtern("Error", "${e.message}", 10.seconds, context)
}

private fun notifyMissingIpOnce(context: Context, detail: String) {
    if (missingIpNotified) {
        Log.w("CLOUDSA", "Keine IP gefunden: $detail")
        return
    }
    missingIpNotified = true
    showSimpleNotificationExtern("❌ Keine IP gefunden", detail, 10.seconds, context)
}

fun onPcCallFailure(context: Context) {
    if (!isLaptopConnected) return
    if (pcCallFailures.incrementAndGet() < MAX_PC_CALL_FAILURES) return
    pcCallFailures.set(0)
    if (!reconnectAttempted) {
        reconnectAttempted = true
        syncTodosWithLaptop(context.applicationContext)
    } else {
        reconnectAttempted = false
        stopAllSyncServices(context.applicationContext)
    }
}
private val mediaScope = CoroutineScope(
    Dispatchers.IO + SupervisorJob() + coroutineExceptionHandler
)

private var listenerJob: Job? = null
private var updateServerSocket: ServerSocket? = null

private var triggerJob: Job? = null
private var triggerWatchdogJob: Job? = null

private var aiResponseJob: Job? = null
private var aiResponseServerSocket: ServerSocket? = null

private var flashcardResponseJob: Job? = null
private var flashcardResponseSocket: ServerSocket? = null

private var mediaCommandJob: Job? = null
private var mediaStateJob: Job? = null
private var clipboardJob: Job? = null
private var executeJob: Job? = null
private var smsJob: Job? = null
private var spotifyHistoryJob: Job? = null

private val activeServers = mutableListOf<ServerSocket>()

private val listenerUnboundSince = java.util.concurrent.ConcurrentHashMap<Int, Long>()
private const val LISTENER_UNBOUND_GRACE = 15_000L

private fun isServerBound(port: Int): Boolean = synchronized(activeServers) {
    activeServers.any { runCatching { it.localPort == port && !it.isClosed }.getOrDefault(false) }
}

private fun listenerNeedsRestart(job: Job?, port: Int): Boolean {
    if (job?.isActive != true) {
        listenerUnboundSince.remove(port)
        return true
    }
    if (isServerBound(port)) {
        listenerUnboundSince.remove(port)
        return false
    }
    val since = listenerUnboundSince.putIfAbsent(port, System.currentTimeMillis()) ?: return false
    if (System.currentTimeMillis() - since < LISTENER_UNBOUND_GRACE) return false
    listenerUnboundSince.remove(port)
    Log.w("CLOUDSA", "Listener auf Port $port ist aktiv, aber kein Socket gebunden - wird abgebrochen")
    job.cancel()
    return true
}

private var cpuWakeLock: PowerManager.WakeLock? = null
private var appContext: Context? = null

private const val PREFS_SYNC = "sync_prefs"
private const val KEY_SYNC_ACTIVE = "sync_active"
private const val KEY_LAST_SYNC = "last_sync"
private const val KEY_LAST_LAPTOP = "last_laptop_name"
private var lastPushedState: String = ""

private var networkCallback: ConnectivityManager.NetworkCallback? = null
private var pendingSyncJob: Job? = null
private var lastTriggerTime = 0L
private const val MIN_TRIGGER_INTERVAL = 15_000L
private var lastReconnectHeartbeat = 0L
private const val RECONNECT_HEARTBEAT_INTERVAL = 120_000L
private const val RECONNECT_BACKOFF_MAX = 1_800_000L
private const val MAX_RECONNECT_FAILURES = 6
@Volatile
private var reconnectFailures = 0
@Volatile
private var reconnectBackoff = RECONNECT_HEARTBEAT_INTERVAL
@Volatile
private var reconnectSuspended = false
private const val WIFI_AP_STATE_CHANGED_ACTION = "android.net.wifi.WIFI_AP_STATE_CHANGED"

private val syncInProgress = AtomicBoolean(false)
private const val SYNC_SUCCESS_COOLDOWN = 120_000L
@Volatile
private var lastSuccessfulSync = 0L

private fun PowerManager.WakeLock?.safeRelease() {
    if (this != null && isHeld) release()
}

private fun logError(service: String, e: Exception) {
    errorInsert(
        service,
        e.stackTraceToString(),
        Instant.now().toString(),
        "ERROR"

    )
}

fun ensureReadyForConnect(context: Context) {
    syncInProgress.set(false)
    pendingSyncJob?.cancel()
    pendingSyncJob = null
    triggerJob?.cancel()
    triggerJob = null
    startTriggerListener(context)
    registerWifiReconnectReceiver(context)
    syncScope.launch {
        val ip = fetchIpFromSupabase()
        if (!ip.isNullOrEmpty()) {
            laptopIp = ip
            showSimpleNotificationExtern(
                "✅ Bereit",
                "TriggerListener aktiv: ${triggerJob?.isActive} | IP: $ip",
                10.seconds,
                context
            )
        } else {
            showSimpleNotificationExtern(
                "⚠️ Kein Laptop-IP",
                "Supabase hat keine IP geliefert",
                10.seconds,
                context
            )
        }
    }
}

fun unregisterWifiReconnectReceiver(context: Context) {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    networkCallback?.let {
        try {
            cm.unregisterNetworkCallback(it)
        } catch (_: Exception) {
        }
    }
    networkCallback = null
}

fun shutdownAllWifiDirectServices(context: Context) {
    triggerJob?.cancel(); triggerJob = null
    triggerWatchdogJob?.cancel(); triggerWatchdogJob = null
    pendingSyncJob?.cancel(); pendingSyncJob = null
    listenerJob?.cancel(); listenerJob = null
    mediaCommandJob?.cancel(); mediaCommandJob = null
    mediaStateJob?.cancel(); mediaStateJob = null
    aiResponseJob?.cancel(); aiResponseJob = null
    flashcardResponseJob?.cancel(); flashcardResponseJob = null
    clipboardJob?.cancel(); clipboardJob = null
    executeJob?.cancel(); executeJob = null
    smsJob?.cancel(); smsJob = null

    updateServerSocket?.let { runCatching { it.close() } }
    updateServerSocket = null
    aiResponseServerSocket?.let { runCatching { it.close() } }
    aiResponseServerSocket = null
    flashcardResponseSocket?.let { runCatching { it.close() } }
    flashcardResponseSocket = null

    synchronized(activeServers) {
        activeServers.forEach { runCatching { it.close() } }
        activeServers.clear()
    }

    unregisterWifiReconnectReceiver(context)
    unregisterDeviceInfoNetworkListeners(context)
    cpuWakeLock.safeRelease(); cpuWakeLock = null
    syncInProgress.set(false)
    lastSuccessfulSync = 0L
    isLaptopConnected = false
}

private fun launchServer(
    scope: CoroutineScope,
    port: Int,
    errorTag: String,
    handler: suspend CoroutineScope.(Socket) -> Unit
): Job = scope.launch(Dispatchers.IO) {
    val mutex = getServerMutex(port)
    var consecutiveFailures = 0

    mutex.withLock {
        while (isActive) {
            var server: ServerSocket? = null
            try {
                synchronized(activeServers) {
                    activeServers.find {
                        runCatching { it.localPort }.getOrNull() == port
                    }?.let {
                        activeServers.remove(it)
                        runCatching { it.close() }
                    }
                }

                server = ServerSocket()
                server.reuseAddress = true
                server.bind(InetSocketAddress(port))
                server.soTimeout = 5000
                synchronized(activeServers) { activeServers.add(server) }
                consecutiveFailures = 0
                Log.d("CLOUDSA", "$errorTag: Server started on port $port")

                while (isActive) {
                    try {
                        val client = server.accept()
                        scope.launch {
                            client.use {
                                try {
                                    handler(it)
                                } catch (e: Exception) {
                                    Log.e("CLOUDSA", "$errorTag: Error handling client", e)
                                    logError(errorTag, e)
                                }
                            }
                        }
                    } catch (_: SocketTimeoutException) {
                        continue
                    } catch (e: SocketException) {
                        Log.w("CLOUDSA", "$errorTag: Socket exception, restarting server", e)
                        break
                    } catch (e: Exception) {
                        Log.e("CLOUDSA", "$errorTag: Unexpected error accepting client", e)
                        logError(errorTag, e)
                        delay(1000L.milliseconds)
                    }
                }
            } catch (_: BindException) {
                consecutiveFailures++
                Log.w("CLOUDSA", "$errorTag: Port $port already bound, retrying... (failures: $consecutiveFailures)")
                delay((2000L * consecutiveFailures.coerceAtMost(15)).milliseconds)
                continue
            } catch (e: Exception) {
                consecutiveFailures++
                Log.e("CLOUDSA", "$errorTag: Server error, restarting... (failures: $consecutiveFailures)", e)
                logError(errorTag, e)
                delay((2000L * consecutiveFailures.coerceAtMost(15)).milliseconds)
            } finally {
                server?.let {
                    synchronized(activeServers) { activeServers.remove(it) }
                    runCatching { it.close() }
                }
            }
            if (isActive) delay((2000L * consecutiveFailures.coerceAtMost(15)).milliseconds)
        }
    }
}

private const val PREFS_PC_SECRETS = "pc_secrets"

fun resolveSyncSecret(context: Context, pcName: String): String? {
    if (pcName.isEmpty()) return null
    val secretsPrefs = context.getSharedPreferences(PREFS_PC_SECRETS, MODE_PRIVATE)
    secretsPrefs.getString(pcName, null)?.let { return it }
    val uuidPrefs = context.getSharedPreferences("pc_uuids", MODE_PRIVATE)
    return uuidPrefs.getString(pcName, null)?.let { deriveTotpSecretFromUuid(it) }
}

fun ensureRandomSyncSecret(context: Context, pcName: String): String {
    val secretsPrefs = context.getSharedPreferences(PREFS_PC_SECRETS, MODE_PRIVATE)
    secretsPrefs.getString(pcName, null)?.let { return it }
    val randomBytes = ByteArray(20).also { SecureRandom().nextBytes(it) }
    val secret = base32Encode(randomBytes)
    secretsPrefs.edit { putString(pcName, secret) }
    return secret
}

private const val PREFS_PC_DISPLAY_NAMES = "pc_display_names"

fun resolveDisplayName(context: Context, pcName: String): String? {
    if (pcName.isEmpty()) return null
    return context.getSharedPreferences(PREFS_PC_DISPLAY_NAMES, MODE_PRIVATE).getString(pcName, null)
}

fun setDisplayName(context: Context, pcName: String, displayName: String) {
    val prefs = context.getSharedPreferences(PREFS_PC_DISPLAY_NAMES, MODE_PRIVATE)
    if (displayName.isBlank()) prefs.edit { remove(pcName) }
    else prefs.edit { putString(pcName, displayName.trim()) }
}

private fun todosToJsonArray(todos: List<TodoItem>): JSONArray = JSONArray().apply {
    todos.forEach { todo ->
        put(JSONObject().apply {
            put("id", todo.id)
            put("text", todo.text)
            put("completed", todo.completed)
        })
    }
}


suspend fun callNvidiaVisionApi(
    context: Context,
    bmp: Bitmap,
    onError: (String) -> Unit,
    onProgress: (String) -> Unit,
    onVocabChange: (List<Vokabel>) -> Unit
) {
    fun Bitmap.scaleForApi(maxPx: Int = 1280): Bitmap {
        val scale = maxPx.toFloat() / maxOf(width, height)
        if (scale >= 1f) return this
        return this.scale((width * scale).toInt(), (height * scale).toInt())
    }

    val scaledBmp = bmp.scaleForApi(1280)

    val prompt = """
        Look at this vocabulary list image carefully.
        There are TWO columns: Latin on the LEFT, German on the RIGHT.
        Each row is one vocabulary entry.

        Rules:
        - Match each Latin entry with the German entry on the SAME vertical position
        - Latin entries are on the left half of the image
        - German entries are on the right half
        - Ignore page numbers (like "116")
        - Ignore any repeated/duplicate blocks at bottom

        Return ONLY a JSON array like this (one object per row):
        [{"latein":"salūtem dīcere (m. Dat.)","deutsch":"(jdn.) grüßen, begrüßen"},{"latein":"gaudium","deutsch":"die Freude"},...]

        No markdown, no explanation, ONLY the JSON array.
    """.trimIndent()

    val provider = getPreferredAiProvider(context, "vision")
    val result = try {
        val sb = StringBuilder()
        if (provider == "nvidia") {
            val base64Pic = java.io.ByteArrayOutputStream().use { bos ->
                scaledBmp.compress(Bitmap.CompressFormat.JPEG, 90, bos)
                android.util.Base64.encodeToString(bos.toByteArray(), android.util.Base64.NO_WRAP)
            }
            val ans = sendAiRequest(
                context = context,
                history = emptyList(),
                userMessage = prompt,
                model = "meta/llama-3.2-90b-vision-instruct",
                pic = base64Pic,
                provider = AiProvider.NVIDIA,
                onToken = { chunk ->
                    sb.append(chunk)
                    Handler(Looper.getMainLooper()).post {
                        onProgress(sb.toString())
                    }
                }
            )
            ans ?: ""
        } else {
            val model = Firebase.ai(backend = GenerativeBackend.googleAI())
                .generativeModel("gemini-3-flash-preview")
            model.generateContentStream(content { image(scaledBmp); text(prompt) })
                .collect { chunk ->
                    val delta = chunk.text ?: ""
                    if (delta.isNotEmpty()) {
                        sb.append(delta)
                        withContext(Dispatchers.Main) { onProgress(sb.toString()) }
                    }
                }
            sb.toString()
        }
    } catch (_: Exception) {
        onError("API keine Antwort – prüfe Key & Netzwerk")
        if (scaledBmp !== bmp) scaledBmp.recycle()
        return
    }

    val finalResult = result.trim()
    if (finalResult.isNotEmpty()) {
        try {
            val startIdx = finalResult.indexOf('[')
            val endIdx = finalResult.lastIndexOf(']')
            if (startIdx != -1 && endIdx > startIdx) {
                val arr = JSONArray(finalResult.substring(startIdx, endIdx + 1))
                onVocabChange((0 until arr.length()).map {
                    val o = arr.getJSONObject(it)
                    Vokabel(o.getString("latein"), o.getString("deutsch"), it)
                })
            }
        } catch (e: Exception) {
            onError("Parse-Fehler: ${e.message}\nRaw: ${finalResult.take(300)}")
        }
    } else {
        onError("API keine Antwort – prüfe Key & Netzwerk")
    }
    if (scaledBmp !== bmp) scaledBmp.recycle()
}

@Volatile
var laptopIp: String = ""

@Volatile
var laptopName: String = ""

data class TodoItem(
    val id: Long,
    val text: String,
    val completed: Boolean
)

data class AiResponseEntry(
    val text: String,
    val timestamp: Long,
    val dateKey: String
)

fun startTriggerListenerIfHomeWifi(context: Context) {
    startTriggerListener(context)
    registerWifiReconnectReceiver(context)

    syncScope.launch {
        val ip = fetchIpFromSupabase()
        if (!ip.isNullOrEmpty()) laptopIp = ip
    }
}

fun registerWifiReconnectReceiver(context: Context) {
    val appContext = context.applicationContext
    val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    networkCallback?.let {
        try {
            cm.unregisterNetworkCallback(it)
        } catch (_: Exception) {
        }
    }

    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            fun getLocalIp(): String {
                return try {
                    NetworkInterface.getNetworkInterfaces()
                        ?.asSequence()
                        ?.flatMap { it.inetAddresses.asSequence() }
                        ?.firstOrNull { addr ->
                            !addr.isLoopbackAddress && addr.hostAddress?.contains(':') == false
                        }
                        ?.hostAddress ?: "Unbekannt"
                } catch (e: Exception) {
                    Log.e("CLOUDSA", "[NET] IP-Fehler", e)
                    "Unbekannt"
                }
            }

            val localIp = getLocalIp()
            if (localIp != "Unbekannt") {
                syncScope.launch { insertMobileIpToSupabase(localIp) }
            }

            val now = System.currentTimeMillis()
            if (now - lastTriggerTime < MIN_TRIGGER_INTERVAL) return
            lastTriggerTime = now

            val isWifi = cm.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            if (isWifi) {
                checkIfNearLocation(appContext, unknownLocationCountsAsHome = false) { atHome ->
                    if (atHome && !isLaptopConnected) {
                        resetReconnectBackoff()
                        syncTodosWithLaptop(appContext)
                    }
                }
            }
        }

        override fun onLost(network: Network) {
            syncInProgress.set(false)
            pendingSyncJob?.cancel()
            if (isLaptopConnected) {
                stopAllSyncServices(appContext)
            }
            // Nach Connection Verlust: Trigger Listener wieder starten
            syncScope.launch {
                delay(2000.milliseconds)
                if (triggerJob?.isActive != true) {
                    startTriggerListener(appContext)
                }
            }
        }

        override fun onLinkPropertiesChanged(
            network: Network,
            linkProperties: LinkProperties
        ) {
            Log.d("CLOUDSA", "${linkProperties.linkAddresses}")
            val localIp = linkProperties.linkAddresses
                .map { it.address.hostAddress }
                .firstOrNull { ip ->
                    ip != null && (ip.startsWith("192.168.") || ip.startsWith("10."))
                }

            if (localIp != null) {
                syncScope.launch {
                    insertMobileIpToSupabase(localIp)
                }
            }
        }
    }

    networkCallback = callback

    val request = NetworkRequest.Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .build()

    cm.registerNetworkCallback(request, callback)
}

@SuppressLint("Wakelock", "WakelockTimeout")
fun startTriggerListener(context: Context) {
    val ctx = context.applicationContext
    if (triggerJob?.isActive == true) return
    appContext = ctx.applicationContext
    
    // Start trigger listener
    triggerJob = launchServer(syncScope, Config.TRIGGER_PORT, "startTriggerListener") { client ->
        val pm = ctx.applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TodoSync:AcceptWakeLock")
        wl.acquire(30_000L)

        try {
            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
            val command = reader.readLine()
            val writer = PrintWriter(client.getOutputStream(), true)

            when {
                command != null && command.startsWith("CONNECT") -> {
                    val payload = command.substringAfter("CONNECT:", "")
                    val parts = payload.split("|")
                    val pcName = parts.getOrNull(0) ?: ""
                    val ipv4Regex = Regex("""\d{1,3}(\.\d{1,3}){3}""")
                    val pcIp = parts.firstOrNull { it.contains(".") && it.matches(ipv4Regex) }
                        ?: parts.getOrNull(1)
                        ?: pcName
                    val totpCode = parts.getOrNull(2) ?: ""
                    val pcUuid = parts.getOrNull(3) ?: "NO_UUID"

                    if (pcName.isNotEmpty()) {
                        val prefs = ctx.getSharedPreferences("registered_pcs", MODE_PRIVATE)

                        if (!prefs.contains(pcName)) {
                            val pendingPrefs = ctx.getSharedPreferences("pending_pcs", MODE_PRIVATE)
                            pendingPrefs.edit { putString(pcName, "$pcIp|$pcUuid") }

                            showSimpleNotificationExtern(
                                "🆕 Verbindungsanfrage",
                                "PC $pcName möchte sich verbinden. Bitte im PC Manager freigeben.",
                                10.seconds,
                                ctx
                            )
                            writer.println("PENDING")
                            stopAllSyncServices(ctx)
                        } else {
                            val secret = resolveSyncSecret(ctx, pcName)
                            val now = System.currentTimeMillis()
                            val totpValid = secret != null && listOf(
                                generateTOTP(secret, now - 30000),
                                generateTOTP(secret, now),
                                generateTOTP(secret, now + 30000)
                            ).contains(totpCode)

                            if (totpValid) {
                                ensureRandomSyncSecret(ctx, pcName)
                                laptopIp = pcIp
                                laptopName = pcName

                                showSimpleNotificationExtern(
                                    "📡 CONNECT",
                                    "PC ${resolveDisplayName(ctx, pcName) ?: pcName} verbunden",
                                    10.seconds,
                                    ctx
                                )

                                val syncWl =
                                    pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TodoSync:SyncWakeLock")
                                syncWl.acquire(60_000L)
                                syncScope.launch {
                                    try {
                                        syncTodosWithLaptop(ctx, true)
                                    } finally {
                                        syncWl.safeRelease()
                                    }
                                }
                                writer.println("ACCEPTED")
                            } else {
                                showSimpleNotificationExtern(
                                    "❌ Auth fehlgeschlagen",
                                    "Ungültiger TOTP-Code von $pcName",
                                    10.seconds,
                                    context
                                )
                                writer.println("REJECTED")
                                stopAllSyncServices(context)
                            }
                        }
                    } else {
                        writer.println("REJECTED")
                        stopAllSyncServices(context)
                    }
                }

                command == "REQUEST_SESSIONS" -> {
                    syncScope.launch { sendSessionDataToLaptop(context) }
                    writer.println("ACCEPTED")
                }
                command == "DISCONNECT" -> {
                    stopAllSyncServices(context)
                    writer.println("ACCEPTED")
                }
                else -> {
                    writer.println("REJECTED")
                    stopAllSyncServices(context)
                }
            }
        } finally {
            wl.safeRelease()
        }
    }
    
    // Start watchdog
    startTriggerWatchdog(context)
}

private fun startTriggerWatchdog(context: Context) {
    val ctx = context.applicationContext
    triggerWatchdogJob?.cancel()
    triggerWatchdogJob = syncScope.launch {
        while (isActive) {
            delay(20_000L.milliseconds)
            if (triggerJob == null || triggerJob?.isActive != true) {
                Log.w("CLOUDSA", "Trigger listener not active, restarting...")
                showSimpleNotificationExtern(
                    "⚠️ Trigger Listener",
                    "Neustart des Trigger Listeners...",
                    5.seconds,
                    ctx
                )
                startTriggerListener(ctx)
            }
            if (isLaptopConnected) {
                ensureSyncListenersAlive(ctx)
            } else if (laptopIp.isNotEmpty() && !reconnectSuspended) {
                val now = System.currentTimeMillis()
                if (now - lastReconnectHeartbeat >= reconnectBackoff) {
                    lastReconnectHeartbeat = now
                    syncTodosWithLaptop(ctx)
                }
            }
        }
    }
}

private fun ensureSyncListenersAlive(context: Context) {
    val ctx = context.applicationContext
    if (listenerNeedsRestart(mediaCommandJob, Config.MEDIA_COMMAND_PORT)) {
        Log.w("CLOUDSA", "Media command listener not active, restarting...")
        startMediaCommandListener(ctx)
    }
    if (listenerNeedsRestart(mediaStateJob, Config.MEDIA_STATE_PORT)) {
        Log.w("CLOUDSA", "Media state listener not active, restarting...")
        startMediaStateServer(context)
    }
    if (listenerNeedsRestart(clipboardJob, Config.CLIPBOARD_PORT)) {
        Log.w("CLOUDSA", "Clipboard listener not active, restarting...")
        startClipboardListener(context)
    }
    if (listenerNeedsRestart(executeJob, Config.EXECUTE_PORT)) {
        Log.w("CLOUDSA", "Execute listener not active, restarting...")
        startExecuteListener(context)
    }
    if (listenerNeedsRestart(smsJob, Config.SMS_PORT)) {
        Log.w("CLOUDSA", "SMS listener not active, restarting...")
        startSmsListener(context)
    }
    if (listenerNeedsRestart(spotifyHistoryJob, Config.SPOTIFY_HISTORY_PORT)) {
        Log.w("CLOUDSA", "Spotify history listener not active, restarting...")
        startSpotifyHistoryListener(context)
    }
}

fun restoreSyncIfNeeded(context: Context) {
    val ctx = context.applicationContext
    appContext = ctx.applicationContext
    val prefs = ctx.getSharedPreferences(PREFS_SYNC, MODE_PRIVATE)
    val syncActive = prefs.getBoolean(KEY_SYNC_ACTIVE, false)
    val lastSync = prefs.getLong(KEY_LAST_SYNC, 0L)
    val thirtyMinutesMs = 30 * 60 * 1000L
    val timeSinceLastSync = System.currentTimeMillis() - lastSync

    if (syncActive && timeSinceLastSync <= thirtyMinutesMs) {
        val minutesAgo = (timeSinceLastSync / 60_000L).toInt().coerceAtLeast(1)
        isLaptopConnected = true
        startUpdateListener(ctx)
        startMediaCommandListener(ctx)
        startExecuteListener(ctx)
        startMediaStateServer(ctx)
        startClipboardListener(ctx)
        startSmsListener(ctx)
        startSpotifyHistoryListener(ctx)
        ctx.registerReceiver(
            akkuReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            Context.RECEIVER_NOT_EXPORTED
        )
        ctx.registerReceiver(bluetoothReceiver, bluetoothIntentFilter, Context.RECEIVER_NOT_EXPORTED)
        ctx.registerReceiver(volumeChangeReceiver, volumeChangeIntentFilter, Context.RECEIVER_NOT_EXPORTED)
        registerDeviceInfoNetworkListeners(ctx)
        syncScope.launch {
            delay(5_000.milliseconds)
            syncTodosWithLaptop(ctx)
        }
        showSimpleNotificationExtern(
            "🔁 Sync wiederhergestellt",
            "Letzter Sync vor $minutesAgo min",
            10.seconds,
            context
        )
    }
}

fun stopUpdateListener(b: Boolean = true) {
    try {
        appContext?.getSharedPreferences(PREFS_SYNC, MODE_PRIVATE)?.edit {
            putBoolean(KEY_SYNC_ACTIVE, false)
        }
        isLaptopConnected = b
        cpuWakeLock.safeRelease(); cpuWakeLock = null
        listenerJob?.cancel(); listenerJob = null
        updateServerSocket?.close(); updateServerSocket = null
    } catch (e: Exception) {
        logError("stopUpdateListener", e)
    }
}

fun stopAllSyncServices(context: Context) {
    val ctx = context.applicationContext
    stopUpdateListener()

    listOf(
        mediaCommandJob, mediaStateJob, aiResponseJob, flashcardResponseJob,
        clipboardJob, executeJob, smsJob
    ).forEach { it?.cancel() }

    mediaCommandJob = null; mediaStateJob = null; aiResponseJob = null
    flashcardResponseJob = null; clipboardJob = null; executeJob = null

    val nm = ctx.getSystemService(NotificationManager::class.java)
    nm.cancel(99999)

    aiResponseServerSocket?.close(); aiResponseServerSocket = null
    flashcardResponseSocket?.close(); flashcardResponseSocket = null

    synchronized(activeServers) {
        activeServers.removeAll { server ->
            val isTriggerServer =
                runCatching { server.localPort }.getOrNull() == Config.TRIGGER_PORT
            if (!isTriggerServer) {
                runCatching { server.close() }
            }
            !isTriggerServer
        }
    }

    cpuWakeLock.safeRelease(); cpuWakeLock = null
    try {
        context.unregisterReceiver(akkuReceiver)
    } catch (_: IllegalArgumentException) {
    }
    try {
        context.unregisterReceiver(bluetoothReceiver)
    } catch (_: IllegalArgumentException) {
    }
    try {
        context.unregisterReceiver(volumeChangeReceiver)
    } catch (_: IllegalArgumentException) {
    }
    unregisterDeviceInfoNetworkListeners(context)
    cache = Cache()
    connectedBluetoothDevices.clear()

    isLaptopConnected = false

    showSimpleNotificationExtern(
        "📴 Laptop getrennt",
        "Alle Sync-Services gestoppt",
        10.seconds,
        context
    )

    if (triggerJob?.isActive != true) {
        startTriggerListener(context)
    }
}

fun isTriggerPortOpen(): Boolean {
    return try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", Config.TRIGGER_PORT), 1000)
            true
        }
    } catch (_: Exception) {
        false
    }
}

fun getTriggerListenerStatus(): String {
    val jobActive = triggerJob?.isActive == true
    val watchdogActive = triggerWatchdogJob?.isActive == true
    val portOpen = isTriggerPortOpen()
    return "Job: ${if (jobActive) "aktiv" else "inaktiv"}, Watchdog: ${if (watchdogActive) "aktiv" else "inaktiv"}, Port: ${if (portOpen) "offen" else "geschlossen"}"
}

fun syncTodosWithLaptop(context: Context, connected: Boolean = false) {
    val ctx = context.applicationContext
    if (triggerJob?.isActive != true) {
        startTriggerListener(ctx)
    }
    if (!connected && System.currentTimeMillis() - lastSuccessfulSync < SYNC_SUCCESS_COOLDOWN) {
        Log.d("CLOUDSA", "Sync übersprungen, letzter Erfolg vor ${(System.currentTimeMillis() - lastSuccessfulSync) / 1000}s")
        return
    }
    if (syncInProgress.getAndSet(true)) return
    if (!Config.realDevice || !prvt()) {
        syncInProgress.set(false)
        return
    }

    syncScope.launch {
        var attemptFailed = true
        try {
            val localip = getHotspotIp()
            if (localip != null) insertMobileIpToSupabase(localip)

            if (!connected) {
                val fetched = withTimeoutOrNull(35_000L.milliseconds) {
                    fetchIpFromSupabase()
                }
                if (fetched.isNullOrEmpty()) {
                    notifyMissingIpOnce(context, "Supabase lieferte keine IP")
                    return@launch
                }
                laptopIp = fetched
            }

            val currentIp = if (laptopIp.contains("|")) {
                val parts = laptopIp.split("|")
                parts.firstOrNull { it.matches(Regex("""\d{1,3}(\.\d{1,3}){3}""")) } ?: parts.getOrNull(1) ?: laptopIp
            } else laptopIp

            if (currentIp.isEmpty()) {
                notifyMissingIpOnce(context, "IP ist leer nach Bereinigung")
                return@launch
            }

            val uuidPrefs = context.getSharedPreferences("pc_uuids", MODE_PRIVATE)
            if (laptopName.isEmpty()) {
                laptopName = context.getSharedPreferences(PREFS_SYNC, MODE_PRIVATE)
                    .getString(KEY_LAST_LAPTOP, "").orEmpty()
                    .ifEmpty { uuidPrefs.all.keys.firstOrNull().orEmpty() }
            }
            if (!uuidPrefs.contains(laptopName)) {
                showSimpleNotificationExtern(
                    "❌ Keine UUID gefunden", "PC $laptopName ist nicht in pc_uuids registriert", 10.seconds, context
                )
                return@launch
            }
            val totpKey = resolveSyncSecret(context, laptopName)
            if (totpKey == null) {
                showSimpleNotificationExtern(
                    "❌ Kein Secret gefunden", "PC $laptopName hat kein TOTP-Secret", 10.seconds, context
                )
                return@launch
            }

            val todos = getTodos(context)
            val response = Socket().use { socket ->
                socket.connect(
                    InetSocketAddress(Inet4Address.getByName(currentIp), SYNC_PORT),
                    3000
                )
                socket.soTimeout = 8000

                val writer = PrintWriter(socket.getOutputStream(), true)
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

                val payloadObj = JSONObject().apply {
                    put("todos", todosToJsonArray(todos))
                    put("totp", generateTOTP(totpKey))
                    if (connected) put("totp_key", totpKey)
                }

                writer.println(payloadObj.toString())
                writer.flush()
                socket.shutdownOutput() // "hier ist Schluss": signalisiert dem PC per TCP-FIN, dass keine weiteren Daten kommen, statt dass der Server auf ein Timeout warten muss

                reader.readLine() ?: throw IOException("Keine Antwort vom Server")
            }

            when (response) {
                "OK" -> {
                    attemptFailed = false
                    lastSuccessfulSync = System.currentTimeMillis()
                    isLaptopConnected = true
                    onPcCallSuccess()

                    // Save last sync time
                    val prefs = context.getSharedPreferences(PREFS_SYNC, MODE_PRIVATE)
                    prefs.edit {
                        putBoolean(KEY_SYNC_ACTIVE, true)
                        putLong(KEY_LAST_SYNC, System.currentTimeMillis())
                        putString(KEY_LAST_LAPTOP, laptopName)
                    }

                    startMediaCommandListener(context)
                    startExecuteListener(context)
                    startMediaStateServer(context)
                    startClipboardListener(context)
                    startSmsListener(context)
                    startSpotifyHistoryListener(context)
                    if (listenerJob == null || listenerJob?.isActive == false) {
                        startUpdateListener(context)
                    }
                    withContext(Dispatchers.Main) {
                        WhatsAppNotificationListener.forwardNotificationsToLaptop1()
                        showSimpleNotificationExtern(
                            "✅ Sync erfolgreich", "${todos.size} To-dos übertragen",
                            10.seconds, context, silent = false
                        )
                    }
                    pushMediaStateToLaptop(context)
                    cache = Cache()
                    connectedBluetoothDevices.clear()
                    context.registerReceiver(
                        akkuReceiver,
                        IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                        Context.RECEIVER_NOT_EXPORTED
                    )
                    context.registerReceiver(bluetoothReceiver, bluetoothIntentFilter, Context.RECEIVER_NOT_EXPORTED)
                    context.registerReceiver(volumeChangeReceiver, volumeChangeIntentFilter, Context.RECEIVER_NOT_EXPORTED)
                    registerDeviceInfoNetworkListeners(context)
                }

                "EMPTY" -> throw IOException("Server erhielt leere Daten")
                "TIMEOUT" -> throw IOException("Server-Timeout beim Lesen")
                "ERROR" -> throw IOException("Server konnte Daten nicht verarbeiten")
                "UNAUTHORIZED" -> throw IOException("Server hat Sync abgelehnt (ungültiger TOTP-Code)")
                else -> throw IOException("Unbekannte Antwort: $response")
            }
        } catch (e: ConnectException) {
            notifyLaptopUnreachableOnce(context, e)
        } catch (e: SocketTimeoutException) {
            notifyLaptopUnreachableOnce(context, e)
        } catch (e: Exception) {
            val msg = e.message
            if (msg == null || !msg.contains("Connection reset") || !msg.contains("IOException")) {
                logError("syncTodosWithLaptop", e)
                withContext(Dispatchers.Main) {
                    showSimpleNotificationExtern(
                        "❌ Sync Fehler",
                        msg ?: "Unbekannter Fehler",
                        10.seconds,
                        context
                    )
                }
            }
        } finally {
            syncInProgress.set(false)
            if (attemptFailed) {
                registerReconnectFailure()
                onPcCallFailure(context)
            }
        }
    }
}

fun startUpdateListener(context: Context) {
    stopUpdateListener(true)
    appContext = context.applicationContext

    listenerJob = syncScope.launch(Dispatchers.IO) {
        try {
            updateServerSocket = ServerSocket().apply {
                reuseAddress = true
                soTimeout = 5000
                bind(InetSocketAddress(UPDATE_PORT))
            }

            while (isActive) {
                try {
                    val client = updateServerSocket?.accept() ?: break
                    client.use { c ->
                        val reader = BufferedReader(InputStreamReader(c.getInputStream()))
                        val jsonData = reader.readLine()

                        if (!jsonData.isNullOrBlank()) {
                            val updatedTodos = parseTodosFromJson(jsonData)
                            saveTodos(context, updatedTodos)
                            withContext(Dispatchers.Main) {
                                showSimpleNotificationExtern(
                                    "🔄 To-dos aktualisiert",
                                    "Änderungen vom Laptop empfangen",
                                    10.seconds,
                                    context
                                )
                            }
                        }
                    }
                } catch (_: SocketTimeoutException) {
                    continue
                } catch (_: SocketException) {
                    break
                } catch (e: Exception) {
                    logError("startUpdateListener", e)
                }
            }
        } catch (e: Exception) {
            logError("startUpdateListener", e)
        } finally {
            updateServerSocket?.let { runCatching { it.close() } }
            updateServerSocket = null
        }
    }
}

private fun sendSessionDataToLaptop(context: Context) {
    MediaAnalyticsManager.init(context)

    val lastAiTimestamp = loadTodayOrYesterdayEntry(context)?.timestamp ?: 0L
    val sessions = getSessions().filter { it.startedAt >= lastAiTimestamp }

    val cal = Calendar.getInstance()
    cal.add(Calendar.DAY_OF_YEAR, -2)
    val twoDaysAgoStart = cal.timeInMillis
    val previousSessions =
        getSessions().filter { it.startedAt in twoDaysAgoStart..<lastAiTimestamp }

    fun buildJsonArray(list: List<ListenSession>): JSONArray = JSONArray().apply {
        list.forEach { s ->
            put(JSONObject().apply {
                put("label", s.label)
                put("type", s.type)
                put("listenedMs", s.listenedMs)
                put("startedAt", s.startedAt)
                put("repeatCount", s.repeatCount)
            })
        }
    }

    val payload = JSONObject().apply {
        put("today", buildJsonArray(sessions))
        put("previous_2_days", buildJsonArray(previousSessions))
    }

    try {
        if (laptopIp == "") return
        Socket().use { socket ->
            socket.connect(InetSocketAddress(laptopIp, Config.SESSION_PORT), 3000)
            val writer = PrintWriter(socket.getOutputStream(), true)
            writer.println(payload.toString())
            writer.flush()
        }
    } catch (_: SocketTimeoutException) {
    } catch (e: Exception) {
        logError("sendSessionDataToLaptop", e)
    }
}

fun getTodos(context: Context): List<TodoItem> {
    val json = context.getSharedPreferences("todos_prefs", MODE_PRIVATE)
        .getString("todos", "[]") ?: "[]"
    return parseTodosFromJson(json)
}

fun showOpenTodos(context: Context) {
    val todos = getTodos(context).filter { !it.completed }

    if (todos.isEmpty()) {
        showSimpleNotificationExtern(
            "📝 To-dos",
            "Keine To-dos vorhanden",
            10.seconds,
            context = context
        )
        return
    }

    val notificationManager = context.getSystemService(NotificationManager::class.java)

    todos.forEachIndexed { index, todoItem ->
        notificationManager.notify(
            TODOS + index, NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_agenda)
                .setContentTitle(todoItem.text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(todoItem.text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setGroup("todos")
                .build()
        )
    }

    notificationManager.notify(
        TODOS + 150, NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("Erledigte Todos")
            .setContentText("${todos.size} Erledigte Todos")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setGroup("todos")
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()
    )
}

fun saveTodos(context: Context, todos: List<TodoItem>) {
    val prefs = context.getSharedPreferences("todos_prefs", MODE_PRIVATE)
    try {
        prefs.edit { putString("todos", todosToJsonArray(todos).toString()) }
    } catch (e: Exception) {
        logError("saveTodos", e)
    }
}

fun addTodo(text: String, context: Context) {
    val todos = getTodos(context).toMutableList()
    todos.add(
        TodoItem(
            id = System.currentTimeMillis(),
            text = text,
            completed = false
        )
    )
    saveTodos(context, todos)
    showSimpleNotificationExtern(
        "✅ To-do hinzugefügt",
        "\"$text\"\n\nGesamt: ${todos.size} To-dos",
        10.seconds,
        context
    )
}

fun completeTodo(index: Int, context: Context) {
    val todos = getTodos(context).toMutableList()
    if (index in todos.indices) {
        todos[index] = todos[index].copy(completed = true)
        saveTodos(context, todos)
        showSimpleNotificationExtern("✓ Erledigt", "\"${todos[index].text}\"", 10.seconds, context)
    } else {
        showSimpleNotificationExtern(
            "❌ Fehler",
            "To-do #${index + 1} existiert nicht",
            10.seconds,
            context
        )
    }
}

fun removeTodo(index: Int, context: Context) {
    val todos = getTodos(context).toMutableList()
    if (index in todos.indices) {
        val removed = todos.removeAt(index)
        saveTodos(context, todos)
        showSimpleNotificationExtern("🗑️ Gelöscht", "\"${removed.text}\"", 10.seconds, context)
    } else {
        showSimpleNotificationExtern(
            "❌ Fehler",
            "To-do #${index + 1} existiert nicht",
            10.seconds,
            context
        )
    }
}

fun showAllTodos(context: Context) {
    val todos = getTodos(context)

    if (todos.isEmpty()) {
        showSimpleNotificationExtern(
            "📝 To-dos",
            "Keine To-dos vorhanden",
            10.seconds,
            context = context
        )
        return
    }

    val activeTodos = todos.filter { !it.completed }
    val completedTodos = todos.filter { it.completed }
    val todoIndexMap = todos.mapIndexed { index, todo -> todo.id to (index + 1) }.toMap()

    val todoText = buildString {
        if (activeTodos.isNotEmpty()) {
            append("📌 OFFEN (${activeTodos.size}):\n")
            activeTodos.forEach { append("${todoIndexMap[it.id]}. ${it.text}\n") }
        }
        if (completedTodos.isNotEmpty()) {
            if (activeTodos.isNotEmpty()) append("\n")
            append("✓ ERLEDIGT (${completedTodos.size}):\n")
            completedTodos.forEach { append("${todoIndexMap[it.id]}. ${it.text}\n") }
        }
    }

    val chunks = splitText(todoText)
    val notificationManager = context.getSystemService(NotificationManager::class.java)

    chunks.forEachIndexed { index, chunk ->
        notificationManager.notify(
            TODOS + index, NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_agenda)
                .setContentTitle("📝 To-do Liste (${todos.size})")
                .setStyle(NotificationCompat.BigTextStyle().bigText(chunk))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setGroup("todos")
                .build()
        )
    }

    notificationManager.notify(
        TODOS + 50, NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("Alle Todos")
            .setContentText("${chunks.size} Todos")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setGroup("todos")
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()
    )
}

private fun splitText(text: String): List<String> {
    val parts = mutableListOf<String>()
    var start = 0
    while (start < text.length) {
        val end = minOf(start + 200, text.length)
        parts.add(text.substring(start, end))
        start = end
    }
    return parts
}

private fun parseTodosFromJson(jsonData: String): List<TodoItem> =
    try {
        val jsonArray = JSONArray(jsonData)
        (0 until jsonArray.length()).map { i ->
            jsonArray.getJSONObject(i).run {
                TodoItem(
                    getLong("id"),
                    getString("text"),
                    getBoolean("completed")
                )
            }
        }
    } catch (e: Exception) {
        logError("parseTodosFromJson", e)
        emptyList()
    }

fun startAiResponseListener(context: Context) {
    if (aiResponseJob?.isActive == true) return

    syncScope.launch {
        val existing = loadTodayOrYesterdayEntry(context)
        if (existing != null) aiResponseFlow.emit(existing)
    }

    aiResponseJob =
        launchServer(syncScope, Config.AI_RECEIVE_PORT, "startAiResponseListener") { client ->
            val text = client.inputStream.readBytes().toString(Charsets.UTF_8)
            saveAiResponse(context, text)
            aiResponseFlow.emit(
                AiResponseEntry(
                    text = text,
                    timestamp = System.currentTimeMillis(),
                    dateKey = getTodayKey()
                )
            )
            showSimpleNotificationExtern(
                "🤖 AI Antwort",
                text.take(100),
                30.seconds,
                context
            )
        }
}

private const val MAX_AI_RESPONSE_ENTRIES = 500

fun saveAiResponse(context: Context, text: String) {
    val prefs = context.getSharedPreferences("ai_responses", MODE_PRIVATE)
    val dateKey = getTodayKey()
    val timestamp = System.currentTimeMillis()

    val arr = JSONArray(prefs.getString("all_entries", "[]") ?: "[]")
    arr.put(JSONObject().apply {
        put("text", text)
        put("timestamp", timestamp)
        put("dateKey", dateKey)
    })

    // "all_entries" auf eine Maximalgröße kappen, statt es für immer wachsen
    // zu lassen, und dabei auch die pro-Tag-Keys der verworfenen Einträge
    // entfernen, damit keine verwaisten entry_/timestamp_-Keys zurückbleiben.
    val trimmed = JSONArray()
    val droppedDateKeys = mutableSetOf<String>()
    val start = maxOf(0, arr.length() - MAX_AI_RESPONSE_ENTRIES)
    for (i in 0 until arr.length()) {
        val obj = arr.getJSONObject(i)
        if (i < start) droppedDateKeys.add(obj.getString("dateKey")) else trimmed.put(obj)
    }
    val remainingDateKeys = (0 until trimmed.length())
        .mapTo(mutableSetOf()) { trimmed.getJSONObject(it).getString("dateKey") }
    droppedDateKeys.removeAll(remainingDateKeys)

    prefs.edit {
        putString("all_entries", trimmed.toString())
        putString("entry_$dateKey", text)
        putLong("timestamp_$dateKey", timestamp)
        droppedDateKeys.forEach { key ->
            remove("entry_$key")
            remove("timestamp_$key")
        }
    }
}

fun deleteAiResponse(context: Context, timestamp: Long) {
    val prefs = context.getSharedPreferences("ai_responses", MODE_PRIVATE)
    val arr = JSONArray(prefs.getString("all_entries", "[]") ?: "[]")
    val newArr = JSONArray()

    for (i in 0 until arr.length()) {
        val obj = arr.getJSONObject(i)
        if (obj.getLong("timestamp") != timestamp) newArr.put(obj)
    }

    prefs.edit {
        putString("all_entries", newArr.toString())
        val dateKey = run {
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                if (obj.getLong("timestamp") == timestamp) return@run obj.getString("dateKey")
            }
            null
        }
        if (dateKey != null) {
            val stillExists = (0 until newArr.length()).any {
                newArr.getJSONObject(it).getString("dateKey") == dateKey
            }
            if (!stillExists) {
                remove("entry_$dateKey")
                remove("timestamp_$dateKey")
            } else {
                val latest = (0 until newArr.length())
                    .map { newArr.getJSONObject(it) }
                    .filter { it.getString("dateKey") == dateKey }
                    .maxByOrNull { it.getLong("timestamp") }
                if (latest != null) {
                    putString("entry_$dateKey", latest.getString("text"))
                    putLong("timestamp_$dateKey", latest.getLong("timestamp"))
                }
            }
        }
    }
}

fun loadTodayOrYesterdayEntry(context: Context): AiResponseEntry? {
    return loadLatestAiResponse(context)
}

fun loadLatestAiResponse(context: Context): AiResponseEntry? {
    val allResponses = loadAllAiResponses(context)
    return allResponses.firstOrNull()
}

fun loadAllAiResponses(context: Context): List<AiResponseEntry> {
    val prefs = context.getSharedPreferences("ai_responses", MODE_PRIVATE)
    return try {
        val arr = JSONArray(prefs.getString("all_entries", "[]") ?: "[]")
        (0 until arr.length()).map { i ->
            arr.getJSONObject(i).run {
                AiResponseEntry(
                    optString("text", ""),
                    optLong("timestamp", 0L),
                    optString("dateKey", "")
                )
            }
        }.sortedByDescending { it.timestamp }
    } catch (e: Exception) {
        logError("loadAllAiResponses", e)
        emptyList()
    }
}

fun getTodayKey(): String {
    val tz = TimeZone.getTimeZone("Europe/Berlin")
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY).apply {
        timeZone = tz
    }
    return sdf.format(Date())
}

suspend fun trySendImageToLaptop(imageBytes: ByteArray): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            var resolvedIp = laptopIp

            if (resolvedIp.isEmpty()) {
                resolvedIp = fetchIpFromSupabase() ?: return@withContext false
                laptopIp = resolvedIp
            }

            flashcardResponseSocket?.close()
            val serverSocket = try {
                ServerSocket().apply {
                    reuseAddress = true
                    soTimeout = 30_000
                    bind(InetSocketAddress(FLASHCARD_RECEIVE_PORT))
                }
            } catch (e: Exception) {
                logError("trySendImageToLaptop_bind", e)
                return@withContext false
            }
            flashcardResponseSocket = serverSocket
            startFlashcardResponseListener(serverSocket)

            Socket().use { sock ->
                sock.connect(InetSocketAddress(resolvedIp, Config.FLASHCARD_SEND_PORT), 3000)
                sock.getOutputStream().write(imageBytes)
                sock.shutdownOutput()
            }
            true
        } catch (_: SocketTimeoutException) {
            false
        } catch (_: ConnectException) {
            false
        } catch (e: Exception) {
            logError("trySendImageToLaptop", e)
            flashcardVokabelnFlow.emit(null)
            false
        }
    }
}

private fun startFlashcardResponseListener(boundSocket: ServerSocket) {
    flashcardResponseJob?.cancel()
    flashcardVokabelnFlow.value = null

    flashcardResponseJob = syncScope.launch(Dispatchers.IO) {
        try {
            val client = boundSocket.accept()
            val vokabeln =
                parseVokabelnFromJson(client.inputStream.readBytes().toString(Charsets.UTF_8))
            client.close()
            flashcardVokabelnFlow.emit(vokabeln.ifEmpty { null })
        } catch (_: SocketTimeoutException) {
            flashcardVokabelnFlow.emit(null)
        } catch (e: Exception) {
            logError("startFlashcardResponseListener", e)
            flashcardVokabelnFlow.emit(null)
        } finally {
            flashcardResponseSocket?.close()
            flashcardResponseSocket = null
        }
    }
}

private fun parseVokabelnFromJson(json: String): List<Vokabel> = try {
    val arr = JSONArray(json)
    (0 until arr.length()).map { i ->
        arr.getJSONObject(i).run { Vokabel(getString("latein"), getString("deutsch"), i) }
    }
} catch (e: Exception) {
    logError("parseVokabelnFromJson", e)
    emptyList()
}

fun startMediaCommandListener(context: Context) {
    if (mediaCommandJob?.isActive == true) return
    mediaCommandJob =
        launchServer(mediaScope, Config.MEDIA_COMMAND_PORT, "startMediaCommandListener") { client ->
            val sb = StringBuilder()
            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
            var line: String?
            while (reader.readLine().also { line = it } != null) sb.append(line)
            client.close()
            handleMediaCommand(context, JSONObject(sb.toString()))
        }
}

private fun handleMediaCommand(context: Context, json: JSONObject) {
    val action = json.optString("action", "")

    fun sendIntent(intentAction: String, extras: (Intent.() -> Unit)? = null) {
        val intent = Intent(context, MediaPlayerService::class.java).apply {
            this.action = intentAction
            extras?.invoke(this)
        }
        context.startService(intent)
    }

    when (action) {
        "togglePlayPause" -> {
            val prefs = context.getSharedPreferences("music_player_prefs", MODE_PRIVATE)
            val isPlaying = prefs.getBoolean("is_playing", false)
            val mode = prefs.getString("current_mode", "music") ?: "music"
            if (isPlaying) {
                sendIntent(if (mode == "music") "com.tabslify.ACTION_MUSIC_PAUSE" else "com.tabslify.ACTION_PODCAST_PAUSE")
            } else {
                if (mode == "music") MediaPlayerService.sendMusicPlayAction(context)
                else MediaPlayerService.sendPodcastPlayAction(context)
            }
        }

        "play" -> MediaPlayerService.sendMusicPlayAction(context)
        "pause" -> sendIntent("com.tabslify.ACTION_MUSIC_PAUSE")
        "next" -> sendIntent("com.tabslify.ACTION_MUSIC_NEXT")
        "previous" -> sendIntent("com.tabslify.ACTION_MUSIC_PREVIOUS")
        "toggleRepeat" -> sendIntent(ACTION_TOGGLE_REPEAT)
        "toggleFavorite" -> sendIntent("com.tabslify.ACTION_TOGGLE_FAVORITE")

        "activatePlaylist" -> {
            val playlistId = json.optString("playlistId", "")
            val songIndex = json.optInt("index", 0)
            if (playlistId.isNotEmpty()) {
                sendIntent("com.tabslify.ACTION_ACTIVATE_PLAYLIST") {
                    putExtra("PLAYLIST_ID", playlistId)
                    putExtra(MediaPlayerService.EXTRA_SONG_INDEX, songIndex)
                }
            }
        }

        "activateAlgorithmicPlaylist" -> {
            val playlistId = json.optString("playlistId", "")
            val songIndex = json.optInt("index", 0)
            if (playlistId.isNotEmpty()) {
                MediaPlayerService.activateAlgorithmicPlaylist(context, playlistId, songIndex)
            }
        }

        "seek" -> {
            sendIntent("com.tabslify.ACTION_SEEK") {
                putExtra("SEEK_POSITION_MS", json.optLong("positionMs", 0L))
            }
        }

        "setVolume" -> {
            val level = json.optInt("level", -1)
            if (level in 0..100) {
                val audioManager =
                    context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val maxVol =
                    audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val targetVol = (level / 100.0 * maxVol).toInt().coerceIn(0, maxVol)
                audioManager.setStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    targetVol,
                    0
                )
            }
        }

        "stopMediaPlayerService" -> {
            MediaPlayerService.stopService(context)
        }

        "deleteNotif" -> {
            val extras = json.optJSONObject("extras")
            val key = extras?.optString("key", "")?.takeIf { it.isNotBlank() }
            val notificationId = when (val idValue = extras?.opt("id")) {
                is Int -> idValue
                is Long -> idValue.toInt()
                is String -> idValue.toIntOrNull()
                else -> null
            }

            // 1) Bevorzugt exakt per stabilem key verwerfen.
            var dismissed = key?.let {
                WhatsAppNotificationListener.cancelExternalNotificationByKey(it)
            } ?: false

            // 2) Fallback auf die (nicht eindeutige) ID.
            if (!dismissed && notificationId != null) {
                dismissed = WhatsAppNotificationListener.cancelExternalNotification(notificationId)
                if (!dismissed) {
                    // 3) Letzter Fallback: lokal/von Tabslify gepostete Notification.
                    val nm = context.getSystemService(NotificationManager::class.java)
                    nm.cancel(notificationId)
                }
            }

            // Frischen Snapshot senden, damit PC/Website das Entfernen schnell
            // bestätigen (statt erst nach dem 500ms-Debounce von onNotificationRemoved).
            WhatsAppNotificationListener.forwardNotificationsToLaptop1()
        }

        else -> {}
    }
}

fun startMediaStateServer(context: Context) {
    if (mediaStateJob?.isActive == true) return
    mediaStateJob =
        launchServer(mediaScope, Config.MEDIA_STATE_PORT, "startMediaStateServer") { client ->
            val command =
                BufferedReader(InputStreamReader(client.getInputStream())).readLine()?.trim()
                    ?: ""
            val response = when (command) {
                "GET_MEDIA_STATE" -> buildMediaStateJson(context)
                "GET_PLAYLISTS" -> buildPlaylistsJson(context)
                else -> "{}"
            }
            client.getOutputStream().apply {
                write(response.toByteArray(Charsets.UTF_8))
                flush()
            }
            client.close()
        }
}

fun startSpotifyHistoryListener(context: Context) {
    if (spotifyHistoryJob?.isActive == true) return
    spotifyHistoryJob =
        launchServer(mediaScope, Config.SPOTIFY_HISTORY_PORT, "startSpotifyHistoryListener") { client ->
            val sb = StringBuilder()
            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
            var line: String?
            while (reader.readLine().also { line = it } != null) sb.append(line)
            client.close()
            handleSpotifyHistoryUpload(context, sb.toString())
        }
}

private fun handleSpotifyHistoryUpload(context: Context, body: String) {
    val sessions = try {
        val arr = JSONArray(body)
        (0 until arr.length()).mapNotNull { i ->
            val obj = arr.optJSONObject(i) ?: return@mapNotNull null
            val label = obj.optString("label", "").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val startedAt = obj.optLong("startedAt", 0L)
            val listenedMs = obj.optLong("listenedMs", 0L)
            if (startedAt <= 0L || listenedMs <= 0L) return@mapNotNull null
            val type = obj.optString("type", "music").takeIf { it == "podcast" } ?: "music"
            ListenSession(
                label = label,
                type = type,
                listenedMs = listenedMs,
                startedAt = startedAt,
                source = "spotify_pc"
            )
        }
    } catch (e: Exception) {
        logError("handleSpotifyHistoryUpload", e)
        emptyList()
    }
    MediaAnalyticsManager.init(context.applicationContext)
    val added = MediaAnalyticsManager.mergeSessions(sessions)
    Log.d("CLOUDSA", "Spotify-PC-Verlauf: ${sessions.size} empfangen, $added neu gemerged")
}

private fun buildMediaStateJson(context: Context): String {
    val musicPrefs = context.getSharedPreferences("music_player_prefs", MODE_PRIVATE)

    if (!MediaPlayerService.isServiceActive()) {
        return JSONObject().apply {
            put("mode", "music")
            put("isPlaying", false)
            put("currentSong", "")
            put("currentPlaylist", "")
            put("songIndex", 0)
            put("isFavorite", false)
            put("activePlaylistId", "")
            put("activeAlgoPlaylistId", "")
            put("positionMs", 0)
            put("durationMs", 0)
        }.toString()
    }

    val podcastPrefs = context.getSharedPreferences("podcast_player_prefs", MODE_PRIVATE)
    val mode = musicPrefs.getString("current_mode", "music") ?: "music"
    val isPlaying = musicPrefs.getBoolean("is_playing", false)

    val songName: String
    val playlistName: String
    val songIndex: Int
    val isFavorite: Boolean
    val activePlaylistId: String
    val activeAlgoPlaylistId: String
    val positionMs: Long
    val durationMs: Long

    if (mode == "music") {
        songName = musicPrefs.getString("current_song_name", "") ?: ""
        val activePl = musicPrefs.getString("active_playlist_id", "") ?: ""
        val activeAlgoPl = musicPrefs.getString("active_algorithmic_playlist_id", "") ?: ""
        songIndex = musicPrefs.getInt("current_song_index", 0)
        val favorites = musicPrefs.getString("favorite_songs", null)
            ?.split("|||")
            ?.mapNotNull { entry -> entry.split(":::").takeIf { it.isNotEmpty() }?.get(0) }
            ?.toSet() ?: emptySet()
        isFavorite = favorites.contains(songName)
        activePlaylistId = activePl
        activeAlgoPlaylistId = activeAlgoPl
        positionMs = 0L
        durationMs = 0L
        playlistName = when {
            activeAlgoPl.isNotEmpty() -> activeAlgoPl
            activePl.isNotEmpty() -> activePl
            else -> "Alle Songs"
        }
    } else {
        val path = podcastPrefs.getString("current_podcast_path", null)
        songName = path?.substringAfterLast("/")?.substringBeforeLast(".") ?: ""
        positionMs = if (path != null) podcastPrefs.getLong(
            "podcast_position_${path.hashCode()}",
            0L
        ) else 0L
        durationMs = 0L
        playlistName = "Podcast"
        songIndex = 0
        isFavorite = false
        activePlaylistId = ""
        activeAlgoPlaylistId = ""
    }

    return JSONObject().apply {
        put("mode", mode)
        put("isPlaying", isPlaying)
        put("currentSong", songName)
        put("currentPlaylist", playlistName)
        put("positionMs", positionMs)
        put("durationMs", durationMs)
        put("songIndex", songIndex)
        put("isFavorite", isFavorite)
        put("activePlaylistId", activePlaylistId)
        put("activeAlgoPlaylistId", activeAlgoPlaylistId)
    }.toString()
}

private fun buildPlaylistsJson(context: Context): String {
    val musicPrefs = context.getSharedPreferences("music_player_prefs", MODE_PRIVATE)

    val playlistsJson = JSONArray()
    try {
        musicPrefs.getString("playlists_json", null)
            ?.split("\n---\n")?.filter { it.isNotBlank() }?.forEach { line ->
                val parts = line.split(":::", limit = 4)
                if (parts.size >= 3) {
                    val items = if (parts.size > 3 && parts[3].isNotEmpty())
                        parts[3].split("|~~|") else emptyList()
                    playlistsJson.put(JSONObject().apply {
                        put("id", parts[0])
                        put("name", parts[1])
                        put("type", parts[2])
                        put("song_count", items.size)
                    })
                }
            }
    } catch (e: Exception) {
        logError("buildPlaylistsJson", e)
    }

    val algoJson = JSONArray()
    AlgorithmicPlaylistRegistry.all.forEach { source ->
        algoJson.put(JSONObject().apply {
            put("id", source.id)
            put("name", context.getString(source.nameRes))
            put("description", context.getString(source.descriptionRes))
            put("icon", source.icon)
        })
    }

    val songsJson = JSONArray()
    try {
        val proj = arrayOf(
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.TITLE
        )
        var index = 0
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj, null, null,
            "${MediaStore.Audio.Media.DISPLAY_NAME} ASC"
        )?.use { cursor ->
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            while (cursor.moveToNext()) {
                val data = cursor.getString(dataCol) ?: continue
                val norm = data.lowercase()
                val inTabslify = norm.contains("/music/tabslify/", ignoreCase = true)
                if (inTabslify && !norm.contains("/podcast")) {
                    val name = cursor.getString(nameCol) ?: continue
                    val title = cursor.getString(titleCol)
                    val displayName = if (!title.isNullOrBlank() && title != "<unknown>") title
                    else name.substringBeforeLast('.')
                    songsJson.put(JSONObject().apply {
                        put("index", index++)
                        put("name", displayName)
                    })
                }
            }
        }
    } catch (e: Exception) {
        logError("buildPlaylistsJson", e)
    }

    return JSONObject().apply {
        put("playlists", playlistsJson)
        put("algorithmicPlaylists", algoJson)
        put("algo_playlists", algoJson)
        put("songs", songsJson)
    }.toString()
}

var lastPushTime: Long = 0L

fun pushMediaStateToLaptop(context: Context) {
    if (!isLaptopConnected) return
    val now = System.currentTimeMillis()
    if (now - lastPushTime < 5000L) return
    val state = buildMediaStateJson(context)
    if (state == lastPushedState) return
    lastPushedState = state
    lastPushTime = now
    syncScope.launch(Dispatchers.IO) {
        try {
            if (laptopIp == "") return@launch
            Socket().use { socket ->
                socket.connect(InetSocketAddress(laptopIp, 8901), 2000)
                socket.getOutputStream().apply {
                    write(state.toByteArray(Charsets.UTF_8))
                    flush()
                }
            }
            onPcCallSuccess()
        } catch (_: SocketTimeoutException) {
            onPcCallFailure(context)
        } catch (_: ConnectException) {
            onPcCallFailure(context)
        } catch (e: Exception) {
            logError("pushMediaStateToLaptop", e)
            onPcCallFailure(context)
        }
    }
}

@SuppressLint("MissingPermission")
fun checkIfNearLocation(
    context: Context,
    targetLat: Double = Config.LAT,
    targetLon: Double = Config.LON,
    radiusMeters: Float = 550f,
    unknownLocationCountsAsHome: Boolean = true,
    callback: (Boolean) -> Unit
) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
    ) {
        callback(unknownLocationCountsAsHome)
        return
    }

    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    fusedLocationClient.lastLocation
        .addOnSuccessListener { location ->
            if (location != null) {
                callback(
                    distanceBetween(
                        location.latitude,
                        location.longitude,
                        targetLat,
                        targetLon
                    ) <= radiusMeters
                )
            } else {
                callback(unknownLocationCountsAsHome)
            }
        }
        .addOnFailureListener { callback(unknownLocationCountsAsHome) }
}

fun distanceBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
    val earthRadius = 6371000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a =
        sin(dLat / 2).pow(2.0) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(
            dLon / 2
        ).pow(
            2.0
        )
    return (earthRadius * 2 * atan2(sqrt(a), sqrt(1 - a))).toFloat()
}

suspend fun askServer(
    history: List<ChatMessage>,
    question: String,
    model: String,
    pic: String?
): String {
    return withContext(Dispatchers.IO) {
        try {
            if (laptopIp == "") throw Exception("Keine laptopIp is vorhanden")
            val request =
                "MODEL=$model PICTURE=${pic ?: "NONE"} ${question}. Here is the chat history:$history "
            Socket(laptopIp, Config.AI_PORT).use { sock ->
                sock.getOutputStream().write(request.toByteArray(Charsets.UTF_8))
                sock.shutdownOutput()
                sock.getInputStream().readBytes().toString(Charsets.UTF_8)
            }
        } catch (e: Exception) {
            var msg = ""
            e.message?.let {
                msg =
                    if (it.contains("failed to connect")) "Keine Verbindung mit Server möglich" else "Fehler: ${e.message}"
            }
            msg
        }
    }
}

fun buildSessionStatsText(sessions: List<ListenSession>): String {
    val totals = mutableMapOf<String, Triple<Long, String, Int>>()
    for (s in sessions) {
        val cur = totals[s.label] ?: Triple(0L, s.type, 0)
        totals[s.label] = Triple(cur.first + s.listenedMs, s.type, cur.third + 1)
    }

    val sorted = totals.entries.sortedByDescending { it.value.first }.take(15)
    val allTimes = sessions.map { it.startedAt }.sorted()
    val fmt = SimpleDateFormat("HH:mm", Locale.GERMANY)

    val lines = mutableListOf(
        "Zeitraum: ${fmt.format(allTimes.first())} – ${fmt.format(allTimes.last())}",
        "Tracks gesamt: ${totals.size}"
    )

    for ((label, info) in sorted) {
        val mins = info.first / 1000.0 / 60.0
        val typ = if (info.second == "music") "Musik" else "Podcast"
        lines += "- [$typ] $label: ${"%.1f".format(mins)} min, ${info.third}x"
    }

    return """Hör-Stats von heute (bereits berechnet):
${lines.joinToString("\n")}

Schreib jetzt 3-5 lockere Sätze auf Deutsch. Musik UND Podcast erwähnen wenn beides da ist. Nur fließender Text, keine Liste, kein Markdown."""
}

fun startClipboardListener(context: Context) {
    if (clipboardJob?.isActive == true) return
    clipboardJob =
        launchServer(syncScope, Config.CLIPBOARD_PORT, "startClipboardListener") { client ->
            val text = client.inputStream.bufferedReader().readText()
            client.close()
            if (text.startsWith("CLIPBOARD:")) {
                val content = text.removePrefix("CLIPBOARD:")
                withContext(Dispatchers.Main) {
                    val cm =
                        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("sync", content))
                }
            }
        }
}

fun startExecuteListener(context: Context) {
    if (executeJob?.isActive == true) return
    executeJob =
        launchServer(syncScope, Config.EXECUTE_PORT, "startExecuteListener") { client ->
            val json = JSONObject(client.inputStream.readBytes().toString(Charsets.UTF_8))
            client.close()
            handleExecuteCommand(context, json)
        }
}

private fun handleExecuteCommand(context: Context, json: JSONObject) {
    val tool = json.optString("tool")
    val args = json.optJSONObject("args") ?: JSONObject()

    when (tool) {
        "execute_command" -> {
            val command = args.optString("command")
            val totp = args.optString("totp")
            val uuidPrefs = context.getSharedPreferences("pc_uuids", MODE_PRIVATE)
            if (laptopName.isEmpty()) {
                laptopName = context.getSharedPreferences(PREFS_SYNC, MODE_PRIVATE)
                    .getString(KEY_LAST_LAPTOP, "").orEmpty()
                    .ifEmpty { uuidPrefs.all.keys.firstOrNull().orEmpty() }
            }
            val totpKey = resolveSyncSecret(context, laptopName)
            val now = System.currentTimeMillis()
            val valid = totpKey != null && listOf(
                generateTOTP(totpKey, now - 30000),
                generateTOTP(totpKey, now),
                generateTOTP(totpKey, now + 30000)
            ).contains(totp)
            if (valid) {
                syncScope.launch(Dispatchers.Main) {
                    executeCommand(command, context)
                }
            } else {
                showSimpleNotificationExtern(
                    "Ungültiger TOTP",
                    "Falscher TOTP Code",
                    10.seconds,
                    context
                )
            }
        }

        "show_toast" -> {
            syncScope.launch(Dispatchers.Main) {
                Toast.makeText(context, args.optString("message", ""), Toast.LENGTH_LONG).show()
            }
        }

        "show_notification" -> {
            showSimpleNotificationExtern(
                args.optString("title", "AI"),
                args.optString("text", ""),
                10.seconds,
                context
            )
        }

        "add_todo" -> addTodo(args.optString("text", ""), context)

        "play_music" -> {
            val intent = Intent(context, MediaPlayerService::class.java).apply {
                this.action = when (args.optString("action", "play")) {
                    "pause" -> "com.tabslify.ACTION_MUSIC_PAUSE"
                    "next" -> "com.tabslify.ACTION_MUSIC_NEXT"
                    "previous" -> "com.tabslify.ACTION_MUSIC_PREVIOUS"
                    else -> "com.tabslify.ACTION_MUSIC_PLAY"
                }
            }
            context.startService(intent)
        }

        "set_alarm" -> {
            val parts = args.optString("time", "").split(":")
            if (parts.size == 2) {
                val hour = parts[0].toIntOrNull() ?: return
                val minutes = parts[1].toIntOrNull() ?: return
                context.startActivity(Intent(AlarmClock.ACTION_SET_ALARM).apply {
                    putExtra(AlarmClock.EXTRA_HOUR, hour)
                    putExtra(AlarmClock.EXTRA_MINUTES, minutes)
                    putExtra(AlarmClock.EXTRA_MESSAGE, "Alarm")
                })
            }
        }

        "send_whatsapp" -> {
            context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                data = "https://wa.me/${
                    args.getString("phone_number").replace("+", "")
                }?text=${Uri.encode(args.getString("message"))}".toUri()
                setPackage("com.whatsapp")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            })
        }

        "get_contacts" -> {
            val query = args.optString("query", "").lowercase()
            val results = JSONArray()
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )?.use { cursor ->
                val nameCol =
                    cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberCol =
                    cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameCol) ?: continue
                    val number = cursor.getString(numberCol) ?: continue
                    if (query.isEmpty() || name.lowercase().contains(query)) {
                        results.put(JSONObject().apply {
                            put("name", name)
                            put("number", number.replace(" ", "").replace("-", ""))
                        })
                    }
                }
            }
            syncScope.launch(Dispatchers.IO) {
                try {
                    if (laptopIp.isEmpty()) return@launch
                    Socket().use { sock ->
                        sock.connect(
                            InetSocketAddress(laptopIp, Config.EXECUTE_RESPONSE_PORT),
                            3000
                        )
                        sock.getOutputStream().use { output ->
                            output.write(results.toString().toByteArray(Charsets.UTF_8))
                            output.flush()
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }

        "lookup_credentials" -> {
            syncScope.launch(Dispatchers.IO) {
                val queries = args.optJSONArray("queries")
                    ?.let { arr ->
                        (0 until arr.length()).map {
                            arr.getString(it).lowercase()
                        }
                    }
                    ?: emptyList()

                val allPasswords = PasswordDatabase.getDatabase(context).passwordDao().getAll()
                val allTwoFa = TwoFADatabase.getDatabase(context).twoFADao().getAll()

                val matchedPasswords = allPasswords.filter { entry ->
                    queries.any { q ->
                        entry.name.contains(
                            q,
                            ignoreCase = true
                        ) || entry.username.contains(q, ignoreCase = true)
                    }
                }
                val matchedTwoFa = allTwoFa.filter { entry ->
                    queries.any { q -> entry.name.contains(q, ignoreCase = true) }
                }

                val response = JSONObject().apply {
                    put("matchedCredentials", JSONArray(matchedPasswords.map { entry ->
                        JSONObject().apply {
                            put("id", entry.id)
                            put("name", entry.name)
                            put("username", entry.username)
                        }
                    }))
                    put("matchedTwoFaCodes", JSONArray(matchedTwoFa.map { entry ->
                        JSONObject().apply {
                            put("id", entry.id)
                            put("name", entry.name)
                        }
                    }))
                }

                val jsonBytes = response.toString().toByteArray(Charsets.UTF_8)
                val sizePrefix = ByteBuffer.allocate(4).putInt(jsonBytes.size).array()

                try {
                    if (laptopIp.isEmpty()) return@launch
                    Socket().use { sock ->
                        sock.connect(
                            InetSocketAddress(laptopIp, Config.EXECUTE_RESPONSE_PORT),
                            3000
                        )
                        sock.getOutputStream().use { output ->
                            output.write(sizePrefix + jsonBytes)
                            output.flush()
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }

        "reveal_credentials" -> {
            syncScope.launch(Dispatchers.IO) {
                val credentialId = args.optInt("credential_id", -1)
                val twofaId = args.optInt("twofa_id", -1)

                if (credentialId == -1) {
                    return@launch
                }

                val entry = PasswordDatabase.getDatabase(context).passwordDao().getAll()
                    .firstOrNull { it.id == credentialId }

                if (entry == null) {
                    showSimpleNotificationExtern(
                        "❌ Zugangsdaten",
                        "Eintrag #$credentialId nicht gefunden",
                        10.seconds,
                        context
                    )
                    return@launch
                }

                val totpCode: String? = if (twofaId != -1) {
                    TwoFADatabase.getDatabase(context).twoFADao().getAll()
                        .firstOrNull { it.id == twofaId }
                        ?.let { runCatching { generateTOTP(it.secret) }.getOrNull() }
                } else null

                showCredentialsOverlay(context, entry.username, entry.password, totpCode ?: "")
            }
        }

        "connect_bluetooth" -> {
            val devices = mutableListOf<String>()
            args.optJSONArray("devices")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optString(i).takeIf { it.isNotBlank() }?.let { devices.add(it) }
                }
            }
            args.optString("device").takeIf { it.isNotBlank() }?.let { devices.add(it) }

            syncScope.launch(Dispatchers.IO) {
                for (device in devices.distinct()) {
                    if (connectedBluetoothDevices.isNotEmpty()) break
                    val result = CompletableDeferred<Boolean>()
                    connectBluetoothDevice(context, device) { ok, msg ->
                        Log.d("CLOUDSA", "[BT] $device -> $msg")
                        result.complete(ok)
                    }
                    val ok = withTimeoutOrNull(8000.milliseconds) { result.await() } ?: false
                    if (ok) {
                        withTimeoutOrNull(10000.milliseconds) {
                            while (connectedBluetoothDevices.isEmpty()) delay(500.milliseconds)
                        }
                        if (connectedBluetoothDevices.isNotEmpty()) break
                    }
                }
            }
        }

        "disconnect_bluetooth" -> {
            val devices = mutableListOf<String>()
            args.optJSONArray("devices")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optString(i).takeIf { it.isNotBlank() }?.let { devices.add(it) }
                }
            }
            args.optString("device").takeIf { it.isNotBlank() }?.let { devices.add(it) }

            syncScope.launch(Dispatchers.IO) {
                for (device in devices.distinct()) {
                    if (connectedBluetoothDevices.isEmpty()) break
                    val result = CompletableDeferred<Boolean>()
                    disconnectBluetoothDevice(context, device) { ok, msg ->
                        Log.d("CLOUDSA", "[BT] disconnect $device -> $msg")
                        result.complete(ok)
                    }
                    withTimeoutOrNull(8000.milliseconds) { result.await() }
                }
            }
        }

        else -> {}
    }
}

fun showCredentialsOverlay(context: Context, us: String, pw: String, totp: String) {
    if (!Settings.canDrawOverlays(context)) {
        Toast.makeText(context, "Overlay-Berechtigung fehlt!", Toast.LENGTH_LONG).show()
        return
    }

    val appContext = context.applicationContext
    val windowManager = appContext.getSystemService(WINDOW_SERVICE) as WindowManager
    var testOverlayView: ComposeView? = null
    var testOverlayLifecycle: OverlayLifecycleOwner? =
        OverlayLifecycleOwner().also { it.onCreate(); it.onResume() }
    var autoCloseRunnable: Runnable? = null

    fun tearDownOverlay() {
        autoCloseRunnable?.let { Handler(Looper.getMainLooper()).removeCallbacks(it) }
        try {
            testOverlayView?.let { windowManager.removeView(it) }
        } catch (_: Exception) {
        }
        try {
            testOverlayLifecycle?.onDestroy()
        } catch (_: Exception) {
        }
        testOverlayView = null
        testOverlayLifecycle = null
        autoCloseRunnable = null
    }

    testOverlayView = ComposeView(appContext).apply {
        setViewTreeLifecycleOwner(testOverlayLifecycle)
        setViewTreeSavedStateRegistryOwner(testOverlayLifecycle)
        setViewTreeViewModelStoreOwner(testOverlayLifecycle)
        setContent {
            Box(
                modifier = Modifier
                    .height(210.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(APP_COLOR),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    listOf(us, pw, totp).forEach { value ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable {
                                    val clipboard =
                                        appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(
                                        ClipData.newPlainText(
                                            "username",
                                            value
                                        )
                                    )
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            Text(
                                text = value,
                                textAlign = TextAlign.Center,
                                color = Color.White,
                                fontSize = 30.sp,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                IconButton(
                    onClick = { tearDownOverlay() },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(40.dp)
                        .background(
                            Color.Black.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(50)
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Schließen",
                        tint = Color.White
                    )
                }
            }
        }
    }

    val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.CENTER }

    try {
        windowManager.addView(testOverlayView, params)
        val runnable = Runnable { tearDownOverlay() }
        autoCloseRunnable = runnable
        Handler(Looper.getMainLooper()).postDelayed(runnable, 30_000L)
    } catch (_: Exception) {
        testOverlayLifecycle?.onDestroy()
        testOverlayView = null
        testOverlayLifecycle = null
    }
}

fun sendAiExecuteCommand(context: Context, userInput: String) {
    if (laptopIp.isEmpty()) {
        showSimpleNotificationExtern(
            "❌ AI Execute",
            "Laptop nicht verbunden",
            10.seconds,
            context
        )
        return
    }

    syncScope.launch(Dispatchers.IO) {
        try {
            Socket().use { sock ->
                sock.connect(
                    InetSocketAddress(laptopIp, Config.EXECUTE_PORT_SEND_FROM_HANDY),
                    3000
                )
                sock.getOutputStream().use { output ->
                    output.write(
                        JSONObject().apply { put("prompt", userInput) }.toString()
                            .toByteArray(Charsets.UTF_8)
                    )
                    output.flush()
                }
                sock.shutdownOutput()
            }
        } catch (e: Exception) {
            logError("sendAiExecuteCommand", e)
        }
    }
}

private suspend fun fetchIpFromSupabase(mobile: Boolean = false): String? =
    withContext(Dispatchers.IO) {
        val column = if (!mobile) "laptop" else "handy"
        repeat(3) { attempt ->
            try {
                val result = Config.safeCall {
                    Config.client
                        .from("device_ips")
                        .select(columns = Columns.list("ip_address")) {
                            filter { eq("device_id", column) }
                        }
                        .data
                }

                JSONArray(result)
                    .optJSONObject(0)
                    ?.optString("ip_address", "")
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { return@withContext it }
            } catch (e: Exception) {
                if (attempt == 2) logError("SupabaseFetch", e)
            }

            if (attempt < 2) delay((1000L * (attempt + 1)).milliseconds)
        }
        null
    }

@Volatile
private var lastInsertedMobileIp = ""

private suspend fun insertMobileIpToSupabase(ipAddress: String): Boolean =
    withContext(Dispatchers.IO) {
        if (!Config.realDevice) return@withContext true
        if (ipAddress == lastInsertedMobileIp) return@withContext true
        try {
            Config.safeCall {
                Config.client
                    .from("device_ips")
                    .update(buildJsonObject {
                        put("ip_address", ipAddress)
                        put("last_seen", Instant.now().toString())
                    }) {
                        filter { eq("device_id", "handy") }
                    }
            }
            lastInsertedMobileIp = ipAddress
            true
        } catch (_: HttpRequestException) {
            false
        }
        catch (e: Exception) {
            logError("SupabaseInsert", e)
            false
        }
    }

fun getHotspotIp(): String? {
    val interfaces = NetworkInterface.getNetworkInterfaces()

    for (intf in interfaces) {
        if (!intf.isUp || intf.isLoopback) continue

        for (addr in intf.inetAddresses) {
            if (!addr.isLoopbackAddress && addr is Inet4Address) {
                val ip = addr.hostAddress
                if (ip != null) {
                    if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.16.")) {
                        return ip
                    }
                }
            }
        }
    }
    return null
}

private fun isMobileDataActive(context: Context): Boolean {
    val hasPhoneState =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_BASIC_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED
    if (hasPhoneState) {
        try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            return tm.isDataEnabled
        } catch (_: SecurityException) {
        }
    }
    return try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        @Suppress("DEPRECATION")
        cm.allNetworks.any { network ->
            cm.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        }
    } catch (_: SecurityException) {
        false
    }
}

private fun isWifiConnectedAndActive(context: Context): Boolean {
    return try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        @Suppress("DEPRECATION")
        cm.allNetworks.any { network ->
            val caps = cm.getNetworkCapabilities(network) ?: return@any false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
    } catch (_: SecurityException) {
        false
    }
}

@SuppressLint("MissingPermission")
private fun isHotspotActive(context: Context): Boolean {
    val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    try {
        val method = wifiManager.javaClass.getMethod("isWifiApEnabled")
        return method.invoke(wifiManager) as Boolean
    } catch (_: Exception) {
    }
    return try {
        NetworkInterface.getNetworkInterfaces()?.toList()?.any { intf ->
            intf.isUp && !intf.isLoopback && (
                intf.name.startsWith("ap") ||
                    intf.name.startsWith("swlan") ||
                    intf.name.contains("softap", ignoreCase = true)
                )
        } ?: false
    } catch (_: Exception) {
        false
    }
}

private fun getHotspotState(context: Context): String {
    if (!isHotspotActive(context)) return "off"
    return if (isWifiConnectedAndActive(context)) "wifi" else "mobile"
}

fun getVolume(context: Context): Int {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
    val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    return if (max > 0) ((current * 100f) / max).roundToInt().coerceIn(0, 100) else 0
}

fun registerDeviceInfoNetworkListeners(context: Context) {
    val appCtx = context.applicationContext
    unregisterDeviceInfoNetworkListeners(appCtx)

    val cm = appCtx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            reportDeviceInformation(appCtx)
        }

        override fun onLost(network: Network) {
            reportDeviceInformation(appCtx)
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            reportDeviceInformation(appCtx)
        }
    }
    deviceInfoNetworkCallback = callback
    cm.registerNetworkCallback(NetworkRequest.Builder().build(), callback)

    val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent?) {
            reportDeviceInformation(ctx.applicationContext)
        }
    }
    deviceInfoBroadcastReceiver = receiver
    val filter = IntentFilter().apply {
        addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
        addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
        addAction(WIFI_AP_STATE_CHANGED_ACTION)
    }
    appCtx.registerReceiver(receiver, filter)

    val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            reportDeviceInformation(appCtx)
        }
    }
    mobileDataObserver = observer
    appCtx.contentResolver.registerContentObserver(
        Settings.Global.getUriFor("mobile_data"),
        false,
        observer
    )
}

fun unregisterDeviceInfoNetworkListeners(context: Context) {
    val appCtx = context.applicationContext
    val cm = appCtx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    deviceInfoNetworkCallback?.let {
        try {
            cm.unregisterNetworkCallback(it)
        } catch (_: Exception) {
        }
    }
    deviceInfoNetworkCallback = null

    deviceInfoBroadcastReceiver?.let {
        try {
            appCtx.unregisterReceiver(it)
        } catch (_: Exception) {
        }
    }
    deviceInfoBroadcastReceiver = null

    mobileDataObserver?.let {
        try {
            appCtx.contentResolver.unregisterContentObserver(it)
        } catch (_: Exception) {
        }
    }
    mobileDataObserver = null
}

fun reportDeviceInformation(context: Context, intent: Intent? = null) {
    val appCtx = context.applicationContext
    if (intent != null) pendingReportIntent = intent

    reportDebounceJob?.cancel()
    reportDebounceJob = syncScope.launch {
        delay(500.milliseconds)
        val finalIntent = pendingReportIntent
        pendingReportIntent = null
        sendDeviceInformation(appCtx, finalIntent)
    }
}

private fun sendDeviceInformation(context: Context, intent: Intent? = null) {
    val mobileData = isMobileDataActive(context)
    val wifi = isWifiConnectedAndActive(context)
    val hotspot = getHotspotState(context)
    val bluetoothOn = isBluetoothOn(context)
    val bluetoothConnectedCount = connectedBluetoothDevices.size
    val volume = getVolume(context)

    val bl = buildString {
        if (intent != null) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val tempRaw = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
            val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
            if (level >= 0) {
                val levelStr = level.toString()
                if (cache.batteryLevel != levelStr) append("battery_level=$levelStr ")
                cache.batteryLevel = levelStr
            }
            if (tempRaw != -1) {
                val temp = (tempRaw / 10f).toString()
                if (cache.temperature != temp) append("temp=$temp ")
                cache.temperature = temp
            }
            val pluggedb = plugged != 0
            if (cache.plugged != pluggedb) append("charging=$pluggedb ")
            cache.plugged = pluggedb
        }
        if (cache.mobileData != mobileData) append("mobile_data=$mobileData ")
        if (cache.wifi != wifi) append("wifi=$wifi ")
        if (cache.hotspot != hotspot) append("hotspot=$hotspot ")
        if (cache.bluetoothOn != bluetoothOn) append("bluetooth_on=$bluetoothOn ")
        if (cache.bluetoothConnectedCount != bluetoothConnectedCount) append("bluetooth_connected=$bluetoothConnectedCount ")
        if (cache.volume != volume) append("volume=$volume")
    }.trim()

    cache.mobileData = mobileData
    cache.wifi = wifi
    cache.hotspot = hotspot
    cache.bluetoothOn = bluetoothOn
    cache.bluetoothConnectedCount = bluetoothConnectedCount
    cache.volume = volume

    if (bl.isEmpty()) return

    syncScope.launch(Dispatchers.IO) {
        try {
            val currentLaptopIp = laptopIp.ifEmpty { return@launch }
            Socket(currentLaptopIp, INFO_PORT).use { socket ->
                socket.getOutputStream().write(bl.toByteArray())
                socket.shutdownOutput()
            }
        } catch (e: Exception) {
            println("Socket error: ${e.message}")
        }
    }
}

fun startSmsListener(context: Context) {
    if (smsJob?.isActive == true) return
    smsJob = launchServer(syncScope, Config.SMS_PORT, "startSmsListener") { client ->
        val command = BufferedReader(InputStreamReader(client.getInputStream())).readLine()?.trim()
        if (command == "fetch") {
            val json = readAllSms(context)
            client.getOutputStream().apply {
                write(json.toByteArray(Charsets.UTF_8))
                flush()
            }
        }
        client.close()
    }
}

private fun readAllSms(context: Context): String {
    val arr = JSONArray()
    try {
        context.contentResolver.query(
            "content://sms/inbox".toUri(),
            arrayOf("_id", "address", "body", "date", "read"),
            null, null, "date DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow("_id")
            val addrCol = cursor.getColumnIndexOrThrow("address")
            val bodyCol = cursor.getColumnIndexOrThrow("body")
            val dateCol = cursor.getColumnIndexOrThrow("date")
            val readCol = cursor.getColumnIndexOrThrow("read")
            while (cursor.moveToNext()) {
                arr.put(JSONObject().apply {
                    put("id", cursor.getLong(idCol))
                    put("address", cursor.getString(addrCol) ?: "")
                    put("body", cursor.getString(bodyCol) ?: "")
                    put("date", cursor.getLong(dateCol))
                    put("read", cursor.getInt(readCol) == 1)
                })
            }
        }
    } catch (e: Exception) {
        logError("readAllSms", e)
    }
    return arr.toString()
}
