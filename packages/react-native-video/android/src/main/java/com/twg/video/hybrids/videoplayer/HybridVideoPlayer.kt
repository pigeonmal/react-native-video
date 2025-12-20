package com.margelo.nitro.video

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.upstream.DefaultAllocator
import androidx.media3.extractor.metadata.emsg.EventMessage
import androidx.media3.extractor.metadata.id3.Id3Frame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.ui.PlayerView
import com.facebook.proguard.annotations.DoNotStrip
import com.margelo.nitro.NitroModules
import com.margelo.nitro.core.Promise
import com.twg.video.core.LibraryError
import com.twg.video.core.PlayerError
import com.twg.video.core.VideoManager
import com.twg.video.core.extensions.startService
import com.twg.video.core.extensions.stopService
import com.twg.video.core.player.OnAudioFocusChangedListener
import com.twg.video.core.recivers.AudioBecomingNoisyReceiver
import com.twg.video.core.services.playback.VideoPlaybackService
import com.twg.video.core.services.playback.VideoPlaybackServiceConnection
import com.twg.video.core.utils.TrackUtils
import com.twg.video.core.utils.Threading.mainThreadProperty
import com.twg.video.core.utils.Threading.runOnMainThread
import com.twg.video.core.utils.Threading.runOnMainThreadSync
import com.twg.video.core.utils.VideoOrientationUtils
import com.twg.video.core.custom.MyRenderersFactory
import com.twg.video.view.VideoView
import java.lang.ref.WeakReference
import kotlin.math.max

@UnstableApi
@DoNotStrip
class HybridVideoPlayer() : HybridVideoPlayerSpec(), AutoCloseable {
  override var source: HybridVideoPlayerSourceSpec? = null
  override var eventEmitter = HybridVideoPlayerEventEmitter()
    set(value) {
      if (field != value) {
        audioFocusChangedListener.setEventEmitter(value)
        audioBecomingNoisyReceiver.setEventEmitter(value)
      }
      field = value
    }

  private var allocator: DefaultAllocator? = null
  private var context = NitroModules.applicationContext
    ?: run {
    throw LibraryError.ApplicationContextNotFound
  }

  var player: ExoPlayer = runOnMainThreadSync {
    // Build Temporary player that will be replaced when source is loaded
    return@runOnMainThreadSync ExoPlayer.Builder(context).build()
  }

  private var renderersFactory: MyRenderersFactory? = null

  var loadedWithSource = false
  private var currentPlayerView: WeakReference<PlayerView>? = null

  var wasAutoPaused = false

  // Buffer Config
  private var bufferConfig: BufferConfig? = null
    get() = source?.config?.bufferConfig

  // Time updates
  private val progressHandler = Handler(Looper.getMainLooper())
  private var progressRunnable: Runnable? = null

  // Listeners
  private val audioFocusChangedListener = OnAudioFocusChangedListener()
  private val audioBecomingNoisyReceiver = AudioBecomingNoisyReceiver()

  // Service Connection
  private val videoPlaybackServiceConnection = VideoPlaybackServiceConnection(WeakReference(this))

  private companion object {
    private const val TAG = "HybridVideoPlayer"
    private const val DEFAULT_MIN_BUFFER_DURATION_MS = 5000
    private const val DEFAULT_MAX_BUFFER_DURATION_MS = 10000
    private const val DEFAULT_BUFFER_FOR_PLAYBACK_DURATION_MS = 1000
    private const val DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_DURATION_MS = 2000
    private const val DEFAULT_BACK_BUFFER_DURATION_MS = 0
  }

  override var status: VideoPlayerStatus = VideoPlayerStatus.IDLE
    set(value) {
      if (field != value) {
        eventEmitter.onStatusChange(value)
      }
      field = value
    }

  override var progressEventInterval = 250.0

  override var showNotificationControls: Boolean = false
    set(value) {
      val wasRunning = (field || playInBackground)
      val shouldRun = (value || playInBackground)

      if (shouldRun && !wasRunning) {
        VideoPlaybackService.startService(context, videoPlaybackServiceConnection)
      }
      if (!shouldRun && wasRunning) {
        VideoPlaybackService.stopService(this, videoPlaybackServiceConnection)
      }

      field = value
      // Inform service to refresh notification/session layout
      try { videoPlaybackServiceConnection.serviceBinder?.service?.updatePlayerPreferences(this) } catch (_: Exception) {}
    }

