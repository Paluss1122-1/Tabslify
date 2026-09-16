package com.tabslify.quiethoursnotificationhelper

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PendingIntent.getBroadcast
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Bundle
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.tabslify.core.activities.Tabslify.Companion.appScope
import com.tabslify.core.functions.errorInsert
import com.tabslify.core.functions.showSimpleNotificationExtern
import com.tabslify.core.objects.Config.cms
import com.tabslify.core.objects.tNotify
import com.tabslify.services.QuietHoursNotificationService
import com.tabslify.services.QuietHoursNotificationService.Companion.ACTION_MARK_PARTS_READ
import com.tabslify.services.QuietHoursNotificationService.Companion.ACTION_MESSAGE_SENT
import com.tabslify.services.QuietHoursNotificationService.Companion.EXTRA_MESSAGE_ID
import com.tabslify.services.QuietHoursNotificationService.Companion.EXTRA_SENDER
import com.tabslify.services.QuietHoursNotificationService.Companion.MAX_MESSAGES_PER_CONTACT
import com.tabslify.services.QuietHoursNotificationService.Companion.isSupportedMessenger
import com.tabslify.services.QuietHoursNotificationService.Companion.readMessageIds
import com.tabslify.services.WhatsAppNotificationListener
import com.tabslify.tabs.aitab.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

private const val GEMINI_CHAT_PREFIX = "Gemini Chat"

private fun resolveKey(sender: String): String? {
    if (sender.contains("|")) return sender
    if (sender.startsWith(GEMINI_CHAT_PREFIX)) return sender
    return WhatsAppNotificationListener.messagesByContact.keys
        .firstOrNull { it.endsWith("|$sender") }
        ?: WhatsAppNotificationListener.replyActions.keys
            .firstOrNull { it.endsWith("|$sender") }
}

private fun buildReplyAction(
    key: String,
    notificationId: Int,
    context: Context
): NotificationCompat.Action {
    val replyBase = Intent(ACTION_MESSAGE_SENT)
    replyBase.putExtra(EXTRA_SENDER, key)
    replyBase.setPackage(context.packageName)
    val pi = getBroadcast(
        context, notificationId,
        replyBase,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
    )
    return NotificationCompat.Action.Builder(
        android.R.drawable.ic_menu_send, "Antworten", pi
    )
        .addRemoteInput(RemoteInput.Builder("key_text_reply").setLabel("Antwort").build())
        .setShowsUserInterface(false)
        .setAllowGeneratedReplies(false)
        .build()
}

