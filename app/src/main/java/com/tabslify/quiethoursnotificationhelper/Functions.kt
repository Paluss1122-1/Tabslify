package com.tabslify.quiethoursnotificationhelper

import android.Manifest
import android.content.Context
import android.content.Context.AUDIO_SERVICE
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.tabslify.R
import com.tabslify.core.activities.Tabslify.Companion.serviceScope
import com.tabslify.core.functions.showSimpleNotificationExtern
import com.tabslify.core.objects.Config
import com.tabslify.core.objects.Config.cms
import com.tabslify.core.objects.tNotify
import com.tabslify.inactive.ChatService
import com.tabslify.services.QuietHoursNotificationService.Companion.CHANNEL_ID
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.seconds

fun showLastFriendMessages(context: Context) {
    serviceScope.launch(Dispatchers.IO) {
        try {
            Handler(Looper.getMainLooper()).post {
                showSimpleNotificationExtern(
                    context.getString(R.string.lade_nachrichten),
                    context.getString(R.string.rufe_letzte_nachrichten_von_friend),
                    3.seconds,
                    context
                )
            }

            val supabase = Config.client

            val response = supabase.from("messages")
                .select {
                    filter {
                        eq("sender_id", "friend")
                        eq("receiver_id", "you")
                    }
                    order("created_at", Order.DESCENDING)
                    limit(10)
                }

            val messages = response.decodeList<ChatService.Message>()
                .reversed()

            if (messages.isEmpty()) {
                Handler(Looper.getMainLooper()).post {
                    showSimpleNotificationExtern(
                        context.getString(R.string.keine_nachrichten),
                        context.getString(R.string.keine_nachrichten_von_friend_gefunden),
                        context = context
                    )
                }
                return@launch
            }

            Handler(Looper.getMainLooper()).post {
                showFriendMessagesNotification(messages, context)
            }

        } catch (e: Exception) {
            Handler(Looper.getMainLooper()).post {
                showSimpleNotificationExtern(
                    context.getString(R.string.fehler_2),
                    context.getString(R.string.nachrichten_konnten_nicht_geladen_werden, e.message),
                    20.seconds,
                    context
                )
            }
        }
    }
}

private fun showFriendMessagesNotification(messages: List<ChatService.Message>, context: Context) {
    try {
        val messageText = messages.joinToString("\n\n") { msg ->
            val timeStr = try {
                msg.created_at?.let {
                    val instant = Instant.parse(it)
                    val formatter = DateTimeFormatter
                        .ofPattern("dd.MM.yyyy HH:mm")
                        .withZone(ZoneId.systemDefault())
                    formatter.format(instant)
                }
            } catch (_: Exception) {
                msg.created_at?.take(16)?.replace("T", " ")
            } ?: context.getString(R.string.unbekannt)

            context.getString(R.string.str_3, timeStr, msg.content)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(context.resources.getQuantityString(R.plurals.letzte_nachrichten_von_friend, messages.size, messages.size))
            .setContentText(messages.lastOrNull()?.content ?: "")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(messageText)
                    .setBigContentTitle(context.getString(R.string.chat_verlauf_mit_friend))
                    .setSummaryText(context.resources.getQuantityString(R.plurals.nachrichten, messages.size, messages.size))
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            tNotify(context, cms(), notification)
        }

    } catch (e: Exception) {
        showSimpleNotificationExtern(
            context.getString(R.string.fehler_2),
            context.getString(R.string.benachrichtigung_konnte_nicht_angezeigt_werden, e.message),
            20.seconds,
            context
        )
    }
}

fun setSoundMode(mode: String, context: Context) {
    try {
        val audioManager = context.getSystemService(AUDIO_SERVICE) as AudioManager

        if (!canWriteSettings(context)) {
            showSimpleNotificationExtern(
                context.getString(R.string.keine_berechtigung),
                context.getString(R.string.write_settings_berechtigung_fehlt_aktiviere),
                20.seconds,
                context
            )
            return
        }

        when (mode.lowercase()) {
            "vibrate", "vib", "v" -> {
                audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE

                try {
                    Settings.System.putInt(
                        context.contentResolver,
                        Settings.System.VIBRATE_ON,
                        1
                    )
                    Settings.System.putInt(
                        context.contentResolver,
                        Settings.System.SOUND_EFFECTS_ENABLED,
                        0
                    )
                } catch (e: Exception) {
                    Log.w("QuietHoursService", "Could not set vibration settings", e)
                }

                showSimpleNotificationExtern(
                    context.getString(R.string.vibration_aktiviert),
                    context.getString(R.string.nur_vibrationen_keine_tone),
                    context = context
                )
            }

            "silent", "mute", "m" -> {
                audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT

                try {
                    Settings.System.putInt(
                        context.contentResolver,
                        Settings.System.VIBRATE_ON,
                        0
                    )
                    Settings.System.putInt(
                        context.contentResolver,
                        Settings.System.SOUND_EFFECTS_ENABLED,
                        0
                    )
                } catch (e: Exception) {
                    Log.w("QuietHoursService", "Could not set silent settings", e)
                }

                showSimpleNotificationExtern(
                    context.getString(R.string.stumm),
                    context.getString(R.string.keine_tone_keine_vibrationen),
                    context = context
                )
            }

            "normal", "loud", "l", "on" -> {
                audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL

                try {
                    Settings.System.putInt(
                        context.contentResolver,
                        Settings.System.VIBRATE_ON,
                        1
                    )
                    Settings.System.putInt(
                        context.contentResolver,
                        Settings.System.SOUND_EFFECTS_ENABLED,
                        1
                    )
                } catch (e: Exception) {
                    Log.w("QuietHoursService", "Could not set normal settings", e)
                }

                showSimpleNotificationExtern(
                    context.getString(R.string.normal),
                    context.getString(R.string.tone_und_vibrationen_aktiviert),
                    context = context
                )
            }

            else -> {
                showSimpleNotificationExtern(
                    context.getString(R.string.ungultig),
                    context.getString(R.string.nutze_sound_vibrate_silent_normal),
                    20.seconds,
                    context
                )
            }
        }

    } catch (e: Exception) {
        showSimpleNotificationExtern(
            context.getString(R.string.fehler_2),
            context.getString(R.string.sound_modus_konnte_nicht_geandert, e.message),
            20.seconds,
            context
        )
    }
}

private fun canWriteSettings(context: Context): Boolean {
    return Settings.System.canWrite(context)
}