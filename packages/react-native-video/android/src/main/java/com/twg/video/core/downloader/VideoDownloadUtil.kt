package com.twg.video.core.downloader

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.cronet.CronetDataSource
import androidx.media3.exoplayer.offline.DownloadManager
import com.margelo.nitro.nitrofetch.NitroFetch
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.Executors

@OptIn(UnstableApi::class)
object VideoDownloadUtil {
    private const val DOWNLOAD_CONTENT_DIRECTORY = "downloads"
    private var downloadManager: DownloadManager? = null
    private var cache: SimpleCache? = null
    private var databaseProvider: StandaloneDatabaseProvider? = null
    private var downloadTracker: DownloadTracker? = null

    @Synchronized
    fun getDownloadTracker(context: Context): DownloadTracker {
        if (downloadTracker == null) {
            downloadTracker = DownloadTracker(context.applicationContext)
        }
        return downloadTracker!!
    }

    @Synchronized
    fun getDownloadManager(context: Context): DownloadManager {
        if (downloadManager == null) {
            downloadManager = DownloadManager(
                context,
                getDatabaseProvider(context),
                getCache(context),
                getHttpDataSourceFactory(),
                Executors.newSingleThreadExecutor()
            ).apply {
                maxParallelDownloads = 3
            }
        }
        return downloadManager!!
    }

    @Synchronized
    fun getCache(context: Context): SimpleCache {
        if (cache == null) {
            val downloadContentDirectory = File(context.getExternalFilesDir(null), DOWNLOAD_CONTENT_DIRECTORY)
            cache = SimpleCache(downloadContentDirectory, NoOpCacheEvictor(), getDatabaseProvider(context))
        }
        return cache!!
    }

    @Synchronized
    private fun getDatabaseProvider(context: Context): StandaloneDatabaseProvider {
        if (databaseProvider == null) {
            databaseProvider = StandaloneDatabaseProvider(context)
        }
        return databaseProvider!!
    }

    fun getHttpDataSourceFactory(): CronetDataSource.Factory {
        return CronetDataSource.Factory(NitroFetch.getEngine(), NitroFetch.ioExecutor)
            .setConnectionTimeoutMs(10_000)
            .setReadTimeoutMs(10_000)
            .setResetTimeoutOnRedirects(true)
            .setHandleSetCookieRequests(true)
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36 Edg/143.0.0.0")
    }

    fun getReadOnlyCacheDataSourceFactory(context: Context): DataSource.Factory {
        val upstreamFactory = DefaultDataSource.Factory(context, getHttpDataSourceFactory())
        return CacheDataSource.Factory()
            .setCache(getCache(context))
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setCacheWriteDataSinkFactory(null) // Read-only for player
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }
}
