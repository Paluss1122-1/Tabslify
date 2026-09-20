package com.tabslify.services

import android.app.PendingIntent
import android.app.NotificationManager
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
import com.tabslify.services.QuietHoursNotificationService.Companion.AI_NOTIFY_CHANNEL_ID
import com.tabslify.tabs.ainotify.AI_NOTIFY_CHANNEL
import com.tabslify.tabs.ainotify.AI_NOTIFY_KIND_CANCEL
import com.tabslify.tabs.ainotify.AI_NOTIFY_KIND_QUESTIONS
import com.tabslify.tabs.ainotify.AI_NOTIFY_TOPIC
import com.tabslify.tabs.ainotify.AiNotifyQuestion
import com.tabslify.tabs.ainotify.AiNotifySession
import com.tabslify.tabs.ainotify.AiNotifyStore
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class MyFirebaseMessagingService : FirebaseMessagingService() {
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        FirebaseMessaging.getInstance()
            .subscribeToTopic("all_users")
        if (prvt()) {
            FirebaseMessaging.getInstance()
                .subscribeToTopic(AI_NOTIFY_TOPIC)
            FirebaseMessaging.getInstance()
                .subscribeToTopic("emails")
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        println("Message received: $remoteMessage")
        if (remoteMessage.data["channel"] == AI_NOTIFY_CHANNEL && prvt()) {
            handleAiNotify(remoteMessage.data)
            return
        }
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

    private fun handleAiNotify(data: Map<String, String>) {
        val appContext = applicationContext
        val sessionId = data["session_id"]?.takeIf { it.isNotEmpty() }
            ?: System.currentTimeMillis().toString()
        if (data["kind"] == AI_NOTIFY_KIND_CANCEL) {
            AiNotifyStore.markCancelled(appContext, sessionId)
            AiNotifyStore.removeSession(appContext, sessionId)
            val manager = appContext.getSystemService(NotificationManager::class.java)
            manager?.cancel("ai_notify", sessionId.hashCode())
            return
        }
        if (AiNotifyStore.isCancelled(appContext, sessionId)) return
        val title = data["title"].orEmpty().ifEmpty { data["source"].orEmpty() }
        val body = data["body"].orEmpty()
        val source = data["source"].orEmpty()
        val intentBase = Intent(appContext, MainActivity::class.java)
        intentBase.action = Intent.ACTION_VIEW
        intentBase.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        intentBase.setPackage(appContext.packageName)
        var priority = NotificationCompat.PRIORITY_DEFAULT
        if (data["kind"] == AI_NOTIFY_KIND_QUESTIONS) {
            val questions = runCatching {
                Json.decodeFromString<List<AiNotifyQuestion>>(data["questions"].orEmpty())
            }.getOrNull().orEmpty()
            if (questions.isNotEmpty()) {
                AiNotifyStore.saveSession(
                    appContext,
                    AiNotifySession(sessionId, title, body, source, questions)
                )
                intentBase.putExtra("target", "ai_questions")
                intentBase.putExtra("ai_session_id", sessionId)
                priority = NotificationCompat.PRIORITY_HIGH
            }
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            sessionId.hashCode(),
            intentBase,
            PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(appContext, AI_NOTIFY_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(priority)
            .setGroup("SSN")
            .setSilent(false)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
        if (source.isNotEmpty()) builder.setSubText(source)
        tNotify(appContext, sessionId.hashCode(), builder.build(), "ai_notify")
    }
}
