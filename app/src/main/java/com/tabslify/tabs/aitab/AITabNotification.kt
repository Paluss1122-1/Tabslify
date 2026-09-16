package com.tabslify.tabs.aitab

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.tabslify.R
import com.tabslify.core.activities.MainActivity
import com.tabslify.core.functions.canNotify
import com.tabslify.core.objects.tNotify

fun isAppInForeground(): Boolean {
    return ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
}

fun sendAITabBackgroundNotification(
    context: Context,
    title: String = context.getString(R.string.ai_tab_antwort),
    message: String
) {
    if (isAppInForeground()) return

    if (!canNotify(context)) return

    val intentBase = Intent(context, MainActivity::class.java)
    intentBase.action = Intent.ACTION_VIEW
    intentBase.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    intentBase.putExtra("target", "aitab")
    intentBase.setPackage(context.packageName)

    val pendingIntent = PendingIntent.getActivity(
        context,
        System.currentTimeMillis().toInt(),
        intentBase,
        PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(context, "ai_tab_notification_channel")
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle(title)
        .setContentText(message)
        .setStyle(NotificationCompat.BigTextStyle().bigText(message))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .build()

    tNotify(
        context,
        System.currentTimeMillis().toInt(),
        notification,
        "ai_response_notification"
    )
}
