package com.tabslify.spotifydownloader_own.domain

import android.net.Uri

sealed class DownloadState {
    object Idle : DownloadState()
    object Searching : DownloadState()
    data class Downloading(val progress: Int) : DownloadState()
    object Converting : DownloadState()
    data class Success(
        val trackId: String,
        val title: String,
        val artist: String,
        val fileName: String,
        val fileUri: Uri,
        val note: String? = null
    ) : DownloadState()

    data class Error(val message: String) : DownloadState()
}
