package com.tabslify.quiethoursnotificationhelper

import android.app.Activity
import android.app.DownloadManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.core.content.edit
import com.tabslify.core.functions.showSimpleNotificationExtern
import com.tabslify.core.objects.tNotify
import com.tabslify.services.MediaPlayerService
import com.tabslify.services.MediaPlayerService.Companion.ACTION_PODCAST_PLAY_SPECIFIED
import com.tabslify.services.MediaPlayerService.Companion.CHANNEL_ID
import com.tabslify.services.QuietHoursNotificationService
import com.tabslify.services.QuietHoursNotificationService.Companion.ACTION_CHANGE_END
import com.tabslify.services.QuietHoursNotificationService.Companion.ACTION_CHANGE_START
import com.tabslify.services.QuietHoursNotificationService.Companion.ACTION_EXECUTE_COMMAND
import com.tabslify.services.QuietHoursNotificationService.Companion.ACTION_MARK_PARTS_READ
import com.tabslify.services.QuietHoursNotificationService.Companion.ACTION_MESSAGE_SENT
import com.tabslify.services.QuietHoursNotificationService.Companion.ACTION_NOTIFICATION_DISMISSED
import com.tabslify.services.QuietHoursNotificationService.Companion.ACTION_RESTORE_NOTIFICATION
import com.tabslify.services.QuietHoursNotificationService.Companion.EXTRA_MESSAGE_ID
import com.tabslify.services.QuietHoursNotificationService.Companion.EXTRA_SENDER
import com.tabslify.services.QuietHoursNotificationService.Companion.NOTIFICATION_ID
import com.tabslify.services.QuietHoursNotificationService.Companion.readMessageIds
import com.tabslify.tabs.mediaplayer.PodcastShowManager
import org.json.JSONObject

class QuietActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("quiet_hours_prefs", MODE_PRIVATE)

        when (intent.action) {
            "SET_START" -> {
                prefs.edit(commit = true) { putString("quiet_hours_end", "21") }
            }

            "SET_END" -> {
                prefs.edit(commit = true) { putString("quiet_hours_start", "7") }
            }
        }
    }
}

class QuietHoursAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        checkQuietHours(context)
        scheduleNextCheck(context)
    }
}

val markReadReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == ACTION_MARK_PARTS_READ) {
            val messageId = intent.getStringExtra(EXTRA_MESSAGE_ID)
            if (messageId != null && context != null) {
                val appContext = context.applicationContext
                Handler(Looper.getMainLooper()).post {
                    markMessageAsRead(
                        messageId,
                        readMessageIds,
                        appContext
                    )
                }
            }
        }
    }
}

val messageSentReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == ACTION_MESSAGE_SENT) {
            val sender = intent.getStringExtra(EXTRA_SENDER) ?: return

            val replyText = RemoteInput.getResultsFromIntent(intent)
                ?.getCharSequence("key_text_reply")?.toString()

            resultCode = Activity.RESULT_OK

            if (replyText != null && context != null) {
                val appContext = context.applicationContext
                Handler(Looper.getMainLooper()).post {
                    handleMessageSent(sender, replyText, appContext)
                }
            }
        }
    }
}

val notificationDismissReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == ACTION_NOTIFICATION_DISMISSED) {
            val notificationId = intent.getIntExtra("notification_id", -1)

            if (notificationId == NOTIFICATION_ID) {
                val appContext = context.applicationContext
                Handler(Looper.getMainLooper()).postDelayed({
                    val serviceIntent =
                        Intent(appContext, QuietHoursNotificationService::class.java).apply {
                            action = ACTION_RESTORE_NOTIFICATION
                        }
                    appContext.startForegroundService(serviceIntent)
                }, 100)
            } else if (notificationId == com.tabslify.core.objects.Config.GAL) {
                QuietHoursNotificationService.galleryImages = emptyList()
                QuietHoursNotificationService.currentGalleryIndex = 0
            }
        }
    }
}

val timeChangeReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val newTime = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence("key_time_input")?.toString()

        if (newTime != null) {
            val timeValue = newTime.toIntOrNull()

            if (timeValue != null && timeValue in 0..23) {
                val prefs = context.getSharedPreferences("quiet_hours_prefs", MODE_PRIVATE)

                when (intent.action) {
                    ACTION_CHANGE_START -> {
                        prefs.edit(commit = true) {
                            putString(
                                "quiet_hours_end",
                                timeValue.toString()
                            )
                        }
                        showSimpleNotificationExtern(
                            "✓ Startzeit geändert",
                            "Neue Startzeit: $timeValue Uhr",
                            context = context
                        )
                    }

                    ACTION_CHANGE_END -> {
                        prefs.edit(commit = true) {
                            putString(
                                "quiet_hours_start",
                                timeValue.toString()
                            )
                        }
                        showSimpleNotificationExtern(
                            "✓ Endzeit geändert",
                            "Neue Endzeit: $timeValue Uhr",
                            context = context
                        )
                    }
                }

                resultCode = Activity.RESULT_OK
            } else {
                showSimpleNotificationExtern(
                    "❌ Ungültige Zeit",
                    "Bitte eine Zahl zwischen 0 und 23 eingeben",
                    context = context,
                    silent = false
                )
            }
        }
    }
}

val commandReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == ACTION_EXECUTE_COMMAND) {
            val commandText = RemoteInput.getResultsFromIntent(intent)
                ?.getCharSequence("key_command_input")?.toString()

            val serviceIntent = Intent(context, QuietHoursNotificationService::class.java).apply {
                action = ACTION_RESTORE_NOTIFICATION
            }
            context.startForegroundService(serviceIntent)

            if (commandText != null) {
                val appContext = context.applicationContext
                Handler(Looper.getMainLooper()).post {
                    executeCommand(commandText.trim(), appContext)
                }
            }
        }
    }
}

class FinishedPdDownload : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
        if (downloadId == -1L) return

        val prefs = context.getSharedPreferences("podcast_downloads", MODE_PRIVATE)
        val pendingJson = prefs.getString("pending_$downloadId", null) ?: return
        prefs.edit { remove("pending_$downloadId") }

        val obj = JSONObject(pendingJson)
        val safeTitle = obj.getString("safeTitle")
        val showName = obj.getString("showName")

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val cursor = dm.query(DownloadManager.Query().setFilterById(downloadId))
        cursor?.use { cursor ->
            if (cursor.moveToFirst()) {
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    PodcastShowManager.init(context)
                    val show = PodcastShowManager.getShows()
                        .find { it.name.equals(showName, ignoreCase = true) }
                        ?: PodcastShowManager.createShow(showName)
                    PodcastShowManager.assignPattern(safeTitle.lowercase(), show.name)
                    Toast.makeText(context, "✓ Heruntergeladen", Toast.LENGTH_SHORT).show()
                    val openBase = Intent(context, MediaPlayerService::class.java)
                    openBase.action = ACTION_PODCAST_PLAY_SPECIFIED
                    openBase.putExtra("safeTitle", safeTitle)
                    openBase.setPackage(context.packageName)
                    val pendingIntent = PendingIntent.getForegroundService(
                        context, downloadId.toInt(), openBase,
                        PendingIntent.FLAG_IMMUTABLE
                    )

                    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.stat_sys_download_done)
                        .setContentTitle("✓ Podcast heruntergeladen")
                        .setContentText(safeTitle)
                        .setContentIntent(pendingIntent)
                        .setAutoCancel(true)
                        .build()

                    tNotify(context, downloadId.toInt(), notification)
                }
            }
        }
    }
}

class AkkuReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        reportDeviceInformation(ctx, intent)
    }
}

class BluetoothReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        handleBluetoothBroadcast(ctx, intent)
    }
}

class VolumeChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val streamType = intent.getIntExtra(
            "android.media.EXTRA_VOLUME_STREAM_TYPE",
            -1
        )

        if (streamType == AudioManager.STREAM_MUSIC) {
            reportDeviceInformation(context, intent)
        }
    }
}