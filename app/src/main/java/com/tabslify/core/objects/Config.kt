package com.tabslify.core.objects

import android.Manifest
import android.annotation.SuppressLint
import android.app.LocaleManager
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.LocaleList
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import com.tabslify.BuildConfig
import com.tabslify.R
import com.tabslify.core.functions.canNotify
import com.tabslify.core.ui.MenuItem
import com.tabslify.core.ui.getDeviceName
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.websocket.WebSockets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.time.Duration.Companion.seconds

object Config {
    const val SUPABASE_URL = BuildConfig.SUPABASE_URL
    const val SUPABASE_PUBLISHABLE_KEY = BuildConfig.SUPABASE_PUBLISHABLE_KEY

    @OptIn(SupabaseInternal::class)
    val client = createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_PUBLISHABLE_KEY
    ) {
        install(Storage)
        install(Postgrest)
        install(Realtime)
        install(Functions)
        install(Auth)

        httpConfig {
            install(WebSockets)
            install(HttpTimeout) {
                requestTimeoutMillis = 30000
                connectTimeoutMillis = 30000
                socketTimeoutMillis = 30000
            }
        }
        httpEngine = OkHttp.create {
            requestTimeout = 60.seconds
        }
    }

    const val DEF_GEMINI = "gemini-2.5-flash"
    const val MAX_GEMINI = "gemini-3-flash-preview"

    const val SUPABASE_BUCKET = "Files"

    val helpFrameEntries: Map<MenuItem, Int> = mapOf(
        MenuItem.PRIVATE_CLOUD to R.string.so_bedienst_du_die_private,
        MenuItem.AITAB to R.string.so_bedienst_du_den_ki,
        MenuItem.BROWSER to R.string.so_bedienst_du_den_browser,
        MenuItem.QUICK to R.string.so_bedienst_du_die_schnellubersicht,
        MenuItem.GALLERY to R.string.so_bedienst_du_die_galerie,
        MenuItem.AUTHENTICATOR to R.string.so_bedienst_du_den_passwort,
        MenuItem.WEATHER to R.string.so_bedienst_du_das_wetter,
        MenuItem.CONTACTS to R.string.so_bedienst_du_die_kontakte,
        MenuItem.RECORDER to R.string.so_bedienst_du_den_rekorder,
        MenuItem.DATECALCULATOR to R.string.so_bedienst_du_den_datumsrechner,
        MenuItem.MOVIEDISCOVER to R.string.so_bedienst_du_die_film,
        MenuItem.NOTES to R.string.so_bedienst_du_die_notizen,
        MenuItem.MEDIAPLAYERTAB to R.string.so_bedienst_du_den_media,
        MenuItem.GMAIL to R.string.so_bedienst_du_die_e,
        MenuItem.Vocabs to R.string.so_bedienst_du_das_vokabeltraining,
        MenuItem.EXPLORE to R.string.so_funktioniert_die_karte_1,
        MenuItem.CALENDAR to R.string.so_bedienst_du_den_kalender,
        MenuItem.REMOTEDESKTOP to R.string.so_bedienst_du_die_fernsteuerung,
        MenuItem.PODCAST to R.string.so_bedienst_du_die_podcasts,
        MenuItem.HEISE_NEWS to R.string.so_bedienst_du_die_news,
        MenuItem.PC_MANAGER to R.string.so_bedienst_du_den_pc,
        MenuItem.APKM_INSTALLER to R.string.so_bedienst_du_den_apkm,
        MenuItem.PUSHUPS to R.string.so_bedienst_du_den_pushup,
        MenuItem.FOCUSGUARD to R.string.so_bedienst_du_den_focusguard
    )

    var LAT: Double = 0.0
    var LON: Double = 0.0

    const val SYNC_PORT = 8888
    const val NOTIFICATION_PORT = 8889
    const val UPDATE_PORT = 8890
    const val CLIPBOARD_PORT = 8891
    const val TRIGGER_PORT = 8893
    const val SESSION_PORT = 8894
    const val AI_RECEIVE_PORT = 8895
    const val FLASHCARD_SEND_PORT = 8896
    const val FLASHCARD_RECEIVE_PORT = 8897

    const val IMAGE_SHARE_PORT = 8898
    const val MEDIA_COMMAND_PORT = 8899
    const val MEDIA_STATE_PORT = 8900
    const val AI_PORT = 8902
    const val SPOTIFY_HISTORY_PORT = 8903
    const val EXECUTE_PORT = 8905
    const val EXECUTE_PORT_SEND_FROM_HANDY = 8906
    const val EXECUTE_RESPONSE_PORT = 8907
    const val INFO_PORT = 8909
    const val SMS_PORT = 8910

    private const val ITERATIONS = 200_000
    private const val KEY_LENGTH = 256

    fun deriveKey(password: String, salt: ByteArray): SecretKey {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val tmp = factory.generateSecret(spec).encoded
        return SecretKeySpec(tmp, "AES")
    }

    var masterPassword: String = ""

    fun init(context: Context) {
        masterPassword = PasswordStorage.loadPassword(context)
            ?: ""
        val prefs = context.getSharedPreferences("tabslify_app_prefs", Context.MODE_PRIVATE)
        LAT = if (prefs.contains("lat_key_d"))
            java.lang.Double.longBitsToDouble(prefs.getLong("lat_key_d", 0L))
        else
            prefs.getFloat("lat_key", 0.0f).toDouble()
        LON = if (prefs.contains("lon_key_d"))
            java.lang.Double.longBitsToDouble(prefs.getLong("lon_key_d", 0L))
        else
            prefs.getFloat("lon_key", 0.0f).toDouble()
    }

    var realDevice = false

    fun setAppLanguage(context: Context, tag: String) {
        val manager = context.getSystemService(LocaleManager::class.java) ?: return
        manager.applicationLocales =
            if (tag.isEmpty()) LocaleList.getEmptyLocaleList()
            else LocaleList.forLanguageTags(tag)
    }

    fun currentAppLanguage(context: Context): String {
        val locales = context.getSystemService(LocaleManager::class.java)?.applicationLocales
        return locales?.takeUnless { it.isEmpty }?.get(0)?.language ?: ""
    }

    fun ensureDefaultLanguage(context: Context) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("language_initialized", false)) return
        val systemLanguage = java.util.Locale.getDefault().language
        val defaultLang = if (systemLanguage == "de") "de" else "en"
        prefs.edit(commit = true) { putBoolean("language_initialized", true) }
        setAppLanguage(context, defaultLang)
    }

    fun cms(): Int = System.currentTimeMillis().toInt()

    const val SHOWCOMMANDS = 20000
    const val GAL = 70000
    const val DEL_GAL_CONF = 70001
    const val PD_QUEUE = 50001
    const val VOICE_NOTE = 40000
    const val TODOS = 10000
    const val CHAT_SERVICE = 30000
    const val CHAT_SERVICE_HISTORY = 30001
    const val COMPLETED_PODCASTS = 50002
    const val PODCASTS = 50003
    const val PLALISTS = 50101
    const val MEDIA_PLAYER = 50000
    const val BLOCKED_MESSAGES = 60000
    const val EXPLORE_TRACKING = 80000

    @Suppress("unused")
    fun sendBridgeCommand(context: Context, json: String) {
        if (prvt()) {
            context.sendBroadcast(Intent("com.paluss1122.accessibily.EXECUTE").apply {
                setPackage("com.paluss1122.accessibily")
                putExtra("cmd", json)
            })
        }
    }

    fun requestPermission(
        target: String,
        launcher: ActivityResultLauncher<Array<String>>
    ): Boolean {
        when (target) {
            "audio" -> launcher.launch(arrayOf(Manifest.permission.READ_MEDIA_AUDIO))
            "img" -> {
                launcher.launch(
                    arrayOf(
                        Manifest.permission.READ_MEDIA_IMAGES,
                        Manifest.permission.READ_MEDIA_VIDEO,
                        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
                    )
                )
            }

            "loc" -> launcher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )

            "loc_bg" -> launcher.launch(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
            "activity" -> launcher.launch(arrayOf(Manifest.permission.ACTIVITY_RECOGNITION))
            "cam" -> launcher.launch(arrayOf(Manifest.permission.CAMERA))
            "con" -> launcher.launch(
                arrayOf(
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.WRITE_CONTACTS
                )
            )

            "not" -> launcher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            "mic" -> launcher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
            "bt" -> launcher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
            "all" -> {
                val permissions = mutableListOf(
                    Manifest.permission.READ_MEDIA_AUDIO,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.CAMERA,
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.WRITE_CONTACTS,
                    Manifest.permission.POST_NOTIFICATIONS,
                    Manifest.permission.BLUETOOTH_CONNECT
                )
                permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
                permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
                permissions.add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
                launcher.launch(permissions.toTypedArray())
            }

            else -> return false
        }
        return true
    }

    private val settingsBasedPermissionKeys = setOf(
        "SYSTEM_ALERT_WINDOW",
        "REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
        "MANAGE_EXTERNAL_STORAGE",
        "ACCESS_NOTIFICATION_POLICY",
        "PACKAGE_USAGE_STATS"
    )

    fun isPermissionRequestable(key: String): Boolean =
        key != "ACCESS_SUPERUSER" && key != "SET_ALARM" &&
                key != "ACCESS_WIFI_STATE" && key != "ACCESS_NETWORK_STATE"

    @SuppressLint("BatteryLife")
    fun requestPermissionForKey(
        context: Context,
        key: String,
        launcher: ActivityResultLauncher<Array<String>>
    ) {
        when (key) {
            "READ_MEDIA_AUDIO" -> launcher.launch(arrayOf(Manifest.permission.READ_MEDIA_AUDIO))
            "POST_NOTIFICATIONS" -> launcher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            "ACCESS_COARSE_LOCATION / ACCESS_FINE_LOCATION" -> launcher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )

            "ACCESS_BACKGROUND_LOCATION" -> launcher.launch(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
            "ACTIVITY_RECOGNITION" -> launcher.launch(arrayOf(Manifest.permission.ACTIVITY_RECOGNITION))
            "FOREGROUND_SERVICE" -> launcher.launch(arrayOf(Manifest.permission.FOREGROUND_SERVICE))
            "READ_MEDIA_IMAGES / READ_MEDIA_VIDEO" -> requestPermission("img", launcher)
            "READ_CONTACTS / WRITE_CONTACTS" -> launcher.launch(
                arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)
            )

            "CAMERA" -> launcher.launch(arrayOf(Manifest.permission.CAMERA))
            "RECEIVE_BOOT_COMPLETED" -> launcher.launch(arrayOf(Manifest.permission.RECEIVE_BOOT_COMPLETED))
            "RECORD_AUDIO" -> launcher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
            "BLUETOOTH_CONNECT" -> launcher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
            "READ_PHONE_STATE / READ_BASIC_PHONE_STATE" -> launcher.launch(arrayOf(Manifest.permission.READ_PHONE_STATE))
            "READ_SMS" -> launcher.launch(arrayOf(Manifest.permission.READ_SMS))

            "SYSTEM_ALERT_WINDOW" -> context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    "package:${context.packageName}".toUri()
                )
                    .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
            )

            "REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" -> context.startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    "package:${context.packageName}".toUri()
                )
                    .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
            )

            "MANAGE_EXTERNAL_STORAGE" ->
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        "package:${context.packageName}".toUri()
                    ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                )

            "PACKAGE_USAGE_STATS" -> context.startActivity(
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                    .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
            )

            "ACCESS_NOTIFICATION_POLICY" -> context.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                    .apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
            )
        }
    }

    private fun mediaImagesVideoPermissions(): List<String> = listOf(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
    )

    fun requestAllRuntimePermissions(
        keys: List<String>,
        launcher: ActivityResultLauncher<Array<String>>
    ) {
        val permissions = mutableListOf<String>()
        keys.filter { isPermissionRequestable(it) && it !in settingsBasedPermissionKeys }
            .forEach { key ->
                when (key) {
                    "READ_MEDIA_AUDIO" -> permissions += Manifest.permission.READ_MEDIA_AUDIO
                    "POST_NOTIFICATIONS" -> permissions += Manifest.permission.POST_NOTIFICATIONS
                    "ACCESS_COARSE_LOCATION / ACCESS_FINE_LOCATION" -> permissions += listOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )

                    "FOREGROUND_SERVICE" -> permissions += Manifest.permission.FOREGROUND_SERVICE
                    "ACTIVITY_RECOGNITION" -> permissions += Manifest.permission.ACTIVITY_RECOGNITION
                    "READ_MEDIA_IMAGES / READ_MEDIA_VIDEO" -> permissions += mediaImagesVideoPermissions()
                    "READ_CONTACTS / WRITE_CONTACTS" -> permissions += listOf(
                        Manifest.permission.READ_CONTACTS,
                        Manifest.permission.WRITE_CONTACTS
                    )

                    "CAMERA" -> permissions += Manifest.permission.CAMERA
                    "RECEIVE_BOOT_COMPLETED" -> permissions += Manifest.permission.RECEIVE_BOOT_COMPLETED
                    "RECORD_AUDIO" -> permissions += Manifest.permission.RECORD_AUDIO
                    "BLUETOOTH_CONNECT" -> permissions += Manifest.permission.BLUETOOTH_CONNECT
                    "READ_PHONE_STATE / READ_BASIC_PHONE_STATE" -> permissions += Manifest.permission.READ_PHONE_STATE
                    "READ_SMS" -> permissions += Manifest.permission.READ_SMS
                }
            }
        if (permissions.isNotEmpty()) launcher.launch(permissions.distinct().toTypedArray())
    }

    fun getAppSignatureSha256(context: Context): String? {
        val allowedHashes = listOf(
            BuildConfig.DEBUG_SHA256,
            BuildConfig.RELEASE_SHA256
        ).map { it.replace(":", "").lowercase() }

        try {
            val packageInfo = context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_SIGNING_CERTIFICATES
            )
            val signingInfo = packageInfo.signingInfo
            val signatures =
                signingInfo?.signingCertificateHistory ?: signingInfo?.apkContentsSigners
            if (signatures != null) {
                for (signature in signatures) {
                    val md = MessageDigest.getInstance("SHA-256")
                    md.update(signature.toByteArray())
                    val digest = md.digest()
                    val toRet =
                        digest.fold("") { str, it -> str + "%02x".format(it) }.replace(":", "")
                            .lowercase()

                    if (allowedHashes.contains(toRet)) {
                        return toRet
                    } else {
                        Log.e(
                            "Config",
                            "Invalid App Signature SHA256: $toRet. Please add this hash to BuildConfig if it is valid."
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("Config", "Error getting signature: ${e.message}")
        }
        return null
    }

    suspend fun <T> safeCall(block: suspend () -> T): T {
        return try {
            block()
        } catch (e: Exception) {
            if (e.message?.contains("JWT expired", ignoreCase = true) == true) {
                try {
                    val refreshToken = client.auth.currentSessionOrNull()?.refreshToken
                        ?: throw IllegalStateException("No refresh token available")
                    client.auth.refreshSession(refreshToken)
                    block()
                } catch (refreshError: Exception) {
                    Log.e("Config", "Refresh failed", refreshError)
                    throw e
                }
            } else {
                throw e
            }
        }
    }

    suspend fun openApiProxyConnection(
        context: Context,
        readTimeoutMs: Int = 15_000
    ): HttpURLConnection? = withContext(Dispatchers.IO) {
        val sha256 = getAppSignatureSha256(context) ?: return@withContext null
        (URL("$SUPABASE_URL/functions/v1/api-proxy").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Authorization", "Bearer $SUPABASE_PUBLISHABLE_KEY")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("X-Android-Cert", sha256)
            connectTimeout = 15_000
            readTimeout = readTimeoutMs
            doOutput = true
        }
    }

    suspend fun apiProxyPost(
        context: Context,
        body: JSONObject,
        tag: String = "apiProxy",
        readTimeoutMs: Int = 15_000
    ): String? = withContext(Dispatchers.IO) {
        val connection = openApiProxyConnection(context, readTimeoutMs) ?: return@withContext null
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            if (connection.responseCode != 200) {
                val errorText =
                    connection.errorStream?.bufferedReader()?.readText() ?: "No error body"
                Log.e(tag, "api-proxy failed: Code ${connection.responseCode}, Body: $errorText")
                return@withContext null
            }
            connection.inputStream.bufferedReader().readText()
        } finally {
            connection.disconnect()
        }
    }

    suspend fun fetchBWMP(context: Context): String {
        if (getAppSignatureSha256(context) == null) return "App-Signatur konnte nicht validiert werden"
        val body = JSONObject().apply { put("action", "BWMP") }
        val responseText = apiProxyPost(context, body, "FetchBWMP") ?: return ""
        return JSONObject(responseText).getString("value")
    }

    fun userApiKey(context: Context, name: String): String {
        return context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .getString("api_key_$name", "")?.trim().orEmpty()
    }
}

fun toast(context: Context, text: String) {
    Toast.makeText(context, text, Toast.LENGTH_LONG).show()
}

fun prvt(): Boolean {
    @Suppress("KotlinConstantConditions", "RedundantSuppression", "SimplifyBooleanWithConstants")
    return (getDeviceName().trim().contains(
        BuildConfig.LOCAL_DEVICE_NAME,
        ignoreCase = true
    ) || !Config.realDevice) && BuildConfig.IS_DEV
}

fun tNotify(ctx: Context, notificationId: Int, notification: Any, tag: String? = null) {
    if (!canNotify(ctx)) return

    val notificationManager = ctx.getSystemService(NotificationManager::class.java)
    val resolvedNotification = when (notification) {
        is NotificationCompat.Builder -> notification.build()
        is Notification -> notification
        else -> {
            Log.w("tNotify", "Unsupported notification type: ${notification::class.java.name}")
            return
        }
    }

    if (tag == null) {
        notificationManager?.notify(notificationId, resolvedNotification)
    } else {
        notificationManager?.notify(tag, notificationId, resolvedNotification)
    }
}