package com.tabslify.tabs.focusguard

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.tabslify.R
import com.tabslify.core.activities.MainActivity
import com.tabslify.core.objects.tNotify

object FocusGuardNotifications {
    const val CHANNEL_ID = "focusguard_channel"
    const val NOTIF_BLOCK = 95000
    const val NOTIF_DAILY_SUMMARY = 95001
    const val NOTIF_SLEEP_WARNING = 95002
    const val NOTIF_STUDY_REMINDER = 95003

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.focusguard_channel_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.focusguard_channel_desc)
                    setShowBadge(true)
                    enableVibration(true)
                    enableLights(true)
                }
            )
        }
    }

    fun postBlock(
        context: Context,
        appName: String,
        reasonText: String,
        untilText: String
    ) {
        if (!com.tabslify.tabs.focusguard.data.FocusGuardConfig.notificationsEnabled) return
        ensureChannels(context)
        val blockBase = Intent(context, MainActivity::class.java)
        blockBase.setPackage(context.packageName)
        val contentIntent = PendingIntent.getActivity(
            context, 9500, blockBase,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(context.getString(R.string.focusguard_blocked_title, appName))
            .setContentText(reasonText)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    context.getString(R.string.focusguard_blocked_big, appName, reasonText, untilText)
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        tNotify(context, NOTIF_BLOCK, notification)
    }

    fun postDailySummary(context: Context, usageText: String, goalText: String, sleepText: String) {
        if (!com.tabslify.tabs.focusguard.data.FocusGuardConfig.notificationsEnabled) return
        ensureChannels(context)
        val summaryBase = Intent(context, MainActivity::class.java)
        summaryBase.setPackage(context.packageName)
        val contentIntent = PendingIntent.getActivity(
            context, 9501, summaryBase,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentTitle(context.getString(R.string.focusguard_daily_summary_title))
            .setContentText(usageText)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    context.getString(R.string.focusguard_daily_summary_body, usageText, goalText, sleepText)
                )
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        tNotify(context, NOTIF_DAILY_SUMMARY, notification)
    }

    fun postSleepWarning(context: Context, sleepText: String, targetText: String) {
        if (!com.tabslify.tabs.focusguard.data.FocusGuardConfig.notificationsEnabled) return
        ensureChannels(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(context.getString(R.string.focusguard_sleep_warning_title))
            .setContentText(context.getString(R.string.focusguard_sleep_warning_body, sleepText, targetText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        tNotify(context, NOTIF_SLEEP_WARNING, notification)
    }

    fun postStudyReminder(context: Context, remaining: Int) {
        if (!com.tabslify.tabs.focusguard.data.FocusGuardConfig.notificationsEnabled) return
        ensureChannels(context)
        val reminderBase = Intent(context, MainActivity::class.java)
        reminderBase.setPackage(context.packageName)
        val contentIntent = PendingIntent.getActivity(
            context, 9503, reminderBase,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle(context.getString(R.string.focusguard_study_reminder_title))
            .setContentText(context.resources.getQuantityString(R.plurals.focusguard_study_reminder_body, remaining, remaining))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        tNotify(context, NOTIF_STUDY_REMINDER, notification)
    }

    fun cancel(context: Context, id: Int) {
        context.getSystemService(NotificationManager::class.java)?.cancel(id)
    }
}
