package com.tabslify.tabs.authenticator

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.ResultPoint
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.tabslify.R
import com.tabslify.core.functions.errorInsert
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLDecoder
import java.security.KeyStore
import java.time.Instant
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlin.time.Duration.Companion.milliseconds

object BiometricKeyHelper {
    private const val KEY_NAME = "tabslify_auth_key"

    fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").also { it.load(null) }
        ks.getKey(KEY_NAME, null)?.let { return it as SecretKey }

        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        keyGen.init(
            KeyGenParameterSpec.Builder(
                KEY_NAME,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true)
                .build()
        )
        return keyGen.generateKey()
    }

    fun deleteKey() {
        val ks = KeyStore.getInstance("AndroidKeyStore").also { it.load(null) }
        if (ks.containsAlias(KEY_NAME)) ks.deleteEntry(KEY_NAME)
    }

    fun getCipher(): Cipher =
        Cipher.getInstance("${KeyProperties.KEY_ALGORITHM_AES}/${KeyProperties.BLOCK_MODE_CBC}/${KeyProperties.ENCRYPTION_PADDING_PKCS7}")
}


@Composable
fun AuthenticatorTab() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val activity = remember {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is FragmentActivity) return@remember ctx
            ctx = ctx.baseContext
        }
        null
    }

    if (activity == null) {
        LaunchedEffect(Unit) {
            errorInsert(
                "AuthenticatorTab",
                "❌ FragmentActivity fehlt",
                Instant.now().toString(),
                "ERROR"

            )
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Icon(
                    Icons.Default.Lock,
                    null,
                    Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.fehler_mainactivity_muss_fragmentactivity_erben),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = Color.White
                )
            }
        }
        return
    }

    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }

    var isAuthenticated by remember { mutableStateOf(false) }
    var lockEnabled by remember { mutableStateOf(prefs.getBoolean("lockEnabled", false)) }
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var shouldShowPrompt by remember { mutableStateOf(false) }
    var activeBiometricPrompt by remember { mutableStateOf<BiometricPrompt?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            activeBiometricPrompt?.cancelAuthentication()
            activeBiometricPrompt = null
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && lockEnabled && !isAuthenticated && !shouldShowPrompt) {
                shouldShowPrompt = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(lockEnabled) {
        if (lockEnabled && !isAuthenticated && !shouldShowPrompt) {
            shouldShowPrompt = true
        }
    }

    LaunchedEffect(shouldShowPrompt) {
        if (shouldShowPrompt && lockEnabled && !isAuthenticated) {
            delay(650.milliseconds)
            if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) || isAuthenticated) {
                shouldShowPrompt = false
                return@LaunchedEffect
            }
            activeBiometricPrompt?.cancelAuthentication()
            showBiometricPrompt(
                activity = activity,
                onSuccess = { _ ->
                    isAuthenticated = true
                    showError = false
                    shouldShowPrompt = false
                },
                onError = { error, isCritical ->
                    if (isCritical) {
                        errorMessage = error
                        showError = true
                        shouldShowPrompt = false
                        isAuthenticated = false
                        errorInsert(
                            "AuthenticatorTab",
                            "❌ AUTH: $error",
                            Instant.now().toString(),
                            "ERROR"

                        )
                    } else {
                        shouldShowPrompt = false
                    }
                },
                onPromptCreated = { activeBiometricPrompt = it }
            )
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
        when {
            !lockEnabled || isAuthenticated -> {
                AuthenticatedContent(context = context)
            }

            showError -> {
                ErrorScreen(
                    message = errorMessage,
                    onRetry = { showError = false; shouldShowPrompt = true },
                    onUnlock = { showError = false; shouldShowPrompt = true }
                )
            }

            else -> {
                LockScreen(onRetry = { shouldShowPrompt = true })
            }
        }
    }
}


@Composable
private fun AuthenticatedContent(context: Context) {
    val passwordDb = remember { PasswordDatabase.getDatabase(context.applicationContext) }
    val twoFaDb = remember { TwoFADatabase.getDatabase(context.applicationContext) }
    var showSettings by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (showSettings) {
            SettingsScreenWithScreenshotProtection()
        } else {
            PasswordManagerScreen(
                db = passwordDb,
                twoFaDb = twoFaDb,
                onSettingsClick = { showSettings = true })
        }
    }
}


@Composable
private fun LockScreen(onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = stringResource(R.string.gesperrt),
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.app_gesperrt),
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.biometrische_authentifizierung_erforderlich),
                style = MaterialTheme.typography.bodyMedium, color = Color.White
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onRetry) { Text(stringResource(R.string.entsperren)) }
        }
    }
}


