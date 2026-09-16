package com.tabslify.inactive

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.core.content.edit
import com.tabslify.core.objects.Config
import com.tabslify.core.objects.tNotify
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

class ChatService : Service() {

    companion object {
        const val CHANNEL_ID = "chat_service_channel"
        private const val ACTION_REPLY = "com.tabslify.ACTION_CHAT_REPLY"
        private const val ACTION_SHOW_HISTORY = "com.tabslify.ACTION_SHOW_HISTORY"
        private const val KEY_REPLY_TEXT = "key_reply_text"
        private const val KEY_NOTIFICATION_ID = "key_notification_id"

        private const val PREFS_NAME = "ChatServicePrefs"
        private const val KEY_SEEN_MESSAGES = "seen_message_ids"
        private const val MAX_SEEN_MESSAGES = 500

        private const val REALTIME_SETUP_TIMEOUT_MS = 5000L

        fun startService(context: Context) {
            val intent = Intent(context, ChatService::class.java)
            context.startForegroundService(intent)
        }
    }

    @Suppress("PropertyName")
    @Serializable
    data class Message(
        val id: String? = null,
        val sender_id: String,
        val receiver_id: String,
        val content: String,
        val created_at: String? = null
    )

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val supabase = Config.client
    private val myUserId = "you"
    private val friendUserId = "friend"

    private lateinit var sharedPreferences: SharedPreferences
    private val seenMessageIds = LinkedHashSet<String>()
    private val messageHistory = mutableListOf<Message>()

    private val replyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_REPLY -> {
                    val replyText = RemoteInput.getResultsFromIntent(intent)
                        ?.getCharSequence(KEY_REPLY_TEXT)?.toString()
                    val notificationId = intent.getIntExtra(KEY_NOTIFICATION_ID, -1)

                    if (replyText != null && context != null) {
                        serviceScope.launch {
                            sendMessage(replyText)
                            withContext(Dispatchers.Main) {
                                if (notificationId != Config.CHAT_SERVICE && notificationId != -1) {
                                    val notificationManager =
                                        context.getSystemService(NotificationManager::class.java)
                                    notificationManager.cancel(notificationId)
                                    Log.d(
                                        "ChatService",
                                        "🗑️ Notification $notificationId removed after reply"
                                    )
                                }
                                updateServiceNotification()
                            }
                        }
                    }
                }

