package com.tabslify.core.activities

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.admin.DeviceAdminReceiver
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.view.animation.AnticipateInterpolator
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import com.tabslify.R
import com.tabslify.core.objects.Config
import com.tabslify.core.objects.Config.requestPermission
import com.tabslify.core.objects.prvt
import com.tabslify.core.ui.LandingPageOrApp
import com.tabslify.core.ui.SharedViewModel
import com.tabslify.core.ui.Typography
import com.tabslify.core.ui.rememberAppColor
import com.tabslify.inactive.ChatService
import com.tabslify.quicksettingsfunctions.startBatteryWorker
import com.tabslify.services.QuietHoursNotificationService
import com.tabslify.tabs.JsonEditorContent
import com.tabslify.tabs.pendingApkmUri
import com.tabslify.tabs.virustotal.pendingVirusTotalReport
import io.github.jan.supabase.storage.storage
import java.io.File

class MyDeviceAdminReceiver : DeviceAdminReceiver()

class MainActivity : FragmentActivity() {
    val sbclient = Config.client
    private val sharedViewModel: SharedViewModel by viewModels()
    private var startTarget by mutableStateOf<String?>(null)

    private var jsonFilePath by mutableStateOf<String?>(null)
    private var jsonFileUri by mutableStateOf<Uri?>(null)
    private var showJsonEditor by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        Config.ensureDefaultLanguage(this)

        enableEdgeToEdge()