@Composable
private fun ErrorScreen(message: String, onRetry: () -> Unit, onUnlock: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = stringResource(R.string.fehler),
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.authentifizierungsfehler),
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
            Spacer(Modifier.height(16.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onRetry) { Text(stringResource(R.string.erneut_versuchen)) }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onUnlock) { Text(stringResource(R.string.ohne_authentifizierung_fortfahren)) }
        }
    }
}


fun biometricUnavailableMessage(context: Context, status: Int): String = when (status) {
    BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
        context.getString(R.string.applock_fehler_none_enrolled)

    BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
        context.getString(R.string.applock_fehler_no_hardware)

    BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
        context.getString(R.string.applock_fehler_hw_unavailable)

    BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ->
        context.getString(R.string.applock_fehler_security_update)

    BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED ->
        context.getString(R.string.applock_fehler_unsupported)

    else -> context.getString(R.string.applock_fehler_unbekannt, status)
}

fun biometricStatusIsFixableBySetup(status: Int): Boolean =
    status == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ||
            status == BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED

fun openBiometricEnrollment(context: Context) {
    val enroll = Intent(Settings.ACTION_BIOMETRIC_ENROLL).apply {
        putExtra(
            Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
            Authenticators.BIOMETRIC_STRONG
        )
    }
    runCatching { context.startActivity(enroll) }.onFailure {
        runCatching { context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) }
    }
}

private fun showBiometricPrompt(
    activity: FragmentActivity,
    onSuccess: (Cipher) -> Unit,
    onError: (error: String, isCritical: Boolean) -> Unit,
    onPromptCreated: (BiometricPrompt) -> Unit
) {
    if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
        onError(activity.getString(R.string.activity_nicht_bereit), true); return
    }

    val bm = BiometricManager.from(activity)
    val canAuth =
        bm.canAuthenticate(Authenticators.BIOMETRIC_STRONG)
    if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
        onError(biometricUnavailableMessage(activity, canAuth), true)
        return
    }

    val executor = ContextCompat.getMainExecutor(activity)

    val cipher = BiometricKeyHelper.getCipher()
    try {
        cipher.init(Cipher.ENCRYPT_MODE, BiometricKeyHelper.getOrCreateKey())
    } catch (_: KeyPermanentlyInvalidatedException) {
        BiometricKeyHelper.deleteKey()
        onError(activity.getString(R.string.applock_fehler_key_invalidated), true)
        return
    }

    val prompt = BiometricPrompt(
        activity, executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                val unlockedCipher = result.cryptoObject?.cipher ?: run {
                    onError(activity.getString(R.string.kryptografisches_objekt_fehlt), true); return
                }
                onSuccess(unlockedCipher)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                when (errorCode) {
                    BiometricPrompt.ERROR_NO_BIOMETRICS ->
                        onError(activity.getString(R.string.applock_fehler_none_enrolled), true)

                    BiometricPrompt.ERROR_HW_NOT_PRESENT ->
                        onError(activity.getString(R.string.applock_fehler_no_hardware), true)

                    BiometricPrompt.ERROR_HW_UNAVAILABLE,
                    BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL ->
                        onError(activity.getString(R.string.applock_fehler_hw_unavailable), true)

                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                    BiometricPrompt.ERROR_CANCELED ->
                        onError(activity.getString(R.string.authentifizierung_abgebrochen), false)

                    BiometricPrompt.ERROR_LOCKOUT ->
                        onError(activity.getString(R.string.applock_fehler_lockout), true)

                    BiometricPrompt.ERROR_LOCKOUT_PERMANENT ->
                        onError(activity.getString(R.string.applock_fehler_lockout_permanent), true)

                    BiometricPrompt.ERROR_TIMEOUT ->
                        onError(activity.getString(R.string.applock_fehler_timeout), false)

                    else ->
                        onError(activity.getString(R.string.applock_fehler_allgemein, errString), false)
                }
            }
        }
    )

    onPromptCreated(prompt)

    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle(activity.getString(R.string.tabslify_passwort_manager))
        .setSubtitle(activity.getString(R.string.authentifizieren_um_fortzufahren))
        .setAllowedAuthenticators(Authenticators.BIOMETRIC_STRONG)
        .setNegativeButtonText(activity.getString(R.string.abbrechen))
        .build()

    try {
        prompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
    } catch (_: KeyPermanentlyInvalidatedException) {
        BiometricKeyHelper.deleteKey()
        onError(activity.getString(R.string.applock_fehler_key_invalidated), true)
        return
    } catch (e: Exception) {
        onError(activity.getString(R.string.fehler_beim_starten, e.message), true)
    }
}

