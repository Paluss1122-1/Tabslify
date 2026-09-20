package com.tabslify.spotifydownloader_own.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tabslify.core.objects.tNotify
import com.tabslify.services.MediaPlayerService
import com.tabslify.spotifydownloader_own.domain.DownloadRepository
import com.tabslify.spotifydownloader_own.domain.DownloadState
import com.tabslify.spotifydownloader_own.domain.generateAndSaveHashtags
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DownloadViewModel(
    private val repository: DownloadRepository,
    context: Context,
    private val httpClient: java.io.Closeable? = null
) : ViewModel() {

    private val appContext: Context = context.applicationContext

    override fun onCleared() {
        runCatching { httpClient?.close() }
    }

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    fun startDownload(url: String) {
        viewModelScope.launch {
            repository.downloadTrack(url).collect { state ->
                _downloadState.value = state
                if (state is DownloadState.Success) {
                    generateAndSaveHashtags(
                        ctx = appContext,
                        trackId = state.trackId,
                        title = state.title,
                        artist = state.artist,
                        album = state.album,
                        fileUri = state.fileUri
                    )
                    showSongDownloadedNotification(state)
                }
            }
        }
    }

    companion object {
        const val DOWNLOAD_CHANNEL_ID = "song_download_channel"
    }

    private fun showSongDownloadedNotification(state: DownloadState.Success) {
        ensureDownloadChannel()
        val songPath = resolveSongPath(state)

        val playBase = Intent(appContext, MediaPlayerService::class.java)
        playBase.action = MediaPlayerService.ACTION_PLAY_ALL_SONGS_AT_INDEX
        playBase.putExtra(MediaPlayerService.EXTRA_SONG_PATH, songPath)
        playBase.setPackage(appContext.packageName)
        val pendingIntent = PendingIntent.getForegroundService(
            appContext, state.trackId.hashCode(), playBase,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(appContext, DOWNLOAD_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("✓ Song heruntergeladen")
            .setContentText("${state.artist} – ${state.title}")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        tNotify(appContext, state.trackId.hashCode(), notification)
    }

    private fun ensureDownloadChannel() {
        val nm = appContext.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(DOWNLOAD_CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    DOWNLOAD_CHANNEL_ID,
                    "Song-Downloads",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Meldet fertige Song-Downloads"
                }
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveSongPath(state: DownloadState.Success): String {
        appContext.contentResolver.query(
            state.fileUri,
            arrayOf(MediaStore.Audio.Media.DATA),
            null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                if (idx != -1) cursor.getString(idx)?.takeIf { it.isNotBlank() }?.let { return it }
            }
        }
        return "${
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        }/Tabslify/${state.fileName}"
    }
}
