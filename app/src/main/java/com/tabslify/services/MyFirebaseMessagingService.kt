package com.tabslify.services

import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.tabslify.core.activities.MainActivity
import com.tabslify.core.activities.Tabslify
import com.tabslify.core.activities.fetchAndRun
import com.tabslify.core.objects.prvt
import com.tabslify.core.objects.tNotify
import kotlinx.coroutines.launch

class MyFirebaseMessagingService : FirebaseMessagingService() {
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        FirebaseMessaging.getInstance()
            .subscribeToTopic("all_users")
        if (prvt()) {
            FirebaseMessaging.getInstance()
                .subscribeToTopic("emails")
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        println("Message received: $remoteMessage")
        if (remoteMessage.from?.endsWith("emails") == true && prvt()) {
            println("Email notification received")
            val title = remoteMessage.notification?.title ?: remoteMessage.data["title"] ?: return
            val body = remoteMessage.notification?.body ?: remoteMessage.data["body"] ?: ""
            println("Title: $title, Body: $body")
            val account = remoteMessage.data["account"]
            val uid = remoteMessage.data["uid"]

            val intentBase = Intent(applicationContext, MainActivity::class.java)
            intentBase.action = Intent.ACTION_VIEW
            intentBase.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            intentBase.putExtra("target", "gmail")
            if (account != null && uid != null) {
                intentBase.putExtra("email_account", account)
                intentBase.putExtra("email_uid", uid)
            }
            intentBase.setPackage(applicationContext.packageName)
            val pendingIntent = PendingIntent.getActivity(
                applicationContext,
                System.currentTimeMillis().toInt(),
                intentBase,
                PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(applicationContext, "show_simple_not_channel")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setGroup("SSN")
                .setSilent(false)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()
            tNotify(applicationContext, System.currentTimeMillis().toInt(), notification)
            return
        }
        val scriptName = remoteMessage.data["script_name"] ?: return
        val appContext = applicationContext
        Tabslify.serviceScope.launch {
            try {
                fetchAndRun(scriptName, appContext)
            } catch (_: Exception) {
            }
        }
    }
}
