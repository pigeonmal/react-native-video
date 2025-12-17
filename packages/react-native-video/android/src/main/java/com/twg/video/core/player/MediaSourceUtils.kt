package com.twg.video.core.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.source.MediaSource
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.drm.DrmSessionManager
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.FilteringMediaSource
import com.margelo.nitro.video.HybridVideoPlayerSource
import com.twg.video.core.LibraryError
import com.twg.video.core.SourceError
import com.twg.video.core.plugins.PluginsRegistry
import com.margelo.nitro.video.ExternalAudio
import androidx.media3.exoplayer.source.MergingMediaSource

@OptIn(UnstableApi::class)
@Throws(SourceError::class)
fun buildMediaSource(context: Context, source: HybridVideoPlayerSource, mediaItem: MediaItem): MediaSource {

  val dataSourceFactory = PluginsRegistry.shared.overrideMediaDataSourceFactory(
    source,
    buildBaseDataSourceFactory(context, source)
  )

  val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

  source.config.drm?.let {
    val drmSessionManager = source.drmSessionManager ?: throw LibraryError.DRMPluginNotFound
    mediaSourceFactory.setDrmSessionManagerProvider { drmSessionManager }
  }

  val mediasource = PluginsRegistry.shared.overrideMediaSourceFactory(
    source,
    mediaSourceFactory,
    dataSourceFactory
  ).createMediaSource(mediaItem)

  return source.config.externalAudios
    ?.takeIf { it.isNotEmpty() }
    ?.let { configAudioSources(mediasource, it, dataSourceFactory) }
    ?: mediasource
}

fun configAudioSources(
    mediaSource: MediaSource,
    externalAudios: Array<ExternalAudio>,
    dataSourceFactory: DataSource.Factory
): MediaSource {
    val sourcesToMerge = mutableListOf(mediaSource)

    externalAudios.forEachIndexed { index, track ->
        try {
            val audioItem = MediaItem.Builder()
                .setUri(track.uri)
                .setMimeType(track.mimetype)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("external-audio-$index")
                        .build()
                )
                .build()

          // TODO: HEADERS
            val audioSource = FilteringMediaSource(
                DefaultMediaSourceFactory(dataSourceFactory).createMediaSource(audioItem),
                C.TRACK_TYPE_AUDIO
            )

            sourcesToMerge.add(audioSource)
        } catch (e: Exception) {

        }
    }

    // If no external audio was successfully added, return original mediaSource
    return if (sourcesToMerge.size > 1) MergingMediaSource(*sourcesToMerge.toTypedArray())
    else mediaSource
}