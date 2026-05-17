package com.twg.video.core.download

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.cronet.CronetDataSource
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadCursor
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import com.facebook.react.modules.network.OkHttpClientProvider
import com.margelo.nitro.NitroModules
import com.margelo.nitro.nitrofetch.NitroFetch
import com.margelo.nitro.video.ExternalForcedType
import com.margelo.nitro.video.NativeExternalSubtitle
import com.margelo.nitro.video.NativeVideoConfig
import com.margelo.nitro.video.VideoDownloadOptions
import com.margelo.nitro.video.VideoDownloadState
import com.margelo.nitro.video.VideoDownloadTask
import com.twg.video.core.DownloadError
import com.twg.video.core.player.DEFAULT_USER_AGENT
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executors

@UnstableApi
object VideoDownloadStore {
    private const val DOWNLOADS_SUBDIR = "rnv-downloads"
    private const val SUBTITLES_SUBDIR = "rnv-subtitles"
    private const val SUBTITLE_MAP_PREFS = "rnv_subtitle_maps"
    private const val SUBTITLE_MAP_KEY_PREFIX = "map:"
    private const val STOP_REASON_PAUSED = 1
    private const val MAX_PARALLEL_DOWNLOADS = 3

    private val context: Context
        get() = NitroModules.applicationContext
            ?: throw com.twg.video.core.LibraryError.ApplicationContextNotFound

    private val databaseProvider: DatabaseProvider by lazy {
        StandaloneDatabaseProvider(context)
    }

    private val cache: Cache by lazy {
        SimpleCache(
            File(context.cacheDir, DOWNLOADS_SUBDIR),
            NoOpCacheEvictor(),
            databaseProvider,
        )
    }

    private val downloadExecutor = Executors.newFixedThreadPool(4)

    private val downloadManager: DownloadManager by lazy {
        val upstreamFactory =
            CronetDataSource.Factory(NitroFetch.getEngine(), NitroFetch.ioExecutor)
                .setConnectionTimeoutMs(10_000)
                .setReadTimeoutMs(10_000)
                .setResetTimeoutOnRedirects(true)
                .setHandleSetCookieRequests(true)
                .setUserAgent(DEFAULT_USER_AGENT)

        DownloadManager(
            context,
            databaseProvider,
            cache,
            upstreamFactory,
            downloadExecutor,
        ).apply {
            maxParallelDownloads = MAX_PARALLEL_DOWNLOADS
            resumeDownloads()
        }
    }

    fun ensureInitialized() {
        val manager = downloadManager
        manager.maxParallelDownloads = MAX_PARALLEL_DOWNLOADS
        manager.resumeDownloads()
    }

