package com.tabslify.quiethoursnotificationhelper

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Context.ALARM_SERVICE
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.tabslify.core.functions.showSimpleNotificationExtern
import com.tabslify.core.objects.prvt
import com.tabslify.inactive.ChatService
import com.tabslify.services.QuietHoursNotificationService
import com.tabslify.services.QuietHoursNotificationService.Companion.ACTION_CHANGE_END
import com.tabslify.services.QuietHoursNotificationService.Companion.ACTION_CONTENT_INTENT
import com.tabslify.services.QuietHoursNotificationService.Companion.ACTION_EXECUTE_COMMAND
import com.tabslify.services.QuietHoursNotificationService.Companion.ACTION_NOTIFICATION_DISMISSED
import com.tabslify.services.QuietHoursNotificationService.Companion.ACTION_RESTORE_NOTIFICATION
import com.tabslify.services.QuietHoursNotificationService.Companion.ACTION_SYNC_LAPTOP
import com.tabslify.services.QuietHoursNotificationService.Companion.ALARM_REQUEST_CODE
import com.tabslify.services.QuietHoursNotificationService.Companion.CHANNEL_ID
import com.tabslify.services.QuietHoursNotificationService.Companion.GALLERY_CHANNEL_ID
import com.tabslify.services.QuietHoursNotificationService.Companion.MAIL_CHANNEL_ID
import com.tabslify.services.QuietHoursNotificationService.Companion.NOTIFICATION_ID
import com.tabslify.services.QuietHoursNotificationService.Companion.SSN_CHANNEL_ID
import com.tabslify.services.QuietHoursNotificationService.Companion.THRESHOLD_MINUTES
import com.tabslify.services.QuietHoursNotificationService.Companion.VIRUSTOTAL_CHANNEL_ID
import com.tabslify.services.QuietHoursNotificationService.Companion.VOICE_NOTE_CHANNEL_ID
import com.tabslify.services.QuietHoursNotificationService.Companion.calculateNextStatusChange
import com.tabslify.services.QuietHoursNotificationService.Companion.handler
import com.tabslify.services.QuietHoursNotificationService.Companion.isCurrentlyQuietHours
import com.tabslify.services.QuietHoursNotificationService.Companion.workerHandler
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

fun checkQuietHours(context: Context) {
    val wasQuietHours = isCurrentlyQuietHours
    val nowQuietHours = isQuietHoursNow(context)

    if (wasQuietHours != nowQuietHours) {
        isCurrentlyQuietHours = nowQuietHours
        updateNotification(context)

        showSimpleNotificationExtern(
            if (nowQuietHours) "🌙 Ruhezeit aktiviert" else "☀️ Ruhezeit beendet",
            if (nowQuietHours) {
                "Benachrichtigungen werden gesammelt"
            } else {
                "Normale Benachrichtigungen aktiv"
            },
            10.seconds,
            context,
            silent = false
        )
    }
}

private fun getQuietStartHour(context: Context): Int {
    val prefs = context.getSharedPreferences("quiet_hours_prefs", MODE_PRIVATE)
    val start = prefs.getString("quiet_hours_end", null)?.toIntOrNull() ?: 21
    return start
}

private fun getQuietEndHour(context: Context): Int {
    val prefs = context.getSharedPreferences("quiet_hours_prefs", MODE_PRIVATE)
    val end = prefs.getString("quiet_hours_start", null)?.toIntOrNull() ?: 7
    return end
}

fun scheduleNextCheck(context: Context) {
    val now = Calendar.getInstance()
    val quietStart = getQuietStartHour(context)
    val quietEnd = getQuietEndHour(context)

    val nextChange = calculateNextStatusChange(now, quietStart, quietEnd)
    val delayMillis = nextChange.timeInMillis - now.timeInMillis
    val delayMinutes = delayMillis / 1000 / 60

    val checkRunnable = QuietHoursNotificationService.getCheckRunnable(context)

    if (delayMinutes < THRESHOLD_MINUTES) {
        workerHandler.removeCallbacksAndMessages(null)
        workerHandler.postDelayed(checkRunnable, maxOf(delayMillis, 30_000L))
    } else {
        workerHandler.removeCallbacksAndMessages(null)
        val alarmManager = context.getSystemService(ALARM_SERVICE) as AlarmManager
        if (alarmManager.canScheduleExactAlarms()) {
            scheduleWithAlarmManager(nextChange.timeInMillis, context, checkRunnable)
        } else {
            workerHandler.postDelayed(checkRunnable, maxOf(delayMillis, 30_000L))
        }
    }
}

