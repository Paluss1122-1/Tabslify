package com.tabslify.quiethoursnotificationhelper

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Context.CAMERA_SERVICE
import android.content.Context.MODE_PRIVATE
import android.content.Context.WINDOW_SERVICE
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.camera2.CameraManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.tabslify.core.activities.Tabslify.Companion.appScope
import com.tabslify.core.activities.Tabslify.Companion.serviceScope
import com.tabslify.core.functions.showSimpleNotificationExtern
import com.tabslify.core.objects.Config
import com.tabslify.core.objects.Config.SHOWCOMMANDS
import com.tabslify.core.objects.Config.client
import com.tabslify.core.objects.Config.fetchBWMP
import com.tabslify.core.objects.prvt
import com.tabslify.core.ui.showBatteryInfo
import com.tabslify.services.MediaPlayerService
import com.tabslify.services.MusicPlayerServiceCompat
import com.tabslify.services.OverlayLifecycleOwner
import com.tabslify.services.PodcastPlayerServiceCompat
import com.tabslify.services.QuietHoursNotificationService.Companion.CHANNEL_ID
import com.tabslify.services.QuietHoursNotificationService.Companion.commandHistory
import com.tabslify.services.QuietHoursNotificationService.Companion.showtestOverlay
import com.tabslify.tabs.OtherBucketViewer
import com.tabslify.tabs.audiorecordertab.AudioForegroundService
import com.tabslify.tabs.fetchWeatherForecast
import com.tabslify.tabs.getLastKnownLocation
import com.tabslify.tabs.mediaplayer.AlgorithmicPlaylistRegistry
import com.tabslify.tabs.mediaplayer.MediaAnalyticsManager
import com.tabslify.tabs.mediaplayer.MediaAnalyticsManager.rebuildSessions
import com.tabslify.tabs.mediaplayer.PodcastShowManager
import com.tabslify.tabs.weathernot
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

data class Command(
    val name: String,
    val aliases: List<String> = emptyList(),
    val description: String,
    val action: () -> Unit
)

private var testOverlayView: ComposeView? = null
private var testOverlayLifecycle: OverlayLifecycleOwner? = null


@SuppressLint("MissingPermission")
fun getHomeWifiStatus(
    context: Context,
    homeSsid: String,
    onResult: (isHome: Boolean) -> Unit
) {
    val cm = context.getSystemService(ConnectivityManager::class.java)
    val activeNetwork = cm.activeNetwork
    val caps = cm.getNetworkCapabilities(activeNetwork)
    val hasWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    if (!hasWifi) {
        onResult(false)
        return
    }

    val request = NetworkRequest.Builder()
        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
        .build()

    val callback = object : ConnectivityManager.NetworkCallback(
        FLAG_INCLUDE_LOCATION_INFO
    ) {
        private var hasFired = false

        override fun onCapabilitiesChanged(
            network: Network,
            caps: NetworkCapabilities
        ) {
            if (hasFired) return

            val wifiInfo = caps.transportInfo as? WifiInfo
            val ssid = wifiInfo?.ssid
                ?.removePrefix("\"")
                ?.removeSuffix("\"")
                ?.takeIf { it != "<unknown ssid>" }

            hasFired = true
            onResult(ssid != null && ssid.equals(homeSsid, ignoreCase = true))
            cm.unregisterNetworkCallback(this)
        }

        override fun onUnavailable() {
            if (hasFired) return
            hasFired = true
            onResult(false)
            cm.unregisterNetworkCallback(this)
        }
    }

    cm.registerNetworkCallback(request, callback)
}