object ScreenshotProtectionManager {
    fun setScreenshotProtection(activity: Activity?, enabled: Boolean) {
        activity?.window?.let { window ->
            if (enabled) {
                window.setFlags(
                    WindowManager.LayoutParams.FLAG_SECURE,
                    WindowManager.LayoutParams.FLAG_SECURE
                )
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }
}

@Composable
fun SettingsScreenWithScreenshotProtection() {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    val screenshotsLockedMsg = stringResource(R.string.screenshots_gesperrt)
    val screenshotsAllowedMsg = stringResource(R.string.screenshots_erlaubt)

    var lockEnabled by remember { mutableStateOf(prefs.getBoolean("lockEnabled", false)) }
    var screenshotProtectionEnabled by remember {
        mutableStateOf(
            prefs.getBoolean(
                "screenshotProtectionEnabled",
                true
            )
        )
    }
    var showBiometricInfoDialog by remember { mutableStateOf(false) }
    var biometricErrorMsg by remember { mutableStateOf("") }
    var biometricErrorFixable by remember { mutableStateOf(false) }

    val bm = remember { BiometricManager.from(context) }
    var canAuth by remember { mutableIntStateOf(bm.canAuthenticate(Authenticators.BIOMETRIC_STRONG)) }
    val isBiometricAvailable = canAuth == BiometricManager.BIOMETRIC_SUCCESS
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canAuth = bm.canAuthenticate(Authenticators.BIOMETRIC_STRONG)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text(stringResource(R.string.einstellungen_2), color = TextP, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Surface1)
                    .border(1.dp, Surface3, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.app_sperre),
                                color = TextP,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(stringResource(R.string.biometrische_authentifizierung), color = TextS, fontSize = 12.sp)
                        }
                        Switch(
                            checked = lockEnabled,
                            onCheckedChange = { enabled ->
                                if (isBiometricAvailable || !enabled) {
                                    lockEnabled = enabled
                                    prefs.edit(commit = true) {
                                        putBoolean("lockEnabled", enabled)
                                        putBoolean("authenticated", !enabled)
                                    }
                                } else {
                                    canAuth = bm.canAuthenticate(Authenticators.BIOMETRIC_STRONG)
                                    if (canAuth == BiometricManager.BIOMETRIC_SUCCESS) {
                                        lockEnabled = true
                                        prefs.edit(commit = true) {
                                            putBoolean("lockEnabled", true)
                                            putBoolean("authenticated", false)
                                        }
                                    } else {
                                        biometricErrorMsg =
                                            biometricUnavailableMessage(context, canAuth)
                                        biometricErrorFixable =
                                            biometricStatusIsFixableBySetup(canAuth)
                                        showBiometricInfoDialog = true
                                    }
                                }
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = AccentBlue)
                        )
                    }

                    HorizontalDivider(color = Surface3)

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.screenshot_schutz),
                                color = TextP,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                stringResource(R.string.screenshots_und_screen_recording_blockieren),
                                color = TextS,
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = screenshotProtectionEnabled,
                            onCheckedChange = { enabled ->
                                screenshotProtectionEnabled = enabled
                                prefs.edit(commit = true) {
                                    putBoolean(
                                        "screenshotProtectionEnabled",
                                        enabled
                                    )
                                }
                                ScreenshotProtectionManager.setScreenshotProtection(
                                    activity,
                                    enabled
                                )
                                Toast.makeText(
                                    context,
                                    if (enabled) screenshotsLockedMsg else screenshotsAllowedMsg,
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = AccentBlue)
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    val intent = Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).apply {
                        data = "package:${context.packageName}".toUri()
                    }
                    context.startActivity(intent)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Surface2),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.autofill_aktivieren), color = TextP)
            }
        }

        if (showBiometricInfoDialog) {
            AlertDialog(
                onDismissRequest = { showBiometricInfoDialog = false },
                containerColor = Surface1,
                title = { Text(stringResource(R.string.applock_fehler_titel), color = TextP) },
                text = { Text(biometricErrorMsg, color = TextS) },
                confirmButton = {
                    if (biometricErrorFixable) {
                        TextButton(onClick = {
                            showBiometricInfoDialog = false
                            openBiometricEnrollment(context)
                        }) {
                            Text(stringResource(R.string.applock_einstellungen_offnen), color = AccentBlue)
                        }
                    } else {
                        TextButton(onClick = { showBiometricInfoDialog = false }) {
                            Text("OK", color = AccentBlue)
                        }
                    }
                },
                dismissButton = if (biometricErrorFixable) {
                    {
                        TextButton(onClick = { showBiometricInfoDialog = false }) {
                            Text(stringResource(R.string.abbrechen), color = TextS)
                        }
                    }
                } else null
            )
        }
    }
}

