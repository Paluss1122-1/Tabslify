package com.tabslify.services

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.DeadObjectException
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.tabslify.R
import com.tabslify.core.objects.Config.BLOCKED_MESSAGES
import com.tabslify.core.objects.Config.NOTIFICATION_PORT
import com.tabslify.core.objects.NotificationRepository
import com.tabslify.core.objects.prvt
import com.tabslify.core.objects.tNotify
import com.tabslify.quiethoursnotificationhelper.isLaptopConnected
import com.tabslify.quiethoursnotificationhelper.laptopIp
import com.tabslify.quiethoursnotificationhelper.onPcCallFailure
import com.tabslify.quiethoursnotificationhelper.onPcCallSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import kotlin.time.Duration.Companion.milliseconds

data class WhatsAppMessage(
    val id: Int = 0,
    val sender: String,
    val text: String,
    val timestamp: Long
)

private fun StatusBarNotification.isGroupSummary(): Boolean =
    (notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY) != 0

private fun StatusBarNotification.hasContent(): Boolean {
    val extras = notification.extras
    val title = extras.getCharSequence("android.title")?.toString().orEmpty()
    val text = extras.getCharSequence("android.text")?.toString().orEmpty()
    return title.isNotBlank() || text.isNotBlank()
}

class WhatsAppNotificationListener : NotificationListenerService() {

    @Volatile
    private var listenerConnected = false

    private val _messages = MutableStateFlow<List<WhatsAppMessage>>(emptyList())

    private fun insert(message: WhatsAppMessage) {
        val newMessages = _messages.value.toMutableList().apply {
            add(message)
            while (size > 100) removeAt(0)
        }
        _messages.value = newMessages
    }

    private fun getAll(): List<WhatsAppMessage> {
        return _messages.value
            .sortedByDescending { it.timestamp }
            .take(100)
    }

    private val serviceJob = SupervisorJob()
    private val forwardScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private var pendingForwardJob: Job? = null
    private var lastForwardTime = 0L

