package com.tabslify.quiethoursnotificationhelper

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Environment
import android.util.Log
import androidx.core.app.NotificationCompat
import com.tabslify.R
import com.tabslify.core.functions.showSimpleNotificationExtern
import com.tabslify.core.objects.Config.VOICE_NOTE
import com.tabslify.core.objects.prvt
import com.tabslify.core.objects.tNotify
import com.tabslify.services.QuietHoursNotificationService
import com.tabslify.services.QuietHoursNotificationService.Companion.ACTION_NEXT_VOICE_NOTE
import com.tabslify.services.QuietHoursNotificationService.Companion.ACTION_PLAY_VOICE_NOTE
import com.tabslify.services.QuietHoursNotificationService.Companion.ACTION_PREV_VOICE_NOTE
import com.tabslify.services.QuietHoursNotificationService.Companion.ACTION_STOP_VOICE_NOTE
import com.tabslify.services.QuietHoursNotificationService.Companion.EXTRA_SENDER_FOR_VOICE
import com.tabslify.services.QuietHoursNotificationService.Companion.MAX_VOICE_NOTE_FILES
import com.tabslify.services.QuietHoursNotificationService.Companion.VOICE_NOTE_CHANNEL_ID
import com.tabslify.services.QuietHoursNotificationService.Companion.currentSenderForVoiceNote
import com.tabslify.services.QuietHoursNotificationService.Companion.currentVoiceNoteIndex
import com.tabslify.services.QuietHoursNotificationService.Companion.mainHandler
import com.tabslify.services.QuietHoursNotificationService.Companion.voiceNoteFiles
import com.tabslify.services.QuietHoursNotificationService.Companion.voiceNotePlayer
import com.tabslify.services.QuietHoursNotificationService.Companion.workerHandler
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun playLatestVoiceNote(sender: String, context: Context) {
    if (!prvt()) return
    try {
        currentSenderForVoiceNote = sender
        workerHandler.post {
            try {
                val files = getVoiceNoteFiles()

                mainHandler.post {
                    voiceNoteFiles = files

                    if (voiceNoteFiles.isEmpty()) {
                        showSimpleNotificationExtern(
                            context.getString(R.string.keine_sprachnachrichten),
                            context.getString(R.string.keine_opus_dateien_gefunden),
                            context = context
                        )
                        return@post
                    }

                    currentVoiceNoteIndex = 0
                    playVoiceNoteAtIndex(currentVoiceNoteIndex, context)
                }
            } catch (_: Exception) {
                mainHandler.post {
                    showSimpleNotificationExtern(
                        context.getString(R.string.fehler),
                        context.getString(R.string.sprachnachrichten_konnten_nicht_geladen_werden),
                        context = context
                    )
                }
            }
        }
    } catch (_: Exception) {
        showSimpleNotificationExtern(
            context.getString(R.string.fehler),
            context.getString(R.string.sprachnachricht_konnte_nicht_abgespielt_werden),
            context = context
        )
    }
}


fun playVoiceNoteAtIndex(index: Int, context: Context) {
    if (!prvt()) return
    try {
        if (index < 0 || index >= voiceNoteFiles.size) {
            showSimpleNotificationExtern(context.getString(R.string.fehler), context.getString(R.string.ungultiger_index_2), context = context)
            return
        }

        voiceNotePlayer?.release()
        voiceNotePlayer = null

        val file = voiceNoteFiles[index]

        voiceNotePlayer = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            setOnPreparedListener { mp ->
                mp.start()
                showVoiceNotePlayerNotification(file, true, context)
            }
            setOnCompletionListener { mp ->
                mp.release()
                voiceNotePlayer = null
                showVoiceNotePlayerNotification(file, false, context)
            }
            setOnErrorListener { mp, what, extra ->
                Log.e("QuietHoursService", "MediaPlayer error: what=$what, extra=$extra")
                mp.release()
                voiceNotePlayer = null
                showSimpleNotificationExtern(context.getString(R.string.fehler), context.getString(R.string.fehler_beim_abspielen), context = context)
                true
            }
            prepareAsync()
        }

    } catch (e: Exception) {
        Log.e("QuietHoursService", "Error playing voice note at index $index", e)
        showSimpleNotificationExtern(
            context.getString(R.string.fehler),
            context.getString(R.string.sprachnachricht_konnte_nicht_abgespielt_werden_2, e.message),
            context = context
        )
    }
}