@Composable
fun SilentCaptureScreen(
    onDismiss: () -> Unit,
    onSecretScanned: ((secret: String) -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    val ungueltigesFormatMsg = stringResource(R.string.ungultiges_format)
    val unbekannMsg = stringResource(R.string.unbekannt)
    val keinSecretMsg = stringResource(R.string.kein_secret_gefunden)
    val existsAlreadyMsg = stringResource(R.string.eintrag_existiert_bereits)
    val addedLocalCloudMsg = stringResource(R.string.hinzugefugt_lokal_cloud)
    val addFailedMsg = stringResource(R.string.hinzugefugt_cloud_fehlgeschlagen)
    val errorMsg = stringResource(R.string.fehler_5)
    
    var isProcessing by remember { mutableStateOf(false) }

    BackHandler {
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                DecoratedBarcodeView(ctx).apply {
                    viewFinder.visibility = View.GONE
                    setStatusText("")
                    decodeContinuous(object : BarcodeCallback {
                        override fun barcodeResult(result: BarcodeResult?) {
                            if (!isProcessing && result?.text != null) {
                                isProcessing = true
                                pause()
                                scope.launch {
                                    try {
                                        val decodedText = withContext(Dispatchers.IO) {
                                            URLDecoder.decode(result.text, "UTF-8")
                                        }
                                        val uri = decodedText.toUri()

                                        if (uri.scheme != "otpauth") {
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(
                                                    context,
                                                    ungueltigesFormatMsg,
                                                    Toast.LENGTH_LONG
                                                ).show()
                                                errorInsert(
                                                    "Capture Activity",
                                                    "❌ Ungültiges Format! ($uri)",
                                                    Instant.now().toString(),
                                                    "ERROR"

                                                )
                                                isProcessing = false; onDismiss()
                                            }
                                            return@launch
                                        }

                                        val label = uri.path?.removePrefix("/") ?: unbekannMsg
                                        val secretParam = uri.getQueryParameter("secret")
                                        val issuerParam = uri.getQueryParameter("issuer")
                                        val displayName =
                                            issuerParam?.let { "$it ($label)" } ?: label

                                        if (secretParam.isNullOrBlank()) {
                                            Toast.makeText(
                                                context,
                                                keinSecretMsg,
                                                Toast.LENGTH_LONG
                                            ).show()
                                            errorInsert(
                                                "Capture Activity",
                                                "❌ Kein Secret! ($uri)",
                                                Instant.now().toString(),
                                                "ERROR"

                                            )
                                            isProcessing = false; onDismiss()
                                            return@launch
                                        }

                                        // NEU: wenn Callback gesetzt → nur Secret zurückgeben, nicht speichern
                                        if (onSecretScanned != null) {
                                            withContext(Dispatchers.Main) {
                                                onSecretScanned(secretParam)
                                                // onDismiss wird vom Aufrufer durch den Callback ausgelöst
                                            }
                                            return@launch
                                        }

                                        // Altverhalten: direkt in DB speichern
                                        val db = TwoFADatabase.getDatabase(context.applicationContext)
                                        val existing = db.twoFADao().getAll()
                                        if (existing.any {
                                                it.secret == secretParam || it.name.equals(
                                                    displayName,
                                                    ignoreCase = true
                                                )
                                            }) {
                                            Toast.makeText(
                                                context,
                                                existsAlreadyMsg,
                                                Toast.LENGTH_LONG
                                            ).show()
                                            isProcessing = false; onDismiss()
                                            return@launch
                                        }

                                        val newEntry =
                                            TwoFAEntry(name = displayName, secret = secretParam)
                                        val inserted = db.twoFADao().insertOrIgnore(newEntry)
                                        if (inserted == -1L) {
                                            Toast.makeText(
                                                context,
                                                existsAlreadyMsg,
                                                Toast.LENGTH_LONG
                                            ).show()
                                            isProcessing = false; onDismiss()
                                            return@launch
                                        }

                                        val ok = saveTwoFaEntryToSupabase(newEntry, db)
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(
                                                context,
                                                if (ok) addedLocalCloudMsg.format(displayName) else addFailedMsg.format(displayName),
                                                Toast.LENGTH_LONG
                                            ).show()
                                            isProcessing = false; onDismiss()
                                        }
                                    } catch (e: Exception) {
                                        Toast.makeText(
                                            context,
                                            errorMsg.format(e.message),
                                            Toast.LENGTH_LONG
                                        ).show()
                                        errorInsert(
                                            "Capture Activity",
                                            "❌ ${e.message}",
                                            Instant.now().toString(),
                                            "ERROR"

                                        )
                                        isProcessing = false; onDismiss()
                                    }
                                }
                            }
                        }

                        override fun possibleResultPoints(resultPoints: MutableList<ResultPoint>?) {}
                    })
                    resume()
                }
            },
            onRelease = { barcodeView ->
                barcodeView.pause()
            },
            modifier = Modifier.fillMaxSize()
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .size(250.dp)
                    .border(3.dp, AccentBlue, RoundedCornerShape(16.dp))
                    .background(Color.Transparent)
            )
            Spacer(Modifier.height(40.dp))
            Text(
                stringResource(R.string.halte_den_qr_code_in),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}