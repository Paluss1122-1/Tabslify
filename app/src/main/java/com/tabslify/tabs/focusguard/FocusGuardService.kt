package com.tabslify.tabs.focusguard

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.tabslify.R
import com.tabslify.core.activities.MainActivity
import com.tabslify.core.functions.errorInsert
import com.tabslify.services.OverlayLifecycleOwner
import com.tabslify.tabs.focusguard.data.FocusGuardConfig
import com.tabslify.tabs.focusguard.data.FocusGuardRepository
import com.tabslify.tabs.focusguard.monitoring.BlockReason
import com.tabslify.tabs.focusguard.monitoring.ContextDetector
import com.tabslify.tabs.focusguard.monitoring.RestrictionEngine
import com.tabslify.tabs.focusguard.monitoring.StudyGoalTracker
import com.tabslify.tabs.focusguard.monitoring.UsageTracker
import com.tabslify.tabs.focusguard.monitoring.scheduleFocusGuardDailySummary
import com.tabslify.tabs.focusguard.monitoring.scheduleFocusGuardWorkers
import com.tabslify.tabs.focusguard.ui.FocusGuardBlockOverlay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FocusGuardService : Service() {
    private val handlerThread = HandlerThread("FocusGuardLoop").apply { start() }
    private val handler = Handler(handlerThread.looper)

    @Volatile
    private var screenOn = true
    private var overlayView: ComposeView? = null
    private var overlayLifecycle: OverlayLifecycleOwner? = null
    private var overlayWindowManager: WindowManager? = null
    private var currentBlockedPackage: String? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            screenOn = intent.action == Intent.ACTION_SCREEN_ON
            scheduleLoop()
        }
    }

    private val checkRunnable = object : Runnable {
        override fun run() {
            try {
                runCheck()
            } catch (e: Exception) {
                reportError("runCheck", e)
                handler.postDelayed(this, POLL_IDLE_MS)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        ensureServiceChannel()
        if (!prefs.getBoolean("services_master", true) || !prefs.getBoolean("service_focusguard", false)) {
            startForeground(NOTIFICATION_ID, createForegroundNotification())
            stopSelf()
            return
        }
        FocusGuardRepository.init(this)
        startForeground(NOTIFICATION_ID, createForegroundNotification())

        try {
            scheduleFocusGuardWorkers(this)
            scheduleFocusGuardDailySummary(this)
        } catch (_: Exception) {
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        try {
            registerReceiver(screenReceiver, filter, RECEIVER_NOT_EXPORTED)
        } catch (e: Exception) {
            reportError("onCreate:registerReceiver", e)
        }
        scheduleLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        scheduleLoop()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        try {
            unregisterReceiver(screenReceiver)
        } catch (_: Exception) {
        }
        ContextDetector.stop()
        removeOverlay()
        handlerThread.quitSafely()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun scheduleLoop() {
        handler.removeCallbacks(checkRunnable)
        handler.post(checkRunnable)
    }

    private fun runCheck() {
        val now = System.currentTimeMillis()
        val schoolDay = FocusGuardConfig.schoolCalendarEnabled && RestrictionEngine.todayIsSchoolDay()
        val rules = FocusGuardRepository.rules.value
        val restricted = FocusGuardConfig.restrictedApps()
        val possible = RestrictionEngine.restrictionsPossibleToday(
            restricted.keys, rules, schoolDay, FocusGuardConfig.overrideUntilMs, now
        )

        if (!screenOn || !possible || !UsageTracker.hasUsageAccess(this)) {
            ContextDetector.stop()
            removeOverlay()
            handler.postDelayed(checkRunnable, POLL_IDLE_MS)
            return
        }

        ContextDetector.start(this)

        val packageName = UsageTracker.currentForegroundPackage(this)
        if (packageName == null || packageName == this.packageName || packageName == "com.android.systemui") {
            if (currentBlockedPackage != null) {
                currentBlockedPackage = null
                removeOverlay()
            }
            handler.postDelayed(checkRunnable, POLL_ACTIVE_MS)
            return
        }

        val category = UsageTracker.categoryFor(packageName)
        val decision = RestrictionEngine.blockedFor(
            category = category,
            now = now,
            rules = rules,
            afternoonThresholdMin = FocusGuardConfig.afternoonThresholdMin,
            isSchoolDay = schoolDay,
            isResting = ContextDetector.isResting(),
            quotaDone = StudyGoalTracker.quotaDone(),
            overrideUntilMs = FocusGuardConfig.overrideUntilMs
        )

        if (decision.blocked && FocusGuardConfig.overlayEnabled) {
            if (currentBlockedPackage != packageName) {
                currentBlockedPackage = packageName
                showOverlay(packageName, decision)
                FocusGuardNotifications.postBlock(
                    this,
                    appLabel(packageName),
                    reasonText(decision),
                    untilTime(decision)
                )
            }
        } else {
            if (currentBlockedPackage != null) {
                currentBlockedPackage = null
                removeOverlay()
            }
        }
        handler.postDelayed(checkRunnable, POLL_ACTIVE_MS)
    }

    private fun showOverlay(packageName: String, decision: com.tabslify.tabs.focusguard.monitoring.BlockDecision) {
        removeOverlay()
        if (!Settings.canDrawOverlays(this)) return

        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val lifecycle = OverlayLifecycleOwner().also { it.onCreate(); it.onResume() }
        overlayLifecycle = lifecycle

        val appName = appLabel(packageName)
        val reason = reasonText(decision)
        val until = untilText(decision)

        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(lifecycle)
            setViewTreeSavedStateRegistryOwner(lifecycle)
            setViewTreeViewModelStoreOwner(lifecycle)
            setContent {
                FocusGuardBlockOverlay(
                    appName = appName,
                    reasonText = reason,
                    untilText = until,
                    cooldownMinutes = FocusGuardConfig.cooldownMinutes,
                    onContinue = { applyOverride() },
                    onSettings = { openSettings() }
                )
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
            windowManager.addView(view, params)
            overlayView = view
            overlayWindowManager = windowManager
        } catch (e: Exception) {
            reportError("showOverlay:addView", e)
            try {
                lifecycle.onDestroy()
            } catch (_: Exception) {
            }
            overlayLifecycle = null
        }
    }

    private fun removeOverlay() {
        overlayView?.let { view ->
            try {
                overlayWindowManager?.removeView(view)
            } catch (_: Exception) {
            }
        }
        try {
            overlayLifecycle?.onDestroy()
        } catch (_: Exception) {
        }
        overlayView = null
        overlayLifecycle = null
        overlayWindowManager = null
    }

    private fun applyOverride() {
        FocusGuardConfig.overrideUntilMs =
            System.currentTimeMillis() + FocusGuardConfig.cooldownMinutes * 60_000L
        removeOverlay()
        currentBlockedPackage = null
    }

    @SuppressLint("LaunchActivityFromNotification")
    private fun openSettings() {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("target", "focusguard")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            reportError("openSettings", e)
        }
    }

    private fun reasonText(decision: com.tabslify.tabs.focusguard.monitoring.BlockDecision): String =
        when (decision.reason) {
            BlockReason.AFTERNOON_THRESHOLD ->
                getString(R.string.focusguard_reason_afternoon, FocusGuardConfig.afternoonThresholdMin / 60)

            BlockReason.CUSTOM_WINDOW -> getString(R.string.focusguard_reason_custom)
            BlockReason.RESTING -> getString(R.string.focusguard_reason_resting)
            BlockReason.STUDY_QUOTA -> getString(R.string.focusguard_reason_quota)
            BlockReason.NONE -> ""
        }

    private fun untilText(decision: com.tabslify.tabs.focusguard.monitoring.BlockDecision): String {
        val time = untilTime(decision)
        if (time.isEmpty()) return ""
        return getString(R.string.focusguard_overlay_until, time)
    }

    private fun untilTime(decision: com.tabslify.tabs.focusguard.monitoring.BlockDecision): String {
        if (decision.untilMs <= 0L) return ""
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(decision.untilMs))
    }

    private fun appLabel(packageName: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        packageName
    }

    private fun createForegroundNotification(): Notification {
        val contentBase = Intent(this, MainActivity::class.java)
        contentBase.setPackage(packageName)
        val contentIntent = PendingIntent.getActivity(
            this, 980, contentBase,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, SERVICE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(getString(R.string.focusguard_service_notif_title))
            .setContentText(getString(R.string.focusguard_service_notif_text))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .build()
    }

    private fun ensureServiceChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(SERVICE_CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    SERVICE_CHANNEL_ID,
                    getString(R.string.focusguard_service_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    setShowBadge(false)
                    setSound(null, null)
                }
            )
        }
    }

    private fun reportError(where: String, throwable: Throwable) {
        errorInsert(
            "FocusGuardService:$where",
            throwable.stackTraceToString().take(8000),
            java.time.Instant.now().toString(),
            "ERROR"
        )
    }

    companion object {
        const val SERVICE_CHANNEL_ID = "focusguard_service_channel"
        const val NOTIFICATION_ID = 98000
        const val ACTION_STOP = "com.tabslify.ACTION_FOCUSGUARD_STOP"
        private const val POLL_ACTIVE_MS = 10_000L
        private const val POLL_IDLE_MS = 60_000L

        fun startService(context: Context) {
            val intent = Intent(context, FocusGuardService::class.java)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (_: Exception) {
                try {
                    context.startService(intent)
                } catch (_: Exception) {
                }
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, FocusGuardService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {
            }
        }
    }
}
