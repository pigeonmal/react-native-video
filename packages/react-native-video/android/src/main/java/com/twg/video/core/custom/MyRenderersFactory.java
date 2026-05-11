package com.twg.video.core.custom;

import androidx.annotation.OptIn;
import androidx.media3.common.util.ExperimentalApi;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.text.TextOutput;
import android.os.Handler;
import androidx.media3.common.util.Log;
import androidx.media3.exoplayer.audio.AudioRendererEventListener;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.exoplayer.video.VideoRendererEventListener;
import android.content.Context;
import android.os.Looper;
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegAudioRenderer;
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegVideoRenderer;

import java.util.ArrayList;

@UnstableApi
public class MyRenderersFactory extends DefaultRenderersFactory implements TextSynchronizer, TextFilter {

 private MyTextRenderer textRenderer;
 private long defaultOffsetUs;
 private boolean isTv;

  public MyRenderersFactory(Context context, long defaultDelaySub, boolean isTv) {
    super(context);
    textRenderer = null;
    isTv = isTv;
    defaultOffsetUs = defaultDelaySub;
  }

    @OptIn(markerClass = ExperimentalApi.class)
    protected void buildTextRenderers(
    Context context,
    TextOutput output,
    Looper outputLooper,
    int extensionRendererMode,
    ArrayList<Renderer> out
  ) {
    textRenderer = new MyTextRenderer(output, outputLooper);
    textRenderer.setTextOffset(defaultOffsetUs);
    textRenderer.experimentalSetLegacyDecodingEnabled(true);
    out.add(textRenderer);
  }

// ---------------------------------------------------------------------------
  // implement: TextSynchronizer
  // ---------------------------------------------------------------------------

  @Override
  public long getTextOffset() {
    // return (textRenderer != null)
    //   ? textRenderer.getTextOffset()
    //   : 0l;
    return defaultOffsetUs;
  }

  @Override
  public void setTextOffset(long value) {
    defaultOffsetUs = value;
    if (textRenderer != null)
      textRenderer.setTextOffset(value);
  }

  @Override
  public void addTextOffset(long value) {
    defaultOffsetUs += value;
    if (textRenderer != null)
      textRenderer.addTextOffset(value);
  }

  // ---------------------------------------------------------------------------
  // implement: TextFilter
  // ---------------------------------------------------------------------------

  @Override
  public void setTextFilters(String[] textFilters) {
    if (textRenderer != null)
      textRenderer.setTextFilters(textFilters);
  }

  @Override
  public void addTextFilters(String[] textFilters) {
    if (textRenderer != null)
      textRenderer.addTextFilters(textFilters);
  }

 @Override
    protected void buildAudioRenderers(
            Context context,
            int extensionRendererMode,
            MediaCodecSelector mediaCodecSelector,
            boolean enableDecoderFallback,
            AudioSink audioSink,
            Handler eventHandler,
            AudioRendererEventListener eventListener,
            ArrayList<Renderer> out) {

        super.buildAudioRenderers(
                context,
                extensionRendererMode,
                mediaCodecSelector,
                enableDecoderFallback,
                audioSink,
                eventHandler,
                eventListener,
                out
        );

        if (extensionRendererMode == EXTENSION_RENDERER_MODE_OFF) return;

        int extensionRendererIndex = out.size();
        if (extensionRendererMode == EXTENSION_RENDERER_MODE_PREFER) {
            extensionRendererIndex--;
        }

        try {
            FfmpegAudioRenderer renderer = new FfmpegAudioRenderer(eventHandler, eventListener, audioSink);
            out.add(extensionRendererIndex++, renderer);
            Log.i(TAG, "Loaded FfmpegAudioRenderer.");
        } catch (Exception e) {
            throw new RuntimeException("Error instantiating Ffmpeg extension", e);
        }
    }

    @Override
    protected void buildVideoRenderers(
            Context context,
            int extensionRendererMode,
            MediaCodecSelector mediaCodecSelector,
            boolean enableDecoderFallback,
            Handler eventHandler,
            VideoRendererEventListener eventListener,
            long allowedVideoJoiningTimeMs,
            ArrayList<Renderer> out) {

        super.buildVideoRenderers(
                context,
                extensionRendererMode,
                mediaCodecSelector,
                enableDecoderFallback,
                eventHandler,
                eventListener,
                allowedVideoJoiningTimeMs,
                out
        );

        if (isTv || extensionRendererMode == EXTENSION_RENDERER_MODE_OFF) return;

        int extensionRendererIndex = out.size();
        if (extensionRendererMode == EXTENSION_RENDERER_MODE_PREFER) {
            extensionRendererIndex--;
        }

        try {
            FfmpegVideoRenderer renderer = new FfmpegVideoRenderer(
                    allowedVideoJoiningTimeMs, eventHandler, eventListener, MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY
            );
            out.add(extensionRendererIndex++, renderer);
            Log.i(TAG, "Loaded FfmpegVideoRenderer.");
        } catch (Exception e) {
            throw new RuntimeException("Error instantiating Ffmpeg extension", e);
        }
    }

    public static final String TAG = "MyRenderersFactory";

}