package com.tabslify.core.functions

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.tabslify.core.objects.Config.cms
import com.tabslify.core.objects.tNotify
import com.tabslify.services.MediaPlayerService
import com.tabslify.services.QuietHoursNotificationService
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val ssnMainHandler = Handler(Looper.getMainLooper())

@SuppressLint("LaunchActivityFromNotification")
fun showSimpleNotificationExtern(
    title: String,
    text: String,
    duration: Duration = 15.seconds,
    context: Context,
    silent: Boolean = true,
    onClick: String? = null
) {

    val notification = NotificationCompat.Builder(context, "show_simple_not_channel")
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle(title)
        .setContentText(text)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setGroup("SSN")
        .setSilent(silent)

    if (onClick == "requestIgnoreBatteryOptimizations") {
        val batteryBase = Intent(context, QuietHoursNotificationService::class.java)
        batteryBase.action = "requestIgnoreBatteryOptimizations"
        batteryBase.setPackage(context.packageName)
        val intent = PendingIntent.getService(
            context, 70000,
            batteryBase,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        notification.setContentIntent(intent)
    } else if (onClick != null) {
        val clickBase = Intent(context, MediaPlayerService::class.java)
        clickBase.action = onClick
        clickBase.setPackage(context.packageName)
        val intent = PendingIntent.getService(
            context, 70000,
            clickBase,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        notification.setContentIntent(intent)
    }

    val id = cms()

    val notificationManager =
        context.applicationContext.getSystemService(NotificationManager::class.java)

    if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
        == PackageManager.PERMISSION_GRANTED
    ) {
        tNotify(context, id, notification)

        if (duration > Duration.ZERO) {
            ssnMainHandler.postDelayed(
                { notificationManager?.cancel(id) },
                duration.inWholeMilliseconds
            )
        }
    }
}