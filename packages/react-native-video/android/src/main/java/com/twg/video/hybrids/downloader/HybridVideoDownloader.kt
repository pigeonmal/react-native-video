package com.margelo.nitro.video

import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadHelper
import androidx.media3.exoplayer.offline.DownloadRequest as Media3DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import com.margelo.nitro.NitroModules
import com.margelo.nitro.video.DownloadProgress
import com.margelo.nitro.video.DownloadRequest
import com.margelo.nitro.video.DownloadState
import com.margelo.nitro.video.HybridVideoDownloaderSpec
import com.margelo.nitro.video.ListenerSubscription
import com.twg.video.core.downloader.DownloadTracker
import com.twg.video.core.downloader.VideoDownloadService
import com.twg.video.core.downloader.VideoDownloadUtil
import com.twg.video.core.custom.MyRenderersFactory
import com.twg.video.core.utils.TvDetector
import com.twg.video.core.extensions.toStringExtension
import java.io.IOException

@OptIn(UnstableApi::class)
class HybridVideoDownloader : HybridVideoDownloaderSpec(), DownloadTracker.Listener {
    private val context = NitroModules.applicationContext!!
    private val downloadTracker = VideoDownloadUtil.getDownloadTracker(context)
    private val listeners = mutableSetOf<(DownloadProgress) -> Unit>()

    init {
        downloadTracker.addListener(this)
    }

