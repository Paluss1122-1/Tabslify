package com.tabslify.privatetabslifyapp

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.TextSnippet
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.rememberAsyncImagePainter
import com.tabslify.core.objects.Config
import io.github.jan.supabase.annotations.SupabaseExperimental
import io.github.jan.supabase.storage.Storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.time.Duration.Companion.seconds

fun fileExistsInDCIM(fileName: String): File? {
    val dcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
    val appFolder = File(dcimDir, "Tabslify")
    val file = File(appFolder, fileName)
    return if (file.exists()) file else null
}

fun fileExistsLocallyWithSameSize(fileName: String, remoteSize: Long): Boolean {
    val downloadsDir =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    val downloadFile = File(downloadsDir, fileName)
    if (downloadFile.exists() && downloadFile.length() == remoteSize) return true

    val dcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
    val dcimFile = File(dcimDir, "Tabslify/$fileName")
    return dcimFile.exists() && dcimFile.length() == remoteSize
}

@OptIn(SupabaseExperimental::class)
@Composable
fun FileIcon(
    fileName: String,
    storage: Storage,
    modifier: Modifier = Modifier
) {
    val isImage = isImageFile(fileName)

    if (isImage) {
        var publicUrl by remember(fileName) { mutableStateOf<String?>(null) }
        var isLoading by remember { mutableStateOf(true) }

        LaunchedEffect(fileName) {
            isLoading = true
            try {
                val signedUrl = withContext(Dispatchers.IO) {
                    storage
                        .from(Config.SUPABASE_BUCKET)
                        .createSignedUrl(fileName, 600.seconds)
                }
                publicUrl = signedUrl
            } catch (e: Exception) {
                e.printStackTrace()
                publicUrl = null
            } finally {
                isLoading = false
            }
        }

        if (!isLoading && publicUrl != null) {
            Image(
                painter = rememberAsyncImagePainter(publicUrl),
                contentDescription = fileName,
                contentScale = ContentScale.Crop,
                modifier = modifier
            )
        } else {
            Box(
                modifier = modifier.background(Color.Gray),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            }
        }
    } else {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val (icon, tint) = when (ext) {
            "apk" -> Icons.Default.Settings to Color(0xFF3DDC84)
            "pdf" -> Icons.Default.Description to Color.Red
            "mp4", "avi", "mkv", "mov" -> Icons.Default.Movie to Color(0xFFFF6B6B)
            "mp3", "wav", "flac", "aac" -> Icons.Default.Audiotrack to Color(0xFF9B59B6)
            "zip", "rar", "7z", "tar", "gz" -> Icons.Default.Archive to Color(0xFFFFD700)
            "txt" -> Icons.AutoMirrored.Filled.TextSnippet to Color.White
            "doc", "docx" -> Icons.Default.Description to Color(0xFF2B579A)
            "xls", "xlsx" -> Icons.Default.Description to Color(0xFF217346)
            "jpg", "jpeg", "png", "gif", "webp" -> Icons.Default.Image to Color.Gray
            else -> Icons.Default.Description to Color.Gray
        }

        Icon(
            imageVector = icon,
            contentDescription = fileName,
            tint = tint,
            modifier = modifier
        )
    }
}

@Composable
fun FullscreenImageDialog(
    imageUrl: String?,
    fileName: String,
    isDownloaded: Boolean,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onOpenInGallery: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .background(Color.Black)
        ) {
            Image(
                painter = rememberAsyncImagePainter(imageUrl),
                contentDescription = fileName,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    )
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)

                            if (scale > 1f) {
                                offsetX += pan.x
                                offsetY += pan.y
                            } else {
                                offsetX = 0f
                                offsetY = 0f
                            }
                        }
                    }
            )

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Schließen",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            if (!isDownloaded) {
                FloatingActionButton(
                    onClick = onDownload,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp),
                    containerColor = Color.White
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Herunterladen",
                        tint = Color.Black
                    )
                }
            } else {
                FloatingActionButton(
                    onClick = onOpenInGallery,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp),
                    containerColor = Color.Black
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "In Galerie öffnen",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Text(
                text = fileName,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(16.dp)
                    .fillMaxWidth()
            )
        }
    }
}

fun getFileNameFromUri(uri: Uri, context: Context): String? {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex != -1 && cursor.moveToFirst()) {
            return cursor.getString(nameIndex)
        }
    }
    return null
}

fun getLocalFileWithPath(fileName: String, remoteSize: Long): File? {
    val downloadsDir =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    val downloadFile = File(downloadsDir, fileName)
    if (downloadFile.exists() && downloadFile.length() == remoteSize) return downloadFile

    val dcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
    val dcimFile = File(dcimDir, "Tabslify/$fileName")
    if (dcimFile.exists() && dcimFile.length() == remoteSize) return dcimFile

    return null
}

fun getMimeType(fileName: String): String {
    return when (fileName.substringAfterLast('.', "").lowercase()) {
        "pdf" -> "application/pdf"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "mp4" -> "video/mp4"
        "mp3" -> "audio/mpeg"
        "txt" -> "text/plain"
        "doc", "docx" -> "application/msword"
        "xls", "xlsx" -> "application/vnd.ms-excel"
        "zip" -> "application/zip"
        else -> "*/*"
    }
}

fun getVideoFirstFrame(file: File): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(file.absolutePath)
        retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
    } catch (_: Exception) {
        null
    } finally {
        retriever.release()
    }
}

fun isImageFile(fileName: String): Boolean {
    val extension = fileName.substringAfterLast(".", "").lowercase()
    return extension in listOf(
        "jpg",
        "jpeg",
        "png",
        "gif",
        "webp",
        "bmp",
        "mov",
        "avi",
        "mkv",
        "3gp"
    )
}

fun isVideoFile(fileName: String): Boolean {
    val cleanName = fileName.substringBefore("?").trim()
    val extension = cleanName.substringAfterLast(".", "").lowercase()
    return extension in setOf("mp4", "mov", "avi", "mkv", "3gp")
}

@RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
fun isOnline(context: Context): Boolean {
    return runCatching {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }.getOrDefault(false)
}