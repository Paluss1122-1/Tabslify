package com.tabslify.apkm

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile
import kotlin.coroutines.resume

/** Art einer im Bundle enthaltenen APK. */
enum class ApkKind { BASE, ARCH, DENSITY, LANGUAGE, FEATURE, OTHER }

/** Eine einzelne APK innerhalb des .apkm/.apks/.xapk Archivs. */
data class ApkEntryInfo(
    val entryName: String,       // Pfad innerhalb des ZIP
    val fileName: String,        // reiner Dateiname
    val size: Long,              // entpackte Größe in Bytes
    val kind: ApkKind,
    val configValue: String?,    // z. B. "arm64_v8a", "de", "xxhdpi"
    val recommended: Boolean     // beste Übereinstimmung für dieses Gerät
)

/** Ergebnis der Signaturprüfung gegen eine ggf. bereits installierte App. */
enum class SignatureState { NOT_INSTALLED, MATCH, MISMATCH, UNKNOWN }

/** Aufbereitete Metadaten eines geparsten Bundles. */
data class ApkmPackage(
    val sourceName: String,
    val cacheFile: File,
    val appName: String,
    val packageName: String,
    val versionName: String?,
    val versionCode: Long,
    val minSdk: Int?,
    val targetSdk: Int?,
    val totalSize: Long,
    val apks: List<ApkEntryInfo>,
    val icon: ImageBitmap?,
    val signatureState: SignatureState,
    val installedVersionName: String?,
    val installedVersionCode: Long?,
    val supportedAbis: List<String>,
    val supportedLanguages: List<String>,
    val supportedDensities: List<String>,
    val infoJsonRaw: String?
)

/** Fehlerkategorien für gezielte Dialoge. */
enum class ParseError { CORRUPTED, NO_APKS, UNREADABLE }

class ApkmParseException(val error: ParseError, message: String) : Exception(message)

/** Terminaler Ausgang der Installation. */
sealed interface InstallOutcome {
    object Success : InstallOutcome
    data class Failure(val statusCode: Int, val message: String?, val isSignatureConflict: Boolean) :
        InstallOutcome
}

/**
 * Kapselt das Parsen und Installieren von Split-APK Bundles über die [PackageInstaller] Session-API.
 * Die [logs] Liste ist Compose-beobachtbar und speist die "Nerd"-Log-Ansicht.
 */
class ApkmInstaller(private val context: Context) {

    val logs = mutableStateListOf<String>()
    private val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    fun log(message: String) {
        val line = "${timeFmt.format(System.currentTimeMillis())}  $message"
        logs.add(line)
        Log.i(TAG, message)
    }

    // ---------------------------------------------------------------------------------------------
    // Schritt 1: Bundle in den Cache kopieren
    // ---------------------------------------------------------------------------------------------