private fun getAvailableCommands(context: Context): List<Command> {
    return listOf(
        Command(
            name = "flo",
            description = "Setup MEDIA folders"
        ) {
            val env = Environment.getExternalStorageDirectory()

            listOf(
                File(env, "Download/Cloud"),
                File(env, "Podcasts/Cloud"),
                File(env, "Music/Cloud")
            ).forEach { it.deleteRecursively() }

            listOf(
                File(env, "Podcasts/Tabslify"),
                File(env, "Music/Tabslify")
            ).forEach { it.mkdirs() }
        },
        Command(
            name = "whatsapp",
            aliases = listOf("wh", "wa", "messages", "msg", "nachrichten"),
            description = "Zeigt ungelesene Nachrichten"
        ) {
            showUnreadMessages(context)
        },
        // Ausgeblendet in public version:
        Command(
            name = "t",
            aliases = listOf(),
            description = "Zeigt ungelesene Nachrichten"
        ) {


//            val intent = Intent(context, QuietHoursNotificationService::class.java).apply {
//                action = ACTION_SCHOOL_DAY_SUMMARY
//            }
//            val pending = PendingIntent.getService(
//                context, 9001, intent,
//                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
//            )
//
//            pending.send()
        },
        Command(
            name = "ai",
            aliases = listOf("aichat", "aiChat", "geminiChat", "gm", "newai", "newaichat", "na", "gemini"),
            description = "Erstellt einen neuen AI-Chat (ai [name optional])"
        ) {},
        Command(
            name = "music",
            aliases = listOf("m", "play", "player", "musik"),
            description = "Startet Musik Player"
        ) {
            MediaPlayerService.startAndPlayMusic(context, null)
        },
        Command(
            name = "podcast",
            aliases = listOf("pd", "pc", "Podcast", "py"),
            description = "Startet PodcastPlayerServiceCompat"
        ) {
            PodcastPlayerServiceCompat.startService(context)
            PodcastPlayerServiceCompat.sendPlayAction(context)
        },
        Command(
            name = "managepodcast",
            aliases = listOf("mpd", "ManagePodcast"),
            description = "Zeigt alle vollendeten Podcasts als Notification"
        ) {
            PodcastPlayerServiceCompat.managePodcast(context)
        },
        Command(
            name = "queue",
            aliases = listOf("q", "warteschlange", "playlist"),
            description = "Zeigt Podcast-Warteschlange"
        ) {
            showPodcastQueue(context)
        },
        Command(
            name = "qadd",
            aliases = listOf("qa", "queueadd", "addqueue"),
            description = "Fügt Podcast zur Queue hinzu (Syntax: qadd [nummer])"
        ) {
            showSimpleNotificationExtern(
                "ℹ️ Queue Add",
                "Syntax: qadd [podcast-nummer]\nVerwende 'podcast' um Nummern zu sehen",
                context = context
            )
        },
        Command(
            name = "qremove",
            aliases = listOf("qr", "queueremove", "removequeue"),
            description = "Entfernt Podcast aus Queue (Syntax: qremove [position])"
        ) {
            showSimpleNotificationExtern(
                "ℹ️ Queue Remove",
                "Syntax: qremove [position in queue 1-X]",
                context = context
            )
        },
        Command(
            name = "qclear",
            aliases = listOf("qc", "queueclear", "clearqueue"),
            description = "Leert die komplette Queue"
        ) {
            clearPodcastQueue(context)
        },
        Command(
            name = "voice",
            aliases = listOf("v", "voicenote", "sprachnachricht", "audio"),
            description = "Spielt Voice Notes von WhatsApp ab"
        ) {
            if (prvt()) playLatestVoiceNote("Manual Command", context)
        },
        Command(
            name = "record",
            aliases = listOf("rec", "aufnahme", "audiorec", "recording"),
            description = "Startet/Stoppt Audioaufnahme (Syntax: record start|stop)"
        ) {
            showSimpleNotificationExtern(
                "ℹ️ Aufnahme",
                "Verwende: record start | record stop",
                20.seconds,
                context
            )
        },
        Command(
            name = "help",
            aliases = listOf("h", "?", "commands", "befehle"),
            description = "Zeigt alle verfügbaren Befehle"
        ) {
            showAvailableCommands(context)
        },
        Command(
            name = "flashlevel",
            aliases = listOf("flashl", "lightlevel", "torchlevel", "helligkeit", "flash", "f"),
            description = "Setze Taschenlampen-Helligkeit (Syntax: flashlevel [1-max])"
        ) {
            val cameraManager = context.getSystemService(CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull()
            if (cameraId != null) {
                val clampedLevel = 1
                cameraManager.turnOnTorchWithStrengthLevel(cameraId, clampedLevel)
            }
        },
        Command(
            name = "message",
            aliases = listOf("send", "senden", "write", "schreiben"),
            description = "Sendet Nachricht an gespeicherten Kontakt"
        ) {
            showSimpleNotificationExtern(
                "ℹ️ Verwendung",
                "Syntax: message [deine nachricht]",
                context = context
            )
        },
        Command(
            name = "stopmediaplayer",
            aliases = listOf("stopm", "mediaplayerstop", "sm"),
            description = "Stoppt Media Player Service"
        ) {
            try {
                MediaPlayerService.stopService(context)
            } catch (e: Exception) {
                showSimpleNotificationExtern(
                    "❌ Fehler",
                    "Media Player konnte nicht gestoppt werden: ${e.message}",
                    context = context
                )
            }
        },
        Command(
            name = "weather",
            aliases = listOf("w", "wetter", "forecast"),
            description = "Zeigt Wetter (Syntax: weather [0-2] [0-23])"
        ) {
            showSimpleNotificationExtern(
                "ℹ️ Wetter",
                "Syntax: weather [0=Heute, 1=Morgen, 2=Übermorgen] [0-23]",
                context = context
            )
        },
        Command(
            name = "extract",
            aliases = listOf("e", "ex", "extrahieren"),
            description = "Zeigt letzte WhatsApp Nachricht als separate Notification"
        ) {
            extractLastMessage(context)
        },
        Command(
            name = "gallerie",
            aliases = listOf("gal", "g", "gallery"),
            description = "Zeigt Gallerie als nots"
        ) {
            loadGalleryImages(0, context)
        },
        Command(
            name = "setdowntime",
            aliases = listOf("set", "dt", "setdr"),
            description = "Lege Downtime für QuietHoursNotificationService fest (setdowntime [Stunde])"
        ) {},
        // Ausgeblendet in public version:
        Command(
            name = "friendmessages",
            aliases = listOf("fm", "friendmsgs", "lastmsgs", "friend"),
            description = "Zeigt letzte 3 Nachrichten von friend"
        ) {
            showLastFriendMessages(context)
        },
        Command(
            name = "sound",
            aliases = listOf("vibrate", "vib", "ton", "sound", "silent", "s"),
            description = "Stellt Vibration/Ton ein (Syntax: sound [vibrate|silent|normal])"
        ) {
            showSimpleNotificationExtern(
                "ℹ️ Sound Befehle",
                "sound vibrate - Nur Vibration\nsound silent - Stumm\nsound normal - Normal mit Ton",
                context = context
            )
        },
        Command(
            name = "clearpodcasts",
            aliases = listOf("cp", "clearpod", "removepod"),
            description = "Löscht alle Podcast-Auswahl Notifications"
        ) {
            clearPodcastSelectionNotifications(context)
        },
        Command(
            name = "tb",
            aliases = listOf("tagesbericht", "upload", "uploadimage"),
            description = "Lädt aktuelles Galeriebild zu Supabase hoch (Syntax: tb [dd.mm.yy] [name])"
        ) {
            showSimpleNotificationExtern(
                "ℹ️ Tagesbericht Upload",
                "Syntax: tb [dd.mm.yy] [bildname]\nBeispiel: tb 08.01.26 sonnenuntergang",
                context = context
            )
        },
        Command(
            name = "bitwarden",
            aliases = listOf("bw", "btw", "b"),
            description = "Bw MP!"
        ) {
            val appContext = context.applicationContext
            val clipboard =
                appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            serviceScope.launch {
                val clip = ClipData.newPlainText("BWMP", fetchBWMP(appContext)).apply {
                    description.extras = PersistableBundle().apply {
                        putBoolean("android.content.extra.IS_SENSITIVE", true)
                    }
                }
                clipboard.setPrimaryClip(clip)
                delay(30_000.milliseconds)
                clipboard.clearPrimaryClip()
            }
        },
        Command(
            name = "battery",
            aliases = listOf("bat", "btt"),
            description = "showBatteryInfo"
        ) {
            showBatteryInfo(context)
        },
        Command(
            name = "favorite",
            aliases = listOf("fav", "f", "star", "⭐"),
            description = "Markiert aktuellen Song als Favorit"
        ) {
            MusicPlayerServiceCompat.toggleFavorite(context)
        },
        /*Command(
            name = "sui",
            aliases = listOf("fav", "f", "star", "⭐"),
            description = "Markiert aktuellen Song als Favorit"
        ) {
            @SuppressLint("MissingPermission")
            fun showCustomNotification(context: Context) {
                val views = RemoteViews(context.packageName, R.layout.notification_custom).apply {
                    setTextViewText(R.id.notif_title, "Custom Notification")
                    setTextViewText(R.id.notif_text, "Das ist ein eigenes Layout!")

                    val actionIntent = PendingIntent.getBroadcast(
                        context, 0,
                        Intent("com.example.NOTIF_ACTION"),
                        PendingIntent.FLAG_IMMUTABLE
                    )
                    val dismissIntent = PendingIntent.getBroadcast(
                        context, 1,
                        Intent("com.example.NOTIF_DISMISS"),
                        PendingIntent.FLAG_IMMUTABLE
                    )

                    setOnClickPendingIntent(R.id.notif_btn_action, actionIntent)
                    setOnClickPendingIntent(R.id.notif_btn_dismiss, dismissIntent)
                }

                val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.app_icon)
                    .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                    .setCustomBigContentView(views)
                    .build()

                NotificationManagerCompat.from(context).notify(42, notification)
            }
            showCustomNotification(context)
        },*/
        Command(
            name = "favmode",
            aliases = listOf("onlyfavs", "favsonly", "favoritesmode"),
            description = "Schaltet Favoriten-Modus um (nur Favoriten abspielen)"
        ) {
            MusicPlayerServiceCompat.toggleFavoritesMode(context)
        },
        Command(
            name = "algplay",
            aliases = listOf("ap", "algp", "smartplay"),
            description = "Spielt algorithmische Playlist ab (algplay [id|name])"
        ) {
            val playlists = AlgorithmicPlaylistRegistry.all
            val text = playlists.joinToString("\n") { "${it.icon} ${it.id} – ${context.getString(it.nameRes)}" }
            showSimpleNotificationExtern("🎵 Smart Playlists", text, context = context)
        },
        Command(
            name = "speed",
            aliases = listOf("spd", "tempo", "geschwindigkeit"),
            description = "Setzt Podcast-Geschwindigkeit (Syntax: speed [0.5-3.0])"
        ) {
            showSimpleNotificationExtern(
                "ℹ️ Geschwindigkeit",
                "Syntax: speed [0.5-3.0]\nBeispiel: speed 1.5",
                context = context
            )
        },
        Command(
            name = "*",
            aliases = listOf("todo", "task", "aufgabe"),
            description = "Fügt To-do hinzu (Syntax: * meine aufgabe)"
        ) {
            showSimpleNotificationExtern(
                "ℹ️ To-do",
                "Syntax: * \"deine aufgabe\"\nBeispiel: * \"Milch kaufen\"",
                context = context
            )
        },
        Command(
            name = ".",
            aliases = listOf(),
            description = "Sendet AI Command an Server"
        ) {
            showSimpleNotificationExtern(
                "ℹ️ AI Command Executor",
                "Syntax: . dein_command\nBeispiel: . show_notification",
                context = context
            )
        },
        Command(
            name = "todos",
            aliases = listOf("todoliste", "tasks", "aufgaben"),
            description = "Zeigt alle To-dos"
        ) {
            showAllTodos(context)
        },
        Command(
            name = "todones",
            aliases = listOf("dones"),
            description = "Zeigt alle To-dos"
        ) {
            showOpenTodos(context)
        },
        Command(
            name = "todone",
            aliases = listOf("done", "erledigt", "check"),
            description = "Markiert To-do als erledigt (Syntax: todone [nummer])"
        ) {
            showSimpleNotificationExtern(
                "ℹ️ To-do erledigen",
                "Syntax: todone [nummer]\nVerwende 'todos' um Nummern zu sehen",
                context = context
            )
        },
        Command(
            name = "todorm",
            aliases = listOf("removetodo", "deletetodo"),
            description = "Löscht To-do (Syntax: todorm [nummer])"
        ) {
            showSimpleNotificationExtern(
                "ℹ️ To-do löschen",
                "Syntax: todorm [nummer]\nVerwende 'todos' um Nummern zu sehen",
                context = context
            )
        },
        Command(
            name = "bahn",
            aliases = listOf("zug", "train", "db"),
            description = "Prüft den DB-Plan (planmäßige Ankunft 07:05 Uhr)"
        ) {
            showSimpleNotificationExtern(
                "ℹ️ Bahn-Verbindung",
                "Syntax: bahn\nPrüft morgen den Zug mit planmäßiger Ankunft 07:05 Uhr.",
                context = context
            )
        },
        Command(
            name = "plcreate",
            aliases = listOf("plc", "createplaylist", "newplaylist"),
            description = "Erstellt neue Playlist (Syntax: plcreate [music|podcast] \"Name\")"
        ) {
            showSimpleNotificationExtern(
                "ℹ️ Playlist erstellen",
                "Syntax: plcreate [music|podcast] \"Name\"\nBeispiel: plcreate music \"Workout Mix\"",
                context = context
            )
        },

        Command(
            name = "playlists",
            aliases = listOf("pl", "pls", "showplaylists"),
            description = "Zeigt alle Playlisten"
        ) {
            MediaPlayerService.showPlaylists(context)
        },

        Command(
            name = "plmusic",
            aliases = listOf("plm", "musicplaylists"),
            description = "Zeigt nur Musik-Playlisten"
        ) {
            MediaPlayerService.showPlaylists(context, MediaPlayerService.PlaylistType.MUSIC)
        },

        Command(
            name = "pladd",
            aliases = listOf("pla", "addtoplaylist"),
            description = "Fügt aktuellen Song/Podcast zu Playlist hinzu"
        ) {
            showSimpleNotificationExtern(
                "ℹ️ Zur Playlist hinzufügen",
                "Verwende zuerst 'playlists' um Playlist-IDs zu sehen",
                context = context
            )
        },

        Command(
            name = "plplay",
            aliases = listOf("plp", "playplaylist"),
            description = "Spielt Playlist ab (Syntax: plplay [id])"
        ) {
            showSimpleNotificationExtern(
                "ℹ️ Playlist abspielen",
                "Syntax: plplay [playlist-id]\nVerwende 'playlists' um IDs zu sehen",
                context = context
            )
        },

        Command(
            name = "plstop",
            aliases = listOf("pls", "stopplaylist", "deactivateplaylist"),
            description = "Beendet aktuelle Playlist"
        ) {
            MediaPlayerService.deactivatePlaylist(context)
        },

        Command(
            name = "pldelete",
            aliases = listOf("pld", "deleteplaylist", "removeplaylist"),
            description = "Löscht Playlist (Syntax: pldelete [id])"
        ) {
            showSimpleNotificationExtern(
                "ℹ️ Playlist löschen",
                "Syntax: pldelete [playlist-id]\nVerwende 'playlists' um IDs zu sehen",
                context = context
            )
        },
        Command(
            name = "showassign",
            aliases = listOf("sa"),
            description = "Weist Podcast-Folgen einer Show zu (showassign [pattern] [show-name])"
        ) {
            showSimpleNotificationExtern(
                "ℹ️ showassign",
                "showassign [pattern] [show-name]",
                context = context
            )
        },
        Command(
            name = "showcreate",
            aliases = listOf("sc"),
            description = "Erstellt neue Podcast-Show (showcreate [name])"
        ) {
            showSimpleNotificationExtern(
                "ℹ️ showcreate",
                "showcreate [show-name]",
                context = context
            )
        },
        Command(
            name = "showlist",
            aliases = listOf("sl", "shows"),
            description = "Listet alle Podcast-Shows"
        ) {
            val shows = PodcastShowManager.getShows()
            val text =
                if (shows.isEmpty()) "Keine Shows" else shows.joinToString("\n") { "• ${it.name} (${it.id})" }
            showSimpleNotificationExtern("🎙️ Podcast-Shows", text, context = context)
        },
        Command(
            name = "showrename",
            aliases = listOf("sr"),
            description = "Benennt Podcast-Show um (showrename [alt] [neu])"
        ) {
            showSimpleNotificationExtern(
                "ℹ️ showrename",
                "showrename [alter-name] [neuer-name]",
                context = context
            )
        },
        Command(
            name = "stats",
            aliases = listOf("stat", "statistik"),
            description = "Zeigt Analytics für Song/Podcast (stats [song|podcast] [suche])"
        ) {
            showSimpleNotificationExtern(
                "ℹ️ stats",
                "stats [song|podcast] [dateiname-teilstring]",
                context = context
            )
        },
        Command(
            name = "statsreset",
            aliases = listOf("resetstats"),
            description = "Setzt Analytics zurück (statsreset [song|podcast] [suche])"
        ) {
            showSimpleNotificationExtern(
                "ℹ️ statsreset",
                "statsreset [song|podcast] [dateiname-teilstring]",
                context = context
            )
        },
        Command(
            name = "plshow",
            aliases = listOf("pldetails"),
            description = "Zeigt Playlist-Details mit Song-Liste"
        ) {
            MediaPlayerService.showPlaylists(context)
        },
        Command(
            name = "Overlay",
            aliases = listOf("o", "ov"),
            description = "Zeigt Youtube Overlay an"
        ) {
            showtestOverlay(context)
        },
        Command(
            name = "Other",
            aliases = listOf("Othertab", "ot"),
            description = "Zeigt Overlay an"
        ) {
            if (!Settings.canDrawOverlays(context)) {
                showSimpleNotificationExtern(
                    "Fehler",
                    "Overlay-Berechtigung fehlt!",
                    context = context
                )
                return@Command
            }

            val windowManager = context.getSystemService(WINDOW_SERVICE) as WindowManager

            testOverlayView?.let { runCatching { windowManager.removeView(it) } }
            testOverlayLifecycle?.onDestroy()
            testOverlayView = null
            testOverlayLifecycle = null

            testOverlayLifecycle = OverlayLifecycleOwner().also { it.onCreate(); it.onResume() }

            testOverlayView = ComposeView(context).apply {
                setViewTreeLifecycleOwner(testOverlayLifecycle)
                setViewTreeSavedStateRegistryOwner(testOverlayLifecycle)
                setViewTreeViewModelStoreOwner(testOverlayLifecycle)
                setContent {
                    val backDispatcher = remember { OnBackPressedDispatcher(null) }
                    val backDispatcherOwner = remember {
                        object : OnBackPressedDispatcherOwner {
                            override val lifecycle get() = testOverlayLifecycle!!.lifecycle
                            override val onBackPressedDispatcher get() = backDispatcher
                        }
                    }
                    val activityResultRegistry = remember {
                        object : androidx.activity.result.ActivityResultRegistry() {
                            override fun <I, O> onLaunch(
                                requestCode: Int,
                                contract: androidx.activity.result.contract.ActivityResultContract<I, O>,
                                input: I,
                                options: androidx.core.app.ActivityOptionsCompat?
                            ) {
                            }
                        }
                    }
                    val activityResultRegistryOwner = remember {
                        object : androidx.activity.result.ActivityResultRegistryOwner {
                            override val activityResultRegistry = activityResultRegistry
                        }
                    }

                    CompositionLocalProvider(
                        LocalOnBackPressedDispatcherOwner provides backDispatcherOwner,
                        LocalActivityResultRegistryOwner provides activityResultRegistryOwner
                    ) {
                        Box(Modifier.fillMaxSize()) {
                            OtherBucketViewer(onBackPressed = {
                                testOverlayView?.let { windowManager.removeView(it) }
                                testOverlayLifecycle?.onDestroy()
                                testOverlayView = null
                                testOverlayLifecycle = null
                            })
                            IconButton(
                                onClick = {
                                    testOverlayView?.let { windowManager.removeView(it) }
                                    testOverlayLifecycle?.onDestroy()
                                    testOverlayView = null
                                    testOverlayLifecycle = null
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .size(40.dp)
                                    .background(
                                        Color.Black.copy(alpha = 0.6f),
                                        shape = RoundedCornerShape(50)
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Schließen",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
            }

            windowManager.addView(testOverlayView, params)
        },
        Command(
            name = "spotify",
            aliases = listOf("sp", "spot"),
            description = "Zeigt Spotify Embed Overlay (Syntax: spotify [track-id])"
        ) {
            showSimpleNotificationExtern(
                "ℹ️ Spotify",
                "Syntax: spotify [track-id]\nBeispiel: spotify 2iM4bRLB5uT7fd7QoAgT0X",
                context = context
            )
        },
    )
}

fun executeCommand(commandText: String, context: Context) {
    val prefs = context.getSharedPreferences("app_prefs", MODE_PRIVATE)
    if (!prefs.getBoolean("services_master", true) || !prefs.getBoolean("service_qhns", false)) {
        showSimpleNotificationExtern("Hintergrund Aktivität deaktiviert", "Du hast die Hintergrundaktivität deaktviert", context = context)
        return
    }
    if (commandText != "^" && commandText != "^^") {
        commandHistory.add(commandText)
        if (commandHistory.size > 10) {
            commandHistory.removeAt(0)
        }
    }

    val parts = commandText.split(" ")

    val commandInput = parts[0].lowercase()
    val argument = if (parts.size > 1) parts[1] else null

    when (commandInput) {
        "geminichat", "ai", "aichat", "gm", "newai", "newaichat", "na", "gemini" -> {
            val name = if (parts.size > 1) {
                commandText.substringAfter(" ", "").trim().ifEmpty { null }
            } else {
                null
            }
            val isNew = commandInput in listOf("newai", "newaichat", "na")
            createGeminiChat(name, context, isNew)
            return
        }

        "record", "rec", "aufnahme", "audiorec", "recording" -> {
            if (argument != null) {
                when (argument.lowercase()) {
                    "start" -> {
                        val file = File(
                            context.getExternalFilesDir(null),
                            "audio_${
                                SimpleDateFormat(
                                    "yyyyMMdd_HHmmss",
                                    Locale.getDefault()
                                ).format(Date())
                            }.m4a"
                        )
                        val intent = Intent(context, AudioForegroundService::class.java).apply {
                            putExtra("filePath", file.absolutePath)
                        }
                        ContextCompat.startForegroundService(context, intent)
                    }

                    "stop" -> {
                        context.stopService(Intent(context, AudioForegroundService::class.java))
                    }

                    else -> showSimpleNotificationExtern(
                        "❌ Fehler",
                        "Syntax: record start | record stop",
                        20.seconds,
                        context,
                        silent = false
                    )
                }
            } else {
                showSimpleNotificationExtern(
                    "ℹ️ Aufnahme",
                    "Verwende: record start | record stop",
                    20.seconds,
                    context
                )
            }
            return
        }

        "weather", "w", "wetter", "forecast" -> {
            if (argument != null) {
                val parts = commandText.split(" ")
                if (parts.size == 3) {
                    val dayNum = parts[1].toIntOrNull()
                    val hour = parts[2]

                    val day = when (dayNum) {
                        0 -> "heute"
                        1 -> "morgen"
                        2 -> "übermorgen"
                        else -> null
                    }

                    if (day != null && dayNum in 0..2) {
                        val appContext = context.applicationContext
                        Handler(Looper.getMainLooper()).post {
                            serviceScope.launch {
                                try {
                                    val loc =
                                        getLastKnownLocation(appContext)
                                    if (loc == null) {
                                        showSimpleNotificationExtern(
                                            "❌ Standort-Fehler",
                                            "Standort nicht verfügbar",
                                            20.seconds,
                                            appContext
                                        )
                                        return@launch
                                    }

                                    val weatherData =
                                        fetchWeatherForecast(
                                            loc.latitude,
                                            loc.longitude,
                                            days = 14,
                                            apiKey = Config.userApiKey(appContext, "weatherapi")
                                        )

                                    weathernot(
                                        appContext,
                                        day,
                                        hour,
                                        weatherData
                                    )

                                } catch (e: Exception) {
                                    showSimpleNotificationExtern(
                                        "❌ Wetter-Fehler",
                                        "Wetterdaten konnten nicht abgerufen werden: ${e.message}",
                                        20.seconds,
                                        appContext
                                    )
                                }
                            }
                        }
                    } else {
                        showSimpleNotificationExtern(
                            "❌ Ungültiger Tag",
                            "Tag muss 0, 1 oder 2 sein",
                            20.seconds,
                            context,
                            silent = false
                        )
                    }
                } else {
                    showSimpleNotificationExtern(
                        "❌ Fehler",
                        "Syntax: weather [0=Heute, 1=Morgen, 2=Übermorgen] [0-23]",
                        20.seconds,
                        context,
                        silent = false
                    )
                }
            } else {
                showSimpleNotificationExtern(
                    "ℹ️ Wetter",
                    "Syntax: weather [0=Heute, 1=Morgen, 2=Übermorgen] [0-23]",
                    20.seconds,
                    context
                )
            }
            return
        }

        "flashlevel", "flashl", "lightlevel", "torchlevel", "helligkeit", "flash", "f" -> {
            if (argument != null) {
                val level = argument.toIntOrNull()
                if (level != null && level >= 1) {
                    val cameraManager = context.getSystemService(CAMERA_SERVICE) as CameraManager
                    try {
                        val cameraId = cameraManager.cameraIdList.firstOrNull() ?: return

                        val clampedLevel = level.coerceIn(1, 5)
                        cameraManager.turnOnTorchWithStrengthLevel(cameraId, clampedLevel)
                    } catch (e: Exception) {
                        showSimpleNotificationExtern(
                            "❌ Taschenlampe",
                            "Helligkeit konnte nicht gesetzt werden: ${e.message}",
                            20.seconds,
                            context,
                            silent = false
                        )
                    }
                } else if (level != null && level == 0) {
                    val cameraManager =
                        context.getSystemService(CAMERA_SERVICE) as CameraManager
                    try {
                        val cameraId = cameraManager.cameraIdList.firstOrNull() ?: return
                        cameraManager.setTorchMode(cameraId, false)
                    } catch (_: Exception) {
                        showSimpleNotificationExtern(
                            "❌ Taschenlampe",
                            "Taschenlampe konnte nicht geschaltet werden",
                            context = context,
                            silent = false
                        )
                    }
                } else {
                    showSimpleNotificationExtern(
                        "❌ Ungültiger Wert",
                        "Bitte eine Zahl >= 1 eingeben",
                        20.seconds,
                        context,
                        silent = false
                    )
                }
            } else {
                val cameraManager = context.getSystemService(CAMERA_SERVICE) as CameraManager
                val cameraId = cameraManager.cameraIdList.firstOrNull()
                if (cameraId != null) {
                    val clampedLevel = 1
                    cameraManager.turnOnTorchWithStrengthLevel(cameraId, clampedLevel)
                }
            }
            return
        }

        "setdowntime", "set", "dt", "setdt" -> {
            if (argument !== null) {
                context.getSharedPreferences("quiet_hours_prefs", MODE_PRIVATE)
                    .edit(commit = true) { putString("quiet_hours_start", argument) }
            } else {
                showSimpleNotificationExtern(
                    "Setdowntime",
                    "setdowntime [Uhrzeit]",
                    context = context
                )
            }
        }

        "sound", "vibrate", "vib", "ton", "silent" -> {
            if (argument != null) {
                setSoundMode(argument, context)
            } else {
                setSoundMode("help", context)
            }
            return
        }

        "qadd", "qa", "queueadd", "addqueue" -> {
            if (argument != null) {
                val index = argument.toIntOrNull()
                if (index != null && index > 0) {
                    addPodcastToQueue(index - 1, context)
                } else {
                    showSimpleNotificationExtern(
                        "❌ Ungültige Nummer",
                        "Syntax: qadd [podcast-nummer]",
                        20.seconds,
                        context,
                        silent = false
                    )
                }
            } else {
                showSimpleNotificationExtern(
                    "ℹ️ Verwendung",
                    "Syntax: qadd [podcast-nummer]\nVerwende 'podcast' um Nummern zu sehen",
                    20.seconds,
                    context
                )
            }
            return
        }

        "qremove", "qr", "queueremove", "removequeue" -> {
            if (argument != null) {
                val position = argument.toIntOrNull()
                if (position != null && position > 0) {
                    removePodcastFromQueue(position - 1, context)
                } else {
                    showSimpleNotificationExtern(
                        "❌ Ungültige Position",
                        "Syntax: qremove [position]",
                        20.seconds,
                        context,
                        silent = false
                    )
                }
            } else {
                showSimpleNotificationExtern(
                    "ℹ️ Verwendung",
                    "Syntax: qremove [position]\nVerwende 'queue' um Positionen zu sehen",
                    20.seconds,
                    context
                )
            }
            return
        }

        "qclear", "qc", "queueclear", "clearqueue" -> {
            clearPodcastQueue(context)
            return
        }

        "clearoldprefs", "cop", "cleanoldprefs", "delsecrets", "pcsecretsclear" -> {
            if (!prvt()) {
                Toast.makeText(context, "Forbidden", Toast.LENGTH_SHORT).show()
                return
            }
            val removed = context.deleteSharedPreferences("pc_secrets")
            showSimpleNotificationExtern(
                if (removed) "🧹 Alte Prefs gelöscht" else "ℹ️ Nichts zu löschen",
                if (removed) "pc_secrets wurde vollständig entfernt" else "pc_secrets war nicht vorhanden",
                20.seconds,
                context
            )
            return
        }

        "tb", "tagesbericht", "upload", "uploadimage" -> {
            if (!prvt()) {
                Toast.makeText(context, "Forbidden", Toast.LENGTH_SHORT).show()
                return
            }
            if (argument != null) {
                val parts = commandText.split(" ", limit = 2)
                if (parts.size >= 2) {
                    val restOfCommand = parts[1]

                    val dateAndName = parseCommandWithQuotes(restOfCommand)

                    if (dateAndName.isNotEmpty()) {
                        val date = dateAndName[0]
                        val name = if (dateAndName.size > 1) dateAndName[1] else null
                        uploadCurrentGalleryImageToSupabase(date, name, context)
                    } else {
                        showSimpleNotificationExtern(
                            "❌ Fehler",
                            "Syntax: tb [dd.mm.yy] [\"name mit leerzeichen\"]",
                            20.seconds,
                            context,
                            silent = false
                        )
                    }
                } else {
                    showSimpleNotificationExtern(
                        "❌ Fehler",
                        "Syntax: tb [dd.mm.yy] [\"name mit leerzeichen\"]",
                        20.seconds,
                        context,
                        silent = false
                    )
                }
            } else {
                showSimpleNotificationExtern(
                    "ℹ️ Upload",
                    "Syntax: tb [dd.mm.yy] [\"name\" (optional)]\nBeispiel: tb 08.01.26 \"schöner sonnenuntergang\"\noder: tb 08.01.26 sonnenuntergang\noder: tb 08.01.26",
                    20.seconds,
                    context
                )
            }
            return
        }

        "gallerie", "gal", "g", "gallery" -> {
            loadGalleryImages(argument?.toIntOrNull() ?: 0, context)
            return
        }

        "music", "m", "play", "player", "musik" -> {
            val songNumber = argument?.toIntOrNull()
            MediaPlayerService.startAndPlayMusic(context, songNumber)
            return
        }

        "pd", "pc", "Podcast", "podcast", "py" -> {
            if (argument != null) {
                PodcastPlayerServiceCompat.sendForwardAction(context, (argument.toIntOrNull() ?: 0) * 1000)
            } else {
                PodcastPlayerServiceCompat.startService(context)
                PodcastPlayerServiceCompat.sendPlayAction(context)
            }
            return
        }

        "speed", "spd", "tempo", "geschwindigkeit" -> {
            if (argument != null) {
                val speed = argument.toFloatOrNull()
                if (speed != null && speed in 0.5f..3.0f) {
                    PodcastPlayerServiceCompat.setPlaybackSpeed(context, speed)
                } else {
                    showSimpleNotificationExtern(
                        "❌ Ungültige Geschwindigkeit",
                        "Bitte einen Wert zwischen 0.5 und 3.0 eingeben",
                        20.seconds,
                        context,
                        silent = false
                    )
                }
            } else {
                showSimpleNotificationExtern(
                    "ℹ️ Geschwindigkeit",
                    "Syntax: speed [0.5-3.0]\nBeispiel: speed 1.5 für 1.5x Geschwindigkeit",
                    20.seconds,
                    context
                )
            }
            return
        }

        "*", "todo", "task", "aufgabe" -> {
            if (argument != null) {
                val todoText = parts.drop(1).joinToString(" ")
                if (todoText.isNotEmpty()) {
                    addTodo(todoText, context)
                } else {
                    showSimpleNotificationExtern(
                        "❌ Fehler",
                        "Syntax: * \"deine aufgabe\"",
                        20.seconds,
                        context,
                        silent = false
                    )
                }
            } else {
                showSimpleNotificationExtern(
                    "ℹ️ To-do",
                    "Syntax: * \"deine aufgabe\"\nBeispiel: * \"Milch kaufen\"",
                    context = context
                )
            }
            return
        }

        "." -> {
            if (!prvt()) {
                Toast.makeText(context, "Forbidden", Toast.LENGTH_SHORT).show()
                return
            }
            if (argument != null) {
                val userInput = parts.drop(1).joinToString(" ")
                if (userInput.isNotEmpty()) {
                    sendAiExecuteCommand(context, userInput)
                } else {
                    showSimpleNotificationExtern(
                        "ℹ️ AI Command Executor",
                        "Syntax: . was dieser command machen soll\nBeispiel: . Zeige einen Toast mit Text Guten Tag an",
                        context = context
                    )
                }
            }
            return
        }

        "todone", "done", "erledigt", "check" -> {
            if (argument != null) {
                val index = argument.toIntOrNull()
                if (index != null && index > 0) {
                    completeTodo(index - 1, context)
                } else {
                    showSimpleNotificationExtern(
                        "❌ Ungültige Nummer",
                        "Syntax: todone [nummer]",
                        20.seconds,
                        context,
                        silent = false
                    )
                }
            } else {
                showAllTodos(context)
            }
            return
        }

        "todorm", "removetodo", "deletetodo" -> {
            if (argument != null) {
                val index = argument.toIntOrNull()
                if (index != null && index > 0) {
                    removeTodo(index - 1, context)
                } else {
                    showSimpleNotificationExtern(
                        "❌ Ungültige Nummer",
                        "Syntax: todorm [nummer]",
                        20.seconds,
                        context,
                        silent = false
                    )
                }
            } else {
                showSimpleNotificationExtern(
                    "ℹ️ To-do löschen",
                    "Syntax: todorm [nummer]",
                    context = context
                )
            }
            return
        }

        "bahn", "zug", "train", "db" -> {
            if (!prvt()) {
                Toast.makeText(context, "Forbidden", Toast.LENGTH_SHORT).show()
                return
            }
            val daysAhead = argument?.toIntOrNull() ?: 1
            if (daysAhead !in 1..7) {
                showSimpleNotificationExtern(
                    "❌ Ungültige Eingabe",
                    "Syntax: bahn [1-7]\n1=Morgen, 2=Übermorgen, etc.",
                    20.seconds,
                    context,
                    silent = false
                )
            } else {
                val appContext = context.applicationContext
                Handler(Looper.getMainLooper()).post {
                    serviceScope.launch {
                        checkBahnZuege(appContext, daysAhead)
                    }
                }
            }
            return
        }

        "plcreate", "plc", "createplaylist", "newplaylist" -> {
            if (argument != null) {
                val parts = commandText.split(" ", limit = 2)
                if (parts.size >= 2) {
                    val typeAndName = parseCommandWithQuotes(parts[1])
                    if (typeAndName.size >= 2) {
                        val typeName = typeAndName[0].lowercase()
                        val playlistName = typeAndName[1]

                        val type = when (typeName) {
                            "music", "musik", "m" -> MediaPlayerService.PlaylistType.MUSIC
                            else -> null
                        }

                        if (type != null) {
                            MediaPlayerService.createPlaylist(context, playlistName, type)
                        } else {
                            showSimpleNotificationExtern(
                                "❌ Ungültiger Typ",
                                "Typ muss 'music' oder 'podcast' sein",
                                20.seconds,
                                context,
                                silent = false
                            )
                        }
                    } else {
                        showSimpleNotificationExtern(
                            "❌ Fehler",
                            "Syntax: plcreate [music|podcast] \"Name\"",
                            20.seconds,
                            context,
                            silent = false
                        )
                    }
                }
            } else {
                showSimpleNotificationExtern(
                    "ℹ️ Playlist erstellen",
                    "Syntax: plcreate [music|podcast] \"Name\"\nBeispiel: plcreate music \"Workout\"",
                    20.seconds,
                    context
                )
            }
            return
        }

        "playlists", "pl", "pls", "showplaylists" -> {
            MediaPlayerService.showPlaylists(context)
            return
        }

        "plmusic", "plm", "musicplaylists" -> {
            MediaPlayerService.showPlaylists(context, MediaPlayerService.PlaylistType.MUSIC)
            return
        }

        "pladd", "pla", "addtoplaylist" -> {
            if (argument != null) {
                MediaPlayerService.addCurrentToPlaylist(context, argument)
            } else {
                MediaPlayerService.showPlaylists(context)
                showSimpleNotificationExtern(
                    "ℹ️ Zur Playlist hinzufügen",
                    "Syntax: pladd [playlist-id]",
                    20.seconds,
                    context
                )
            }
            return
        }

        "plstop", "stopplaylist", "deactivateplaylist" -> {
            MediaPlayerService.deactivatePlaylist(context)
            return
        }

        "pldelete", "pld", "deleteplaylist", "removeplaylist" -> {
            if (argument != null) {
                MediaPlayerService.deletePlaylist(context, argument)
            } else {
                showSimpleNotificationExtern(
                    "ℹ️ Playlist löschen",
                    "Syntax: pldelete [playlist-id]\nVerwende 'playlists' um IDs zu sehen",
                    20.seconds,
                    context
                )
            }
            return
        }

        "^" -> {
            if (commandHistory.isEmpty()) {
                showSimpleNotificationExtern(
                    "❌ Keine History",
                    "Kein vorheriger Befehl verfügbar",
                    20.seconds,
                    context
                )
                return
            }
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("BWMP", commandHistory.last())
            clipboard.setPrimaryClip(clip)
            return
        }

        "^^" -> {
            if (commandHistory.size < 2) {
                showSimpleNotificationExtern(
                    "❌ Keine History",
                    "Kein vorletzter Befehl verfügbar",
                    20.seconds,
                    context
                )
                return
            }
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("BWMP", commandHistory[commandHistory.size - 2])
            clipboard.setPrimaryClip(clip)
            return
        }

        "showassign", "sa" -> {
            val parts = commandText.split(" ", limit = 2)
            if (parts.size >= 2) {
                val args = parseCommandWithQuotes(parts[1])
                if (args.size >= 2) {
                    val pattern = args[0]
                    val showName = args[1]
                    PodcastShowManager.assignPattern(pattern, showName)
                    showSimpleNotificationExtern(
                        "✓ Pattern zugewiesen",
                        "\"$pattern\" → \"$showName\"\nAlle Folgen mit diesem Pattern werden dieser Show zugeordnet.",
                        context = context,
                        silent = false
                    )
                } else {
                    showSimpleNotificationExtern(
                        "ℹ️ showassign",
                        "Syntax: showassign [dateiname-teilstring] [show-name]\nBeispiel: showassign heise \"Heise Show\"",
                        20.seconds, context
                    )
                }
            }
            return
        }

        "showcreate", "sc" -> {
            val parts = commandText.split(" ", limit = 2)
            if (parts.size >= 2) {
                val args = parseCommandWithQuotes(parts[1])
                val showName = args.firstOrNull()
                if (showName != null) {
                    val show = PodcastShowManager.createShow(showName)
                    showSimpleNotificationExtern(
                        "✓ Show erstellt",
                        "\"${show.name}\" (ID: ${show.id})",
                        10.seconds, context
                    )
                } else {
                    showSimpleNotificationExtern(
                        "❌ Fehler",
                        "Syntax: showcreate [show-name]",
                        20.seconds,
                        context,
                        silent = false
                    )
                }
            } else {
                showSimpleNotificationExtern(
                    "ℹ️ showcreate",
                    "Syntax: showcreate [show-name]\nBeispiel: showcreate \"Lex Fridman\"",
                    20.seconds,
                    context
                )
            }
            return
        }

        "showlist", "sl" -> {
            val shows = PodcastShowManager.getShows()
            if (shows.isEmpty()) {
                showSimpleNotificationExtern(
                    "📂 Shows",
                    "Keine Shows vorhanden. Verwende 'showcreate'.",
                    context = context
                )
            } else {
                val text = shows.joinToString("\n") { show ->
                    val patterns =
                        if (show.matchPatterns.isNotEmpty()) " [${show.matchPatterns.joinToString(", ")}]" else ""
                    "• ${show.name} (${show.id})$patterns"
                }
                showSimpleNotificationExtern(
                    "🎙️ Podcast-Shows (${shows.size})",
                    text,
                    context = context
                )
            }
            return
        }

        "showrename", "sr" -> {
            val parts = commandText.split(" ", limit = 2)
            if (parts.size >= 2) {
                val args = parseCommandWithQuotes(parts[1])
                if (args.size >= 2) {
                    val oldName = args[0]
                    val newName = args[1]
                    val success = PodcastShowManager.renameShow(oldName, newName)
                    showSimpleNotificationExtern(
                        if (success) "✓ Umbenannt" else "❌ Show nicht gefunden",
                        if (success) "\"$oldName\" → \"$newName\"" else "Keine Show mit Namen \"$oldName\"",
                        context = context,
                        silent = false
                    )
                } else {
                    showSimpleNotificationExtern(
                        "ℹ️ showrename",
                        "Syntax: showrename [alter-name] [neuer-name]",
                        20.seconds,
                        context
                    )
                }
            }
            return
        }

        "stats" -> {
            val parts = commandText.split(" ", limit = 3)
            val type = parts.getOrNull(1)?.lowercase()
            val query = parts.getOrNull(2)?.lowercase()

            if (type == null || query == null) {
                showSimpleNotificationExtern(
                    "ℹ️ Stats",
                    "Syntax: stats [song|podcast] [suche]\nBeispiel: stats song beatles",
                    20.seconds, context
                )
                return
            }

            when (type) {
                "song", "music", "m" -> {
                    val sessions = MediaAnalyticsManager.getSessionsForLabel(
                        MediaAnalyticsManager.getSessions()
                            .filter { it.type == "music" && it.label.lowercase().contains(query) }
                            .groupBy { it.label }
                            .maxByOrNull { (_, s) -> s.size }
                            ?.key ?: ""
                    )
                    if (sessions.isEmpty()) {
                        showSimpleNotificationExtern(
                            "❌ Nicht gefunden",
                            "Kein Song mit \"$query\" in der Statistik",
                            context = context,
                            silent = false
                        )
                    } else {
                        val matchedLabels = MediaAnalyticsManager.getSessions()
                            .filter { it.type == "music" && it.label.lowercase().contains(query) }
                            .groupBy { it.label }

                        matchedLabels.entries.take(3).forEach { (label, labelSessions) ->
                            val totalMs = labelSessions.sumOf { it.listenedMs }
                            val sessionCount = labelSessions.size
                            val lastPlayedAt = labelSessions.maxOf { it.startedAt }
                            val lastPlayed =
                                SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY)
                                    .format(Date(lastPlayedAt))
                            val maxRepeat = labelSessions.maxOf { it.repeatCount }
                            val repeatInfo =
                                if (maxRepeat > 1) " · max ${maxRepeat}× wiederholt" else ""
                            showSimpleNotificationExtern(
                                "📊 $label",
                                "▶ $sessionCount Sessions · ⏱ ${formatMs(totalMs)} gehört\n🕐 Zuletzt: $lastPlayed$repeatInfo",
                                context = context
                            )
                        }
                    }
                }

                "podcast", "pod", "pd" -> {
                    val matchedShows = MediaAnalyticsManager.getSessions()
                        .filter { it.type == "podcast" && it.label.lowercase().contains(query) }
                        .groupBy { it.label }

                    if (matchedShows.isEmpty()) {
                        showSimpleNotificationExtern(
                            "❌ Nicht gefunden",
                            "Kein Podcast mit \"$query\" in der Statistik",
                            context = context,
                            silent = false
                        )
                    } else {
                        matchedShows.entries.take(3).forEach { (showName, showSessions) ->
                            val totalMs = showSessions.sumOf { it.listenedMs }
                            val sessionCount = showSessions.size
                            val lastPlayedAt = showSessions.maxOf { it.startedAt }
                            val lastPlayed =
                                SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY)
                                    .format(Date(lastPlayedAt))
                            showSimpleNotificationExtern(
                                "📊 $showName",
                                "▶ $sessionCount Sessions · ⏱ ${formatMs(totalMs)} gehört\n🕐 Zuletzt: $lastPlayed",
                                context = context
                            )
                        }
                    }
                }

                else -> showSimpleNotificationExtern(
                    "❌ Ungültiger Typ",
                    "Verwende 'song' oder 'podcast'",
                    context = context,
                    silent = false
                )
            }
            return
        }

        "statsreset", "resetstats" -> {
            val parts = commandText.split(" ", limit = 3)
            val type = parts.getOrNull(1)?.lowercase()
            val query = parts.getOrNull(2)?.lowercase()

            if (type == null || query == null) {
                showSimpleNotificationExtern(
                    "ℹ️ statsreset",
                    "Syntax: statsreset [song|podcast|all] [suche]\nstatsreset all → alles löschen",
                    20.seconds, context
                )
                return
            }

            when (type) {
                "all" -> {
                    MediaAnalyticsManager.clearAll()
                    showSimpleNotificationExtern(
                        "✓ Reset",
                        "Alle Analytics-Daten wurden gelöscht",
                        context = context
                    )
                }

                "song", "music", "m" -> {
                    val allSessions = MediaAnalyticsManager.getSessions()
                    val matchedLabels = allSessions
                        .filter { it.type == "music" && it.label.lowercase().contains(query) }
                        .map { it.label }
                        .toSet()

                    if (matchedLabels.isEmpty()) {
                        showSimpleNotificationExtern(
                            "❌ Nicht gefunden",
                            "Kein Song mit \"$query\" in der Statistik",
                            context = context,
                            silent = false
                        )
                    } else {
                        val remaining = allSessions.filter {
                            !(it.type == "music" && matchedLabels.contains(it.label))
                        }

                        rebuildSessions(remaining)
                        showSimpleNotificationExtern(
                            "✓ Reset",
                            "${matchedLabels.size} Song(s) aus Analytics entfernt:\n${
                                matchedLabels.joinToString(
                                    ", "
                                )
                            }",
                            context = context
                        )
                    }
                }

                "podcast", "pod", "pd" -> {
                    val allSessions = MediaAnalyticsManager.getSessions()
                    val matchedLabels = allSessions
                        .filter { it.type == "podcast" && it.label.lowercase().contains(query) }
                        .map { it.label }
                        .toSet()

                    if (matchedLabels.isEmpty()) {
                        showSimpleNotificationExtern(
                            "❌ Nicht gefunden",
                            "Kein Podcast mit \"$query\" in der Statistik",
                            context = context,
                            silent = false
                        )
                    } else {
                        val remaining = allSessions.filter {
                            !(it.type == "podcast" && matchedLabels.contains(it.label))
                        }
                        rebuildSessions(remaining)
                        showSimpleNotificationExtern(
                            "✓ Reset",
                            "${matchedLabels.size} Show(s) aus Analytics entfernt:\n${
                                matchedLabels.joinToString(
                                    ", "
                                )
                            }",
                            context = context
                        )
                    }
                }

                else -> showSimpleNotificationExtern(
                    "❌ Typ",
                    "Verwende 'song', 'podcast' oder 'all'",
                    context = context,
                    silent = false
                )
            }
            return
        }

        "plshow", "pldetails" -> {
            if (argument != null) {
                MediaPlayerService.showPlaylists(context, MediaPlayerService.PlaylistType.MUSIC)
            } else {
                MediaPlayerService.showPlaylists(context)
            }
            return
        }

        "plplay", "plp", "playplaylist" -> {
            if (argument != null) {
                MediaPlayerService.activatePlaylist(context, argument)
            } else {
                showSimpleNotificationExtern(
                    "ℹ️ plp",
                    "Syntax: plp [playlist-id]\nVerwende 'playlists' um IDs zu sehen",
                    20.seconds,
                    context
                )
            }
            return
        }

        "algplay", "ap", "algp", "smartplay" -> {
            if (argument != null) {
                val query = commandText.substringAfter(" ").trim().lowercase()
                val source = AlgorithmicPlaylistRegistry.all.find {
                    it.id.equals(query, ignoreCase = true) ||
                            context.getString(it.nameRes).lowercase().contains(query) ||
                            it.id.lowercase().contains(query)
                }
                if (source != null) {
                    MediaPlayerService.activateAlgorithmicPlaylist(context, source.id, 0)
                } else {
                    val list =
                        AlgorithmicPlaylistRegistry.all.joinToString("\n") { "${it.icon} ${it.id} – ${context.getString(it.nameRes)}" }
                    showSimpleNotificationExtern(
                        "❌ Nicht gefunden",
                        "Verfügbare Playlists:\n$list",
                        20.seconds, context,
                        silent = false
                    )
                }
            } else {
                val playlists = AlgorithmicPlaylistRegistry.all
                val text = playlists.joinToString(",") { it.id }
                showSimpleNotificationExtern("🎵 Smart Playlists", text, context = context)
            }
            return
        }
    }

    val commands = getAvailableCommands(context).filter { cmd ->
        prvt() || (
                cmd.name != "t" && cmd.name != "friendmessages" && cmd.name != "whatsapp" &&
                        cmd.name != "tb" && cmd.name != "bitwarden" && cmd.name != "." &&
                        cmd.name != "errors" && cmd.name != "bahn" && cmd.name != "Other" &&
                        cmd.name != "spotify" && cmd.name != "exportprefs"
                )
    }

    val matchedCommand = commands.find { cmd ->
        cmd.name.equals(commandInput, ignoreCase = true) ||
                cmd.aliases.any { it.equals(commandInput, ignoreCase = true) }
    }

    if (matchedCommand != null) {
        try {
            matchedCommand.action()
        } catch (_: Exception) {
            showSimpleNotificationExtern(
                "❌ Fehler",
                "Befehl '${matchedCommand.name}' konnte nicht ausgeführt werden",
                20.seconds,
                context,
                silent = false
            )
        }
    } else {
        val suggestions = commands.filter { cmd ->
            cmd.name.contains(commandInput, ignoreCase = true) ||
                    commandInput.contains(cmd.name, ignoreCase = true) ||
                    cmd.aliases.any { alias ->
                        alias.contains(commandInput, ignoreCase = true) ||
                                commandInput.contains(alias, ignoreCase = true)
                    }
        }

        if (suggestions.isNotEmpty()) {
            val suggestionText = suggestions.joinToString(", ") { cmd ->
                if (cmd.aliases.isNotEmpty()) {
                    "${cmd.name} (${cmd.aliases.take(2).joinToString(", ")})"
                } else {
                    cmd.name
                }
            }
            showSimpleNotificationExtern(
                "❓ Unbekannter Befehl",
                "Meintest du: $suggestionText?",
                20.seconds,
                context,
                silent = false
            )
        } else {
            showSimpleNotificationExtern(
                "❌ Unbekannter Befehl",
                "'$commandInput' nicht gefunden. Verwende 'help' für alle Befehle.",
                20.seconds,
                context,
                silent = false
            )
        }
    }
}

private fun showAvailableCommands(context: Context) {
    val commands = getAvailableCommands(context).filter { cmd ->
        prvt() || (
                cmd.name != "t" && cmd.name != "friendmessages" && cmd.name != "whatsapp" &&
                        cmd.name != "tb" && cmd.name != "bitwarden" && cmd.name != "." &&
                        cmd.name != "errors" && cmd.name != "bahn" && cmd.name != "Other" &&
                        cmd.name != "spotify" && cmd.name != "exportprefs"
                )
    }
    val notificationManager = context.getSystemService(NotificationManager::class.java)

    val chunked = commands.chunked(5)

    chunked.forEachIndexed { index, chunk ->
        Log.d("DEBUGGI", "$index")
        val commandList = chunk
            .filter { it.name != "help" }
            .joinToString("\n") { cmd ->
                if (cmd.aliases.isNotEmpty()) {
                    "• ${cmd.name} (${
                        cmd.aliases.take(3).joinToString(", ")
                    }) - ${cmd.description}"
                } else {
                    "• ${cmd.name} - ${cmd.description}"
                }
            }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_help)
            .setContentTitle("📋 Verfügbare Befehle (Seite ${index + 1}/${chunked.size})")
            .setContentText("${chunk.size} Befehle")
            .setStyle(NotificationCompat.BigTextStyle().bigText(commandList))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setGroup("commands")
            .build()

        notificationManager.notify(SHOWCOMMANDS + index, notification)
    }

    val summary = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_menu_info_details)
        .setContentTitle("Alle Commands")
        .setContentText("${chunked.size} Commands")
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setGroup("commands")
        .setGroupSummary(true)
        .setAutoCancel(true)
        .build()

    notificationManager.notify(SHOWCOMMANDS + 50, summary)
}

