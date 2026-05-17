package com.margelo.nitro.video

import com.margelo.nitro.core.Promise
import com.twg.video.core.download.VideoDownloadStore

class HybridVideoDownloadManager : HybridVideoDownloadManagerSpec() {
  init {
    VideoDownloadStore.ensureInitialized()
  }

  override fun enqueueDownload(
    config: NativeVideoConfig,
    options: VideoDownloadOptions,
  ): Promise<String> {
    return Promise.async {
      VideoDownloadStore.enqueueDownload(config, options)
    }
  }

  override fun pauseDownload(downloadId: String): Promise<Unit> {
    return Promise.async {
      VideoDownloadStore.pauseDownload(downloadId)
    }
  }

  override fun resumeDownload(downloadId: String): Promise<Unit> {
    return Promise.async {
      VideoDownloadStore.resumeDownload(downloadId)
    }
  }

  override fun removeDownload(downloadId: String): Promise<Unit> {
    return Promise.async {
      VideoDownloadStore.removeDownload(downloadId)
    }
  }

  override fun getDownload(downloadId: String): Promise<VideoDownloadTask> {
    return Promise.async {
      VideoDownloadStore.getDownload(downloadId)
    }
  }

  override fun getDownloads(): Promise<Array<VideoDownloadTask>> {
    return Promise.async {
      VideoDownloadStore.getDownloads()
    }
  }

  override fun resolveSourceForOffline(
    config: NativeVideoConfig,
    downloadId: String,
  ): Promise<NativeVideoConfig> {
    return Promise.async {
      VideoDownloadStore.resolveSourceForOffline(config, downloadId)
    }
  }

  override fun clearDownloads(): Promise<Unit> {
    return Promise.async {
      VideoDownloadStore.clearDownloads()
    }
  }

  override val memorySize: Long
    get() = 0
}