private fun postChatNotification(key: String, context: Context, sourceLabel: String) {
    try {
        val displayName = if (key.contains("|")) key.substringAfter("|") else key
        val notifId = key.hashCode() and 0x0FFFFFFF
        val summaryId = notifId + 1000000
        val messagingId = notifId + 2000000
        val nm = context.getSystemService(NotificationManager::class.java)

        val mePerson = Person.Builder().setName("Du").setKey("me").build()
        val senderPerson = Person.Builder().setName(displayName).setKey(displayName).build()
        val messages = WhatsAppNotificationListener.messagesByContact[key] ?: emptyList()

        val style = NotificationCompat.MessagingStyle(mePerson)
            .setConversationTitle("$sourceLabel · $displayName")

        var partIndex = 0

        messages.takeLast(5).forEach { msg ->
            val msgId = "${key}_${msg.timestamp}"
            if (readMessageIds.contains(msgId)) return@forEach

            val timeText =
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestamp))
            val text = msg.text

            if (text.length > 200) {
                val parts = text.chunked(200)
                parts.reversed().forEachIndexed { idx, part ->
                    val partId = notifId + partIndex
                    partIndex++
                    val markBase = Intent(context, QuietHoursNotificationService::class.java)
                    markBase.action = ACTION_MARK_PARTS_READ
                    markBase.putExtra(EXTRA_MESSAGE_ID, msgId)
                    markBase.setPackage(context.packageName)
                    val markPi = PendingIntent.getService(
                        context, partId + 500000,
                        markBase,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    val partNum = parts.size - idx
                    if (ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        )
                        == PackageManager.PERMISSION_GRANTED
                    ) {
                        nm.notify(
                            partId, NotificationCompat.Builder(context, "Nachrichten")
                                .setSmallIcon(android.R.drawable.stat_notify_chat)
                                .setContentTitle("$sourceLabel · $displayName (Teil $partNum/${parts.size})")
                                .setContentText(part.take(100) + if (part.length > 100) "..." else "")
                                .setStyle(
                                    NotificationCompat.BigTextStyle()
                                        .bigText(part)
                                        .setBigContentTitle("$sourceLabel · $displayName (Teil $partNum/${parts.size})")
                                        .setSummaryText("⏰ $timeText")
                                )
                                .setPriority(NotificationCompat.PRIORITY_HIGH)
                                .setGroup("long_$key")
                                .setAutoCancel(false)
                                .addAction(
                                    android.R.drawable.ic_menu_close_clear_cancel,
                                    "Als gelesen markieren",
                                    markPi
                                )
                                .build()
                        )
                    }
                }
            } else {
                style.addMessage(
                    NotificationCompat.MessagingStyle.Message(
                        "$text • $timeText",
                        msg.timestamp,
                        if (msg.isOwnMessage) mePerson else senderPerson
                    )
                )
            }
        }

        if (partIndex > 0) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED
            ) {
                nm.notify(
                    summaryId, NotificationCompat.Builder(context, "Nachrichten")
                        .setSmallIcon(android.R.drawable.stat_notify_chat)
                        .setContentTitle("$sourceLabel · Lange Nachricht von $displayName")
                        .setContentText("$partIndex Teile")
                        .setPriority(NotificationCompat.PRIORITY_LOW)
                        .setGroup("long_$key")
                        .setGroupSummary(true)
                        .setAutoCancel(true)
                        .build()
                )
            }
        }

        val notification = NotificationCompat.Builder(context, "Nachrichten")
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setStyle(style)
            .setAutoCancel(false)
            .setOngoing(false)
            .setOnlyAlertOnce(true)
            .addAction(buildReplyAction(key, messagingId, context))
            .build()

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            nm.notify(messagingId, notification)
        }
    } catch (e: Exception) {
        Log.e("Messages", "postChatNotification failed: ${e.message}")
    }
}

fun updateSingleSenderNotification(sender: String, context: Context) {
    val key = resolveKey(sender) ?: run {
        Log.w("Messages", "No key for sender: $sender")
        return
    }
    if (WhatsAppNotificationListener.messagesByContact[key] == null) {
        Log.w("Messages", "No messages for key: $key")
        return
    }
    postChatNotification(key, context, "📨 Neu")
}

fun updateChatNotification(key: String, context: Context) {
    postChatNotification(key, context, "💬 Chat")
}

fun showUnreadMessages(context: Context) {
    val msgs = WhatsAppNotificationListener.messagesByContact
    if (msgs.isEmpty()) {
        showSimpleNotificationExtern(
            "Keine unbeantworteten Nachrichten",
            "Alle Nachrichten wurden beantwortet oder keine Daten verfügbar.",
            context = context,
            silent = false
        )
        return
    }
    msgs.keys.forEach { key -> postChatNotification(key, context, "📋 Ungelesen") }
}