        splashScreen.setOnExitAnimationListener { splashScreenView ->
            val animator = AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(
                        splashScreenView,
                        View.TRANSLATION_Y,
                        0f,
                        -splashScreenView.height.toFloat()
                    )
                )
                interpolator = AnticipateInterpolator()
                duration = 1000L
            }
            animator.doOnEnd { splashScreenView.remove() }
            animator.start()
        }

        WindowCompat.getInsetsController(
            window,
            window.decorView
        ).isAppearanceLightStatusBars = false
        val launcher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { results ->
            val notificationsGranted = results[Manifest.permission.POST_NOTIFICATIONS] == true
            if (notificationsGranted) {
                QuietHoursNotificationService.startService(this)
            }
        }

        val onboardingFinished = getSharedPreferences("app_prefs", MODE_PRIVATE)
            .getBoolean("has_seen_permission_info", false)
        if (onboardingFinished) {
            if (prvt() && Config.realDevice) {
                requestPermission("all", launcher)
            } else {
                requestPermission("not", launcher)
            }
        }

        if (prvt()) {
            val musicPrefs = getSharedPreferences("music_player_prefs", MODE_PRIVATE)
            val wasPlayingMusic = musicPrefs.getBoolean("was_playing_music", false)
            val wasPlayingPodcast = musicPrefs.getBoolean("was_playing_podcast", false)

            if (wasPlayingMusic || wasPlayingPodcast) {
                runCatching { com.tabslify.services.MediaPlayerService.startMusicService(this) }
            }

            val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
            val masterEnabled = prefs.getBoolean("services_master", true)
            if (masterEnabled) {
                runCatching { ChatService.startService(this) }

                if (prefs.getBoolean("service_qhns", true)) {
                    runCatching { QuietHoursNotificationService.startService(this) }
                }

                if (prefs.getBoolean("service_battery", true)) {
                    runCatching { startBatteryWorker(this) }
                }
            }
        }

        checkPermissionsAndHandleIntent(intent)

        val apkmUri = resolveApkmUri(intent)
        if (apkmUri != null) pendingApkmUri = apkmUri
        startTarget = if (apkmUri != null) "apkm" else intent.getStringExtra("target")
        applyEmailDeepLink(intent)
        applyAiNotifyDeepLink(intent)
        applyVirusTotalDeepLink(intent)
        setContent {
            val appColor = rememberAppColor()
            MaterialTheme(
                colorScheme = MaterialTheme.colorScheme.copy(
                    primary = appColor,
                    onSurface = Color.White
                ),
                typography = Typography,
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = appColor
                ) {
                    if (showJsonEditor && jsonFilePath != null && prvt()) {
                        JsonEditorContent(
                            filePath = jsonFilePath!!,
                            fileUri = jsonFileUri,
                            context = this,
                            onClose = {
                                showJsonEditor = false
                                jsonFilePath = null
                                jsonFileUri = null
                            }
                        )
                    } else if (startTarget == "files") {
                        //FilesTabContent()
                    } else {
                        LandingPageOrApp(sbclient.storage, startTarget)
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        if (showJsonEditor) {
            showJsonEditor = false
            jsonFilePath = null
            jsonFileUri = null
        }
        checkPermissionsAndHandleIntent(intent)

        val apkmUri = resolveApkmUri(intent)
        if (apkmUri != null) {
            pendingApkmUri = apkmUri
            startTarget = "apkm"
        } else {
            intent.getStringExtra("target")?.let { startTarget = it }
        }
        applyEmailDeepLink(intent)
        applyAiNotifyDeepLink(intent)
        applyVirusTotalDeepLink(intent)
    }

    private fun applyEmailDeepLink(intent: Intent) {
        val account = intent.getStringExtra("email_account")
        val uid = intent.getStringExtra("email_uid")
        if (account != null && uid != null) {
            sharedViewModel.setPendingEmailOpen(account to uid)
        }
    }

    private fun applyAiNotifyDeepLink(intent: Intent) {
        intent.getStringExtra("ai_session_id")?.let { sessionId ->
            sharedViewModel.setPendingAiSession(sessionId)
        }
    }

    private fun applyVirusTotalDeepLink(intent: Intent) {
        intent.getStringExtra("vt_report_id")?.let { reportId ->
            pendingVirusTotalReport = reportId
        }
    }

    private fun checkPermissionsAndHandleIntent(intent: Intent) {
        if (resolveApkmUri(intent) != null) return
        if (intent.action == Intent.ACTION_VIEW || intent.action == Intent.ACTION_EDIT) {
            handleIncomingIntent(intent)
        }
    }

    private fun resolveApkmUri(intent: Intent): Uri? {
        val hasApkmMimeType = intent.type in APKM_MIME_TYPES
        val hasApkmSuffix = intent.data?.lastPathSegment
            ?.lowercase()
            ?.let { name -> APKM_SUFFIXES.any { name.endsWith(it) } } == true
        if (!hasApkmMimeType && !hasApkmSuffix) return null
        return when (intent.action) {
            Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            else -> intent.data
        }
    }

    private fun handleIncomingIntent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_VIEW, Intent.ACTION_EDIT -> {
                intent.data?.let { uri ->
                    loadFileFromUri(uri)
                }
            }
        }
    }

    private fun loadFileFromUri(uri: Uri) {
        try {
            val filePath = when (uri.scheme) {
                "file" -> {
                    uri.path ?: throw Exception(getString(R.string.ungultiger_dateipfad))
                }

                "content" -> {
                    copyContentToTempFile(uri)
                }

                else -> throw Exception(getString(R.string.nicht_unterstutztes_uri_schema, uri.scheme))
            }

            jsonFilePath = filePath
            jsonFileUri = uri
            showJsonEditor = true

        } catch (e: Exception) {
            Toast.makeText(
                this,
                getString(R.string.fehler_beim_laden_der_json, e.message),
                Toast.LENGTH_LONG
            ).show()
            e.printStackTrace()
        }
    }

    private fun copyContentToTempFile(uri: Uri): String {
        val inputStream = contentResolver.openInputStream(uri)
            ?: throw Exception(getString(R.string.datei_konnte_nicht_geoffnet_werden_2))

        val fileName = try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                cursor.moveToFirst()
                cursor.getString(nameIndex)
            } ?: "temp_${System.currentTimeMillis()}.json"
        } catch (_: Exception) {
            "temp_${System.currentTimeMillis()}.json"
        }

        val tempFile = File(cacheDir, fileName)
        tempFile.outputStream().use { output ->
            inputStream.use { input ->
                input.copyTo(output)
            }
        }

        return tempFile.absolutePath
    }

    companion object {
        private val APKM_MIME_TYPES = setOf(
            "application/vnd.apkm", "application/vnd.apks", "application/vnd.xapk"
        )
        private val APKM_SUFFIXES = listOf(".apkm", ".apks", ".xapk")
    }
}