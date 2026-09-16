package com.tabslify.quiethoursnotificationhelper

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.tabslify.R
import com.tabslify.core.activities.Tabslify.Companion.serviceScope
import com.tabslify.core.functions.showSimpleNotificationExtern
import com.tabslify.core.objects.Config
import com.tabslify.core.objects.Config.DEL_GAL_CONF
import com.tabslify.core.objects.Config.GAL
import com.tabslify.core.objects.tNotify
import com.tabslify.services.QuietHoursNotificationService
import com.tabslify.services.QuietHoursNotificationService.Companion.ACTION_CANCEL_DELETE
import com.tabslify.services.QuietHoursNotificationService.Companion.ACTION_CONFIRM_DELETE_IMAGE
import com.tabslify.services.QuietHoursNotificationService.Companion.ACTION_DELETE_IMAGE
import com.tabslify.services.QuietHoursNotificationService.Companion.ACTION_NEXT_GALLERY_IMAGE
import com.tabslify.services.QuietHoursNotificationService.Companion.ACTION_PREV_GALLERY_IMAGE
import com.tabslify.services.QuietHoursNotificationService.Companion.EXTRA_IMAGE_INDEX
import com.tabslify.services.QuietHoursNotificationService.Companion.GALLERY_CHANNEL_ID
import com.tabslify.services.QuietHoursNotificationService.Companion.currentGalleryIndex
import com.tabslify.services.QuietHoursNotificationService.Companion.galleryImages
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

data class GalleryImage(
    val uri: Uri,
    val lastModified: Long,
    val createdAt: Long,
    val displayName: String? = null
)

@OptIn(DelicateCoroutinesApi::class)
fun uploadCurrentGalleryImageToSupabase(date: String, imageName: String?, context: Context) {
    serviceScope.launch(Dispatchers.IO) {
        try {
            if (galleryImages.isEmpty()) {
                Handler(Looper.getMainLooper()).post {
                    showSimpleNotificationExtern(
                        context.getString(R.string.keine_galerie),
                        context.getString(R.string.offne_zuerst_die_galerie_mit),
                        20.seconds,
                        context
                    )
                }
                return@launch
            }

            if (currentGalleryIndex < 0 || currentGalleryIndex >= galleryImages.size) {
                Handler(Looper.getMainLooper()).post {
                    showSimpleNotificationExtern(
                        context.getString(R.string.fehler_2),
                        context.getString(R.string.ungultiger_galerie_index),
                        20.seconds,
                        context
                    )
                }
                return@launch
            }

            val imageUri = galleryImages[currentGalleryIndex].uri

            val imageName = if (imageName == null) {
                val cursor = context.contentResolver.query(
                    imageUri,
                    arrayOf(MediaStore.Images.Media.DISPLAY_NAME),
                    null,
                    null,
                    null
                )
                val name = cursor?.use {
                    if (it.moveToFirst()) {
                        it.getString(0)?.substringBeforeLast(".") ?: "image"
                    } else {
                        "image"
                    }
                } ?: "image"
                cursor?.close()
                name
            } else {
                imageName
            }

            val imageBytes =
                context.contentResolver.openInputStream(imageUri)?.use { it.readBytes() }

            if (imageBytes == null) {
                Handler(Looper.getMainLooper()).post {
                    showSimpleNotificationExtern(
                        context.getString(R.string.fehler_2),
                        context.getString(R.string.bild_konnte_nicht_gelesen_werden),
                        20.seconds,
                        context
                    )
                }
                return@launch
            }

            val mimeType = context.contentResolver.getType(imageUri) ?: "image/jpeg"
            val extension = when (mimeType) {
                "image/jpeg" -> "jpg"
                "image/png" -> "png"
                "image/webp" -> "webp"
                else -> "jpg"
            }

            val supabaseUrl = Config.SUPABASE_URL
            val supabaseKey = Config.SUPABASE_PUBLISHABLE_KEY
            val bucketName = "Tagesberichte"
            val imagename = imageName.replace(" ", "_")
            val storagePath = "$date/${imagename}.${extension}"

            val url = URL("$supabaseUrl/storage/v1/object/$bucketName/$storagePath")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer $supabaseKey")
            connection.setRequestProperty("apikey", supabaseKey)
            connection.setRequestProperty("Content-Type", mimeType)
            connection.setRequestProperty("x-upsert", "false")
            connection.setRequestProperty("Content-Length", imageBytes.size.toString())
            connection.connectTimeout = 30000
            connection.readTimeout = 60000
            connection.doOutput = true

            connection.outputStream.use { output ->
                output.write(imageBytes)
                output.flush()
            }

            when (val responseCode = connection.responseCode) {
                in 200..299 -> {
                    Handler(Looper.getMainLooper()).post {
                        showSimpleNotificationExtern(
                            context.getString(R.string.upload_erfolgreich),
                            context.getString(R.string.bild_wurde_hochgeladen, imagename),
                            context = context
                        )
                    }
                }

                400 -> {
                    val error =
                        connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                    if (error.contains("Duplicate") || error.contains("already exists")) {
                        Log.w("QuietHoursService", "⚠️ Existiert bereits: $imagename")
                        Handler(Looper.getMainLooper()).post {
                            showSimpleNotificationExtern(
                                context.getString(R.string.bereits_vorhanden),
                                context.getString(R.string.bild_existiert_bereits, imagename),
                                context = context
                            )
                        }
                    } else {
                        Log.e("QuietHoursService", "❌ Fehler 400: $error")
                        Handler(Looper.getMainLooper()).post {
                            showSimpleNotificationExtern(
                                context.getString(R.string.upload_fehlgeschlagen),
                                context.getString(R.string.fehler_400_2, error),
                                20.seconds,
                                context
                            )
                        }
                    }
                }

                409 -> {
                    Log.w("QuietHoursService", "⚠️ Existiert bereits (409): $imagename")
                    Handler(Looper.getMainLooper()).post {
                        showSimpleNotificationExtern(
                            context.getString(R.string.bereits_vorhanden),
                            context.getString(R.string.bild_existiert_bereits, imagename),
                            context = context
                        )
                    }
                }

                else -> {
                    val error = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    Log.e("QuietHoursService", "❌ Fehler $responseCode: $error")
                    Handler(Looper.getMainLooper()).post {
                        showSimpleNotificationExtern(
                            context.getString(R.string.upload_fehlgeschlagen),
                            context.getString(R.string.fehler_4, responseCode, error),
                            20.seconds,
                            context
                        )
                    }
                }
            }

        } catch (e: Exception) {
            Log.e("QuietHoursService", "❌ Upload-Fehler", e)
            Handler(Looper.getMainLooper()).post {
                showSimpleNotificationExtern(
                    context.getString(R.string.upload_fehlgeschlagen),
                    context.getString(R.string.fehler_msg, e.message),
                    20.seconds,
                    context
                )
            }
        }
    }
}