  // Player Properties
  override var currentTime: Double by mainThreadProperty(
    get = { player.currentPosition.toDouble() / 1000.0 },
    set = { value -> runOnMainThread { player.seekTo((value * 1000).toLong()) } }
  )

  override var subtitleDelay: Long by mainThreadProperty(
    get = { renderersFactory?.getTextOffset() ?: 0L },
    set = { value -> runOnMainThread { renderersFactory?.setTextOffset(value * 1_000L) } }
  )

  // volume defined by user
  var userVolume: Double = 1.0

  override var volume: Double by mainThreadProperty(
    get = { player.volume.toDouble() },
    set = { value ->
      userVolume = value
      player.volume = value.toFloat()
    }
  )

  override val duration: Double by mainThreadProperty(
    get = {
      return@mainThreadProperty player.duration.toSecondsOrNaN()
    }
  )

  override var loop: Boolean by mainThreadProperty(
    get = {
      player.repeatMode == Player.REPEAT_MODE_ONE
    },
    set = { value ->
      player.repeatMode = if (value) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }
  )

  override var muted: Boolean by mainThreadProperty(
    get = {
      val playerVolume = player.volume.toDouble()
      return@mainThreadProperty playerVolume == 0.0
    },
    set = { value ->
      if (value) {
        userVolume = volume
        player.volume = 0f
      } else {
        player.volume = userVolume.toFloat()
      }
      eventEmitter.onVolumeChange(onVolumeChangeData(
        volume = player.volume.toDouble(),
        muted = muted
      ))
    }
  )

  override var rate: Double by mainThreadProperty(
    get = { player.playbackParameters.speed.toDouble() },
    set = { value ->
      player.playbackParameters = player.playbackParameters.withSpeed(value.toFloat())
    }
  )

  override var mixAudioMode: MixAudioMode = MixAudioMode.AUTO
    set(value) {
      VideoManager.audioFocusManager.requestAudioFocusUpdate()
      field = value
    }

  // iOS only property
  override var ignoreSilentSwitchMode: IgnoreSilentSwitchMode = IgnoreSilentSwitchMode.AUTO

  override var playInBackground: Boolean = false
    set(value) {
      val shouldRun = (value || showNotificationControls)
      val wasRunning = (field || showNotificationControls)

      if (shouldRun && !wasRunning) {
        VideoPlaybackService.startService(context, videoPlaybackServiceConnection)
      }
      if (!shouldRun && wasRunning) {
        VideoPlaybackService.stopService(this, videoPlaybackServiceConnection)
      }
      field = value
      // Update preferences to refresh notifications/registration
      try { videoPlaybackServiceConnection.serviceBinder?.service?.updatePlayerPreferences(this) } catch (_: Exception) {}
    }

  override var playWhenInactive: Boolean = false

  override var isPlaying: Boolean by mainThreadProperty(
    get = { player.isPlaying == true }
  )

  private fun initializePlayer() {
    if (NitroModules.applicationContext == null) {
      throw LibraryError.ApplicationContextNotFound
    }

    val hybridSource = source as? HybridVideoPlayerSource ?: throw PlayerError.InvalidSource

    // Initialize the allocator
    allocator = DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE)

    // Create a LoadControl with the allocator
    val loadControl = DefaultLoadControl.Builder()
      .setAllocator(allocator!!)
      .setBufferDurationsMs(
        bufferConfig?.minBufferMs?.toInt() ?: DEFAULT_MIN_BUFFER_DURATION_MS, // minBufferMs
        bufferConfig?.maxBufferMs?.toInt() ?: DEFAULT_MAX_BUFFER_DURATION_MS, // maxBufferMs
        bufferConfig?.bufferForPlaybackMs?.toInt()
          ?: DEFAULT_BUFFER_FOR_PLAYBACK_DURATION_MS, // bufferForPlaybackMs
        bufferConfig?.bufferForPlaybackAfterRebufferMs?.toInt()
          ?: DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_DURATION_MS // bufferForPlaybackAfterRebufferMs
      )
      .setBackBuffer(
        bufferConfig?.backBufferDurationMs?.toInt()
          ?: DEFAULT_BACK_BUFFER_DURATION_MS, // backBufferDurationMs,
        false // retainBackBufferFromKeyframe
      )
      .build()