    override fun download(request: DownloadRequest) {
        Log.d("HybridVideoDownloader", "download() called for: ${request.uri}")
        val uri = Uri.parse(request.uri)
        val mediaItemBuilder = MediaItem.Builder()
            .setUri(uri)
            .setMediaId(request.uri)

        // Add external subtitles to the MediaItem so they are downloaded too
        val subtitleConfigs = mutableListOf<MediaItem.SubtitleConfiguration>()
        request.externalSubtitles?.forEach { subtitle ->
            Log.d("HybridVideoDownloader", "Processing external subtitle: ${subtitle.uri} (type: ${subtitle.type})")
            
            val ext = if (subtitle.type == SubtitleType.AUTO) {
                MimeTypeMap.getFileExtensionFromUrl(subtitle.uri)
            } else {
                subtitle.type.toStringExtension()
            }

            val mimeType = when (ext?.lowercase()) {
                "vtt" -> MimeTypes.TEXT_VTT
                "srt" -> MimeTypes.APPLICATION_SUBRIP
                "ass", "ssa" -> MimeTypes.TEXT_SSA
                else -> null
            }

            if (mimeType != null) {
                Log.d("HybridVideoDownloader", "Adding external subtitle configuration with mimeType: $mimeType")
                val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitle.uri))
                    .setMimeType(mimeType)
                    .setLanguage(subtitle.language)
                    .setLabel(subtitle.label)
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build()
                subtitleConfigs.add(subtitleConfig)
            } else {
                Log.w("HybridVideoDownloader", "Could not determine mimeType for subtitle: ${subtitle.uri}. It might not be downloaded.")
            }
        }
        mediaItemBuilder.setSubtitleConfigurations(subtitleConfigs)

        val mediaItem = mediaItemBuilder.build()

        val dataSourceFactory = VideoDownloadUtil.getHttpDataSourceFactory()
        val renderersFactory = MyRenderersFactory(context, 0, TvDetector.isTv(context))

        val helper = DownloadHelper.Factory()
            .setRenderersFactory(renderersFactory)
            .setDataSourceFactory(dataSourceFactory)
            .create(mediaItem)

        Log.d("HybridVideoDownloader", "Preparing DownloadHelper for ${request.uri} with ${subtitleConfigs.size} external subtitles...")
        helper.prepare(object : DownloadHelper.Callback {
            override fun onPrepared(helper: DownloadHelper, tracksInfoAvailable: Boolean) {
                Log.d("HybridVideoDownloader", "onPrepared() tracksAvailable: $tracksInfoAvailable, periods: ${helper.periodCount}")
                
                for (i in 0 until helper.periodCount) {
                    val mappedTrackInfo = helper.getMappedTrackInfo(i)
                    val parametersBuilder = DefaultTrackSelector.Parameters.Builder(context)
                        .setForceHighestSupportedBitrate(true)

                    for (j in 0 until mappedTrackInfo.rendererCount) {
                        val rendererType = mappedTrackInfo.getRendererType(j)
                        val trackGroups = mappedTrackInfo.getTrackGroups(j)

                        if (rendererType == C.TRACK_TYPE_AUDIO || rendererType == C.TRACK_TYPE_TEXT) {
                            Log.d("HybridVideoDownloader", "Selecting all tracks for renderer $j (type $rendererType), groups: ${trackGroups.length}")
                            // Select ALL tracks for audio and text to ensure they are available offline
                            for (groupIndex in 0 until trackGroups.length) {
                                val trackGroup = trackGroups.get(groupIndex)
                                val trackCount = trackGroup.length
                                Log.d("HybridVideoDownloader", "  Adding override for group $groupIndex, tracks: $trackCount")
                                parametersBuilder.addOverride(TrackSelectionOverride(trackGroup, (0 until trackCount).toList()))
                            }
                            // Ensure text tracks are not disabled
                            if (rendererType == C.TRACK_TYPE_TEXT) {
                                parametersBuilder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                            }
                        }
                    }

                    // Clear existing selections and add our combined parameters for this period
                    helper.clearTrackSelections(i)
                    val parameters = parametersBuilder.build()
                    Log.d("HybridVideoDownloader", "Applying track selections for period $i. Total overrides: ${parameters.overrides.size}")
                    helper.addTrackSelection(i, parameters)
                }

                val downloadRequest = helper.getDownloadRequest(null)
                Log.d("HybridVideoDownloader", "Generated DownloadRequest with ${downloadRequest.streamKeys.size} stream keys. Submitting to service.")
                DownloadService.sendAddDownload(
                    context,
                    VideoDownloadService::class.java,
                    downloadRequest,
                    true
                )
                helper.release()
            }

            override fun onPrepareError(helper: DownloadHelper, e: IOException) {
                Log.e("HybridVideoDownloader", "onPrepareError() for ${request.uri}", e)
                helper.release()
            }
        })
    }


    override fun pause(uri: String) {
        Log.d("HybridVideoDownloader", "pause() called for: $uri")
        DownloadService.sendSetStopReason(
            context,
            VideoDownloadService::class.java,
            uri,
            1, // Any non-zero value pauses the download
            false
        )
    }

    override fun resume(uri: String) {
        Log.d("HybridVideoDownloader", "resume() called for: $uri")
        DownloadService.sendSetStopReason(
            context,
            VideoDownloadService::class.java,
            uri,
            Download.STOP_REASON_NONE,
            false
        )
    }

    override fun remove(uri: String) {
        Log.d("HybridVideoDownloader", "remove() called for: $uri")
        DownloadService.sendRemoveDownload(
            context,
            VideoDownloadService::class.java,
            uri,
            false
        )
    }

    override fun getAllDownloads(): Array<DownloadProgress> {
        val all = downloadTracker.getAllDownloads()
        Log.d("HybridVideoDownloader", "getAllDownloads() count: ${all.size}")
        return all
            .filter { 
                val isRemoving = it.state == Download.STATE_REMOVING
                if (isRemoving) Log.d("HybridVideoDownloader", "Filtering out removing download: ${it.request.uri}")
                !isRemoving 
            }
            .map { it.toProgress() }
            .toTypedArray()
    }

    override fun addOnDownloadChangedListener(listener: (progress: DownloadProgress) -> Unit): ListenerSubscription {
        listeners.add(listener)
        Log.d("HybridVideoDownloader", "Added listener. Current listeners: ${listeners.size}")
        
        // Notify listener of current downloads immediately
        downloadTracker.getAllDownloads().forEach { download ->
            listener(download.toProgress())
        }

        return ListenerSubscription {
            listeners.remove(listener)
            Log.d("HybridVideoDownloader", "Removed listener. Current listeners: ${listeners.size}")
        }
    }

    override fun onDownloadsChanged(download: Download) {
        val progress = download.toProgress()
        Log.d("HybridVideoDownloader", "Download changed: ${progress.uri} - ${progress.state} - ${progress.percent}%")
        listeners.forEach { it(progress) }
    }

    override fun onDownloadRemoved(download: Download) {
        val progress = download.toProgress(isDeleted = true)
        Log.d("HybridVideoDownloader", "Download removed (DELETED state): ${progress.uri}")
        listeners.forEach { it(progress) }
    }

    private fun Download.toProgress(isDeleted: Boolean = false): DownloadProgress {
        val state = if (isDeleted) {
            DownloadState.DELETED
        } else {
            when (state) {
                Download.STATE_QUEUED -> DownloadState.QUEUED
                Download.STATE_DOWNLOADING -> DownloadState.DOWNLOADING
                Download.STATE_RESTARTING -> DownloadState.RESTARTING
                Download.STATE_COMPLETED -> DownloadState.COMPLETED
                Download.STATE_FAILED -> DownloadState.FAILED
                Download.STATE_REMOVING -> DownloadState.REMOVING
                else -> DownloadState.PAUSED
            }
        }

        return DownloadProgress(
            uri = request.uri.toString(),
            state = state,
            percent = if (percentDownloaded < 0) 0.0 else percentDownloaded.toDouble(),
            bytesDownloaded = bytesDownloaded.toDouble(),
            contentLength = contentLength.toDouble(),
            error = if (state == DownloadState.FAILED) "Download failed" else null
        )
    }

    override val memorySize: Long
        get() = 0
}