fun loadGalleryImages(number: Int, context: Context) {
    Log.d("CURRENTINDEX", "$number")
    
    val hasFullAccess = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
    val hasPartialAccess = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
            
    if (!hasFullAccess && !hasPartialAccess) {
        showSimpleNotificationExtern(
            context.getString(R.string.fehler_2),
            context.getString(R.string.zugriff_auf_medien_benotigt),
            20.seconds,
            context = context
        )
        return
    }

    try {
        galleryImages = emptyList()
        val images = mutableListOf<GalleryImage>()

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DISPLAY_NAME
        )

        val sortOrder = "${MediaStore.Images.Media.DATE_MODIFIED} DESC"

        val cursor = context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )

        cursor?.use {
            val idColumn =
                it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val dateColumn =
                it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
            val createdColumn =
                it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val nameColumn =
                it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)

            while (it.moveToNext()) {
                val id = it.getLong(idColumn)
                val dateModified = it.getLong(dateColumn) * 1000
                val dateCreated = it.getLong(createdColumn) * 1000
                val displayName = it.getString(nameColumn)

                val uri = Uri.withAppendedPath(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id.toString()
                )

                images.add(GalleryImage(uri, dateModified, dateCreated, displayName))

                if (images.size >= 5000) break
            }
        }

        galleryImages = images

        if (galleryImages.isEmpty()) {
            showSimpleNotificationExtern(
                context.getString(R.string.galerie_leer),
                context.getString(R.string.keine_bilder_in_deiner_galerie),
                context = context
            )
            return
        }

        currentGalleryIndex = number
        showGalleryImage(number, context)
    } catch (e: Exception) {
        showSimpleNotificationExtern(
            context.getString(R.string.fehler_2),
            context.getString(R.string.galerie_konnte_nicht_geladen_werden, e.message),
            20.seconds,
            context = context
        )
    }
}