                ACTION_SHOW_HISTORY -> {
                    Log.d("ChatService", "History angefordert: ${messageHistory.size} Nachrichten")
                    serviceScope.launch {
                        withContext(Dispatchers.Main) {
                            showHistoryNotification()
                        }
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        loadSeenMessageIds()

        val filter = IntentFilter().apply {
            addAction(ACTION_REPLY)
            addAction(ACTION_SHOW_HISTORY)
        }
        registerReceiver(replyReceiver, filter, RECEIVER_NOT_EXPORTED)

        startForeground(
            Config.CHAT_SERVICE,
            createServiceNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )

        serviceScope.launch {
            setupRealtimeListener()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "ACTION_NOTIFICATION_DELETED" -> {
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        val notification = createServiceNotification()
                        tNotify(this, Config.CHAT_SERVICE, notification)
                    } catch (_: Exception) {
                    }
                }, 100)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null


    private fun loadSeenMessageIds() {
        val json = sharedPreferences.getString(KEY_SEEN_MESSAGES, "[]")
        try {
            val ids = Json.decodeFromString<List<String>>(json ?: "[]")
            seenMessageIds.addAll(ids)
            trimSeenMessageIds()
            Log.d("ChatService", "✅ Loaded ${seenMessageIds.size} seen messages")
        } catch (_: Exception) {
        }
    }

    private fun trimSeenMessageIds() {
        while (seenMessageIds.size > MAX_SEEN_MESSAGES) {
            val oldest = seenMessageIds.firstOrNull() ?: break
            seenMessageIds.remove(oldest)
        }
    }

    private fun saveSeenMessageIds() {
        trimSeenMessageIds()
        try {
            val json = Json.encodeToString(seenMessageIds.toList())
            sharedPreferences.edit { putString(KEY_SEEN_MESSAGES, json) }
        } catch (_: Exception) {
        }
    }

    fun createServiceNotification(): Notification {
        val replyRemoteInput = RemoteInput.Builder(KEY_REPLY_TEXT)
            .setLabel("Nachricht schreiben...")
            .build()

        val replyBase = Intent(ACTION_REPLY)
        replyBase.putExtra(KEY_NOTIFICATION_ID, Config.CHAT_SERVICE)
        replyBase.setPackage(packageName)

        val replyPendingIntent = PendingIntent.getBroadcast(
            this,
            Config.CHAT_SERVICE,
            replyBase,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send,
            "Schreiben",
            replyPendingIntent
        )
            .addRemoteInput(replyRemoteInput)
            .setShowsUserInterface(false)
            .build()

        val historyBase = Intent(ACTION_SHOW_HISTORY)
        historyBase.setPackage(packageName)

        val historyPendingIntent = PendingIntent.getBroadcast(
            this,
            9999,
            historyBase,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val historyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_view,
            "Letzte 5",
            historyPendingIntent
        ).build()

        val deleteBase = Intent(this, ChatService::class.java)
        deleteBase.action = "ACTION_NOTIFICATION_DELETED"
        deleteBase.setPackage(packageName)

        val deletePendingIntent = PendingIntent.getService(
            this,
            999,
            deleteBase,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("💬 Chat Service")
            .setContentText("Tippe auf 'Schreiben' um eine Nachricht zu senden")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .addAction(replyAction)
            .addAction(historyAction)
            .setDeleteIntent(deletePendingIntent)
            .setGroup("Services")
            .setGroupSummary(true)
            .build()
    }

    private fun updateServiceNotification() {
        try {
            val notificationManager = getSystemService(NotificationManager::class.java)
            val notification = createServiceNotification()
            notificationManager.notify(Config.CHAT_SERVICE, notification)
        } catch (_: Exception) {
        }
    }


    private fun showHistoryNotification() {
        try {
            val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())

            val sortedHistory = messageHistory
                .filter { it.sender_id == friendUserId }
                .sortedBy { msg ->
                    try {
                        isoFormat.parse(msg.created_at?.substring(0, 19) ?: "")?.time ?: 0L
                    } catch (_: Exception) {
                        0L
                    }
                }
                .takeLast(5)

            val historyText = sortedHistory.joinToString("\n\n") { msg ->
                val dateTime = try {
                    val date = isoFormat.parse(msg.created_at?.substring(0, 19) ?: "")
                    val dateFormatter = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                    if (date != null) {
                        dateFormatter.format(date)
                    } else {
                        "??:??"
                    }
                } catch (_: Exception) {
                    "??:??"
                }
                "[$dateTime] ${msg.sender_id}:\n${msg.content}"
            }

            val notification = NotificationCompat.Builder(this, "chat_messages")
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentTitle("📜 Letzte Nachrichten")
                .setContentText("${messageHistory.size} Nachrichten")
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(historyText.ifEmpty { "No Nachrichten" })
                )
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)
                .build()

            tNotify(this, Config.CHAT_SERVICE_HISTORY, notification)

        } catch (_: Exception) {
        }
    }


    private suspend fun setupRealtimeListener() {
        try {
            Log.d("ChatService", "Setting up Realtime listener...")

            val setupSuccess = withTimeoutOrNull(REALTIME_SETUP_TIMEOUT_MS.milliseconds) {
                try {
                    val initialMessages = Config.safeCall {
                        supabase.from("messages")
                            .select()
                            .decodeList<Message>()
                    }.takeLast(10)

                    initialMessages.forEach { msg ->
                        msg.id?.let { seenMessageIds.add(it) }
                    }
                    messageHistory.addAll(initialMessages.takeLast(5))
                    saveSeenMessageIds()

                    val channel = supabase.channel("chat:messages")

                    val insertFlow = channel
                        .postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                            table = "messages"
                        }

                    insertFlow.onEach { change ->
                        try {
                            val message = Json.decodeFromString<Message>(change.record.toString())

                            if (message.sender_id == friendUserId &&
                                message.id != null &&
                                !seenMessageIds.contains(message.id)
                            ) {

                                Log.d("ChatService", "📩 New message: ${message.content}")

                                withContext(Dispatchers.Main) {
                                    showMessageNotification(message)
                                }

                                seenMessageIds.add(message.id)
                                messageHistory.add(message)
                                if (messageHistory.size > 5) {
                                    messageHistory.removeAt(0)
                                }
                                saveSeenMessageIds()
                            }
                        } catch (_: Exception) {
                        }
                    }.launchIn(serviceScope)

                    channel.subscribe(blockUntilSubscribed = true)
                    Log.i("ChatService", "✅ Realtime channel subscribed")

                    true
                } catch (_: Exception) {
                    false
                }
            }

            if (setupSuccess == null) {
                Log.w(
                    "ChatService",
                    "⚠️ Realtime setup timed out after ${REALTIME_SETUP_TIMEOUT_MS}ms"
                )
            } else if (!setupSuccess) {
                Log.e("ChatService", "❌ Realtime setup failed")
            }

        } catch (e: TimeoutCancellationException) {
            Log.e("ChatService", "Realtime setup timed out", e)
        } catch (_: Exception) {
        }
    }


    private fun showMessageNotification(message: Message) {
        try {
            val replyRemoteInput = RemoteInput.Builder(KEY_REPLY_TEXT)
                .setLabel("Antwort")
                .build()

            val replyBase = Intent(ACTION_REPLY)
            replyBase.putExtra(KEY_NOTIFICATION_ID, Config.cms())
            replyBase.setPackage(packageName)

            val replyPendingIntent = PendingIntent.getBroadcast(
                this,
                Config.cms(),
                replyBase,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

            val replyAction = NotificationCompat.Action.Builder(
                android.R.drawable.ic_menu_send,
                "Antworten",
                replyPendingIntent
            )
                .addRemoteInput(replyRemoteInput)
                .setShowsUserInterface(false)
                .build()

            val timeText = SimpleDateFormat("HH:mm", Locale.getDefault())
                .format(Date())

            val notification = NotificationCompat.Builder(this, "chat_messages")
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentTitle("💬 ${message.sender_id}")
                .setContentText(message.content)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText("${message.content}\n\n$timeText")
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .addAction(replyAction)
                .build()

            tNotify(this, Config.cms(), notification)

        } catch (_: Exception) {
        }
    }


    private suspend fun sendMessage(content: String) {
        try {
            val message = Message(
                sender_id = myUserId,
                receiver_id = friendUserId,
                content = content
            )

            Config.safeCall {
                supabase.from("messages").insert(message)
            }
            Log.d("ChatService", "✅ Message sent: $content")

        } catch (e: Exception) {
            Log.e("ChatService", "Error sending message", e)
        }
    }


    override fun onDestroy() {
        super.onDestroy()

        try {
            unregisterReceiver(replyReceiver)
            Log.d("ChatService", "Receiver unregistered")
        } catch (e: Exception) {
            Log.e("ChatService", "Error unregistering receiver", e)
        }

        serviceScope.cancel()
        Log.d("ChatService", "Service destroyed - scope cancelled")
    }
}