fun getVoiceNoteFiles(): List<File> {
    try {
        val possiblePaths = listOf(
            "/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Voice Notes",
            "${Environment.getExternalStorageDirectory()}/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Voice Notes",
            "/storage/emulated/0/WhatsApp/Media/WhatsApp Voice Notes"
        )

        for (path in possiblePaths) {
            val mainDir = File(path)
            if (!mainDir.exists()) continue

            val allFiles = mainDir.walkTopDown()
                .filter { it.isFile && it.extension.lowercase() == "opus" }
                .sortedByDescending { it.lastModified() }
                .take(MAX_VOICE_NOTE_FILES)
                .toList()

            if (allFiles.isNotEmpty()) {
                return allFiles
            }
        }

        return emptyList()
    } catch (e: Exception) {
        Log.e("QuietHoursService", "Error getting voice notes", e)
        return emptyList()
    }
}

fun playNextVoiceNote(context: Context) {
    if (!prvt()) return
    if (voiceNoteFiles.isEmpty()) return

    currentVoiceNoteIndex = (currentVoiceNoteIndex + 1) % voiceNoteFiles.size

    playVoiceNoteAtIndex(
        currentVoiceNoteIndex,
        context
    )
}

fun playPreviousVoiceNote(context: Context) {
    if (!prvt()) return
    if (voiceNoteFiles.isEmpty()) return

    currentVoiceNoteIndex = if (currentVoiceNoteIndex - 1 < 0) {
        voiceNoteFiles.size - 1
    } else {
        currentVoiceNoteIndex - 1
    }
    playVoiceNoteAtIndex(
        currentVoiceNoteIndex,
        context
    )
}

fun stopVoiceNote(context: Context) {
    if (!prvt()) return
    try {
        voiceNotePlayer?.apply {
            if (isPlaying) stop()
            reset()
            release()
        }
        voiceNotePlayer = null
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.cancel(VOICE_NOTE)
    } catch (e: Exception) {
        Log.e("QuietHoursService", "Error stopping voice note", e)
    }
}


private fun showVoiceNotePlayerNotification(file: File, isPlaying: Boolean, context: Context) {
    if (!prvt()) return
    try {
        val fileName = file.name
        val fileDate = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
            .format(Date(file.lastModified()))

        val prevBase = Intent(context, QuietHoursNotificationService::class.java)
        prevBase.action = ACTION_PREV_VOICE_NOTE
        prevBase.setPackage(context.packageName)
        val prevPendingIntent = PendingIntent.getService(
            context, 41, prevBase,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playStopBase = Intent(context, QuietHoursNotificationService::class.java)
        if (isPlaying) {
            playStopBase.action = ACTION_STOP_VOICE_NOTE
        } else {
            playStopBase.action = ACTION_PLAY_VOICE_NOTE
            playStopBase.putExtra(EXTRA_SENDER_FOR_VOICE, currentSenderForVoiceNote)
        }
        playStopBase.setPackage(context.packageName)
        val playStopPendingIntent = PendingIntent.getService(
            context, 42, playStopBase,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextBase = Intent(context, QuietHoursNotificationService::class.java)
        nextBase.action = ACTION_NEXT_VOICE_NOTE
        nextBase.setPackage(context.packageName)
        val nextPendingIntent = PendingIntent.getService(
            context, 43, nextBase,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, VOICE_NOTE_CHANNEL_ID)
            .setSmallIcon(if (isPlaying) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause)
            .setContentTitle("${if (isPlaying) "▶️" else "⏸️"} Sprachnachricht")
            .setContentText("$fileName • $fileDate")
            .setSubText(context.getString(R.string.fortschritt_von, currentVoiceNoteIndex + 1, voiceNoteFiles.size))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(isPlaying)
            .setAutoCancel(!isPlaying)
            .setGroup("group_media")
            .setGroupSummary(false)
            .addAction(android.R.drawable.ic_media_previous, context.getString(R.string.zuruck), prevPendingIntent)
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) context.getString(R.string.stop) else context.getString(R.string.play),
                playStopPendingIntent
            )
            .addAction(android.R.drawable.ic_media_next, context.getString(R.string.weiter), nextPendingIntent)
            .build()

        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            tNotify(context, VOICE_NOTE, notification)
        }

    } catch (e: Exception) {
        Log.e("QuietHoursService", "Error showing voice note player notification", e)
    }
}