private fun scheduleWithAlarmManager(
    triggerAtMillis: Long,
    context: Context,
    checkRunnable: Runnable
) {
    try {
        val alarmManager = context.getSystemService(ALARM_SERVICE) as AlarmManager
        if (!alarmManager.canScheduleExactAlarms()) return

        handler.removeCallbacks(checkRunnable)

        val alarmBase = Intent(context, QuietHoursAlarmReceiver::class.java)
        alarmBase.setPackage(context.packageName)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            alarmBase,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.cancel(pendingIntent)

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent
        )

        Log.d(
            "QuietHoursService", "⏰ AlarmManager scheduled: ${
                SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(triggerAtMillis))
            }"
        )
    } catch (e: Exception) {
        Log.e("QuietHoursService", "AlarmManager failed: ", e)
    }
}

fun createNotificationChannel(context: Context) {
    val notificationManager = context.getSystemService(NotificationManager::class.java)

    notificationManager.createNotificationChannel(
        NotificationChannel(
            CHANNEL_ID,
            "Ruhezeiten Überwachung",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Überwacht Ruhezeiten "
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        })

    notificationManager.createNotificationChannel(
        NotificationChannel(
            GALLERY_CHANNEL_ID,
            "Galerie",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Deine persönliche Galerie"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        })

    notificationManager.createNotificationChannel(
        NotificationChannel(
            SSN_CHANNEL_ID,
            "Show Simple Notification",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Generelle Benachrichtigung"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        })

    if (!prvt()) {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                "Nachrichten",
                "Nachrichten",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Nachrichten"
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            })
        notificationManager.createNotificationChannel(
            NotificationChannel(
                VOICE_NOTE_CHANNEL_ID,
                "Sprachnachrichten Player",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Steuerung für WhatsApp Sprachnachrichten"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            })
        notificationManager.createNotificationChannel(
            NotificationChannel(
                MAIL_CHANNEL_ID,
                "AI Zusammenfassungen von E-Mails",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "AI generierte Zusammenfassungen von E-Mails"
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            })
        notificationManager.createNotificationChannel(
            NotificationChannel(
                ChatService.CHANNEL_ID,
                "Chat Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Hintergrund-Service für Chat-Nachrichten"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)
            })
        notificationManager.createNotificationChannel(
            NotificationChannel(
                "chat_messages",
                "Chat Nachrichten",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Benachrichtigungen für neue Chat-Nachrichten"
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                enableVibration(true)
                enableLights(true)
            })
    }

    notificationManager.createNotificationChannel(
        NotificationChannel(
            "ai_tab_notification_channel",
            "AITab Answers",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notification if AITab is not opened and receives an AI answer"
            setShowBadge(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            enableVibration(true)
            enableLights(true)
        })

    notificationManager.createNotificationChannel(
        NotificationChannel(
            VIRUSTOTAL_CHANNEL_ID,
            "VirusTotal Scans",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Zusammenfassung abgeschlossener VirusTotal-Scans"
            setShowBadge(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            enableVibration(true)
            enableLights(true)
        })
}

@SuppressLint("LaunchActivityFromNotification")
fun createNotification(isQuietHours: Boolean, context: Context): Notification {
    val deleteBase = Intent(ACTION_NOTIFICATION_DISMISSED)
    deleteBase.putExtra("notification_id", NOTIFICATION_ID)
    deleteBase.setPackage(context.packageName)
    val deletePendingIntent = PendingIntent.getBroadcast(
        context,
        999,
        deleteBase,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val contentBase = Intent(context, QuietHoursNotificationService::class.java)
    contentBase.action = ACTION_CONTENT_INTENT
    contentBase.setPackage(context.packageName)
    val contentPendingIntent = PendingIntent.getService(
        context,
        1002,
        contentBase,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val commandInput = RemoteInput.Builder("key_command_input")
        .setLabel("Befehl eingeben...")
        .build()

    val commandBase = Intent(ACTION_EXECUTE_COMMAND)
    commandBase.setPackage(context.packageName)

    val commandPendingIntent = PendingIntent.getBroadcast(
        context,
        200,
        commandBase,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
    )

    val commandAction = NotificationCompat.Action.Builder(
        android.R.drawable.ic_menu_search,
        "Befehl",
        commandPendingIntent
    )
        .addRemoteInput(commandInput)
        .setAllowGeneratedReplies(true)
        .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
        .build()

    val builder = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setOngoing(true)
        .setDeleteIntent(deletePendingIntent)
        .setContentIntent(contentPendingIntent)
        .setOngoing(true)
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        .addAction(commandAction)
        .setAllowSystemGeneratedContextualActions(true)
        .setGroup("quiet_hours_main_group")
        .setShowWhen(false)

    if (isQuietHours) {
        builder
            .setContentTitle("🔥 Ready")

        val settingsTarget = context.packageManager.resolveActivity(
            Intent(Settings.ACTION_SETTINGS),
            android.content.pm.PackageManager.ResolveInfoFlags.of(0)
        )?.activityInfo
        if (settingsTarget != null) {
            val settingsBase = Intent(Settings.ACTION_SETTINGS)
            settingsBase.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            settingsBase.setClassName(settingsTarget.packageName, settingsTarget.name)
            val settingsPendingIntent = PendingIntent.getActivity(
                context,
                1001,
                settingsBase,
                PendingIntent.FLAG_IMMUTABLE
            )

            builder.addAction(
                android.R.drawable.ic_menu_preferences,
                "Settings",
                settingsPendingIntent
            )
        }
    } else {
        val prefs = context.getSharedPreferences("quiet_hours_prefs", MODE_PRIVATE)
        val currentStart = prefs.getString("quiet_hours_end", "21") ?: "21"
        val currentEnd = prefs.getString("quiet_hours_start", "7") ?: "7"

        val endRemoteInput = RemoteInput.Builder("key_time_input")
            .setLabel("Endzeit (0-23)")
            .build()

        val endBase = Intent(ACTION_CHANGE_END)
        endBase.setPackage(context.packageName)

        val endPending = PendingIntent.getBroadcast(
            context, 101, endBase,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val endAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_recent_history,
            "Ende: ${currentEnd}h",
            endPending
        )
            .addRemoteInput(endRemoteInput)
            .setShowsUserInterface(false)
            .build()

        builder
            .setContentTitle("👀 Warte...")
            .setContentText("Ready ab $currentEnd Uhr bis $currentStart Uhr")
            .addAction(endAction)
    }

    if (prvt()) {
        val syncBase = Intent(context, QuietHoursNotificationService::class.java)
        syncBase.action = ACTION_SYNC_LAPTOP
        syncBase.setPackage(context.packageName)
        val syncPendingIntent = PendingIntent.getService(
            context, 0, syncBase,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(
            android.R.drawable.ic_dialog_email,
            "Connect",
            syncPendingIntent
        )
    }

    return builder.build()
}

fun updateNotification(context: Context) {
    val serviceIntent = Intent(context, QuietHoursNotificationService::class.java).apply {
        action = ACTION_RESTORE_NOTIFICATION
    }
    context.startForegroundService(serviceIntent)
}

fun isQuietHoursNow(context: Context): Boolean {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val prefs = context.getSharedPreferences("quiet_hours_prefs", MODE_PRIVATE)
    val quietStart = prefs.getString("quiet_hours_start", null)?.toIntOrNull() ?: 7
    val quietEnd = prefs.getString("quiet_hours_end", null)?.toIntOrNull() ?: 21
    return if (quietStart <= quietEnd) {
        hour in quietStart..<quietEnd
    } else {
        hour !in quietEnd..<quietStart
    }
}