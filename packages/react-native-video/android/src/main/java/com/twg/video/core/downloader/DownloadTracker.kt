package com.twg.video.core.downloader

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import java.util.concurrent.CopyOnWriteArraySet

@OptIn(UnstableApi::class)
class DownloadTracker(context: Context) {
    interface Listener {
        fun onDownloadsChanged(download: Download)
        fun onDownloadRemoved(download: Download)
    }

    private val listeners = CopyOnWriteArraySet<Listener>()
    private val downloadManager: DownloadManager = VideoDownloadUtil.getDownloadManager(context)

    init {
        downloadManager.addListener(DownloadManagerListener())
    }

    fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    fun getAllDownloads(): List<Download> {
        val downloads = mutableListOf<Download>()
        val cursor = downloadManager.downloadIndex.getDownloads()
        while (cursor.moveToNext()) {
            downloads.add(cursor.download)
        }
        cursor.close()
        return downloads
    }

    private inner class DownloadManagerListener : DownloadManager.Listener {
        override fun onDownloadChanged(downloadManager: DownloadManager, download: Download, finalException: Exception?) {
            listeners.forEach { it.onDownloadsChanged(download) }
        }

        override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
            listeners.forEach { it.onDownloadRemoved(download) }
        }
    }
}