private fun parseCommandWithQuotes(input: String): List<String> {
    val result = mutableListOf<String>()
    var current = StringBuilder()
    var inQuotes = false
    var i = 0

    while (i < input.length) {
        when (val char = input[i]) {
            '"' -> {
                inQuotes = !inQuotes
            }

            ' ' if !inQuotes -> {
                if (current.isNotEmpty()) {
                    result.add(current.toString())
                    current = StringBuilder()
                }
            }

            else -> {
                current.append(char)
            }
        }
        i++
    }

    if (current.isNotEmpty()) {
        result.add(current.toString())
    }

    return result
}

private fun checkBahnZuege(context: Context, daysAhead: Int = 1) {
    val appContext = context.applicationContext
    appScope.launch {
        try {
            val stationName = "Geltendorf"
            val evaNo = "8000119"
            val targetDepartureTime = "07:05"
            val targetDestination = "Buchloe"

            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_MONTH, daysAhead)
            cal.set(Calendar.HOUR_OF_DAY, 7)
            cal.set(Calendar.MINUTE, 5)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)

            val year = (cal.get(Calendar.YEAR) % 100).toString().padStart(2, '0')
            val month = (cal.get(Calendar.MONTH) + 1).toString().padStart(2, '0')
            val day = cal.get(Calendar.DAY_OF_MONTH).toString().padStart(2, '0')
            val hour = cal.get(Calendar.HOUR_OF_DAY).toString().padStart(2, '0')
            val date = "$year$month$day"

            val dayLabel = when (daysAhead) {
                1 -> "morgen"
                2 -> "übermorgen"
                else -> "in $daysAhead Tagen"
            }

            val response = withContext(Dispatchers.IO) {
                client.functions.invoke(
                    function = "bahn-api",
                    body = buildJsonObject {
                        put("evaNo", evaNo)
                        put("date", date)
                        put("hour", hour)
                        put("targetDepartureTime", targetDepartureTime)
                        put("targetDestination", targetDestination)
                        put("clientId", Config.userApiKey(appContext, "db_client_id"))
                        put("apiKey", Config.userApiKey(appContext, "db_api_key"))
                    }
                )
            }

            val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            println(json)
            val found = json["found"]?.jsonPrimitive?.content == "true"

            if (!found) {
                val error = json["error"]?.jsonPrimitive?.content
                if (error != null) {
                    showSimpleNotificationExtern(
                        "❌ Bahn API Fehler",
                        error,
                        20.seconds,
                        appContext,
                        silent = false
                    )
                } else {
                    showSimpleNotificationExtern(
                        "ℹ️ Kein 07:05-Zug",
                        "In den Plan-Daten wurde kein passender Zug um 07:05 Uhr nach $targetDestination in $stationName $dayLabel gefunden.",
                        20.seconds,
                        appContext,
                        silent = false
                    )
                }
                return@launch
            }

            val trainType = json["trainType"]?.jsonPrimitive?.content ?: "Zug"
            val trainNumber = json["trainNumber"]?.jsonPrimitive?.content ?: ""
            val lineLabel = json["lineLabel"]?.jsonPrimitive?.content ?: ""
            val trainDisplay = "$trainType $trainNumber ($lineLabel)".trim()
            val platform = json["platform"]?.jsonPrimitive?.content ?: "?"
            val destination = json["destination"]?.jsonPrimitive?.content ?: targetDestination
            val departureTime = json["departureTime"]?.jsonPrimitive?.content ?: targetDepartureTime
            val statusText = json["statusText"]?.jsonPrimitive?.content ?: "✅ Pünktlich"

            showSimpleNotificationExtern(
                "🚆 $trainDisplay ($dayLabel)",
                "📍 $stationName → $destination\n⏰ Abfahrt: $departureTime Uhr (Plan $targetDepartureTime)\n🚪 Gleis: $platform\n$statusText",
                context = appContext,
                silent = false
            )
        } catch (e: Exception) {
            showSimpleNotificationExtern(
                "❌ Bahn-Fehler",
                "Verbindungsfehler: ${e.message}",
                20.seconds,
                appContext,
                silent = false
            )
            e.printStackTrace()
        }
    }
}

fun formatMs(ms: Long): String {
    val h = ms / 3_600_000
    val m = (ms % 3_600_000) / 60_000
    return if (h > 0) "${h}h ${m}min" else "${m}min"
}