    fun createPlaybackDataSourceFactory(upstreamFactory: DataSource.Factory): DataSource.Factory {
        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    fun enqueueDownload(
        config: NativeVideoConfig,
        options: VideoDownloadOptions
    ): String {
        val uri = config.uri.trim()
        if (uri.isEmpty()) {
            throw DownloadError.InvalidConfig("URI cannot be empty")
        }
        if (!uri.startsWith("http://") && !uri.startsWith("https://")) {
            throw DownloadError.InvalidConfig("Only network sources are supported for downloads")
        }

        val downloadId = options.downloadId.ifBlank {
            buildStableId(uri)
        }

        if (options.downloadExternalSubtitles && !config.externalSubtitles.isNullOrEmpty()) {
            downloadExternalSubtitles(
                downloadId,
                config.externalSubtitles,
                config.headers ?: emptyMap()
            )
        }

        val requestBuilder = DownloadRequest.Builder(downloadId, Uri.parse(uri))
            .setStreamKeys(emptyList())

        when (config.forceType) {
            ExternalForcedType.M3U8 -> requestBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
            ExternalForcedType.MPD -> requestBuilder.setMimeType(MimeTypes.APPLICATION_MPD)
            else -> {
            }
        }

        downloadManager.addDownload(requestBuilder.build())
        return downloadId
    }

    fun pauseDownload(downloadId: String) {
        downloadManager.setStopReason(downloadId, STOP_REASON_PAUSED)
    }

    fun resumeDownload(downloadId: String) {
        downloadManager.resumeDownloads()
        downloadManager.setStopReason(downloadId, Download.STOP_REASON_NONE)
    }

    fun removeDownload(downloadId: String) {
        downloadManager.removeDownload(downloadId)
        clearSubtitleMap(downloadId)
        val subtitleFolder = File(File(context.filesDir, SUBTITLES_SUBDIR), downloadId)
        if (subtitleFolder.exists()) {
            subtitleFolder.deleteRecursively()
        }
    }

    fun clearDownloads() {
        val cursor = downloadManager.downloadIndex.getDownloads()
        cursor.use {
            while (it.moveToNext()) {
                removeDownload(it.download.request.id)
            }
        }
    }

    fun getDownload(downloadId: String): VideoDownloadTask {
        val download = downloadManager.downloadIndex.getDownload(downloadId)
            ?: throw DownloadError.NotFound(downloadId)
        return toTask(download)
    }

    fun getDownloads(): Array<VideoDownloadTask> {
        val cursor: DownloadCursor = downloadManager.downloadIndex.getDownloads()
        val tasks = mutableListOf<VideoDownloadTask>()
        cursor.use {
            while (it.moveToNext()) {
                tasks.add(toTask(it.download))
            }
        }
        return tasks.toTypedArray()
    }

    fun resolveSourceForOffline(config: NativeVideoConfig, downloadId: String): NativeVideoConfig {
        val targetDownloadId = downloadId.ifBlank {
            buildStableId(config.uri)
        }

        val mapping = readSubtitleMap(targetDownloadId)
        if (mapping.isEmpty() || config.externalSubtitles.isNullOrEmpty()) {
            return config
        }

        val remapped = config.externalSubtitles.map { subtitle ->
            val localUri = mapping[subtitle.uri]
            if (localUri.isNullOrBlank()) subtitle else subtitle.copy(uri = localUri)
        }.toTypedArray()

        return config.copy(externalSubtitles = remapped)
    }

    private fun toTask(download: Download): VideoDownloadTask {
        val percentDownloaded = if (download.percentDownloaded == -1.0F) {
            -1.0
        } else {
            download.percentDownloaded.toDouble()
        }

        val bytesTotal = if (download.contentLength == C.LENGTH_UNSET.toLong()) {
            -1.0
        } else {
            download.contentLength.toDouble()
        }

        val failureReason = when (download.failureReason) {
            Download.FAILURE_REASON_NONE -> null
            Download.FAILURE_REASON_UNKNOWN -> "unknown"
            else -> "failure-${download.failureReason}"
        }

        return VideoDownloadTask(
            id = download.request.id,
            uri = download.request.uri.toString(),
            state = toState(download.state),
            bytesDownloaded = download.bytesDownloaded.toDouble(),
            bytesTotal = bytesTotal,
            percentDownloaded = percentDownloaded,
            failureReason = failureReason,
            updatedAtMs = download.updateTimeMs.toDouble(),
        )
    }

    private fun toState(state: Int): VideoDownloadState {
        return when (state) {
            Download.STATE_QUEUED -> VideoDownloadState.QUEUED
            Download.STATE_STOPPED -> VideoDownloadState.PAUSED
            Download.STATE_DOWNLOADING, Download.STATE_RESTARTING -> VideoDownloadState.DOWNLOADING
            Download.STATE_COMPLETED -> VideoDownloadState.COMPLETED
            Download.STATE_FAILED -> VideoDownloadState.FAILED
            Download.STATE_REMOVING -> VideoDownloadState.REMOVING
            else -> VideoDownloadState.QUEUED
        }
    }

    private fun downloadExternalSubtitles(
        downloadId: String,
        subtitles: Array<NativeExternalSubtitle>,
        headers: Map<String, String>,
    ) {
        val subtitleFolder = File(File(context.filesDir, SUBTITLES_SUBDIR), downloadId)
        if (!subtitleFolder.exists() && !subtitleFolder.mkdirs()) {
            throw IllegalStateException("Failed to create subtitle storage folder")
        }

        val client = OkHttpClientProvider.getOkHttpClient()
        val mapping = mutableMapOf<String, String>()

        subtitles.forEachIndexed { index, subtitle ->
            try {
                val originalUri = subtitle.uri
                val extension = subtitle.type.name.lowercase()
                val subtitleFile = File(subtitleFolder, "subtitle-$index.$extension")

                val requestBuilder = Request.Builder().url(originalUri)
                headers.forEach { (key, value) -> requestBuilder.addHeader(key, value) }

                client.newCall(requestBuilder.build()).execute().use { res ->
                    if (!res.isSuccessful) {
                        throw IllegalStateException("HTTP ${res.code}")
                    }
                    val body = res.body ?: throw IllegalStateException("Empty body")
                    body.byteStream().use { input ->
                        subtitleFile.outputStream().use { output ->
                            input.copyTo(output, 32 * 1024)
                        }
                    }
                }

                mapping[originalUri] = subtitleFile.toURI().toString()
            } catch (t: Exception) {
                android.util.Log.w(
                    "VideoDownloadStore",
                    "Failed to download subtitle: ${subtitle.uri}",
                    t,
                )
            }
        }

        if (mapping.isNotEmpty()) {
            writeSubtitleMap(downloadId, mapping)
        } else {
            clearSubtitleMap(downloadId)
        }
    }

    private fun subtitlePrefs() =
        context.getSharedPreferences(SUBTITLE_MAP_PREFS, Context.MODE_PRIVATE)

    private fun writeSubtitleMap(downloadId: String, mapping: Map<String, String>) {
        val json = JSONArray()
        mapping.forEach { (sourceUri, localUri) ->
            json.put(
                JSONObject()
                    .put("sourceUri", sourceUri)
                    .put("localUri", localUri),
            )
        }
        subtitlePrefs()
            .edit()
            .putString("$SUBTITLE_MAP_KEY_PREFIX$downloadId", json.toString())
            .apply()
    }

    private fun clearSubtitleMap(downloadId: String) {
        subtitlePrefs().edit().remove("$SUBTITLE_MAP_KEY_PREFIX$downloadId").apply()
    }

    private fun readSubtitleMap(downloadId: String): Map<String, String> {
        val encoded = subtitlePrefs().getString("$SUBTITLE_MAP_KEY_PREFIX$downloadId", null)
            ?: return emptyMap()

        val result = mutableMapOf<String, String>()
        val array = JSONArray(encoded)
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val sourceUri = item.optString("sourceUri", "")
            val localUri = item.optString("localUri", "")
            if (sourceUri.isNotBlank() && localUri.isNotBlank()) {
                result[sourceUri] = localUri
            }
        }
        return result
    }

    private fun buildStableId(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
