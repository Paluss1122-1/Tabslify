package com.tabslify.core.functions

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import com.tabslify.R
import com.tabslify.core.activities.Tabslify.Companion.serviceScope
import com.tabslify.core.objects.tNotify
import com.tabslify.tabs.focusguard.monitoring.UsageTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object BluetoothAppWatcher {
    private const val PREFS = "bluetooth_app_watcher"
    private const val KEY_CURSOR = "cursor_ms"
    private const val KEY_WATCHED_FOREGROUND = "watched_foreground"
    private const val KEY_LAST_APP = "last_app"
    private const val KEY_OWES_SWITCH_OFF = "owes_switch_off"
    private const val KEY_GRACE_UNTIL = "grace_until_ms"
    private const val KEY_LAST_ENABLE_REQUEST = "last_enable_request_ms"
    private const val CHANNEL_ID = "bluetooth_watch_channel"
    private const val NOTIFICATION_ID = 47120
    private const val POLL_MS = 5000L
    private const val ACCESS_CHECK_MS = 60000L
    private const val GRACE_MS = 90000L
    private const val INITIAL_WINDOW_MS = 30000L
    private const val ENABLE_REQUEST_COOLDOWN_MS = 600000L

    private val watchedPackages = setOf("com.awox.homecontrol", "com.abus.one")

    private var job: Job? = null
    private var nextAccessCheckAt = 0L

    fun start(context: Context) {
        if (job != null) return
        val ctx = context.applicationContext
        job = serviceScope.launch(Dispatchers.IO) {
            while (isActive) {
                runCatching { tick(ctx) }
                delay(POLL_MS)
            }
        }
    }

    private fun tick(ctx: Context) {
        val now = System.currentTimeMillis()
        if (now >= nextAccessCheckAt) {
            nextAccessCheckAt = now + ACCESS_CHECK_MS
            if (!UsageTracker.hasUsageAccess(ctx)) return
        }

        val powerManager = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isInteractive) return

        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val cursor = prefs.getLong(KEY_CURSOR, now - INITIAL_WINDOW_MS)
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events = usm.queryEvents(cursor, now)
        val event = UsageEvents.Event()
        var watchedInForeground = prefs.getBoolean(KEY_WATCHED_FOREGROUND, false)
        var lastStamp = cursor

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.timeStamp > lastStamp) lastStamp = event.timeStamp
            val isWatched = event.packageName in watchedPackages
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    val pkg = event.packageName.lowercase()
                    if ("abus" in pkg || "awox" in pkg) {
                        Log.d("CLOUDSA", "[BT] Vordergrund-Event ${event.packageName}, watched=$isWatched")
                    }
                    if (isWatched && !watchedInForeground) {
                        onAppEntered(ctx, event.packageName, now)
                        watchedInForeground = true
                    } else if (!isWatched && watchedInForeground) {
                        onAppLeft(ctx, now)
                        watchedInForeground = false
                    }
                }

                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED ->
                    if (isWatched && watchedInForeground) {
                        onAppLeft(ctx, now)
                        watchedInForeground = false
                    }
            }
        }

        prefs.edit {
            putLong(KEY_CURSOR, lastStamp)
            putBoolean(KEY_WATCHED_FOREGROUND, watchedInForeground)
        }
    }

    private fun onAppEntered(ctx: Context, packageName: String, now: Long) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val btOn = isBluetoothOn(ctx)
        prefs.edit {
            putString(KEY_LAST_APP, packageName)
            putBoolean(KEY_OWES_SWITCH_OFF, !btOn)
            putLong(KEY_GRACE_UNTIL, now + GRACE_MS)
        }
        Log.d("CLOUDSA", "[BT] ${appLabel(ctx, packageName)} vorne, bluetoothOn=$btOn")
        if (btOn) return
        if (!hasBluetoothConnect(ctx)) return
        requestBluetoothEnable(ctx, now)
    }

    private fun requestBluetoothEnable(ctx: Context, now: Long) {
        if (!Settings.canDrawOverlays(ctx)) {
            Log.d("CLOUDSA", "[BT] kein Enable-Start, canDrawOverlays=false")
            return
        }

        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (now - prefs.getLong(KEY_LAST_ENABLE_REQUEST, 0L) < ENABLE_REQUEST_COOLDOWN_MS) {
            return
        }

        try {
            ctx.startActivity(
                Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            prefs.edit { putLong(KEY_LAST_ENABLE_REQUEST, now) }
            Log.d("CLOUDSA", "[BT] ACTION_REQUEST_ENABLE direkt gestartet")
        } catch (e: Exception) {
            Log.e("CLOUDSA", "[BT] direkter Enable-Start nicht moeglich", e)
        }
    }

    private fun onAppLeft(ctx: Context, now: Long) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val packageName = prefs.getString(KEY_LAST_APP, "") ?: ""
        val btOn = isBluetoothOn(ctx)
        Log.d("CLOUDSA", "[BT] $packageName verlassen, bluetoothOn=$btOn")
        if (!prefs.getBoolean(KEY_OWES_SWITCH_OFF, false)) return
        prefs.edit { putBoolean(KEY_OWES_SWITCH_OFF, false) }
        if (!btOn) return
        if (prefs.getLong(KEY_GRACE_UNTIL, 0L) > now) return

        postNotification(
            ctx,
            ctx.getString(R.string.bluetooth_noch_an),
            ctx.getString(R.string.bluetooth_noch_an_text, appLabel(ctx, packageName)),
            ctx.getString(R.string.bluetooth_ausschalten),
            Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
        )
    }

    private fun postNotification(
        ctx: Context,
        title: String,
        text: String,
        actionLabel: String,
        actionIntent: Intent
    ) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    ctx.getString(R.string.bluetooth),
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }

        val pending = PendingIntent.getActivity(
            ctx,
            NOTIFICATION_ID,
            actionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .addAction(android.R.drawable.ic_dialog_info, actionLabel, pending)
            .build()

        tNotify(ctx, NOTIFICATION_ID, notification)
    }

    private fun appLabel(ctx: Context, packageName: String): String = runCatching {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrElse { packageName }

    private fun hasBluetoothConnect(ctx: Context): Boolean =
        ctx.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun isBluetoothOn(ctx: Context): Boolean {
        if (!hasBluetoothConnect(ctx)) return false
        val adapter =
            (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        return adapter?.isEnabled == true
    }
}