    renderersFactory = MyRenderersFactory(
      context,
      hybridSource.config.initialSubtitleDelay ?: 0L
    ).apply {
      setExtensionRendererMode(MyRenderersFactory.EXTENSION_RENDERER_MODE_ON)
      forceEnableMediaCodecAsynchronousQueueing()
      setEnableDecoderFallback(true)
    }

    // Build the player with the LoadControl
    player = ExoPlayer.Builder(context)
      .setLoadControl(loadControl)
      .setLooper(Looper.getMainLooper())
      .setRenderersFactory(renderersFactory!!)
      .build()

    currentPlayerView?.get()?.player = player

    loadedWithSource = true

    player.addListener(playerListener)
    player.addAnalyticsListener(analyticsListener)
    
    setPlayerMediaSource(hybridSource)
  }

  fun setPlayerMediaSource(hybridSource: HybridVideoPlayerSource) {
   val startPosition = hybridSource.config.startPosition
    if (startPosition != null && startPosition > 0L) {
      player.setMediaSource(hybridSource.mediaSource, startPosition)
    } else {
      player.setMediaSource(hybridSource.mediaSource)
    }
  }

  fun preparePlayer(hybridSource: HybridVideoPlayerSource) {
    player.prepare()
    // Emit onLoadStart
    val sourceType = if (hybridSource.uri.startsWith("http")) SourceType.NETWORK else SourceType.LOCAL
    eventEmitter.onLoadStart(onLoadStartData(sourceType = sourceType, source = hybridSource))
    status = VideoPlayerStatus.LOADING
    startProgressUpdates()
  }

  override fun initialize(): Promise<Unit> {
    return Promise.async {
      return@async runOnMainThreadSync {
        initializePlayer()
        preparePlayer(source)
      }
    }
  }

  constructor(source: HybridVideoPlayerSource?) : this() {
    this.source = source
    if (source != null) {
      runOnMainThread {
        if (source.config.initializeOnCreation == true) {
          initializePlayer()
          preparePlayer(source)
        }
      }
    }
    VideoManager.registerPlayer(this)
  }

  override fun play() {
    runOnMainThread {
      player.play()
    }
  }

  override fun pause() {
    runOnMainThread {
      player.pause()
    }
  }

  override fun seekBy(time: Double) {
    currentTime = (currentTime + time).coerceIn(0.0, duration)
  }

  override fun seekTo(time: Double) {
    currentTime = time.coerceIn(0.0, duration)
  }

  override fun replaceSourceAsync(source: Variant_NullType_HybridVideoPlayerSourceSpec?): Promise<Unit> {
    return Promise.async {
      val source = source?.asSecondOrNull()

      if (source == null) {
        release()
        return@async
      }

      val hybridSource = source as? HybridVideoPlayerSource ?: throw PlayerError.InvalidSource

      val oldSource = this.source as? HybridVideoPlayerSource
      oldSource?.sourceLoader?.cancel()

      runOnMainThreadSync {
        // Update source
        this.source = source
        if (!loadedWithSource) {
          initializePlayer()
        } else {
          renderersFactory?.setTextOffset((hybridSource.config.initialSubtitleDelay ?: 0L) * 1_000L)
          setPlayerMediaSource(hybridSource)
        }
        
        // Prepare player
        preparePlayer(hybridSource)
      }
    }
  }

  override fun preload(): Promise<Unit> {
    return Promise.async {
      runOnMainThreadSync {
        if (!loadedWithSource) {
          initializePlayer()
        }

        if (player.playbackState != Player.STATE_IDLE) {
          return@runOnMainThreadSync
        }

        preparePlayer(source)
      }
    }
  }

  override fun release() {
    if (playInBackground || showNotificationControls) {
      VideoPlaybackService.stopService(this, videoPlaybackServiceConnection)
    }

    runOnMainThread {
      VideoManager.unregisterPlayer(this)
      stopProgressUpdates()
      loadedWithSource = false
      source = null
      eventEmitter.clearAllListeners()

      player.removeListener(playerListener)
      player.removeAnalyticsListener(analyticsListener)
      player.release() // Release player
      // Clean Listeners
      audioFocusChangedListener.removeEventEmitter()
      audioBecomingNoisyReceiver.removeEventEmitter()

      // Update status
      status = VideoPlayerStatus.IDLE
    }
  }

  fun movePlayerToVideoView(videoView: VideoView) {
    VideoManager.addViewToPlayer(videoView, this)

    runOnMainThreadSync {
      PlayerView.switchTargetView(player, currentPlayerView?.get(), videoView.playerView)
      currentPlayerView = WeakReference(videoView.playerView)
    }
  }

  override fun dispose() {
    release()
  }

  override fun close() {
    release()
  }

 override val memorySize: Long
    // 1 MiB by default
    get() = allocator?.totalBytesAllocated?.toLong() ?: (1024L * 1024L)


  private fun startProgressUpdates() {
    stopProgressUpdates() // Ensure no multiple runnables
    progressRunnable = object : Runnable {
      override fun run() {
        if (player.playbackState != Player.STATE_IDLE && player.playbackState != Player.STATE_ENDED) {
          val currentTimeSeconds = player.currentPosition / 1000.0
          val bufferedDurationSeconds = player.bufferedPosition / 1000.0
          // bufferDuration is the time from current time that is buffered.
          val playableDurationFromNow = max(0.0, bufferedDurationSeconds - currentTimeSeconds)

          eventEmitter.onProgress(
            onProgressData(
              currentTime = currentTimeSeconds,
              bufferDuration = playableDurationFromNow,
              seekableDuration = player.duration.toSecondsOrNaN()
            )
          )
          progressHandler.postDelayed(this, progressEventInterval.toLong())
        }
      }
    }
    progressHandler.post(progressRunnable ?: return)
  }

  private fun stopProgressUpdates() {
    progressRunnable?.let { progressHandler.removeCallbacks(it) }
    progressRunnable = null
  }

  private val analyticsListener = object: AnalyticsListener {
    override fun onBandwidthEstimate(
      eventTime: AnalyticsListener.EventTime,
      totalLoadTimeMs: Int,
      totalBytesLoaded: Long,
      bitrateEstimate: Long
    ) {
      val videoFormat = player.videoFormat
      eventEmitter.onBandwidthUpdate(
        BandwidthData(
          bitrate = bitrateEstimate.toDouble(),
          width = if (videoFormat != null) videoFormat.width.toDouble() else null,
          height = if (videoFormat != null) videoFormat.height.toDouble() else null
        )
      )
    }
  }

  private val playerListener = object : Player.Listener {
    override fun onPlaybackStateChanged(playbackState: Int) {
      val isPlayingUpdate = player.isPlaying
      val isBufferingUpdate = playbackState == Player.STATE_BUFFERING

      eventEmitter.onPlaybackStateChange(
        onPlaybackStateChangeData(
          isPlaying = isPlayingUpdate,
          isBuffering = isBufferingUpdate
        )
      )

      when (playbackState) {
        Player.STATE_IDLE -> {
          status = VideoPlayerStatus.IDLE
          eventEmitter.onBuffer(false)
        }
        Player.STATE_BUFFERING -> {
          status = VideoPlayerStatus.LOADING
          eventEmitter.onBuffer(true)
        }
        Player.STATE_READY -> {
          status = VideoPlayerStatus.READYTOPLAY
          eventEmitter.onBuffer(false)

          val allTracks = TrackUtils.getAllPlayerTracksInternal(player)
          val selectedVideo: VideoPlayerTrack? = player.videoFormat?.let { format ->
                VideoPlayerTrack(
                  width = format.width.toDouble(),
                  height = format.height.toDouble(),
                  // We don't care for the true information, we use only width and height
                  id = "general", 
                  label = "General",
                  selected = true,
                  language = null
                )
              } ?: allTracks.videos.firstOrNull { it.selected }
          val width = selectedVideo?.width ?: 0.0
          val height = selectedVideo?.height ?: 0.0
//          val rotationDegrees = selectedVideoTrackFormat?.rotationDegrees ?: generalVideoFormat?.rotationDegrees

          eventEmitter.onLoad(
            onLoadData(
              currentTime = player.currentPosition / 1000.0,
              duration = player.duration.toSecondsOrNaN(),
              width = width,
              height = height,
              orientation = VideoOrientationUtils.fromWHR(width, height, null),
              allPlayerTracks = allTracks
            )
          )
          // If player becomes ready and is set to play, start progress updates
          if (player.playWhenReady) {
            startProgressUpdates()
          }

          eventEmitter.onReadyToDisplay()
        }
        Player.STATE_ENDED -> {
          status = VideoPlayerStatus.IDLE // Or a specific 'COMPLETED' status if you add one
          eventEmitter.onEnd()
          eventEmitter.onBuffer(false)
          stopProgressUpdates()
        }
      }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
      super.onIsPlayingChanged(isPlaying)
      eventEmitter.onPlaybackStateChange(
        onPlaybackStateChangeData(
          isPlaying = isPlaying,
          isBuffering = player.playbackState == Player.STATE_BUFFERING
        )
      )
      if (isPlaying) {
        VideoManager.setLastPlayedPlayer(this@HybridVideoPlayer)
        startProgressUpdates()
      } else {
        if (player.playbackState == Player.STATE_ENDED || player.playbackState == Player.STATE_IDLE) {
          stopProgressUpdates()
        }
      }
    }

    override fun onPlayerError(error: PlaybackException) {
      status = VideoPlayerStatus.ERROR
      stopProgressUpdates()
    }

    override fun onPositionDiscontinuity(
      oldPosition: Player.PositionInfo,
      newPosition: Player.PositionInfo,
      reason: Int
    ) {
      if (reason == Player.DISCONTINUITY_REASON_SEEK || reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT) {
        eventEmitter.onSeek(newPosition.positionMs / 1000.0)
      }
      // Update progress immediately after a discontinuity if needed by your logic
       val currentTimeSeconds = newPosition.positionMs / 1000.0
       val bufferedDurationSeconds = player.bufferedPosition / 1000.0
       eventEmitter.onProgress(
         onProgressData(
           currentTime = currentTimeSeconds,
           bufferDuration = max(0.0, bufferedDurationSeconds - currentTimeSeconds),
           seekableDuration = player.duration.toSecondsOrNaN()
         )
       )
    }

    override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
      eventEmitter.onPlaybackRateChange(playbackParameters.speed.toDouble())
    }

    override fun onVolumeChanged(volume: Float) {
      // We get here device volume changes, and if
      // player is not muted we will sync it
      if (!muted) {
        this@HybridVideoPlayer.volume = volume.toDouble()
      }

      VideoManager.audioFocusManager.requestAudioFocusUpdate()
      eventEmitter.onVolumeChange(onVolumeChangeData(
        volume = volume.toDouble(),
        muted = muted
      ))
    }

    override fun onCues(cueGroup: CueGroup) {
      val texts = cueGroup.cues.mapNotNull { it.text?.toString() }
      if (texts.isNotEmpty()) {
        eventEmitter.onTextTrackDataChanged(texts.toTypedArray())
      }
    }

    override fun onMetadata(metadata: Metadata) {
      val timedMetadataObjects = mutableListOf<TimedMetadataObject>()
      for (i in 0 until metadata.length()) {
        val entry = metadata.get(i)

        when (entry) {
          is Id3Frame -> {
            var value = ""

            if (entry is TextInformationFrame) {
              value = entry.values.first()
            }

            timedMetadataObjects.add(TimedMetadataObject(entry.id, value))
          }
          is EventMessage ->
            timedMetadataObjects.add(TimedMetadataObject(entry.schemeIdUri, entry.value))
          else -> Log.d(TAG, "Unknown metadata: $entry")
        }
      }
      if (timedMetadataObjects.isNotEmpty()) {
        eventEmitter.onTimedMetadata(TimedMetadata(metadata = timedMetadataObjects.toTypedArray()))
      }
    }

    override fun onTracksChanged(tracks: Tracks) {
      super.onTracksChanged(tracks)
    }
  }

  // MARK: - Text Track Management

  override fun getAvailableTextTracks(): Array<PlayerTrack> {
    return TrackUtils.getAvailableTextTracks(player)
  }

  override fun getAllPlayerTracks(): AllPlayerTracks {
    return TrackUtils.getAllPlayerTracks(player)
  }

  override fun selectTrackById(type: TrackType, id: String?) {
    TrackUtils.selectTrackById(player, type.value, id)
  }

  override fun selectTrackByIndex(type: TrackType, index: Double?) {
    TrackUtils.selectTrackByIndex(player, type.value, index?.toInt())
  }

  override fun resetForReuse() {
    runOnMainThread {
      stopProgressUpdates()
      this.source = null
      if (player.playbackState != Player.STATE_IDLE) {
        player.stop()
      }
      player.clearMediaItems()
    }
  }

  override val selectedTrack: PlayerTrack?
    get() = TrackUtils.getTextSelectedTrack(player)
}


fun Long.toSecondsOrNaN(): Double =
    if (this == C.TIME_UNSET) Double.NaN else this.toDouble() / 1000.0