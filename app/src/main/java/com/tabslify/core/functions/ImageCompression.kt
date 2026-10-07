package com.tabslify.core.functions

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.core.graphics.scale
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val THUMBNAIL_MAX_PX = 480
const val THUMBNAIL_JPEG_QUALITY = 80

private const val THUMBNAIL_CACHE_DIR = "video_thumbnails"

fun videoThumbnailKey(file: File): String =
    "${file.absolutePath}|${file.lastModified()}|${file.length()}"

fun videoThumbnailKey(uri: String, lastModified: Long): String =
    "$uri|$lastModified"

fun scaledVideoFirstFrame(file: File, maxPx: Int = THUMBNAIL_MAX_PX): Bitmap? =
    scaledFirstFrame(maxPx) { it.setDataSource(file.absolutePath) }

fun scaledVideoFirstFrame(
    context: Context,
    uri: Uri,
    maxPx: Int = THUMBNAIL_MAX_PX
): Bitmap? = scaledFirstFrame(maxPx) { it.setDataSource(context, uri) }

private fun scaledFirstFrame(
    maxPx: Int,
    prepare: (MediaMetadataRetriever) -> Unit
): Bitmap? {
    val retriever = MediaMetadataRetriever()
    try {
        prepare(retriever)
        val sourceWidth =
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        val sourceHeight =
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        if (sourceWidth <= 0 || sourceHeight <= 0) return null

        val longest = maxOf(sourceWidth, sourceHeight)
        val factor = if (longest > maxPx) maxPx.toFloat() / longest else 1f
        val targetWidth = (sourceWidth * factor).toInt().coerceAtLeast(1)
        val targetHeight = (sourceHeight * factor).toInt().coerceAtLeast(1)

        return retriever.getScaledFrameAtTime(
            0L,
            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
            targetWidth,
            targetHeight
        )
    } catch (_: Exception) {
        return null
    } finally {
        runCatching { retriever.release() }
    }
}

suspend fun videoThumbnail(
    context: Context,
    key: String,
    loadFrame: () -> Bitmap?
): Bitmap? = withContext(Dispatchers.IO) {
    val cacheFile = thumbnailCacheFile(context, key)
    if (cacheFile.exists()) {
        BitmapFactory.decodeFile(cacheFile.absolutePath)
    } else {
        val bitmap = loadFrame() ?: return@withContext null
        try {
            cacheFile.parentFile?.mkdirs()
            FileOutputStream(cacheFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, THUMBNAIL_JPEG_QUALITY, out)
            }
        } catch (_: Exception) {
            cacheFile.delete()
        }
        bitmap
    }
}

fun clearVideoThumbnailCache(context: Context) {
    File(context.cacheDir, THUMBNAIL_CACHE_DIR).deleteRecursively()
}

private fun thumbnailCacheFile(context: Context, key: String): File {
    val digest = MessageDigest.getInstance("SHA-1").digest(key.toByteArray())
    val name = digest.joinToString("") { "%02x".format(it.toInt() and 0xFF) } + ".jpg"
    return File(File(context.cacheDir, THUMBNAIL_CACHE_DIR), name)
}

fun compressForUpload(
    bytes: ByteArray,
    fileName: String,
    quality: Int = 80,
    maxSize: Int = 1600
): Pair<ByteArray, String> {
    val lower = fileName.lowercase()
    val isImage = lower.endsWith(".png") || lower.endsWith(".jpg") ||
            lower.endsWith(".jpeg") || lower.endsWith(".webp") || lower.endsWith(".bmp")
    if (!isImage) return bytes to fileName

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

    var sampleSize = 1
    var w = bounds.outWidth
    var h = bounds.outHeight
    if (w <= 0 || h <= 0) return bytes to fileName
    while (w > maxSize * 2 || h > maxSize * 2) {
        sampleSize *= 2
        w /= 2
        h /= 2
    }

    val decoded = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decoded)
        ?: return bytes to fileName

    val scaled = if (bitmap.width > maxSize || bitmap.height > maxSize) {
        val ratio = maxSize.toFloat() / maxOf(bitmap.width, bitmap.height)
        bitmap.scale((bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
    } else bitmap

    val out = ByteArrayOutputStream()
    try {
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
    } finally {
        if (scaled !== bitmap) runCatching { scaled.recycle() }
        runCatching { bitmap.recycle() }
    }

    val jpgName = fileName.substringBeforeLast(".") + ".jpg"
    return out.toByteArray() to jpgName
}