fun handleMessageSent(sender: String, messageText: String, context: Context) {
    val key = resolveKey(sender) ?: sender
    val displayName = if (key.contains("|")) key.substringAfter("|") else key

    try {
        if (displayName == GEMINI_CHAT_PREFIX || displayName.startsWith("$GEMINI_CHAT_PREFIX:")) {
            val list =
                WhatsAppNotificationListener.messagesByContact.getOrPut(key) { mutableListOf() }
            val trimmed = messageText.trim()
            if (trimmed.isNotEmpty()) {
                list.add(
                    WhatsAppNotificationListener.Companion.ChatMessage(
                        trimmed,
                        System.currentTimeMillis(),
                        true
                    )
                )
            }
            if (list.size > MAX_MESSAGES_PER_CONTACT)
                list.subList(0, list.size - MAX_MESSAGES_PER_CONTACT).clear()
            updateChatNotification(key, context)

            val appContext = context.applicationContext
            appScope.launch {
                val snapshot = mutableListOf<ChatMessage>()
                list.dropLast(1).forEach { obj ->
                    snapshot.add(
                        ChatMessage(
                            text = obj.text,
                            ts = obj.timestamp,
                            own = obj.isOwnMessage,
                            mode = "AI"
                        )
                    )
                }
                val answer = sendAiRequest(appContext, userMessage = trimmed, history = snapshot, target = "notif", serviceKey = "chat")
                if (!answer.isNullOrBlank()) {
                    list.add(
                        WhatsAppNotificationListener.Companion.ChatMessage(
                            answer,
                            System.currentTimeMillis(),
                            false
                        )
                    )
                    if (list.size > MAX_MESSAGES_PER_CONTACT)
                        list.subList(0, list.size - MAX_MESSAGES_PER_CONTACT).clear()
                    withContext(Dispatchers.Main) { updateChatNotification(key, appContext) }
                } else {
                    withContext(Dispatchers.Main) {
                        showSimpleNotificationExtern(
                            "❌ Chat",
                            "Antwort konnte nicht geladen werden.",
                            context = appContext
                        )
                    }
                }
            }
            return
        }

        val replyData = WhatsAppNotificationListener.replyActions[key]

        if (replyData == null) {
            Log.e(
                "Messages",
                "replyData ist null für key='$key'. replyActions-Keys: ${WhatsAppNotificationListener.replyActions.keys}"
            )
            showSimpleNotificationExtern(
                "⚠️ Kein replyData",
                "Kein Eintrag in replyActions für: $key",
                context = context,
                silent = false
            )
            return
        }

        val creatorPackage = replyData.pendingIntent.creatorPackage
        if (creatorPackage == null) {
            Log.e("Messages", "creatorPackage ist null für key='$key'")
            showSimpleNotificationExtern(
                "⚠️ Kein Package",
                "PendingIntent hat kein creatorPackage",
                context = context,
                silent = false
            )
            return
        }

        val supported =
            replyData.pendingIntent.creatorPackage?.let { isSupportedMessenger(it) } ?: false
        Log.d("Messages", "creatorPackage='$creatorPackage', supported=$supported")

        if (!supported) {
            Log.w("Messages", "Unsupported messenger: ${replyData.pendingIntent.creatorPackage}")
            showSimpleNotificationExtern(
                "⚠️ Nicht unterstützt",
                "Messenger wird nicht unterstützt: ${replyData.pendingIntent.creatorPackage}",
                20.seconds, context,
                silent = false
            )
            return
        }

        try {
            val intent = Intent().apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            val bundle =
                Bundle().apply { putCharSequence(replyData.originalResultKey, messageText) }
            val ri = RemoteInput.Builder(replyData.originalResultKey)
                .setLabel(replyData.remoteInput.label)
                .setChoices(replyData.remoteInput.choices)
                .setAllowFreeFormInput(replyData.remoteInput.allowFreeFormInput)
                .build()
            RemoteInput.addResultsToIntent(arrayOf(ri), intent, bundle)
            replyData.pendingIntent.send(context, 0, intent)
        } catch (e: Exception) {
            Log.e("Messages", "Send failed: ${e.message}")
            showSimpleNotificationExtern(
                "Fehler", "Nachricht konnte nicht gesendet werden", context = context,
                silent = false
            )
            return
        }

        val list = WhatsAppNotificationListener.messagesByContact.getOrPut(key) { mutableListOf() }
        val isDup = list.takeLast(3).any { it.text == messageText && messageText.length > 5 }
        if (!isDup) {
            list.add(
                WhatsAppNotificationListener.Companion.ChatMessage(
                    messageText,
                    System.currentTimeMillis(),
                    true
                )
            )
        }

        updateChatNotification(key, context)

        if (list.size > MAX_MESSAGES_PER_CONTACT)
            list.subList(0, list.size - MAX_MESSAGES_PER_CONTACT).clear()

    } catch (e: Exception) {
        Log.e("Messages", "handleMessageSent error: ${e.message}")
    }
}

