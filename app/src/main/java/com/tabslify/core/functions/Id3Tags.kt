package com.tabslify.core.functions

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile

object Id3Tags {

    const val HASHTAG_DESCRIPTION = "HASHTAGS"
    const val PADDING_BYTES = 512

    private const val LOG = "Id3Tags"
    private const val SPOTIFY_UFID_OWNER = "http://www.spotify.com"
    private const val SPOTIFY_TRACK_PREFIX = "spotify:track:"
    private const val MAX_TAG_BYTES = 8 * 1024 * 1024
    private const val BUFFER_BYTES = 64 * 1024

    private class Frame(val id: String, val payload: ByteArray)

    private sealed interface TagState {
        data object Absent : TagState
        data object Unsupported : TagState
        class Parsed(val frames: List<Frame>, val totalSize: Int) : TagState
    }

    fun readHashtags(context: Context, uri: Uri, filePath: String?): String? {
        val payload = scanTag(context, uri, filePath) { id, _ -> id == "TXXX" } ?: return null
        return decodeTxxx(payload, HASHTAG_DESCRIPTION)
    }

    fun readSpotifyTrackId(context: Context, uri: Uri, filePath: String?): String? {
        val payload = scanTag(context, uri, filePath) { id, _ -> id == "UFID" } ?: return null
        val ownerEnd = indexOfTerminator(payload, 0, 1)
        if (ownerEnd <= 0) return null
        val owner = String(payload, 0, ownerEnd, Charsets.ISO_8859_1)
        if (!owner.equals(SPOTIFY_UFID_OWNER, ignoreCase = true)) return null
        val idStart = ownerEnd + 1
        if (idStart >= payload.size) return null
        val rawId = String(payload, idStart, payload.size - idStart, Charsets.ISO_8859_1)
        return rawId.removePrefix(SPOTIFY_TRACK_PREFIX).trim().takeIf { it.isNotEmpty() }
    }

    private fun scanTag(
        context: Context,
        uri: Uri,
        filePath: String?,
        wanted: (String, ByteArray) -> Boolean
    ): ByteArray? {
        val found: ByteArray? = readVia(context, uri, filePath) { input ->
            val header = ByteArray(10)
            if (!input.readFully(header)) return@readVia null
            if (header[0] != 0x49.toByte() ||
                header[1] != 0x44.toByte() ||
                header[2] != 0x33.toByte()
            ) {
                return@readVia null
            }
            val versionMajor = header[3].toInt() and 0xFF
            val flags = header[5].toInt() and 0xFF
            if (versionMajor !in 3..4) return@readVia null
            if ((flags and 0x80) != 0 || (flags and 0x40) != 0) return@readVia null
            var remaining = syncsafeToInt(header, 6)
            if (remaining !in 0..MAX_TAG_BYTES) return@readVia null

            val skipBuffer = ByteArray(BUFFER_BYTES)
            while (remaining >= 10) {
                val frameHeader = ByteArray(10)
                if (!input.readFully(frameHeader)) return@readVia null
                val id = String(frameHeader, 0, 4, Charsets.ISO_8859_1)
                if (id[0].code == 0) return@readVia null
                val frameSize =
                    if (versionMajor >= 4) syncsafeToInt(frameHeader, 4)
                    else bigEndianToInt(frameHeader, 4)
                if (frameSize < 0 || frameSize > remaining - 10) return@readVia null
                remaining -= 10 + frameSize
                if (id == "TXXX" || id == "UFID") {
                    val payload = ByteArray(frameSize)
                    if (!input.readFully(payload)) return@readVia null
                    if (wanted(id, payload)) return@readVia payload
                } else {
                    var skipped = 0L
                    while (skipped < frameSize) {
                        val step = minOf(BUFFER_BYTES.toLong(), frameSize - skipped).toInt()
                        val read = input.read(skipBuffer, 0, step)
                        if (read <= 0) return@readVia null
                        skipped += read
                    }
                }
            }
            null
        }
        return found
    }

    fun writeHashtags(context: Context, uri: Uri, filePath: String?, hashtags: String): Boolean {
        val value = hashtags.trim()
        if (value.isEmpty()) return false

        val state = readTagState(context, uri, filePath)
        if (state is TagState.Unsupported) {
            Log.w(LOG, "ID3-Tag nicht unterstützt, Schreiben vermieden: $filePath")
            return false
        }
        val parsed = state as? TagState.Parsed
        val oldTotal = parsed?.totalSize ?: 0
        val kept = (parsed?.frames ?: emptyList()).filterNot {
            it.id == "TXXX" && decodeTxxx(it.payload, HASHTAG_DESCRIPTION) != null
        }
        val frames = kept + Frame("TXXX", buildTxxxPayload(HASHTAG_DESCRIPTION, value))

        val body = ByteArrayOutputStream()
        frames.forEach { frame ->
            body.write(frame.id.toByteArray(Charsets.ISO_8859_1))
            body.write(frameHeaderBytes(frame.payload.size))
            body.write(frame.payload)
        }
        body.write(ByteArray(PADDING_BYTES))
        val bodyBytes = body.toByteArray()
        val newTag = id3HeaderBytes(10 + bodyBytes.size) + bodyBytes

        return if (newTag.size <= oldTotal) {
            overwriteHeader(context, uri, filePath, newTag, oldTotal)
        } else {
            prependTag(context, uri, filePath, newTag, oldTotal)
        }
    }

