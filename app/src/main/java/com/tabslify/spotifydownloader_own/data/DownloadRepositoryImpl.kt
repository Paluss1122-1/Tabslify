package com.tabslify.spotifydownloader_own.data

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import com.tabslify.core.functions.Id3Tags
import com.tabslify.core.functions.SongHashtags
import com.tabslify.core.functions.getAppCheckToken
import com.tabslify.core.objects.Config
import com.tabslify.spotifydownloader_own.domain.DownloadRepository
import com.tabslify.spotifydownloader_own.domain.DownloadState
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.net.URL
import kotlin.time.Duration.Companion.milliseconds

class DownloadRepositoryImpl(
    private val httpClient: HttpClient,
    private val context: Context
) : DownloadRepository {

    override fun downloadTrack(spotifyUrl: String): Flow<DownloadState> = channelFlow {
        send(DownloadState.Searching)

        try {
            val trackId = spotifyUrl.split("/track/").getOrNull(1)?.split("?")?.getOrNull(0)
            if (trackId == null) {
                send(DownloadState.Error("Ungültige Spotify URL"))
                return@channelFlow
            }
            val sha256 = Config.getAppSignatureSha256(context) ?: run {
                send(DownloadState.Error("App-Signatur konnte nicht validiert werden"))
                return@channelFlow
            }

            send(DownloadState.Downloading(5))

            val prepared = SongHashtags.prepareSong(context, trackId)
            if (prepared.prepared != null) {
                val song = prepared.prepared
                val safeArtist = song.artist.replace(" ", "_")
                val safeTitle = song.title.replace(" ", "_")
                val fileName = "${safeArtist}_${safeTitle}.mp3"

                if (fileExistsInMediaStore(fileName)) {
                    SongHashtags.releaseSong(context, trackId)
                    send(DownloadState.Error("Datei existiert bereits lokal"))
                    return@channelFlow
                }

                send(DownloadState.Downloading(35))
                val finished = httpClient.get(song.url) {
                    timeout {
                        requestTimeoutMillis = 180_000
                        connectTimeoutMillis = 60_000
                        socketTimeoutMillis = 180_000
                    }
                }

                if (finished.status != HttpStatusCode.OK) {
                    SongHashtags.releaseSong(context, trackId)
                    send(DownloadState.Error("Download fehlgeschlagen: ${finished.status}"))
                    return@channelFlow
                }

                val songBytes = readAll(finished.bodyAsChannel())
                if (songBytes.isEmpty()) {
                    SongHashtags.releaseSong(context, trackId)
                    send(DownloadState.Error("Leere Datei erhalten"))
                    return@channelFlow
                }

                send(DownloadState.Downloading(80))
                val fileUri = saveBytesToMediaStore(songBytes, fileName, song.title, song.artist, song.album)
                SongHashtags.releaseSong(context, trackId)

                send(DownloadState.Converting)
                delay(500.milliseconds)
                send(
                    DownloadState.Success(
                        trackId = trackId,
                        title = song.title,
                        artist = song.artist,
                        fileName = fileName,
                        fileUri = fileUri,
                        note = song.hashtags.takeIf { it.isNotBlank() }
                            ?: song.analysisError.takeIf { it.isNotBlank() }
                    )
                )
                return@channelFlow
            }

            val fallbackNote = prepared.reason
            val requestBody = kotlinx.serialization.json.buildJsonObject {
                put("action", "rapidapi_spotify")
                put("payload", kotlinx.serialization.json.buildJsonObject {
                    put("songId", "https://open.spotify.com/track/$trackId")
                })
                put("apiKey", Config.userApiKey(context, "rapidapi"))
            }.toString()

            val appCheckToken = getAppCheckToken()
            val response: HttpResponse =
                httpClient.post("${Config.SUPABASE_URL}/functions/v1/api-proxy") {
                    contentType(ContentType.Application.Json)
                    setBody(requestBody)
                    timeout {
                        requestTimeoutMillis = 120_000
                        connectTimeoutMillis = 60_000
                        socketTimeoutMillis = 120_000
                    }
                    headers {
                        append("Authorization", "Bearer ${Config.SUPABASE_PUBLISHABLE_KEY}")
                        append("X-Android-Cert", sha256)
                        if (appCheckToken != null) {
                            append("X-Firebase-AppCheck", appCheckToken)
                        }
                    }
                }

            if (response.status != HttpStatusCode.OK) {
                send(DownloadState.Error("API Fehler: ${response.status}"))
                return@channelFlow
            }

            val jsonBody = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            val success = jsonBody["success"]?.jsonPrimitive?.content?.toBoolean() ?: false

            if (!success) {
                send(DownloadState.Error("Track konnte nicht gefunden werden"))
                return@channelFlow
            }

            val dataObject = jsonBody["data"]?.jsonObject
            val downloadUrl = dataObject?.get("downloadLink")?.jsonPrimitive?.content
            val trackTitle = dataObject?.get("title")?.jsonPrimitive?.content ?: "Track"
            val artist = dataObject?.get("artist")?.jsonPrimitive?.content ?: "Unknown"

            if (downloadUrl == null) {
                send(DownloadState.Error("Kein Download-Link erhalten"))
                return@channelFlow
            }

            val album = dataObject["album"]?.jsonPrimitive?.content ?: ""
            val coverUrl = dataObject["cover"]?.jsonPrimitive?.content

            val safeArtist = artist.replace(" ", "_")
            val safeTitle = trackTitle.replace(" ", "_")
            val fileName = "${safeArtist}_${safeTitle}.mp3"

            if (fileExistsInMediaStore(fileName)) {
                send(DownloadState.Error("Datei existiert bereits lokal"))
                return@channelFlow
            }

            send(DownloadState.Downloading(20))

            val hashtagJob = async {
                SongHashtags.requestHashtags(context, trackTitle, artist, album)
            }

            val audioResponse: HttpResponse = httpClient.get(downloadUrl) {
                timeout {
                    requestTimeoutMillis = 120_000
                    connectTimeoutMillis = 60_000
                    socketTimeoutMillis = 120_000
                }
            }
            val channel: ByteReadChannel = audioResponse.bodyAsChannel()
            val contentLength = audioResponse.contentLength() ?: 0L

            val coverBytes: ByteArray? = coverUrl?.let {
                try {
                    withTimeout(30_000L.milliseconds) { URL(it).readBytes() }
                } catch (_: Exception) {
                    null
                }
            }

            val hashtags = hashtagJob.await()

            val fileUri = saveFileFromChannel(
                fileName,
                channel,
                contentLength,
                "audio/mpeg",
                trackId,
                trackTitle,
                artist,
                album,
                coverBytes,
                hashtags
            ) { progress ->
                send(DownloadState.Downloading(20 + (progress * 0.7).toInt()))
            }

            send(DownloadState.Converting)
            delay(500.milliseconds)
            send(
                DownloadState.Success(
                    trackId = trackId,
                    title = trackTitle,
                    artist = artist,
                    fileName = fileName,
                    fileUri = fileUri,
                    note = hashtags?.takeIf { it.isNotBlank() }
                        ?: fallbackNote?.takeIf { it.isNotBlank() }
                )
            )
        } catch (e: Exception) {
            send(DownloadState.Error("Download fehlgeschlagen: ${e.localizedMessage}"))
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun readAll(channel: ByteReadChannel): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (!channel.isClosedForRead) {
            val read = channel.readAvailable(buffer, 0, buffer.size)
            if (read == -1) break
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun saveBytesToMediaStore(
        bytes: ByteArray,
        fileName: String,
        title: String,
        artist: String,
        album: String
    ): android.net.Uri {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "audio/mpeg")
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_MUSIC + "/Tabslify"
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: throw Exception("Konnte MediaStore Eintrag nicht erstellen")

        try {
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: throw Exception("Konnte Datei nicht schreiben")

            resolver.update(
                uri,
                ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                    put(MediaStore.Audio.Media.TITLE, title)
                    put(MediaStore.Audio.Media.ARTIST, artist)
                    put(MediaStore.Audio.Media.ALBUM, album)
                },
                null,
                null
            )
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return uri
    }

    private suspend fun saveFileFromChannel(
        fileName: String,
        channel: ByteReadChannel,
        contentLength: Long,
        mimeType: String,
        trackId: String,
        title: String,
        artist: String,
        album: String,
        coverBytes: ByteArray?,
        hashtags: String?,
        onProgress: suspend (Int) -> Unit
    ): android.net.Uri {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/Tabslify")

            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: throw Exception("Konnte MediaStore Eintrag nicht erstellen")

        try {
            withContext(Dispatchers.IO) {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    val id3Tag = buildId3Tag(trackId, title, artist, album, coverBytes, hashtags)
                    outputStream.write(id3Tag)

                    val buffer = ByteArray(8192)
                    var totalBytesRead = 0L
                    var id3Skipped = false
                    var id3SkipBytes = 0

                    while (!channel.isClosedForRead) {
                        val read = channel.readAvailable(buffer, 0, buffer.size)
                        if (read == -1) break

                        var writeOffset = 0
                        var writeLength = read

                        if (!id3Skipped) {
                            if (id3SkipBytes == 0 && read >= 10 &&
                                buffer[0] == 0x49.toByte() &&
                                buffer[1] == 0x44.toByte() &&
                                buffer[2] == 0x33.toByte()
                            ) {
                                val tagSize = ((buffer[6].toInt() and 0x7F) shl 21) or
                                        ((buffer[7].toInt() and 0x7F) shl 14) or
                                        ((buffer[8].toInt() and 0x7F) shl 7) or
                                        (buffer[9].toInt() and 0x7F)
                                id3SkipBytes = 10 + tagSize
                            }
                            if (id3SkipBytes > 0) {
                                val skip = minOf(id3SkipBytes, read)
                                id3SkipBytes -= skip
                                writeOffset = skip
                                writeLength = read - skip
                                if (id3SkipBytes == 0) id3Skipped = true
                            } else {
                                id3Skipped = true
                            }
                        }

                        if (writeLength > 0) outputStream.write(buffer, writeOffset, writeLength)
                        totalBytesRead += read
                        if (contentLength > 0) {
                            val progress = ((totalBytesRead * 100) / contentLength).toInt()
                            withContext(Dispatchers.Main) { onProgress(progress) }
                        }
                    }
                    outputStream.flush()
                }
            }

            val updateValues = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
                put(MediaStore.Audio.Media.TITLE, title)
                put(MediaStore.Audio.Media.ARTIST, artist)
                put(MediaStore.Audio.Media.ALBUM, album)
            }
            resolver.update(uri, updateValues, null, null)

        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return uri
    }

    private fun buildId3Tag(
        trackId: String,
        title: String,
        artist: String,
        album: String,
        coverBytes: ByteArray?,
        hashtags: String?
    ): ByteArray {
        val frames = ByteArrayOutputStream()

        fun writeFrameHeader(id: String, size: Int) {
            frames.write(id.toByteArray(Charsets.ISO_8859_1))
            frames.write(
                byteArrayOf(
                    ((size shr 24) and 0xFF).toByte(),
                    ((size shr 16) and 0xFF).toByte(),
                    ((size shr 8) and 0xFF).toByte(),
                    (size and 0xFF).toByte(),
                    0x00, 0x00
                )
            )
        }

        fun encodeTextFrame(id: String, text: String) {
            val textBytes = byteArrayOf(0x03) + text.toByteArray(Charsets.UTF_8)
            writeFrameHeader(id, textBytes.size)
            frames.write(textBytes)
        }

        encodeTextFrame("TIT2", title)
        encodeTextFrame("TPE1", artist)
        encodeTextFrame("TALB", album)

        val hashtagsValue = hashtags?.trim()
        if (!hashtagsValue.isNullOrEmpty()) {
            val txxx = ByteArrayOutputStream()
            txxx.write(0x03)
            txxx.write(Id3Tags.HASHTAG_DESCRIPTION.toByteArray(Charsets.ISO_8859_1))
            txxx.write(0x00)
            txxx.write(hashtagsValue.toByteArray(Charsets.UTF_8))
            val txxxBytes = txxx.toByteArray()
            writeFrameHeader("TXXX", txxxBytes.size)
            frames.write(txxxBytes)
        }

        val ufidOwner = "http://www.spotify.com".toByteArray(Charsets.ISO_8859_1)
        val ufidId = "spotify:track:$trackId".toByteArray(Charsets.ISO_8859_1)
        val ufidContent = ufidOwner + byteArrayOf(0x00) + ufidId
        writeFrameHeader("UFID", ufidContent.size)
        frames.write(ufidContent)

        if (coverBytes != null) {
            val mimeBytes = "image/jpeg".toByteArray(Charsets.ISO_8859_1)

            val apicHeader = ByteArrayOutputStream().apply {
                write(0x00)
                write(mimeBytes)
                write(0x00)
                write(0x03)
                write(0x00)
            }

            val apicContent = apicHeader.toByteArray() + coverBytes
            writeFrameHeader("APIC", apicContent.size)
            frames.write(apicContent)
        }

        val framesBytes = frames.toByteArray() + ByteArray(Id3Tags.PADDING_BYTES)
        val tagSize = framesBytes.size

        fun toSyncsafe(n: Int) = byteArrayOf(
            ((n shr 21) and 0x7F).toByte(),
            ((n shr 14) and 0x7F).toByte(),
            ((n shr 7) and 0x7F).toByte(),
            (n and 0x7F).toByte()
        )

        val header = byteArrayOf(0x49, 0x44, 0x33, 0x03, 0x00, 0x00) + toSyncsafe(tagSize)
        return header + framesBytes
    }

    private fun HttpResponse.contentLength(): Long? = headers["Content-Length"]?.toLongOrNull()

    private suspend fun fileExistsInMediaStore(fileName: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val projection = arrayOf(MediaStore.MediaColumns._ID)
                val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
                val selectionArgs = arrayOf(fileName)
                context.contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    null
                )?.use { cursor ->
                    cursor.count > 0
                } ?: false
            } catch (_: Exception) {
                false
            }
        }
}