    suspend fun copyToCache(uri: Uri, onProgress: (Long, Long) -> Unit): File {
        val name = queryDisplayName(uri) ?: "bundle_${System.currentTimeMillis()}.apkm"
        log("Kopiere \"$name\" in den Cache…")
        val total = querySize(uri)
        val dir = File(context.cacheDir, "apkm_install").apply { mkdirs() }
        // Alte Reste aufräumen, damit der Cache nicht vollläuft.
        dir.listFiles()?.forEach { runCatching { it.delete() } }
        val target = File(dir, "source.bundle")

        val input = context.contentResolver.openInputStream(uri)
            ?: throw ApkmParseException(ParseError.UNREADABLE, "Datei konnte nicht geöffnet werden.")
        input.use { ins ->
            target.outputStream().use { out ->
                val buf = ByteArray(1 shl 16)
                var copied = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = ins.read(buf)
                    if (read < 0) break
                    out.write(buf, 0, read)
                    copied += read
                    onProgress(copied, total)
                }
            }
        }
        log("Kopiert: ${humanSize(target.length())}")
        return target
    }

    // ---------------------------------------------------------------------------------------------
    // Schritt 2: Bundle parsen
    // ---------------------------------------------------------------------------------------------

    fun parse(cacheFile: File, sourceName: String): ApkmPackage {
        val zip = try {
            ZipFile(cacheFile)
        } catch (e: ZipException) {
            throw ApkmParseException(ParseError.CORRUPTED, "Archiv ist beschädigt: ${e.message}")
        } catch (e: Exception) {
            throw ApkmParseException(ParseError.UNREADABLE, "Archiv unlesbar: ${e.message}")
        }

        zip.use { zf ->
            val apkEntries = zf.entries().asSequence()
                .filter { !it.isDirectory && it.name.substringAfterLast('/').endsWith(".apk", true) }
                .toList()

            if (apkEntries.isEmpty()) {
                throw ApkmParseException(
                    ParseError.NO_APKS,
                    "Keine APK-Dateien im Archiv gefunden – möglicherweise kein gültiges Bundle."
                )
            }
            log("${apkEntries.size} APK(s) im Archiv gefunden")

            val infoJsonRaw = zf.getEntry("info.json")?.let { entry ->
                runCatching { zf.getInputStream(entry).bufferedReader().use { it.readText() } }.getOrNull()
            }

            // Basis-APK bestimmen und temporär entpacken, um authentische Metadaten zu lesen.
            val baseEntry = apkEntries.firstOrNull { it.name.substringAfterLast('/').equals("base.apk", true) }
                ?: apkEntries.firstOrNull { !it.name.substringAfterLast('/').startsWith("split", true) }
                ?: apkEntries.first()

            val baseApkFile = File(cacheFile.parentFile, "base_meta.apk")
            zf.getInputStream(baseEntry).use { input ->
                baseApkFile.outputStream().use { input.copyTo(it) }
            }

            val pm = context.packageManager
            val flags = PackageManager.GET_SIGNING_CERTIFICATES
            val baseInfo: PackageInfo? = runCatching {
                pm.getPackageArchiveInfo(baseApkFile.absolutePath, flags)
            }.getOrNull()

            if (baseInfo == null || baseInfo.packageName.isBlank()) {
                runCatching { baseApkFile.delete() }
                throw ApkmParseException(
                    ParseError.CORRUPTED,
                    "Basis-APK konnte nicht gelesen werden – Bundle vermutlich beschädigt."
                )
            }
            // Damit loadLabel/loadIcon aus der Archiv-APK funktionieren.
            baseInfo.applicationInfo?.apply {
                sourceDir = baseApkFile.absolutePath
                publicSourceDir = baseApkFile.absolutePath
            }

            val packageName = baseInfo.packageName
            val versionName = baseInfo.versionName
            @Suppress("DEPRECATION")
            val versionCode =
                baseInfo.longVersionCode
            val minSdk = baseInfo.applicationInfo?.minSdkVersion
            val targetSdk = baseInfo.applicationInfo?.targetSdkVersion

            // App-Name: info.json → APK-Label → Paketname
            val jsonName = infoJsonRaw?.let { readJsonAppName(it) }
            val labelName = runCatching {
                baseInfo.applicationInfo?.loadLabel(pm)?.toString()
            }.getOrNull()?.takeIf { it.isNotBlank() && it != packageName }
            val appName = jsonName ?: labelName ?: packageName

            // Icon: icon.png im Archiv → App-Icon aus Basis-APK
            val icon = loadIcon(zf, baseInfo, pm)

            // Splits klassifizieren + Empfehlungen berechnen
            val deviceAbis = Build.SUPPORTED_ABIS.map { it.replace('-', '_').lowercase() }
            val deviceLangs = deviceLanguages()
            val deviceDensity = context.resources.displayMetrics.densityDpi
            val deviceBucket = densityBucket(deviceDensity)

            val classified = apkEntries.map { entry ->
                classify(entry, baseEntry, deviceAbis, deviceLangs, deviceBucket)
            }

            // Falls kein Arch-Split exakt passt, den ersten verfügbaren empfehlen (App liefe sonst nicht).
            val archSplits = classified.filter { it.kind == ApkKind.ARCH }
            val hasRecommendedArch = archSplits.any { it.recommended }
            val densitySplits = classified.filter { it.kind == ApkKind.DENSITY }
            val hasRecommendedDensity = densitySplits.any { it.recommended }

            val finalized = classified.map { info ->
                when (info.kind) {
                    ApkKind.ARCH if !hasRecommendedArch && info == archSplits.firstOrNull() ->
                        info.copy(recommended = true)

                    ApkKind.DENSITY if !hasRecommendedDensity && info == nearestDensity(densitySplits, deviceDensity) ->
                        info.copy(recommended = true)

                    else -> info
                }
            }.sortedWith(compareBy({ it.kind.ordinal }, { it.fileName }))

            runCatching { baseApkFile.delete() }

            val signatureState = computeSignatureState(baseInfo, packageName)
            val installed = runCatching {
                pm.getPackageInfo(packageName, 0)
            }.getOrNull()
            @Suppress("DEPRECATION")
            val installedCode = installed?.longVersionCode

            log("Paket: $packageName v$versionName ($versionCode)")
            log("Signatur: $signatureState")

            return ApkmPackage(
                sourceName = sourceName,
                cacheFile = cacheFile,
                appName = appName,
                packageName = packageName,
                versionName = versionName,
                versionCode = versionCode,
                minSdk = minSdk,
                targetSdk = targetSdk,
                totalSize = finalized.sumOf { it.size },
                apks = finalized,
                icon = icon,
                signatureState = signatureState,
                installedVersionName = installed?.versionName,
                installedVersionCode = installedCode,
                supportedAbis = finalized.filter { it.kind == ApkKind.ARCH }.mapNotNull { it.configValue },
                supportedLanguages = finalized.filter { it.kind == ApkKind.LANGUAGE }.mapNotNull { it.configValue },
                supportedDensities = finalized.filter { it.kind == ApkKind.DENSITY }.mapNotNull { it.configValue },
                infoJsonRaw = infoJsonRaw
            )
        }
    }

    private fun classify(
        entry: ZipEntry,
        baseEntry: ZipEntry,
        deviceAbis: List<String>,
        deviceLangs: Set<String>,
        deviceBucket: String
    ): ApkEntryInfo {
        val fileName = entry.name.substringAfterLast('/')
        val lower = fileName.lowercase()
        val size = entry.size.coerceAtLeast(0L)

        if (entry == baseEntry || lower == "base.apk") {
            return ApkEntryInfo(entry.name, fileName, size, ApkKind.BASE, null, recommended = true)
        }

        // split_config.<value>.apk  oder  config.<value>.apk
        val configValue = Regex("(?:split_)?config[._]([a-z0-9_]+)\\.apk", RegexOption.IGNORE_CASE)
            .find(lower)?.groupValues?.getOrNull(1)

        if (configValue != null) {
            return when (configValue) {
                in ARCH_VALUES -> ApkEntryInfo(
                    entry.name, fileName, size, ApkKind.ARCH, configValue,
                    recommended = deviceAbis.contains(configValue)
                )
                in DENSITY_VALUES -> ApkEntryInfo(
                    entry.name, fileName, size, ApkKind.DENSITY, configValue,
                    recommended = configValue == deviceBucket || configValue == "nodpi"
                )
                else -> ApkEntryInfo(
                    // 2-4 stellige Codes → Sprache
                    entry.name, fileName, size, ApkKind.LANGUAGE, configValue,
                    recommended = deviceLangs.contains(configValue.substringBefore('_'))
                )
            }
        }

        // split_<feature>.apk (dynamisches Feature-Modul) → standardmäßig mitnehmen
        val featureName = Regex("split_([a-z0-9_]+)\\.apk", RegexOption.IGNORE_CASE)
            .find(lower)?.groupValues?.getOrNull(1)
        if (featureName != null) {
            return ApkEntryInfo(entry.name, fileName, size, ApkKind.FEATURE, featureName, recommended = true)
        }

        return ApkEntryInfo(entry.name, fileName, size, ApkKind.OTHER, null, recommended = true)
    }

    private fun nearestDensity(splits: List<ApkEntryInfo>, deviceDpi: Int): ApkEntryInfo? {
        if (splits.isEmpty()) return null
        return splits.minByOrNull { split ->
            val dpi = DENSITY_DPI[split.configValue] ?: return@minByOrNull Int.MAX_VALUE
            kotlin.math.abs(dpi - deviceDpi)
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Signaturprüfung
    // ---------------------------------------------------------------------------------------------

    private fun computeSignatureState(newInfo: PackageInfo, packageName: String): SignatureState {
        val installed = runCatching {
            context.packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        }.getOrNull() ?: return SignatureState.NOT_INSTALLED

        val newHashes = certHashes(newInfo)
        val oldHashes = certHashes(installed)
        if (newHashes.isEmpty() || oldHashes.isEmpty()) return SignatureState.UNKNOWN
        return if (newHashes.intersect(oldHashes).isNotEmpty()) SignatureState.MATCH
        else SignatureState.MISMATCH
    }

    private fun certHashes(info: PackageInfo): Set<String> {
        val signers = info.signingInfo ?: return emptySet()
        val sigs = if (signers.hasMultipleSigners()) signers.apkContentsSigners
        else signers.signingCertificateHistory ?: signers.apkContentsSigners
        val md = MessageDigest.getInstance("SHA-256")
        return sigs?.mapNotNull { sig ->
            runCatching { md.digest(sig.toByteArray()).joinToString("") { "%02x".format(it) } }.getOrNull()
        }?.toSet() ?: emptySet()
    }

    // ---------------------------------------------------------------------------------------------
    // Schritt 3: Installation über PackageInstaller-Session
    // ---------------------------------------------------------------------------------------------

    /**
     * Schreibt die [selected] APKs in eine Session und committed sie.
     * Bei [PackageInstaller.STATUS_PENDING_USER_ACTION] wird [onNeedUserAction] mit dem
     * System-Bestätigungs-Intent aufgerufen. Bei jedem Fehler wird die Session verworfen (Rollback).
     */
    @SuppressLint("RequestInstallPackagesPolicy")
    suspend fun install(
        pkg: ApkmPackage,
        selected: List<ApkEntryInfo>,
        onNeedUserAction: (Intent) -> Unit
    ): InstallOutcome = suspendCancellableCoroutine { cont ->
        val installer = context.packageManager.packageInstaller
        val action = "$ACTION_INSTALL_STATUS.${System.nanoTime()}"
        var sessionId = -1
        var finished = false
        var receiverHolder: BroadcastReceiver? = null

        fun finish(outcome: InstallOutcome) {
            if (finished) return
            finished = true
            receiverHolder?.let { runCatching { context.unregisterReceiver(it) } }
            if (cont.isActive) cont.resume(outcome)
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                val status = intent?.getIntExtra(PackageInstaller.EXTRA_STATUS, Int.MIN_VALUE)
                    ?: Int.MIN_VALUE
                val message = intent?.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                when (status) {
                    PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                        log("Warte auf Bestätigung im System-Dialog…")
                        @Suppress("DEPRECATION")
                        val confirm = intent?.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                        if (confirm != null) {
                            confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            onNeedUserAction(confirm)
                        }
                    }

                    PackageInstaller.STATUS_SUCCESS -> {
                        log("✅ Installation erfolgreich abgeschlossen.")
                        finish(InstallOutcome.Success)
                    }

                    else -> {
                        val signatureConflict = status == PackageInstaller.STATUS_FAILURE_CONFLICT ||
                            (message?.contains("signature", true) == true) ||
                            (message?.contains("INSTALL_FAILED_UPDATE_INCOMPATIBLE", true) == true) ||
                            (message?.contains("INCONSISTENT_CERTIFICATES", true) == true)
                        log("❌ Installation fehlgeschlagen (Status $status): ${message ?: "unbekannt"}")
                        if (sessionId >= 0) runCatching { installer.openSession(sessionId).use { it.abandon() } }
                        log("↩ Session verworfen – keine Teilinstallation zurückgeblieben (Rollback).")
                        finish(InstallOutcome.Failure(status, message, signatureConflict))
                    }
                }
            }
        }
        receiverHolder = receiver

        try {
            ContextCompat.registerReceiver(
                context, receiver, IntentFilter(action),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )

            val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL
            ).apply {
                setAppPackageName(pkg.packageName)
                runCatching { setInstallReason(PackageManager.INSTALL_REASON_USER) }
            }
            sessionId = installer.createSession(params)
            log("Session #$sessionId erstellt (${selected.size} APKs, ${humanSize(selected.sumOf { it.size })})")

            installer.openSession(sessionId).use { session ->
                ZipFile(pkg.cacheFile).use { zf ->
                    var written = 0L
                    for ((entryName, fileName, size, _, _, _) in selected) {
                        val entry = zf.getEntry(entryName)
                            ?: throw IllegalStateException("Eintrag fehlt: $entryName")
                        val length = if (size > 0) size else entry.size
                        log("Schreibe $fileName (${humanSize(size)})…")
                        zf.getInputStream(entry).use { input ->
                            session.openWrite(safeName(fileName), 0, length).use { out ->
                                input.copyTo(out, 1 shl 16)
                                session.fsync(out)
                            }
                        }
                        written += size
                    }
                    log("Alle APKs geschrieben (${humanSize(written)}). Committe…")
                }

                val statusIntent = Intent(action).setPackage(context.packageName)
                val pending = android.app.PendingIntent.getBroadcast(
                    context, sessionId, statusIntent,
                    android.app.PendingIntent.FLAG_MUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
                )
                session.commit(pending.intentSender)
            }
        } catch (e: Exception) {
            log("❌ Fehler beim Schreiben der Session: ${e.message}")
            if (sessionId >= 0) runCatching { installer.openSession(sessionId).use { it.abandon() } }
            log("↩ Session verworfen (Rollback).")
            finish(InstallOutcome.Failure(Int.MIN_VALUE, e.message, false))
        }

        cont.invokeOnCancellation {
            if (sessionId >= 0) runCatching { installer.openSession(sessionId).use { it.abandon() } }
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Hilfsfunktionen
    // ---------------------------------------------------------------------------------------------

    private fun loadIcon(zf: ZipFile, baseInfo: PackageInfo, pm: PackageManager): ImageBitmap? {
        // 1) icon.png / icon.webp direkt aus dem Bundle
        val iconEntry = zf.entries().asSequence().firstOrNull {
            val n = it.name.substringAfterLast('/').lowercase()
            n == "icon.png" || n == "icon.webp" || n == "icon.jpg"
        }
        if (iconEntry != null) {
            runCatching {
                val bytes = zf.getInputStream(iconEntry).use { it.readBytes() }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = calcInSampleSize(bounds.outWidth, bounds.outHeight, 256)
                }
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                    ?: return@runCatching null
                return bmp.asImageBitmap()
            }
        }
        // 2) App-Icon aus der Basis-APK rendern
        return runCatching {
            val drawable = baseInfo.applicationInfo?.loadIcon(pm) ?: return null
            val w = drawable.intrinsicWidth.coerceIn(1, 256)
            val h = drawable.intrinsicHeight.coerceIn(1, 256)
            val bmp = createBitmap(w, h)
            val canvas = android.graphics.Canvas(bmp)
            drawable.setBounds(0, 0, w, h)
            drawable.draw(canvas)
            bmp.asImageBitmap()
        }.getOrNull()
    }

    private fun readJsonAppName(raw: String): String? = runCatching {
        val json = JSONObject(raw)
        listOf("app_name", "apk_title", "name", "label", "title")
            .firstNotNullOfOrNull { key -> json.optString(key, "").takeIf { it.isNotBlank() } }
    }.getOrNull()

    private fun deviceLanguages(): Set<String> {
        val config = context.resources.configuration
        val result = mutableSetOf<String>()
        val locales = config.locales
        for (i in 0 until locales.size()) result.add(locales[i].language.lowercase())
        result.add(Locale.getDefault().language.lowercase())
        result.add("en") // Fallback-Sprache immer empfehlen
        return result
    }

    private fun queryDisplayName(uri: Uri): String? {
        if (uri.scheme == "file") return uri.lastPathSegment
        return runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx != -1) return@use c.getString(idx)
                }
                null
            }
        }.getOrNull()
    }

    private fun querySize(uri: Uri): Long {
        if (uri.scheme == "file") return runCatching { File(uri.path!!).length() }.getOrDefault(0L)
        return runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val idx = c.getColumnIndex(OpenableColumns.SIZE)
                    if (idx != -1 && !c.isNull(idx)) return@use c.getLong(idx)
                }
                0L
            } ?: 0L
        }.getOrDefault(0L)
    }

    private fun safeName(name: String): String =
        name.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "split_${System.nanoTime()}.apk" }

    private fun calcInSampleSize(w: Int, h: Int, maxSize: Int): Int {
        var sample = 1
        if (w <= 0 || h <= 0) return sample
        var scaledW = w
        var scaledH = h
        while (scaledW / 2 >= maxSize && scaledH / 2 >= maxSize) {
            scaledW /= 2
            scaledH /= 2
            sample *= 2
        }
        return sample
    }

    fun canRequestInstalls(): Boolean =
        context.packageManager.canRequestPackageInstalls()

    companion object {
        private const val TAG = "ApkmInstaller"
        const val ACTION_INSTALL_STATUS = "com.tabslify.apkm.INSTALL_STATUS"

        private val ARCH_VALUES = setOf("arm64_v8a", "armeabi_v7a", "armeabi", "x86", "x86_64", "mips", "mips64")
        private val DENSITY_VALUES = setOf(
            "ldpi", "mdpi", "tvdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi", "nodpi", "anydpi"
        )
        private val DENSITY_DPI = mapOf(
            "ldpi" to 120, "mdpi" to 160, "tvdpi" to 213, "hdpi" to 240,
            "xhdpi" to 320, "xxhdpi" to 480, "xxxhdpi" to 640, "nodpi" to 160, "anydpi" to 160
        )

        fun humanSize(bytes: Long): String {
            if (bytes < 1024) return "$bytes B"
            val kb = bytes / 1024.0
            if (kb < 1024) return "%.1f KB".format(kb)
            val mb = kb / 1024.0
            if (mb < 1024) return "%.2f MB".format(mb)
            return "%.2f GB".format(mb / 1024.0)
        }

        fun densityBucket(dpi: Int): String = when {
            dpi <= 120 -> "ldpi"
            dpi <= 160 -> "mdpi"
            dpi <= 213 -> "tvdpi"
            dpi <= 240 -> "hdpi"
            dpi <= 320 -> "xhdpi"
            dpi <= 480 -> "xxhdpi"
            else -> "xxxhdpi"
        }
    }
}