    private fun overwriteHeader(
        context: Context,
        uri: Uri,
        filePath: String?,
        newTag: ByteArray,
        oldTotal: Int
    ): Boolean = writeVia(context, uri, filePath) { output ->
        output.write(newTag)
        output.write(ByteArray(oldTotal - newTag.size))
        output.flush()
    }

    private fun prependTag(
        context: Context,
        uri: Uri,
        filePath: String?,
        newTag: ByteArray,
        oldTotal: Int
    ): Boolean {
        val temp = try {
            File.createTempFile("id3", ".mp3", context.cacheDir)
        } catch (e: Exception) {
            Log.w(LOG, "Temp-Datei nicht anlegbar: ${e.message}", e)
            return false
        }
        try {
            val copied = readVia(context, uri, filePath) { input ->
                var skipped = 0L
                while (skipped < oldTotal) {
                    val step = minOf(BUFFER_BYTES.toLong(), oldTotal - skipped).toInt()
                    val read = input.read(ByteArray(step))
                    if (read <= 0) break
                    skipped += read
                }
                FileOutputStream(temp).use { target ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        target.write(buffer, 0, read)
                    }
                    target.flush()
                }
                true
            } ?: false
            return copied && writeVia(context, uri, filePath) { output ->
                output.write(newTag)
                FileInputStream(temp).use { source ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    while (true) {
                        val read = source.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                    }
                }
                output.flush()
            }
        } finally {
            if (!temp.delete()) Log.w(LOG, "Temp-Datei blieb liegen: ${temp.name}")
        }
    }

    private fun readTagState(context: Context, uri: Uri, filePath: String?): TagState =
        readVia(context, uri, filePath) { input ->
            val header = ByteArray(10)
            if (!input.readFully(header)) return@readVia TagState.Absent
            if (header[0] != 0x49.toByte() ||
                header[1] != 0x44.toByte() ||
                header[2] != 0x33.toByte()
            ) {
                return@readVia TagState.Absent
            }
            val versionMajor = header[3].toInt() and 0xFF
            val flags = header[5].toInt() and 0xFF
            val size = syncsafeToInt(header, 6)
            if (versionMajor !in 3..4) return@readVia TagState.Unsupported
            if ((flags and 0x80) != 0 || (flags and 0x40) != 0) return@readVia TagState.Unsupported
            if (size !in 0..MAX_TAG_BYTES) return@readVia TagState.Unsupported
            val body = ByteArray(size)
            if (!input.readFully(body)) return@readVia TagState.Unsupported
            TagState.Parsed(parseFrames(versionMajor, body, size), 10 + size)
        } ?: TagState.Unsupported

    private fun parseFrames(versionMajor: Int, body: ByteArray, size: Int): List<Frame> {
        val frames = mutableListOf<Frame>()
        var pos = 0
        while (pos + 10 <= size) {
            val id = String(body, pos, 4, Charsets.ISO_8859_1)
            if (id[0].code == 0) break
            val frameSize =
                if (versionMajor >= 4) syncsafeToInt(body, pos + 4)
                else bigEndianToInt(body, pos + 4)
            if (frameSize <= 0 || pos + 10 + frameSize > size) break
            frames.add(Frame(id, body.copyOfRange(pos + 10, pos + 10 + frameSize)))
            pos += 10 + frameSize
        }
        return frames
    }

    private fun buildTxxxPayload(description: String, value: String): ByteArray {
        val payload = ByteArrayOutputStream()
        payload.write(0x03)
        payload.write(description.toByteArray(Charsets.UTF_8))
        payload.write(0x00)
        payload.write(value.toByteArray(Charsets.UTF_8))
        return payload.toByteArray()
    }

    private fun decodeTxxx(payload: ByteArray, description: String): String? {
        if (payload.isEmpty()) return null
        val encoding = payload[0].toInt() and 0xFF
        val wide = encoding == 1 || encoding == 2
        val descEnd = indexOfTerminator(payload, 1, if (wide) 2 else 1)
        if (descEnd < 0) return null
        val found = decodeText(payload.copyOfRange(1, descEnd), encoding) ?: return null
        if (!found.equals(description, ignoreCase = true)) return null
        val valueStart = descEnd + if (wide) 2 else 1
        if (valueStart >= payload.size) return null
        return decodeText(payload.copyOfRange(valueStart, payload.size), encoding)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun decodeText(bytes: ByteArray, encoding: Int): String? {
        if (bytes.isEmpty()) return null
        return when (encoding) {
            1 -> decodeUtf16(bytes)
            2 -> String(bytes.dropTrailingOddByte(), Charsets.UTF_16BE)
            3 -> String(bytes, Charsets.UTF_8)
            else -> String(bytes, Charsets.ISO_8859_1)
        }
    }

    private fun decodeUtf16(bytes: ByteArray): String {
        val bigEndian = bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()
        val littleEndian = bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()
        val body = if (bigEndian || littleEndian) bytes.copyOfRange(2, bytes.size) else bytes
        return String(body.dropTrailingOddByte(), if (bigEndian) Charsets.UTF_16BE else Charsets.UTF_16LE)
    }

    private fun indexOfTerminator(data: ByteArray, from: Int, width: Int): Int {
        var pos = from
        while (pos + width <= data.size) {
            var allZero = true
            for (index in 0 until width) {
                if (data[pos + index] != 0.toByte()) {
                    allZero = false
                    break
                }
            }
            if (allZero) return pos
            pos += width
        }
        return -1
    }

    private fun syncsafeToInt(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0x7F) shl 21) or
            ((data[offset + 1].toInt() and 0x7F) shl 14) or
            ((data[offset + 2].toInt() and 0x7F) shl 7) or
            (data[offset + 3].toInt() and 0x7F)

    private fun bigEndianToInt(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 24) or
            ((data[offset + 1].toInt() and 0xFF) shl 16) or
            ((data[offset + 2].toInt() and 0xFF) shl 8) or
            (data[offset + 3].toInt() and 0xFF)

    private fun frameHeaderBytes(payloadSize: Int): ByteArray = byteArrayOf(
        ((payloadSize shr 24) and 0xFF).toByte(),
        ((payloadSize shr 16) and 0xFF).toByte(),
        ((payloadSize shr 8) and 0xFF).toByte(),
        (payloadSize and 0xFF).toByte(),
        0x00,
        0x00
    )

    private fun id3HeaderBytes(tagSize: Int): ByteArray = byteArrayOf(
        0x49, 0x44, 0x33, 0x03, 0x00, 0x00,
        ((tagSize shr 21) and 0x7F).toByte(),
        ((tagSize shr 14) and 0x7F).toByte(),
        ((tagSize shr 7) and 0x7F).toByte(),
        (tagSize and 0x7F).toByte()
    )

    private fun ByteArray.dropTrailingOddByte(): ByteArray =
        if (size % 2 == 0) this else copyOfRange(0, size - 1)

    private fun InputStream.readFully(target: ByteArray): Boolean {
        var offset = 0
        while (offset < target.size) {
            val read = read(target, offset, target.size - offset)
            if (read <= 0) return false
            offset += read
        }
        return true
    }

    private fun <T> readVia(
        context: Context,
        uri: Uri,
        filePath: String?,
        block: (InputStream) -> T
    ): T? {
        var stream = openResolverRead(context, uri)
        if (stream == null && filePath != null) {
            stream = try {
                FileInputStream(File(filePath))
            } catch (_: Exception) {
                null
            }
        }
        if (stream == null) {
            Log.w(LOG, "Kein Lesezugriff auf $uri")
            return null
        }
        return try {
            stream.use(block)
        } catch (e: Exception) {
            Log.w(LOG, "Lesen fehlgeschlagen: ${e.message}", e)
            null
        }
    }

    private fun writeVia(
        context: Context,
        uri: Uri,
        filePath: String?,
        block: (OutputStream) -> Unit
    ): Boolean {
        var stream = openResolverWrite(context, uri)
        if (stream == null && filePath != null && Environment.isExternalStorageManager()) {
            stream = try {
                val raf = RandomAccessFile(File(filePath), "rw")
                raf.seek(0)
                object : OutputStream() {
                    override fun write(b: Int) {
                        raf.write(b)
                    }
                    override fun write(b: ByteArray, off: Int, len: Int) {
                        raf.write(b, off, len)
                    }
                    override fun flush() {
                        raf.fd.sync()
                    }
                    override fun close() {
                        raf.close()
                    }
                }
            } catch (_: Exception) {
                null
            }
        }
        if (stream == null) {
            Log.w(LOG, "Kein Schreibzugriff auf $uri")
            return false
        }
        return try {
            stream.use(block)
            true
        } catch (e: Exception) {
            Log.w(LOG, "Schreiben fehlgeschlagen: ${e.message}", e)
            false
        }
    }

    private fun openResolverRead(context: Context, uri: Uri): InputStream? = try {
        context.contentResolver.openInputStream(uri)
    } catch (_: Exception) {
        null
    }

    private fun openResolverWrite(context: Context, uri: Uri): OutputStream? = try {
        context.contentResolver.openOutputStream(uri, "rw")
    } catch (_: Exception) {
        null
    }
}