    companion object {
        private val WHATSAPP_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")
        private val TELEGRAM_PACKAGES =
            setOf("org.telegram.messenger", "org.telegram.messenger.web")

        private const val FORWARD_DEBOUNCE_MS = 500L
        private const val PREFS_BLOCKED = "blocked_notifications_prefs"
        private val BLOCKED_SENDERS = setOf("N", "Nico", "E")

        private var instance: WeakReference<WhatsAppNotificationListener>? = null

        val replyActions = java.util.concurrent.ConcurrentHashMap<String, ReplyData>()
        val messagesByContact =
            java.util.concurrent.ConcurrentHashMap<String, MutableList<ChatMessage>>()

        fun forwardNotificationsToLaptop1() {
            val svc = instance?.get() ?: return
            if (!svc.listenerConnected) return
            try {
                svc.activeNotifications?.let {
                    svc.forwardNotificationsToLaptop(it, svc.packageManager)
                }
            } catch (_: SecurityException) {
            }
        }

        fun cancelExternalNotification(notificationId: Int): Boolean {
            val svc = instance?.get() ?: return false
            if (!svc.listenerConnected) return false
            try {
                val activeNotifs = svc.activeNotifications ?: return false
                val targetSbn = activeNotifs.find { it.id == notificationId }
                if (targetSbn != null) {
                    svc.cancelNotification(targetSbn.key)
                    Log.d("MessageListener", "Successfully cancelled notification with ID: $notificationId (key: ${targetSbn.key})")
                    return true
                }
            } catch (e: Exception) {
                Log.w("MessageListener", "Failed to cancel notification with ID $notificationId: ${e.message}")
            }
            return false
        }

        fun cancelExternalNotificationByKey(key: String): Boolean {
            val svc = instance?.get() ?: return false
            if (!svc.listenerConnected) return false
            return try {
                val exists = svc.activeNotifications?.any { it.key == key } == true
                if (exists) {
                    svc.cancelNotification(key)
                    Log.d("MessageListener", "Successfully cancelled notification by key: $key")
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                Log.w("MessageListener", "Failed to cancel notification by key $key: ${e.message}")
                false
            }
        }

        fun keyFor(packageName: String, title: String): String = "$packageName|$title"

        fun isSupportedApp(packageName: String): Boolean {
            return WHATSAPP_PACKAGES.contains(packageName) || TELEGRAM_PACKAGES.contains(packageName)
        }

        data class ChatMessage(
            val text: String,
            val timestamp: Long = System.currentTimeMillis(),
            val isOwnMessage: Boolean,
            val imageUri: Uri? = null
        )

        data class ReplyData(
            val pendingIntent: android.app.PendingIntent,
            val remoteInput: RemoteInput,
            val originalResultKey: String,
            val timestamp: Long = System.currentTimeMillis()
        )
    }

    private fun forwardNotificationsToLaptop(
        notifications: Array<StatusBarNotification>,
        packageManager: PackageManager
    ) {
        if (!isLaptopConnected || !prvt()) return
        pendingForwardJob?.cancel()
        pendingForwardJob = forwardScope.launch {
            val now = System.currentTimeMillis()
            val timeSinceLast = now - lastForwardTime
            if (timeSinceLast < FORWARD_DEBOUNCE_MS) {
                delay((FORWARD_DEBOUNCE_MS - timeSinceLast).milliseconds)
            }
            lastForwardTime = System.currentTimeMillis()

            val jsonArray = org.json.JSONArray()
            val relevant = notifications
                .filter { it.packageName != "com.paluss1122.tabslify" && it.packageName != "com.paluss1122.tabslify.private" && it.packageName != "com.android.systemui" }

            val packagesWithChildren = relevant
                .filter { !it.isGroupSummary() && it.hasContent() }
                .map { it.packageName }
                .toSet()

            relevant
                .filter { !(it.isGroupSummary() && it.packageName in packagesWithChildren) }
                .sortedByDescending { it.postTime }
                .forEach { sbn ->
                    val extras = sbn.notification.extras
                    val title = extras.getCharSequence("android.title")?.toString() ?: ""
                    val text = extras.getCharSequence("android.text")?.toString() ?: ""
                    if (title.isBlank() && text.isBlank()) return@forEach

                    val appName = try {
                        packageManager.getApplicationLabel(
                            packageManager.getApplicationInfo(
                                sbn.packageName,
                                PackageManager.GET_META_DATA
                            )
                        ).toString()
                    } catch (_: Exception) {
                        sbn.packageName
                    }

                    jsonArray.put(org.json.JSONObject().apply {
                        put("app", appName)
                        put("title", title)
                        put("text", text)
                        put("time", sbn.postTime)
                        put("id", sbn.id)
                        put("package", sbn.packageName)
                        put("category", sbn.notification.category ?: "")
                        put("ongoing", (sbn.notification.flags and android.app.Notification.FLAG_ONGOING_EVENT) != 0)
                        put("actions", org.json.JSONArray(sbn.notification.actions?.map { it.title.toString() } ?: emptyList<String>()))
                        put("key", sbn.key)
                    })
                }

            val targetIp = laptopIp.ifEmpty { null } ?: return@launch
            try {
                java.net.Socket().use { socket ->
                    socket.connect(java.net.InetSocketAddress(targetIp, NOTIFICATION_PORT), 3000)
                    socket.getOutputStream().bufferedWriter().use { writer ->
                        writer.write(jsonArray.toString())
                        writer.newLine()
                        writer.flush()
                    }
                }
                onPcCallSuccess()
            } catch (e: Exception) {
                Log.w("NotifForwarder", "❌ $targetIp: ${e.message}")
                onPcCallFailure(this@WhatsAppNotificationListener)
            }
        }
    }

    override fun onDestroy() {
        serviceJob.cancel()
        listenerConnected = false
        if (instance?.get() === this) {
            instance?.clear()
            instance = null
        }
        pendingForwardJob?.cancel()
        pendingForwardJob = null
        replyActions.clear()
        messagesByContact.clear()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!prvt() || !isWhForwardingEnabled()) return
        try {
            super.onNotificationPosted(sbn)

            if (sbn.packageName != "com.example.tabslify" && sbn.packageName != "com.android.systemui") {
                if (listenerConnected) {
                    try {
                        activeNotifications?.let {
                            forwardNotificationsToLaptop(
                                it,
                                packageManager
                            )
                        }
                    } catch (se: SecurityException) {
                        Log.w(
                            "MessageListener",
                            "getActiveNotifications not allowed yet: ${se.message}"
                        )
                    }
                }
            }

            if (!isSupportedApp(sbn.packageName)) return

            val notification = sbn.notification
            val extras = notification.extras
            val title = extras.getString(android.app.Notification.EXTRA_TITLE) ?: ""
            val text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString() ?: ""

            if (handleBlockedSender(sbn, title, text)) return

            if (title.contains("Backup", ignoreCase = true) ||
                title.contains("Du", ignoreCase = true) ||
                title.contains("WhatsApp", ignoreCase = true) ||
                title.contains("Telegram", ignoreCase = true)
            ) return

            val key = keyFor(sbn.packageName, title)
            val existingMessages = messagesByContact[key] ?: mutableListOf()
            val lastMessage = existingMessages.lastOrNull()

            if (lastMessage != null &&
                !lastMessage.isOwnMessage &&
                lastMessage.text == text &&
                (System.currentTimeMillis() - lastMessage.timestamp) < 5000
            ) {
                Log.d("MessageListener", "Duplicate message detected from $title, ignoring")
                return
            }

            Log.d("MessageListener", "Received message from $title")

            val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000
            replyActions.entries.removeAll { it.value.timestamp < cutoff }
            if (replyActions.size > 50) {
                val oldestKeys = replyActions.entries
                    .sortedBy { it.value.timestamp }
                    .take(replyActions.size - 50)
                    .map { it.key }
                oldestKeys.forEach { replyActions.remove(it) }
            }

            if (messagesByContact.size > 200) {
                val oldestKeys = messagesByContact.entries
                    .sortedBy { it.value.lastOrNull()?.timestamp ?: 0L }
                    .take(messagesByContact.size - 200)
                    .map { it.key }
                oldestKeys.forEach { messagesByContact.remove(it) }
            }

            notification.actions?.forEach { action ->
                action.remoteInputs?.firstOrNull()?.let { systemRemoteInput ->
                    val remoteInput = RemoteInput.Builder(systemRemoteInput.resultKey)
                        .setLabel(systemRemoteInput.label)
                        .setChoices(systemRemoteInput.choices)
                        .setAllowFreeFormInput(systemRemoteInput.allowFreeFormInput)
                        .build()

                    replyActions[key] = ReplyData(
                        pendingIntent = action.actionIntent,
                        remoteInput = remoteInput,
                        originalResultKey = systemRemoteInput.resultKey,
                        timestamp = System.currentTimeMillis()
                    )
                    Log.d(
                        "MessageListener",
                        "Saved reply action for $title with key: ${systemRemoteInput.resultKey}"
                    )
                }
            }

            val messageList = messagesByContact.getOrPut(key) { mutableListOf() }
            messageList.add(ChatMessage(text, isOwnMessage = false))
            while (messageList.size > 50) {
                messageList.removeAt(0)
            }

            forwardScope.launch {
                val exists = getAll().any { it.sender == title && it.text == text }
                if (!exists && !title.contains("Du")) {
                    insert(
                        WhatsAppMessage(
                            sender = title,
                            text = text,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                    val broadcastIntent = Intent("WHATSAPP_MESSAGE_RECEIVED").apply {
                        setPackage(applicationContext.packageName)
                    }
                    sendBroadcast(broadcastIntent)
                }
            }

            val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            val prefs = getSharedPreferences("quiet_hours_prefs", MODE_PRIVATE)
            val quietEnd = prefs.getString("quiet_hours_start", null)?.toIntOrNull() ?: 7
            val quietStart = prefs.getString("quiet_hours_end", null)?.toIntOrNull() ?: 21

            if (hour !in quietEnd..<quietStart) return

            QuietHoursNotificationService.updateSingleSenderNotification(this, title)
        } catch (_: DeadObjectException) {
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (!prvt() || !isWhForwardingEnabled()) return
        try {
            super.onNotificationRemoved(sbn)

            if (sbn.packageName != "com.example.tabslify" &&
                sbn.packageName != "com.android.systemui" &&
                sbn.packageName != "com.google.android.gms.supervision" &&
                sbn.packageName != "com.spotify.music"
            ) {
                if (listenerConnected) {
                    try {
                        activeNotifications?.let {
                            forwardNotificationsToLaptop(
                                it,
                                packageManager
                            )
                        }
                    } catch (se: SecurityException) {
                        Log.w(
                            "MessageListener",
                            "getActiveNotifications not allowed yet: ${se.message}"
                        )
                    }
                }
            }

            if (isSupportedApp(sbn.packageName)) {
                val title = sbn.notification.extras.getString(android.app.Notification.EXTRA_TITLE)
                title?.let {
                    val key = keyFor(sbn.packageName, it)
                    replyActions.remove(key)
                    messagesByContact.remove(key)
                }
            }

            NotificationRepository.removeNotification(sbn)
        } catch (_: DeadObjectException) {
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        listenerConnected = true
        instance = WeakReference(this)
        scheduleBlockedNotificationsAlarm()
        com.tabslify.tabs.mediaplayer.SpotifyPlaybackTracker.start(applicationContext)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        listenerConnected = false
        instance = null
        replyActions.clear()
        messagesByContact.clear()
        NotificationRepository.clear()
        com.tabslify.tabs.mediaplayer.SpotifyPlaybackTracker.stop()
    }

    private fun isWhForwardingEnabled(): Boolean {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        return prefs.getBoolean("services_master", true) && prefs.getBoolean("service_wh", false)
    }

    private fun handleBlockedSender(
        sbn: StatusBarNotification,
        title: String,
        text: String
    ): Boolean {
        val normalizedTitle = title.trim()
        if (BLOCKED_SENDERS.none { it.equals(normalizedTitle, ignoreCase = true) }) return false

        val cal = java.util.Calendar.getInstance()
        val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
        val dow = cal.get(java.util.Calendar.DAY_OF_WEEK)

        val isWeekday = dow in java.util.Calendar.MONDAY..java.util.Calendar.FRIDAY
        val isBlockHours = hour in 7..13
        if (!isWeekday || !isBlockHours) return false

        val prefs = getSharedPreferences(PREFS_BLOCKED, MODE_PRIVATE)
        val existingKeys = prefs.getStringSet("all_blocked_keys", mutableSetOf()) ?: mutableSetOf()
        if (existingKeys.size >= 50) return true

        val timestamp = System.currentTimeMillis()
        val entryKey = "blocked_${timestamp}_${title.replace(" ", "_")}"

        prefs.edit().apply {
            putString("${entryKey}_sender", title)
            putString("${entryKey}_text", text)
            putLong("${entryKey}_timestamp", timestamp)
            putString("${entryKey}_package", sbn.packageName)
            putStringSet("all_blocked_keys", existingKeys.toMutableSet().also { it.add(entryKey) })
            apply()
        }

        try {
            cancelNotification(sbn.key)
            Log.d("BlockedSender", "Cancelled notification for $title (key=${sbn.key})")
        } catch (e: Exception) {
            Log.w("BlockedSender", "Could not cancel notification: ${e.message}")
        }

        return true
    }

    private fun scheduleBlockedNotificationsAlarm() {
        val alarmManager = getSystemService(ALARM_SERVICE) as android.app.AlarmManager
        if (!alarmManager.canScheduleExactAlarms()) return

        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 14)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(java.util.Calendar.DAY_OF_YEAR, 1)
            }
        }

        val alarmBase = Intent(this, BlockedNotificationReceiver::class.java)
        alarmBase.setPackage(packageName)
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            this, 0, alarmBase,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(
                android.app.AlarmManager.RTC_WAKEUP,
                cal.timeInMillis,
                pendingIntent
            )
        }
        Log.d("BlockedSender", "Alarm gesetzt für 14:00 Uhr (${cal.time})")
    }
}

class BlockedNotificationReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: android.content.Context, intent: Intent) {
        val prefs = context.getSharedPreferences(
            "blocked_notifications_prefs",
            android.content.Context.MODE_PRIVATE
        )
        val keys = prefs.getStringSet("all_blocked_keys", emptySet()) ?: emptySet()
        if (keys.isEmpty()) return

        val notificationManager =
            context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

        val channel = android.app.NotificationChannel(
            "blocked_summary_channel",
            context.getString(R.string.zuruckgehaltene_nachrichten),
            android.app.NotificationManager.IMPORTANCE_HIGH
        )
        notificationManager.createNotificationChannel(channel)

        keys.forEachIndexed { index, key ->
            val sender = prefs.getString("${key}_sender", null) ?: return@forEachIndexed
            val text = prefs.getString("${key}_text", null) ?: return@forEachIndexed
            val timestamp = prefs.getLong("${key}_timestamp", 0L)

            val timeStr = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(timestamp))

            val notification = NotificationCompat.Builder(context, "blocked_summary_channel")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(sender)
                .setContentText(text)
                .setSubText(context.getString(R.string.erhalten_um_zuruckgehalten, timeStr))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()

            tNotify(context, BLOCKED_MESSAGES + index, notification)
        }

        prefs.edit().apply {
            keys.forEach { key ->
                remove("${key}_sender")
                remove("${key}_text")
                remove("${key}_timestamp")
                remove("${key}_package")
            }
            remove("all_blocked_keys")
            apply()
        }

        rescheduleAlarm(context)
    }

    private fun rescheduleAlarm(context: android.content.Context) {
        val cal = java.util.Calendar.getInstance().apply {
            add(java.util.Calendar.DAY_OF_YEAR, 1)
            set(java.util.Calendar.HOUR_OF_DAY, 14)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }

        val rescheduleBase = Intent(context, BlockedNotificationReceiver::class.java)
        rescheduleBase.setPackage(context.packageName)
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            context, 0, rescheduleBase,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager =
            context.getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
        if (!alarmManager.canScheduleExactAlarms()) return
        alarmManager.setExactAndAllowWhileIdle(
            android.app.AlarmManager.RTC_WAKEUP,
            cal.timeInMillis,
            pendingIntent
        )
    }
}