fun markMessageAsRead(messageId: String, readMessageIds: MutableSet<String>, context: Context) {
    try {
        if (readMessageIds.size > 200) {
            val toRemove = readMessageIds.take(100)
            readMessageIds.removeAll(toRemove.toSet())
        }
        readMessageIds.add(messageId)
        val key = messageId.substringBeforeLast("_")
        val notifId = key.hashCode() and 0x0FFFFFFF
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        for (i in 0 until 100) nm.cancel(notifId + i)
        nm.cancel(notifId + 1000000)
    } catch (e: Exception) {
        errorInsert("markMessageAsRead", "ERROR: ${e.message}", Instant.now().toString(), "ERROR")
    }
}

fun createGeminiChat(name: String?, context: Context, replace: Boolean) {
    if (replace) WhatsAppNotificationListener.messagesByContact.remove(GEMINI_CHAT_PREFIX)
    val title = if (name.isNullOrBlank()) GEMINI_CHAT_PREFIX else "$GEMINI_CHAT_PREFIX: $name"
    val list = WhatsAppNotificationListener.messagesByContact.getOrPut(title) { mutableListOf() }
    if (list.isEmpty()) {
        list.add(
            WhatsAppNotificationListener.Companion.ChatMessage(
                "Neuer Gemini-Chat \"$title\" erstellt. Antworte auf diese Nachricht, um zu schreiben.",
                System.currentTimeMillis(), false
            )
        )
    }
    updateChatNotification(title, context)
}

fun extractLastMessage(context: Context) {
    val msgs = WhatsAppNotificationListener.messagesByContact
    if (msgs.isEmpty()) {
        showSimpleNotificationExtern(
            "❌ Keine Nachrichten",
            "Keine Nachrichten verfügbar",
            context = context
        )
        return
    }
    var newestMsg: WhatsAppNotificationListener.Companion.ChatMessage? = null
    var newestKey = ""
    msgs.forEach { (key, list) ->
        val last = list.lastOrNull()
        if (last != null && (newestMsg == null || last.timestamp > newestMsg.timestamp)) {
            newestMsg = last; newestKey = key
        }
    }
    val msg = newestMsg ?: return
    val displayName = if (newestKey.contains("|")) newestKey.substringAfter("|") else newestKey
    val timeText = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(msg.timestamp))
    val builder = NotificationCompat.Builder(context, "Nachrichten")
        .setSmallIcon(android.R.drawable.ic_menu_info_details)
        .setContentTitle("📋 Extrahierte Nachricht")
        .setContentText("Von: $displayName um $timeText")
        .setStyle(
            NotificationCompat.BigTextStyle()
                .bigText("Von: $displayName\nZeit: $timeText\n\n${msg.text}")
        )
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
    if (msg.imageUri != null) {
        try {
            val source = ImageDecoder.createSource(context.contentResolver, msg.imageUri)
            val bmp = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val targetSize = 1024
                if (info.size.width > targetSize || info.size.height > targetSize) {
                    val scale =
                        (info.size.width.toFloat() / targetSize).coerceAtLeast(info.size.height.toFloat() / targetSize)
                    decoder.setTargetSize(
                        (info.size.width / scale).toInt(),
                        (info.size.height / scale).toInt()
                    )
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            builder.setStyle(
                NotificationCompat.BigPictureStyle().bigPicture(bmp).bigLargeIcon(null as Bitmap?)
            )
            builder.setLargeIcon(bmp)
        } catch (e: Exception) {
            Log.e("Messages", "extractLastMessage image error: ${e.message}")
        }
    }
    tNotify(context, cms(), builder.build())
}