fun showDeleteConfirmation(imageIndex: Int, context: Context) {
    try {
        if (galleryImages.isEmpty() || imageIndex < 0 || imageIndex >= galleryImages.size) {
            showSimpleNotificationExtern(context.getString(R.string.fehler_2), context.getString(R.string.ungultiger_bildindex), context = context)
            return
        }

        val imageUri = galleryImages[imageIndex].uri

        val bitmap = try {
            val source = ImageDecoder.createSource(context.contentResolver, imageUri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val targetSize = 512 // Für Bestätigung reicht kleiner
                if (info.size.width > targetSize || info.size.height > targetSize) {
                    val scale =
                        (info.size.width.toFloat() / targetSize).coerceAtLeast(info.size.height.toFloat() / targetSize)
                    decoder.setTargetSize(
                        (info.size.width / scale).toInt(),
                        (info.size.height / scale).toInt()
                    )
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } catch (_: Exception) {
            null
        }

        val deleteBase = Intent(context, QuietHoursNotificationService::class.java)
        deleteBase.action = ACTION_DELETE_IMAGE
        deleteBase.putExtra(EXTRA_IMAGE_INDEX, imageIndex)
        deleteBase.setPackage(context.packageName)
        val deletePendingIntent = PendingIntent.getService(
            context, 81, deleteBase,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelBase = Intent(context, QuietHoursNotificationService::class.java)
        cancelBase.action = ACTION_CANCEL_DELETE
        cancelBase.setPackage(context.packageName)
        val cancelPendingIntent = PendingIntent.getService(
            context, 82, cancelBase,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, GALLERY_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_delete)
            .setContentTitle(context.getString(R.string.bild_loschen))
            .setContentText(context.getString(R.string.bild_von_wirklich_loschen, imageIndex + 1, galleryImages.size))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setGroup("group_confirmations")
            .addAction(android.R.drawable.ic_delete, context.getString(R.string.loschen), deletePendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                context.getString(R.string.abbrechen),
                cancelPendingIntent
            )

        if (bitmap != null) {
            builder.setLargeIcon(bitmap)
                .setStyle(
                    NotificationCompat.BigPictureStyle()
                        .bigPicture(bitmap)
                        .bigLargeIcon(null as Bitmap?)
                )
        }

        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            tNotify(context, DEL_GAL_CONF, builder)
        }

    } catch (e: Exception) {
        Log.e("QuietHoursService", "Error showing delete confirmation", e)
        showSimpleNotificationExtern(
            context.getString(R.string.fehler_2),
            context.getString(R.string.loschbestatigung_konnte_nicht_angezeigt_werden, e.message),
            20.seconds,
            context = context
        )
    }
}

fun deleteGalleryImage(imageIndex: Int, context: Context) {
    try {
        if (galleryImages.isEmpty() || imageIndex < 0 || imageIndex >= galleryImages.size) {
            showSimpleNotificationExtern(context.getString(R.string.fehler_2), context.getString(R.string.ungultiger_bildindex), context = context)
            return
        }

        val imageUri = galleryImages[imageIndex].uri

        val deleted = context.contentResolver.delete(imageUri, null, null)

        if (deleted > 0) {
            val mutableList = galleryImages.toMutableList()
            mutableList.removeAt(imageIndex)
            galleryImages = mutableList

            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.cancel(DEL_GAL_CONF)

            showSimpleNotificationExtern(
                context.getString(R.string.geloscht_2),
                context.getString(R.string.bild_wurde_erfolgreich_geloscht_verbleibend, galleryImages.size),
                context = context
            )

            if (galleryImages.isNotEmpty()) {
                if (currentGalleryIndex >= galleryImages.size) {
                    currentGalleryIndex = galleryImages.size - 1
                }
                showGalleryImage(currentGalleryIndex, context)
            } else {
                notificationManager.cancel(GAL)
                showSimpleNotificationExtern(
                    context.getString(R.string.galerie_leer),
                    context.getString(R.string.alle_bilder_wurden_geloscht),
                    context = context
                )
            }

        } else {
            showSimpleNotificationExtern(
                context.getString(R.string.loschen_fehlgeschlagen),
                context.getString(R.string.bild_konnte_nicht_geloscht_werden),
                20.seconds,
                context = context
            )
        }

    } catch (e: Exception) {
        Log.e("QuietHoursService", "Error deleting gallery image", e)
        showSimpleNotificationExtern(
            context.getString(R.string.fehler_2),
            context.getString(R.string.fehler_beim_loschen, e.message),
            20.seconds,
            context = context
        )
    }
}

private fun showGalleryImage(index: Int, context: Context) {
    try {
        if (galleryImages.isEmpty() || index < 0 || index >= galleryImages.size) {
            showSimpleNotificationExtern(
                context.getString(R.string.fehler_2),
                context.getString(R.string.ungultiger_image_index),
                20.seconds,
                context = context
            )
            return
        }

        val galleryImage = galleryImages[index]
        val imageUri = galleryImage.uri

        val lastModifiedText = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
            .format(Date(galleryImage.lastModified))

        val createdAtText = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
            .format(Date(galleryImage.createdAt))

        val originalBitmap = try {
            val source = ImageDecoder.createSource(context.contentResolver, imageUri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                // Skaliere das Bild auf eine vernünftige Größe für Benachrichtigungen (z.B. max 1024px)
                val targetSize = 1024
                if (info.size.width > targetSize || info.size.height > targetSize) {
                    val scale =
                        (info.size.width.toFloat() / targetSize).coerceAtLeast(info.size.height.toFloat() / targetSize)
                    decoder.setTargetSize(
                        (info.size.width / scale).toInt(),
                        (info.size.height / scale).toInt()
                    )
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE // Wichtig für Notifications
            }
        } catch (e: Exception) {
            Log.e("QuietHoursService", "Error decoding image at index $index", e)
            showSimpleNotificationExtern(
                context.getString(R.string.fehler_2),
                context.getString(R.string.bild_konnte_nicht_geladen_werden),
                20.seconds,
                context = context
            )
            return
        }

        val prevBase = Intent(context, QuietHoursNotificationService::class.java)
        prevBase.action = ACTION_PREV_GALLERY_IMAGE
        prevBase.setPackage(context.packageName)
        val prevPendingIntent = PendingIntent.getService(
            context, 71, prevBase,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextBase = Intent(context, QuietHoursNotificationService::class.java)
        nextBase.action = ACTION_NEXT_GALLERY_IMAGE
        nextBase.setPackage(context.packageName)
        val nextPendingIntent = PendingIntent.getService(
            context, 72, nextBase,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val confirmDeleteBase =
            Intent(context, QuietHoursNotificationService::class.java)
        confirmDeleteBase.action = ACTION_CONFIRM_DELETE_IMAGE
        confirmDeleteBase.putExtra(EXTRA_IMAGE_INDEX, index)
        confirmDeleteBase.setPackage(context.packageName)
        val confirmDeletePendingIntent = PendingIntent.getService(
            context, 73, confirmDeleteBase,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val viewProbe = Intent(Intent.ACTION_VIEW)
        viewProbe.setDataAndType(imageUri, "image/*")
        val viewer = context.packageManager.resolveActivity(
            viewProbe, PackageManager.ResolveInfoFlags.of(0)
        )?.activityInfo
        val openPendingIntent = viewer?.let {
            val viewBase = Intent(Intent.ACTION_VIEW)
            viewBase.setDataAndType(imageUri, "image/*")
            viewBase.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            viewBase.setClassName(it.packageName, it.name)
            PendingIntent.getActivity(
                context, 74, viewBase,
                PendingIntent.FLAG_IMMUTABLE
            )
        }

        val notification = NotificationCompat.Builder(context, GALLERY_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_gallery)
            .setContentTitle("📷 ${galleryImage.displayName ?: context.getString(R.string.galerie)}")
            .setContentText(context.getString(R.string.tippen_zum_loschen_wischen_fur))
            .setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(originalBitmap)
                    .bigLargeIcon(null as Bitmap?)
                    .showBigPictureWhenCollapsed(true)
                    .setSummaryText(context.getString(R.string.bild, index + 1, galleryImages.size, lastModifiedText, createdAtText))
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setAutoCancel(false)
            .setGroup("group_services")
            .setGroupSummary(false)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_media_previous, "◀", prevPendingIntent)
            .addAction(android.R.drawable.ic_menu_view, context.getString(R.string.loschen), confirmDeletePendingIntent)
            .addAction(android.R.drawable.ic_media_next, "▶", nextPendingIntent)
            .build()

        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            tNotify(context, GAL, notification)
        }
    } catch (e: Exception) {
        Log.e("QuietHoursService", "Error showing gallery image", e)
        showSimpleNotificationExtern(
            context.getString(R.string.fehler_2),
            context.getString(R.string.galerie_konnte_nicht_angezeigt_werden, e.message),
            20.seconds,
            context = context
        )
    }
}

fun showNextGalleryImage(context: Context) {
    if (galleryImages.isEmpty()) {
        showSimpleNotificationExtern(
            context.getString(R.string.galerie_leer_2),
            context.getString(R.string.keine_bilder_zum_anzeigen),
            20.seconds,
            context = context
        )
        return
    }

    currentGalleryIndex = (currentGalleryIndex + 1) % galleryImages.size
    showGalleryImage(currentGalleryIndex, context)
}

fun showPreviousGalleryImage(context: Context) {
    if (galleryImages.isEmpty()) {
        showSimpleNotificationExtern(
            context.getString(R.string.galerie_leer_2),
            context.getString(R.string.keine_bilder_zum_anzeigen),
            20.seconds,
            context = context
        )
        return
    }

    currentGalleryIndex = if (currentGalleryIndex - 1 < 0) {
        galleryImages.size - 1
    } else {
        currentGalleryIndex - 1
    }
    showGalleryImage(currentGalleryIndex, context)
}