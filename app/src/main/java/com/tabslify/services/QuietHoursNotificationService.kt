package com.tabslify.services

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlarmManager
import android.app.DownloadManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.hardware.camera2.CameraManager
import android.media.MediaPlayer
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.tabslify.R
import com.tabslify.core.activities.MainActivity
import com.tabslify.core.activities.Tabslify.Companion.appScope
import com.tabslify.core.functions.errorInsert
import com.tabslify.core.functions.showSimpleNotificationExtern
import com.tabslify.core.objects.Config
import com.tabslify.core.objects.Config.DEL_GAL_CONF
import com.tabslify.core.objects.Config.cms
import com.tabslify.core.objects.Config.realDevice
import com.tabslify.core.objects.prvt
import com.tabslify.core.objects.tNotify
import com.tabslify.core.ui.getDeviceName
import com.tabslify.quiethoursnotificationhelper.AiProvider
import com.tabslify.quiethoursnotificationhelper.AiResponseEntry
import com.tabslify.quiethoursnotificationhelper.CleanupWorker
import com.tabslify.quiethoursnotificationhelper.DailySummaryReceiver
import com.tabslify.quiethoursnotificationhelper.GalleryImage
import com.tabslify.quiethoursnotificationhelper.aiResponseFlow
import com.tabslify.quiethoursnotificationhelper.buildSessionStatsText
import com.tabslify.quiethoursnotificationhelper.checkQuietHours
import com.tabslify.quiethoursnotificationhelper.commandReceiver
import com.tabslify.quiethoursnotificationhelper.createNotification
import com.tabslify.quiethoursnotificationhelper.createNotificationChannel
import com.tabslify.quiethoursnotificationhelper.deleteGalleryImage
import com.tabslify.quiethoursnotificationhelper.ensureReadyForConnect
import com.tabslify.quiethoursnotificationhelper.getTodayKey
import com.tabslify.quiethoursnotificationhelper.isQuietHoursNow
import com.tabslify.quiethoursnotificationhelper.loadGalleryImages
import com.tabslify.quiethoursnotificationhelper.loadTodayOrYesterdayEntry
import com.tabslify.quiethoursnotificationhelper.markMessageAsRead
import com.tabslify.quiethoursnotificationhelper.markReadReceiver
import com.tabslify.quiethoursnotificationhelper.messageSentReceiver
import com.tabslify.quiethoursnotificationhelper.notificationDismissReceiver
import com.tabslify.quiethoursnotificationhelper.playLatestVoiceNote
import com.tabslify.quiethoursnotificationhelper.playNextVoiceNote
import com.tabslify.quiethoursnotificationhelper.playPreviousVoiceNote
import com.tabslify.quiethoursnotificationhelper.restoreSyncIfNeeded
import com.tabslify.quiethoursnotificationhelper.saveAiResponse
import com.tabslify.quiethoursnotificationhelper.scheduleNextCheck
import com.tabslify.quiethoursnotificationhelper.sendAiRequest
import com.tabslify.quiethoursnotificationhelper.showDeleteConfirmation
import com.tabslify.quiethoursnotificationhelper.showNextGalleryImage
import com.tabslify.quiethoursnotificationhelper.showPreviousGalleryImage
import com.tabslify.quiethoursnotificationhelper.showUnreadMessages
import com.tabslify.quiethoursnotificationhelper.shutdownAllWifiDirectServices
import com.tabslify.quiethoursnotificationhelper.startAiResponseListener
import com.tabslify.quiethoursnotificationhelper.startTriggerListenerIfHomeWifi
import com.tabslify.quiethoursnotificationhelper.stopVoiceNote
import com.tabslify.quiethoursnotificationhelper.syncTodosWithLaptop
import com.tabslify.quiethoursnotificationhelper.timeChangeReceiver
import com.tabslify.quiethoursnotificationhelper.updateNotification
import com.tabslify.quiethoursnotificationhelper.updateSingleSenderNotification
import com.tabslify.tabs.audiorecordertab.AudioForegroundService
import com.tabslify.tabs.exploretab.ExploreLocationTracker
import com.tabslify.tabs.mediaplayer.MediaAnalyticsManager
import com.tabslify.tabs.mediaplayer.MediaAnalyticsManager.getSessions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.xml.sax.InputSource
import java.io.File
import java.io.StringReader
import java.lang.ref.WeakReference
import java.net.URL
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class QuietHoursNotificationService : Service() {
    private val errorScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val checkRunnable: Runnable by lazy { getCheckRunnable(this) }
    private val exploreTracker get() = ExploreLocationTracker

    private fun reportServiceError(where: String, t: Throwable) {
        Log.e("QuietHoursService", "Unhandled error in $where", t)
        val stack = t.stackTraceToString()
        val trimmed = if (stack.length > 8000) stack.take(8000) + "\n...[truncated]" else stack
        errorInsert(
            "QuietHoursNotificationService:$where",
            trimmed,
            Instant.now().toString(),
            "ERROR"
        )
    }

    @SuppressLint("BatteryLife")
    fun requestIgnoreBatteryOptimizations(context: Context) {
        val pm = context.getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
            val prefs = context.getSharedPreferences("quiet_hours_prefs", MODE_PRIVATE)
            val alreadyRequested = prefs.getBoolean("battery_optimization_requested", false)

            if (!alreadyRequested) {
                prefs.edit { putBoolean("battery_optimization_requested", true) }
                try {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = "package:${context.packageName}".toUri()
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                    )
                } catch (e: Exception) {
                    reportServiceError("requestIgnoreBatteryOptimizations", e)
                }
            }

            showSimpleNotificationExtern(
                getString(R.string.eingeschrankte_hintergrundaktivitaten),
                getString(R.string.services_laufen_evtl_verzogert_oder),
                Duration.ZERO,
                context = this,
                onClick = "requestIgnoreBatteryOptimizations"
            )
        }
    }

    companion object {
        const val CHANNEL_ID = "quiet_hours_channel"
        const val NOTIFICATION_ID = 999999

        private val WHATSAPP_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")
        private val TELEGRAM_PACKAGES =
            setOf("org.telegram.messenger", "org.telegram.messenger.web")

        fun isSupportedMessenger(packageName: String): Boolean {
            return WHATSAPP_PACKAGES.contains(packageName) || TELEGRAM_PACKAGES.contains(packageName)
        }

        const val ACTION_SHOW_MESSAGES = "com.tabslify.ACTION_SHOW_MESSAGES"
        const val ACTION_SCHEDULED_START = "com.tabslify.ACTION_QUIET_SCHEDULED_START"
        const val ACTION_SCHEDULED_STOP = "com.tabslify.ACTION_QUIET_SCHEDULED_STOP"
        private const val ACTION_OPEN_SETTINGS = "com.tabslify.ACTION_OPEN_SETTINGS"

        const val ACTION_MESSAGE_SENT = "com.tabslify.ACTION_MESSAGE_SENT"
        const val EXTRA_SENDER = "extra_sender"

        private var sharedPreferences: SharedPreferences? = null
        private const val ACTION_OPEN_MUSIC_PLAYER = "com.tabslify.ACTION_OPEN_MUSIC_PLAYER"
        const val ACTION_RESTART_MUSIC_PLAYER =
            "com.tabslify.ACTION_RESTART_MUSIC_PLAYER"

        const val ACTION_NOTIFICATION_DISMISSED =
            "com.tabslify.ACTION_NOTIFICATION_DISMISSED"

        const val ACTION_CHANGE_START = "com.tabslify.ACTION_CHANGE_START"
        const val ACTION_CHANGE_END = "com.tabslify.ACTION_CHANGE_END"


        const val ACTION_PLAY_VOICE_NOTE = "com.tabslify.ACTION_PLAY_VOICE_NOTE"
        const val ACTION_NEXT_VOICE_NOTE = "com.tabslify.ACTION_NEXT_VOICE_NOTE"
        const val ACTION_PREV_VOICE_NOTE = "com.tabslify.ACTION_PREV_VOICE_NOTE"
        const val ACTION_STOP_VOICE_NOTE = "com.tabslify.ACTION_STOP_VOICE_NOTE"
        const val EXTRA_SENDER_FOR_VOICE = "extra_sender_for_voice"

        const val VOICE_NOTE_CHANNEL_ID = "voice_note_player_channel"
        const val ACTION_EXECUTE_COMMAND = "com.tabslify.ACTION_EXECUTE_COMMAND"

        val commandHistory = mutableListOf<String>()

        private const val ACTION_SHOW_GALLERY = "com.tabslify.ACTION_SHOW_GALLERY"
        const val ACTION_NEXT_GALLERY_IMAGE = "com.tabslify.ACTION_NEXT_GALLERY_IMAGE"
        const val ACTION_PREV_GALLERY_IMAGE = "com.tabslify.ACTION_PREV_GALLERY_IMAGE"
        const val GALLERY_CHANNEL_ID = "gallery_channel"

        const val SSN_CHANNEL_ID = "show_simple_not_channel"

        const val SCHOOL_SUMMARY_CHANNEL_ID = "school_day_summary_channel"
        const val SCHOOL_SUMMARY_NOTIF_ID = 9002

        const val ACTION_CONFIRM_DELETE_IMAGE =
            "com.tabslify.ACTION_CONFIRM_DELETE_IMAGE"
        const val ACTION_DELETE_IMAGE = "com.tabslify.ACTION_DELETE_IMAGE"
        const val ACTION_CANCEL_DELETE = "com.tabslify.ACTION_CANCEL_DELETE"
        const val EXTRA_IMAGE_INDEX = "extra_image_index"
        const val MAIL_CHANNEL_ID = "mail_channel"
        const val VIRUSTOTAL_CHANNEL_ID = "virustotal_scan_channel"

        const val ACTION_MARK_PARTS_READ = "com.tabslify.ACTION_MARK_PARTS_READ"
        const val EXTRA_MESSAGE_ID = "extra_message_id"
        const val ALARM_REQUEST_CODE = 1001

        const val THRESHOLD_MINUTES = 30
        const val MAX_MESSAGES_PER_CONTACT = 15
        const val MAX_VOICE_NOTE_FILES = 20

        const val ACTION_RESTORE_NOTIFICATION = "com.tabslify.ACTION_RESTORE_NOTIFICATION"

        const val ACTION_UPDATE_SINGLE_SENDER = "com.tabslify.ACTION_UPDATE_SINGLE_SENDER"
        const val ACTION_CONTENT_INTENT = "com.tabslify.ACTION_CONTENT_INTENT"
        const val ACTION_SYNC_LAPTOP = "com.tabslify.ACTION_SYNC_LAPTOP"
        const val SHOW_OVERLAY = "com.tabslify.SHOW_OVERLAY"
        const val ACTION_DAILY_MUSIC_SUMMARY = "com.tabslify.ACTION_DAILY_MUSIC_SUMMARY"
        const val ACTION_SCHOOL_DAY_SUMMARY = "com.tabslify.ACTION_SCHOOL_DAY_SUMMARY"
        const val SCHEDULE_DAILY_SUMMARY_ALARM = "com.tabslify.SCHEDULE_DAILY_SUMMARY_ALARM"

        const val ACTION_PODCAST_CHECK = "com.tabslify.ACTION_PODCAST_CHECK"
        const val ACTION_PODCAST_DOWNLOAD = "com.tabslify.ACTION_PODCAST_DOWNLOAD"
        const val ACTION_PODCAST_RETRY = "com.tabslify.ACTION_PODCAST_RETRY"
        const val PODCAST_CHANNEL_ID = "podcast_check_channel"
        const val PODCAST_NOTIFICATION_ID = 9100

        var currentSenderForVoiceNote: String? = null
        var voiceNoteFiles: List<File> = emptyList()
        var voiceNotePlayer: MediaPlayer? = null
        var currentVoiceNoteIndex = 0
        val readMessageIds = mutableSetOf<String>()

        val handlerThread = HandlerThread("QuietHoursWorker").apply { start() }
        val workerHandler = Handler(handlerThread.looper)
        val mainHandler = Handler(Looper.getMainLooper())
        private var galleryImagesRef: WeakReference<List<GalleryImage>> =
            WeakReference(emptyList())

        var galleryImages: List<GalleryImage>
            get() = galleryImagesRef.get() ?: emptyList()
            set(value) {
                galleryImagesRef = WeakReference(value)
            }
        var currentGalleryIndex = 0
        var isCurrentlyQuietHours = false
        val handler = Handler(Looper.getMainLooper())

        fun getCheckRunnable(context: Context): Runnable = Runnable {
            try {
                checkQuietHours(context)
                scheduleNextCheck(context)
            } catch (e: Exception) {
                errorInsert(
                    "QuietHoursNotificationService:checkRunnable",
                    e.stackTraceToString().take(8000),
                    Instant.now().toString(),
                    "ERROR"
                )
            }
        }

        fun startService(context: Context) {
            val intent = Intent(context, QuietHoursNotificationService::class.java)
            context.startForegroundService(intent)
        }

        fun updateSingleSenderNotification(context: Context, sender: String) {
            val intent = Intent(context, QuietHoursNotificationService::class.java).apply {
                action = ACTION_UPDATE_SINGLE_SENDER
                putExtra("EXTRA_SENDER", sender)
            }
            context.startService(intent)
        }

        fun showtestOverlay(context: Context) {
            val intent = Intent(context, QuietHoursNotificationService::class.java).apply {
                action = SHOW_OVERLAY
            }
            context.startService(intent)
        }

        fun calculateNextStatusChange(
            now: Calendar,
            quietStart: Int,
            quietEnd: Int
        ): Calendar {
            val nextChange = Calendar.getInstance().apply {
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val currentHour = now.get(Calendar.HOUR_OF_DAY)
            val spansOverMidnight = quietEnd < quietStart

            val isQuietNow = if (spansOverMidnight) {
                currentHour !in quietEnd..<quietStart
            } else {
                currentHour in quietStart..<quietEnd
            }

            if (isQuietNow) {
                nextChange.set(Calendar.HOUR_OF_DAY, quietEnd)
                nextChange.set(Calendar.MINUTE, 0)

                if (spansOverMidnight && currentHour >= quietStart) {
                    nextChange.add(Calendar.DAY_OF_YEAR, 1)
                } else if (nextChange.timeInMillis <= now.timeInMillis) {
                    nextChange.add(Calendar.DAY_OF_YEAR, 1)
                }
            } else {
                nextChange.set(Calendar.HOUR_OF_DAY, quietStart)
                nextChange.set(Calendar.MINUTE, 0)

                if (nextChange.timeInMillis <= now.timeInMillis) {
                    nextChange.add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            return nextChange
        }

        fun scheduledailysummaryalarm(context: Context) {
            val intent = Intent(context, QuietHoursNotificationService::class.java).apply {
                action = SCHEDULE_DAILY_SUMMARY_ALARM
            }
            context.startService(intent)
        }

        fun schedulePodcastCheck(context: Context) {
            val am = context.getSystemService(ALARM_SERVICE) as AlarmManager

            if (!am.canScheduleExactAlarms()) return

            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 15)
                set(Calendar.MINUTE, 30)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
            }

            val podcastCheckBase = Intent(context, QuietHoursNotificationService::class.java)
            podcastCheckBase.action = ACTION_PODCAST_CHECK
            podcastCheckBase.setPackage(context.packageName)
            val pi = PendingIntent.getService(
                context, 0,
                podcastCheckBase,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
        }
    }

    private val prefChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "quiet_hours_start" || key == "quiet_hours_end") {
                handler.removeCallbacks(checkRunnable)

                try {
                    isCurrentlyQuietHours = isQuietHoursNow(this)
                    updateNotification(this)
                } catch (e: Exception) {
                    reportServiceError("prefChangeListener:$key", e)
                }

                handler.post(checkRunnable)
            }
        }

    override fun onCreate() {
        super.onCreate()
        realDevice = !getDeviceName().trim().contains("sdk_gphone", ignoreCase = true)
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        if (!prefs.getBoolean("services_master", true) || !prefs.getBoolean("service_qhns", false)) {
            createNotificationChannel(this)
            startForeground(
                NOTIFICATION_ID,
                createNotification(isCurrentlyQuietHours, this)
            )
            stopSelf()
            return
        }
        requestIgnoreBatteryOptimizations(this)
        Config.init(this)
        sharedPreferences = getSharedPreferences("quiet_hours_prefs", MODE_PRIVATE)
        createNotificationChannel(this)

        isCurrentlyQuietHours = isQuietHoursNow(this)
        try {
            startForeground(NOTIFICATION_ID, createNotification(isCurrentlyQuietHours, this))
        } catch (e: Exception) {
            reportServiceError("onCreate:startForeground", e)
        }

        try {
            sharedPreferences?.registerOnSharedPreferenceChangeListener(prefChangeListener)
        } catch (e: Exception) {
            reportServiceError("onCreate:registerOnSharedPreferenceChangeListener", e)
        }

        handler.post(checkRunnable)
        schedulePeriodicCleanup(this)
        scheduleSyncTriggerWorker(this)
        scheduleSchoolDaySummary(this)
        schedulePodcastCheck(this)
        restoreSyncIfNeeded(this)
        startTriggerListenerIfHomeWifi(this)
        startAiResponseListener(this)
        scheduleDailySummaryAlarm()

        val filter = IntentFilter(ACTION_MESSAGE_SENT)
        try {
            registerReceiver(messageSentReceiver, filter, RECEIVER_NOT_EXPORTED)
        } catch (e: Exception) {
            reportServiceError("onCreate:registerReceiver messageSentReceiver", e)
        }

        val dismissFilter = IntentFilter(ACTION_NOTIFICATION_DISMISSED)
        try {
            registerReceiver(notificationDismissReceiver, dismissFilter, RECEIVER_NOT_EXPORTED)
        } catch (e: Exception) {
            reportServiceError("onCreate:registerReceiver notificationDismissReceiver", e)
        }

        val timeChangeFilter = IntentFilter().apply {
            addAction(ACTION_CHANGE_START)
            addAction(ACTION_CHANGE_END)
        }
        try {
            registerReceiver(timeChangeReceiver, timeChangeFilter, RECEIVER_NOT_EXPORTED)
        } catch (e: Exception) {
            reportServiceError("onCreate:registerReceiver timeChangeReceiver", e)
        }

        val commandFilter = IntentFilter(ACTION_EXECUTE_COMMAND)
        try {
            registerReceiver(commandReceiver, commandFilter, RECEIVER_NOT_EXPORTED)
        } catch (e: Exception) {
            reportServiceError("onCreate:registerReceiver commandReceiver", e)
        }

        val markReadFilter = IntentFilter(ACTION_MARK_PARTS_READ)
        try {
            registerReceiver(markReadReceiver, markReadFilter, RECEIVER_NOT_EXPORTED)
        } catch (e: Exception) {
            reportServiceError("onCreate:registerReceiver markReadReceiver", e)
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        ) {
            exploreTracker.start(this)
        }
    }

    private fun scheduleDailySummaryAlarm() {
        val am = getSystemService(ALARM_SERVICE) as AlarmManager

        if (!am.canScheduleExactAlarms()) {
            return
        }

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 20)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val summaryBase = Intent(this, DailySummaryReceiver::class.java)
        summaryBase.setPackage(packageName)
        val pi = PendingIntent.getBroadcast(
            this, 0,
            summaryBase,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
    }

    private fun schedulePeriodicCleanup(context: Context) {
        val work = PeriodicWorkRequestBuilder<CleanupWorker>(
            6, TimeUnit.HOURS
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "periodic_cleanup",
            ExistingPeriodicWorkPolicy.KEEP,
            work
        )
    }

    private fun scheduleSyncTriggerWorker(context: Context) {
        val work = PeriodicWorkRequestBuilder<com.tabslify.quiethoursnotificationhelper.SyncTriggerWorker>(
            15, TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "sync_trigger_check",
            ExistingPeriodicWorkPolicy.KEEP,
            work
        )
    }

    private fun scheduleSchoolDaySummary(context: Context) {
        if (!prvt()) return
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val schoolBase = Intent(context, QuietHoursNotificationService::class.java)
        schoolBase.action = ACTION_SCHOOL_DAY_SUMMARY
        schoolBase.setPackage(context.packageName)
        val pending = PendingIntent.getService(
            context, 9001, schoolBase,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 20)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }

        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            cal.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pending
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return try {
            when (intent?.action) {
                ACTION_SCHEDULED_STOP -> {
                    stopSelf()
                    START_NOT_STICKY
                }

                SCHEDULE_DAILY_SUMMARY_ALARM -> {
                    scheduleDailySummaryAlarm()
                    if (prvt()) {
                        checkPermissionsForPrvt()
                    }
                    START_NOT_STICKY
                }

                ACTION_SCHEDULED_START -> {
                    isCurrentlyQuietHours = isQuietHoursNow(this)
                    startForeground(
                        NOTIFICATION_ID,
                        createNotification(isCurrentlyQuietHours, this)
                    )
                    START_STICKY
                }

                ACTION_SHOW_MESSAGES -> {
                    showUnreadMessages(this)
                    START_STICKY
                }

                ACTION_MARK_PARTS_READ -> {
                    val messageId = intent.getStringExtra(EXTRA_MESSAGE_ID)
                    if (messageId != null) {
                        markMessageAsRead(messageId, readMessageIds, this)
                    }
                    START_STICKY
                }

                SHOW_OVERLAY -> {
                    showTestOverlay()
                    START_STICKY
                }

                ACTION_RESTORE_NOTIFICATION -> {
                    val notification = createNotification(isCurrentlyQuietHours, this)
                    tNotify(this, NOTIFICATION_ID, notification)
                    START_STICKY
                }

                ACTION_UPDATE_SINGLE_SENDER -> {
                    val sender = intent.getStringExtra("EXTRA_SENDER")
                    if (sender != null) updateSingleSenderNotification(sender, this)
                    START_STICKY
                }

                ACTION_CONTENT_INTENT -> {
                    val cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
                    try {
                        val cameraId = cameraManager.cameraIdList.firstOrNull()
                        if (cameraId != null) cameraManager.turnOnTorchWithStrengthLevel(
                            cameraId,
                            1
                        )
                    } catch (e: Exception) {
                        showSimpleNotification(
                            getString(R.string.taschenlampe),
                            getString(R.string.helligkeit_konnte_nicht_gesetzt_werden, e.message),
                            20.seconds
                        )
                    }
                    START_STICKY
                }

                ACTION_OPEN_SETTINGS -> {
                    openAndroidSettings()
                    START_STICKY
                }

                ACTION_OPEN_MUSIC_PLAYER -> {
                    openMusicPlayer()
                    START_STICKY
                }

                ACTION_RESTART_MUSIC_PLAYER -> {
                    MediaPlayerService.startAndPlayMusic(this)
                    START_STICKY
                }

                ACTION_PLAY_VOICE_NOTE -> {
                    val sender = intent.getStringExtra(EXTRA_SENDER_FOR_VOICE)
                    if (prvt() && sender != null) playLatestVoiceNote(sender, this)
                    START_STICKY
                }

                ACTION_NEXT_VOICE_NOTE -> {
                    playNextVoiceNote(this)
                    START_STICKY
                }

                ACTION_PREV_VOICE_NOTE -> {
                    playPreviousVoiceNote(this)
                    START_STICKY
                }

                ACTION_STOP_VOICE_NOTE -> {
                    stopVoiceNote(this)
                    START_STICKY
                }

                ACTION_SHOW_GALLERY -> {
                    loadGalleryImages(0, this)
                    START_STICKY
                }

                ACTION_NEXT_GALLERY_IMAGE -> {
                    showNextGalleryImage(this)
                    START_STICKY
                }

                ACTION_PREV_GALLERY_IMAGE -> {
                    showPreviousGalleryImage(this)
                    START_STICKY
                }

                ACTION_CONFIRM_DELETE_IMAGE -> {
                    val imageIndex = intent.getIntExtra(EXTRA_IMAGE_INDEX, -1)
                    if (imageIndex >= 0) showDeleteConfirmation(imageIndex, this)
                    START_STICKY
                }

                ACTION_DELETE_IMAGE -> {
                    val imageIndex = intent.getIntExtra(EXTRA_IMAGE_INDEX, -1)
                    if (imageIndex >= 0) deleteGalleryImage(imageIndex, this)
                    START_STICKY
                }

                ACTION_CANCEL_DELETE -> {
                    val notificationManager = getSystemService(NotificationManager::class.java)
                    notificationManager.cancel(DEL_GAL_CONF)
                    START_STICKY
                }

                ACTION_SYNC_LAPTOP -> {
                    sendBroadcast(Intent("com.paluss1122.accessibily.EXECUTE").apply {
                        setPackage("com.paluss1122.accessibily")
                        putExtra("cmd", """{"action":"close_nots"}""")
                    })
                    ensureReadyForConnect(this@QuietHoursNotificationService)
                    syncTodosWithLaptop(this@QuietHoursNotificationService)
                    START_STICKY
                }

                ACTION_DAILY_MUSIC_SUMMARY -> {
                    dailyMusicSummaryJob = appScope.launch {
                        try {
                            kotlinx.coroutines.delay(500.milliseconds)
                            MediaAnalyticsManager.init(this@QuietHoursNotificationService)
                            val lastAiTimestamp =
                                loadTodayOrYesterdayEntry(this@QuietHoursNotificationService)?.timestamp
                                    ?: 0L

                            val sessions = getSessions().filter { it.startedAt >= lastAiTimestamp }
                            if (sessions.isEmpty()) return@launch

                            val stats = buildSessionStatsText(sessions)
                            val result = sendAiRequest(this@QuietHoursNotificationService, userMessage = stats, anlytic = true, serviceKey = "music_summary", provider = AiProvider.NVIDIA, model = "openai/gpt-oss-20b")
                                ?: return@launch
                            val musicMs =
                                sessions.filter { it.type == "music" }.sumOf { it.listenedMs }
                            val podcastMs =
                                sessions.filter { it.type == "podcast" }.sumOf { it.listenedMs }

                            fun fmt(ms: Long): String {
                                val mins = ms / 1000.0 / 60.0
                                return if (mins < 1) "${(ms / 1000).toInt()}s" else "${
                                    "%.1f".format(
                                        mins
                                    )
                                } min"
                            }

                            val parts = mutableListOf<String>()
                            if (musicMs > 0) parts += "🎵${fmt(musicMs)}"
                            if (podcastMs > 0) parts += "🎙️${fmt(podcastMs)}"
                            val suffix = parts.joinToString(" · ")

                            val finalResult =
                                if (suffix.isNotEmpty()) "$result\n\n$suffix" else result

                            saveAiResponse(this@QuietHoursNotificationService, finalResult)
                            aiResponseFlow.emit(
                                AiResponseEntry(
                                    finalResult,
                                    System.currentTimeMillis(),
                                    getTodayKey()
                                )
                            )
                            showSimpleNotification(
                                getString(R.string.tages_zusammenfassung),
                                finalResult,
                                60.seconds
                            )
                        } catch (e: Exception) {
                            reportServiceError("ACTION_DAILY_MUSIC_SUMMARY", e)
                        }
                    }
                    START_STICKY
                }

                ACTION_PODCAST_CHECK -> {
                    try {
                        if (isWifiConnected()) {
                            podcastJob = appScope.launch(Dispatchers.IO) {
                                try {
                                    checkPodcastsAndNotify(this@QuietHoursNotificationService)
                                } catch (e: Exception) {
                                    reportServiceError("ACTION_PODCAST_CHECK", e)
                                }
                            }
                        } else {
                            showPodcastRetryNotification()
                        }
                    } catch (e: Exception) {
                        reportServiceError("ACTION_PODCAST_CHECK", e)
                    }
                    START_NOT_STICKY
                }

                ACTION_PODCAST_RETRY -> {
                    try {
                        if (isWifiConnected()) {
                            podcastJob = appScope.launch(Dispatchers.IO) {
                                try {
                                    checkPodcastsAndNotify(this@QuietHoursNotificationService)
                                } catch (e: Exception) {
                                    reportServiceError("ACTION_PODCAST_RETRY", e)
                                }
                            }
                        } else {
                            showPodcastRetryNotification()
                        }
                    } catch (e: Exception) {
                        reportServiceError("ACTION_PODCAST_RETRY", e)
                    }
                    START_NOT_STICKY
                }

                ACTION_PODCAST_DOWNLOAD -> {
                    try {
                        val json = intent.getStringExtra("episodes_json")
                        if (!json.isNullOrEmpty()) {
                            val arr = JSONArray(json)
                            for (i in 0 until arr.length()) {
                                val o = arr.getJSONObject(i)
                                val url = o.optString("audioUrl")
                                val title = o.optString("title")
                                val show = o.optString("showName")
                                if (url.isNotEmpty()) startPodcastDownload(url, title, show, this)
                            }
                        }
                    } catch (e: Exception) {
                        reportServiceError("ACTION_PODCAST_DOWNLOAD", e)
                    }
                    START_NOT_STICKY
                }

                ACTION_SCHOOL_DAY_SUMMARY -> {
                    if (!prvt()) return START_STICKY
                    val dayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
                    if (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY) return START_STICKY
                    val nm = getSystemService(NotificationManager::class.java)
                    if (nm.getNotificationChannel(SCHOOL_SUMMARY_CHANNEL_ID) == null) {
                        android.app.NotificationChannel(
                            SCHOOL_SUMMARY_CHANNEL_ID,
                            getString(R.string.schultag_zusammenfassung),
                            NotificationManager.IMPORTANCE_HIGH
                        ).apply {
                            enableVibration(true)
                            enableLights(true)
                        }.also { nm.createNotificationChannel(it) }
                    }

                    val launchBase = Intent(this, MainActivity::class.java)
                    launchBase.putExtra("target", "files")
                    launchBase.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    launchBase.setPackage(packageName)
                    val pi = PendingIntent.getActivity(
                        this, SCHOOL_SUMMARY_NOTIF_ID, launchBase,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    val notification = NotificationCompat.Builder(this, SCHOOL_SUMMARY_CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.ic_menu_upload)
                        .setContentTitle(getString(R.string.schultag_beendet))
                        .setContentText(getString(R.string.materialien_von_heute_in_die))
                        .setStyle(
                            NotificationCompat.BigTextStyle()
                                .bigText(getString(R.string.lade_deine_unterlagen_fotos_und))
                        )
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setCategory(NotificationCompat.CATEGORY_REMINDER)
                        .setAutoCancel(true)
                        .setContentIntent(pi)
                        .build()

                    tNotify(this, SCHOOL_SUMMARY_NOTIF_ID, notification)
                    START_STICKY
                }

                "requestIgnoreBatteryOptimizations" -> {
                    requestIgnoreBatteryOptimizations(this)
                    START_STICKY
                }

                else -> {
                    START_STICKY
                }
            }
        } catch (e: Exception) {
            reportServiceError("onStartCommand", e)
            START_STICKY
        }
    }

    private var testOverlayView: ComposeView? = null
    private var testOverlayLifecycle: OverlayLifecycleOwner? = null
    private var testOverlayWebView: WebView? = null
    private var dailyMusicSummaryJob: Job? = null
    private var podcastJob: Job? = null

    private fun tearDownOverlay() {
        try {
            testOverlayView?.let { view ->
                (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(view)
            }
        } catch (e: Exception) {
            reportServiceError("showTestOverlay:removeView", e)
        }
        try {
            testOverlayWebView?.apply {
                stopLoading()
                clearHistory()
                clearCache(true)
                loadUrl("about:blank")
                onPause()
                removeAllViews()
                destroy()
            }
        } catch (e: Exception) {
            reportServiceError("showTestOverlay:webView:destroy", e)
        }
        try {
            testOverlayLifecycle?.onDestroy()
        } catch (e: Exception) {
            reportServiceError("showTestOverlay:overlayLifecycle:onDestroy", e)
        }
        testOverlayView = null
        testOverlayLifecycle = null
        testOverlayWebView = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun showTestOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            showSimpleNotification(getString(R.string.fehler), getString(R.string.overlay_berechtigung_fehlt))
            return
        }

        if (testOverlayView != null) tearDownOverlay()

        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        var currentUrl = "https://www.youtube.com"

        testOverlayLifecycle = OverlayLifecycleOwner().also { it.onCreate(); it.onResume() }

        testOverlayView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(testOverlayLifecycle)
            setViewTreeSavedStateRegistryOwner(testOverlayLifecycle)
            setViewTreeViewModelStoreOwner(testOverlayLifecycle)
            setContent {

                var isDesktopMode by remember { mutableStateOf(false) }
                val webView = remember {
                    WebView(context).apply {
                        webChromeClient = object : WebChromeClient() {
                            private var customView: View? = null
                            private var customViewCallback: CustomViewCallback? = null

                            override fun onShowCustomView(
                                view: View?,
                                callback: CustomViewCallback?
                            ) {
                                (context as? Activity)?.let { activity ->
                                    val decor =
                                        activity.window.decorView as? FrameLayout ?: return@let
                                    val toAdd = view ?: return@let
                                    decor.addView(
                                        toAdd,
                                        FrameLayout.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT
                                        )
                                    )
                                    activity.requestedOrientation =
                                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                    customView = toAdd
                                    customViewCallback = callback

                                    activity.window.insetsController?.apply {
                                        hide(WindowInsets.Type.systemBars())
                                        systemBarsBehavior =
                                            WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                                    }
                                }
                            }

                            override fun onHideCustomView() {
                                (context as? Activity)?.let { activity ->
                                    val decor =
                                        activity.window.decorView as? FrameLayout ?: return@let
                                    customView?.let { decor.removeView(it) }
                                    customView = null
                                    customViewCallback?.onCustomViewHidden()
                                    customViewCallback = null

                                    activity.window.insetsController?.show(
                                        WindowInsets.Type.systemBars()
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
                                if (url != null) currentUrl = url
                                view?.evaluateJavascript(
                                    "var l=document.createElement('link');l.rel='dns-prefetch';l.href='//i.ytimg.com';document.head.appendChild(l);",
                                    null
                                )
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

                            cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                        }

                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(this, true)
                        cookieManager.flush()

                        loadUrl(currentUrl)
                    }
                }
                testOverlayWebView = webView

                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = { webView },
                        update = { },
                        modifier = Modifier.fillMaxSize()
                    )

                    Button(
                        onClick = {
                            isDesktopMode = !isDesktopMode

                            webView.settings.apply {
                                if (isDesktopMode) {
                                    userAgentString =
                                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                                    useWideViewPort = true
                                    loadWithOverviewMode = true
                                } else {
                                    userAgentString =
                                        "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                                    useWideViewPort = true
                                    loadWithOverviewMode = true
                                }
                            }

                            webView.reload()
                        },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Laptop,
                            contentDescription = null,
                            tint = Color.White
                        )
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
                            contentDescription = stringResource(R.string.schliesen),
                            tint = Color.White
                        )
                    }
                }
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        try {
            windowManager.addView(testOverlayView, params)
        } catch (e: Exception) {
            reportServiceError("showTestOverlay:addView", e)
            showSimpleNotification(getString(R.string.fehler), getString(R.string.overlay_konnte_nicht_gestartet_werden))
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        testOverlayView?.let { view ->
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
            }
            try {
                wm.updateViewLayout(view, params)
            } catch (e: Exception) {
                reportServiceError("onConfigurationChanged:updateViewLayout", e)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()

        tearDownOverlay()
        dailyMusicSummaryJob?.cancel()
        podcastJob?.cancel()

        handler.removeCallbacksAndMessages(null)
        workerHandler.removeCallbacksAndMessages(null)

        voiceNotePlayer?.apply {
            try {
                if (isPlaying) stop()
                reset()
                release()
            } catch (e: Exception) {
                reportServiceError("onDestroy:voiceNotePlayerRelease", e)
            }
        }
        voiceNotePlayer = null

        stopService(Intent(this, AudioForegroundService::class.java))

        readMessageIds.clear()
        voiceNoteFiles = emptyList()
        galleryImages = emptyList()
        commandHistory.clear()

        try { unregisterReceiver(messageSentReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(notificationDismissReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(timeChangeReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(commandReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(markReadReceiver) } catch (_: Exception) {}

        try {
            sharedPreferences?.unregisterOnSharedPreferenceChangeListener(prefChangeListener)
        } catch (e: Exception) {
            reportServiceError("onDestroy:unregisterOnSharedPreferenceChangeListener", e)
        }

        try {
            shutdownAllWifiDirectServices(applicationContext)
        } catch (e: Exception) {
            reportServiceError("onDestroy:shutdownAllWifiDirectServices", e)
        }
        
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val shouldRestart = prefs.getBoolean("services_master", true) &&
                prefs.getBoolean("service_qhns", false)

        if (shouldRestart) {
            scheduleRestart(1)
        }

        errorScope.cancel()
        exploreTracker.stop(this)
    }
    
    private fun scheduleRestart(requestCode: Int) {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val now = SystemClock.elapsedRealtime()
        val lastRestart = prefs.getLong("last_service_restart_elapsed", 0L)
        val sinceLast = now - lastRestart

        val delayMs = if (lastRestart != 0L && sinceLast < 10_000L) 15_000L else 1_000L

        prefs.edit { putLong("last_service_restart_elapsed", now) }

        val restartBase = Intent(applicationContext, QuietHoursNotificationService::class.java)
        restartBase.setPackage(applicationContext.packageName)
        val pendingIntent = PendingIntent.getService(
            applicationContext, requestCode, restartBase,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                now + delayMs,
                pendingIntent
            )
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val notification = createNotification(isCurrentlyQuietHours, this)
        startForeground(NOTIFICATION_ID, notification)

        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val shouldRestart = prefs.getBoolean("services_master", true) &&
                prefs.getBoolean("service_qhns", false)

        if (shouldRestart) {
            scheduleRestart(2)
        }
    }

    private fun openAndroidSettings() {
        try {
            val intent = Intent(Settings.ACTION_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("QuietHoursService", "Error opening settings", e)
            showSimpleNotification(getString(R.string.fehler), getString(R.string.einstellungen_konnten_nicht_geoffnet_werden))
        }
    }

    @SuppressLint("LaunchActivityFromNotification")
    suspend fun checkPodcastsAndNotify(context: Context, forGui: Boolean = false): List<JSONObject> {
        val found = mutableListOf<JSONObject>()
        try {
            val prefs = context.getSharedPreferences("podcast_favs", MODE_PRIVATE)
            val raw = prefs.getString("favs", null) ?: return found
            val favsArr = JSONArray(raw)
            val prefsDl = context.getSharedPreferences("podcast_downloads", MODE_PRIVATE)

            val threshold = ZonedDateTime.now(ZoneId.systemDefault())
                .minusDays(7)
                .withHour(15)
                .withMinute(30)
                .withSecond(0)
                .withNano(0)
                .toInstant()

            val destDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PODCASTS),
                "/Tabslify"
            )
            destDir.mkdirs()

            val perFeedResults = coroutineScope {
                (0 until favsArr.length()).map { i ->
                    async(Dispatchers.IO) {
                        val feedFound = mutableListOf<JSONObject>()
                        try {
                            val o = favsArr.getJSONObject(i)
                            val feedTitle = o.optString("title")
                            val feedUrl = o.optString("feedUrl")
                            if (feedUrl.isEmpty()) return@async feedFound

                            val xml = URL(feedUrl).readText()
                            val doc = DocumentBuilderFactory.newInstance()
                                .apply {
                                    setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                                    setFeature("http://xml.org/sax/features/external-general-entities", false)
                                    setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                                    isExpandEntityReferences = false
                                }
                                .newDocumentBuilder()
                                .parse(InputSource(StringReader(xml)))

                            val items = doc.getElementsByTagName("item")
                            for (j in 0 until items.length) {
                                try {
                                    val item = items.item(j)
                                    val children = item.childNodes
                                    var title = ""
                                    var audioUrl = ""
                                    var pubDateTxt = ""
                                    for (k in 0 until children.length) {
                                        val node = children.item(k)
                                        when (node.nodeName) {
                                            "title" -> title = node.textContent.trim()
                                            "enclosure" -> audioUrl =
                                                node.attributes?.getNamedItem("url")?.nodeValue ?: ""

                                            "pubDate" -> pubDateTxt = node.textContent.trim()
                                        }
                                    }

                                    if (audioUrl.isEmpty()) continue

                                    val pubInstant = try {
                                        ZonedDateTime.parse(
                                            pubDateTxt,
                                            DateTimeFormatter.RFC_1123_DATE_TIME.withLocale(Locale.ENGLISH)
                                        ).toInstant()
                                    } catch (_: Exception) {
                                        try {
                                            Instant.parse(pubDateTxt)
                                        } catch (_: Exception) {
                                            null
                                        }
                                    }

                                    if (pubInstant == null) continue
                                    if (pubInstant <= threshold) continue
                                    if (prefsDl.getBoolean("dl_$audioUrl", false)) continue

                                    val safeTitle = title.replace(Regex("[/\\\\:*?\"<>|]"), "_")
                                    val filename = "$safeTitle.mp3"
                                    if (File(destDir, filename).exists()) continue

                                    feedFound.add(JSONObject().apply {
                                        put("audioUrl", audioUrl)
                                        put("title", title)
                                        put("showName", feedTitle)
                                    })
                                } catch (_: Exception) {
                                }
                            }
                        } catch (_: Exception) {
                        }
                        feedFound
                    }
                }.awaitAll()
            }
            perFeedResults.forEach { found.addAll(it) }

            if (found.isNotEmpty() && !forGui) {
                val arr = JSONArray()
                found.forEach { arr.put(it) }

                val nm = context.getSystemService(NotificationManager::class.java)
                if (nm.getNotificationChannel(PODCAST_CHANNEL_ID) == null) {
                    android.app.NotificationChannel(
                        PODCAST_CHANNEL_ID,
                        context.getString(R.string.podcast_check),
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).also { nm.createNotificationChannel(it) }
                }

                val podcastBase = Intent(context, QuietHoursNotificationService::class.java)
                podcastBase.action = ACTION_PODCAST_DOWNLOAD
                podcastBase.putExtra("episodes_json", arr.toString())
                podcastBase.setPackage(context.packageName)
                val pi = PendingIntent.getService(
                    context,
                    PODCAST_NOTIFICATION_ID,
                    podcastBase,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val contentText = if (found.size == 1) {
                    context.getString(R.string.neue_folge, found[0].optString("title"))
                } else {
                    context.resources.getQuantityString(R.plurals.neue_folgen_verfugbar, found.size, found.size)
                }

                val bigText = buildString {
                    found.forEachIndexed { index, episode ->
                        if (index > 0) append(", ")
                        append("📻 ${episode.optString("showName")}: ${episode.optString("title")}")
                    }
                }

                val notification = NotificationCompat.Builder(context, PODCAST_CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_menu_save)
                    .setContentTitle(context.getString(R.string.neue_podcast_folgen_gefunden))
                    .setContentText(contentText)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
                    .setAutoCancel(true)
                    .setContentIntent(pi)
                    .build()

                tNotify(context, PODCAST_NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            reportServiceError("checkPodcastsAndNotify", e)
        }
        return found
    }

    private fun startPodcastDownload(
        audioUrl: String,
        title: String,
        showName: String,
        context: Context
    ) {
        try {
            val safeTitle = title.replace(Regex("[/\\\\:*?\"<>|]"), "_")
            val filename = "$safeTitle.mp3"
            val destDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PODCASTS),
                "/Tabslify"
            )
            destDir.mkdirs()

            val request = DownloadManager.Request(audioUrl.toUri()).apply {
                setTitle(filename)
                setDescription(context.getString(R.string.podcast_wird_heruntergeladen))
                setDestinationUri(File(destDir, filename).toUri())
                setAllowedOverMetered(true)
                addRequestHeader("User-Agent", "Mozilla/5.0")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            }
            val dm = context.getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = dm.enqueue(request)

            val prefs = context.getSharedPreferences("podcast_downloads", MODE_PRIVATE)
            prefs.edit {
                putString("pending_$downloadId", JSONObject().apply {
                    put("safeTitle", safeTitle)
                    put("showName", showName)
                }.toString())
                putBoolean("dl_$audioUrl", true)
            }
        } catch (e: Exception) {
            reportServiceError("startPodcastDownload", e)
        }
    }

    fun showSimpleNotification(
        title: String,
        text: String,
        duration: Duration = Duration.ZERO
    ) {
        val notification = NotificationCompat.Builder(this, SSN_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setGroup("group_info")
            .setGroupSummary(false)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)

        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            tNotify(this, cms(), notification)

            if (duration > Duration.ZERO) {
                Handler(Looper.getMainLooper()).postDelayed(
                    { notificationManager.cancel(cms()) },
                    duration.inWholeMilliseconds
                )
            }
        }
    }

    private fun openMusicPlayer() {
        try {
            MusicPlayerServiceCompat.startService(this)
            MusicPlayerServiceCompat.startAndPlay(this)
        } catch (_: Exception) {
            showSimpleNotification(getString(R.string.fehler), getString(R.string.musik_player_konnte_nicht_geoffnet))
        }
    }

    private fun isWifiConnected(): Boolean {
        return try {
            val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } catch (e: Exception) {
            Log.e("QuietHoursService", "Error checking WiFi connectivity", e)
            false
        }
    }

    private fun checkPermissionsForPrvt() {
        try {
            val dpm =
                getSystemService(DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
            val adminComponent = android.content.ComponentName(
                this,
                "com.tabslify.core.activities.MyDeviceAdminReceiver"
            )
            if (!dpm.isAdminActive(adminComponent)) {
                val intent =
                    Intent(android.app.admin.DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                        putExtra(
                            android.app.admin.DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                            adminComponent
                        )
                        putExtra(
                            android.app.admin.DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                            getString(R.string.ermoglicht_sicherheitsfunktionen)
                        )
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                startActivity(intent)
            }

            val listeners =
                Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
            if (listeners == null || !listeners.contains(packageName)) {
                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            }
        } catch (e: Exception) {
            reportServiceError("checkPermissionsForPrvt", e)
        }
    }

    @SuppressLint("LaunchActivityFromNotification")
    private fun showPodcastRetryNotification() {
        try {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(PODCAST_CHANNEL_ID) == null) {
                android.app.NotificationChannel(
                    PODCAST_CHANNEL_ID,
                    getString(R.string.podcast_check),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).also { nm.createNotificationChannel(it) }
            }

            val retryBase = Intent(this, QuietHoursNotificationService::class.java)
            retryBase.action = ACTION_PODCAST_RETRY
            retryBase.setPackage(packageName)
            val retryPendingIntent = PendingIntent.getService(
                this,
                PODCAST_NOTIFICATION_ID,
                retryBase,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(this, PODCAST_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_save)
                .setContentTitle(getString(R.string.podcast_check_fehlgeschlagen))
                .setContentText(getString(R.string.keine_wifi_verbindung_tippen_zum))
                .setAutoCancel(true)
                .addAction(android.R.drawable.ic_menu_rotate, getString(R.string.wiederholen), retryPendingIntent)
                .setContentIntent(retryPendingIntent)
                .build()

            tNotify(this, PODCAST_NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            reportServiceError("showPodcastRetryNotification", e)
        }
    }
}

class OverlayLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle
        field = LifecycleRegistry(this)
    override val viewModelStore = ViewModelStore()
    override val savedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    fun onCreate() {
        savedStateRegistryController.performRestore(null)
        lifecycle.currentState = Lifecycle.State.CREATED
    }

    fun onResume() {
        lifecycle.currentState = Lifecycle.State.RESUMED
    }

    fun onDestroy() {
        lifecycle.currentState = Lifecycle.State.DESTROYED
    }
}