package com.twg.video.core.custom;

import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.text.TextOutput;

import android.content.Context;
import android.os.Looper;

import java.util.ArrayList;

public class MyRenderersFactory extends DefaultRenderersFactory implements TextSynchronizer, TextFilter {

 private MyTextRenderer textRenderer;
 private long defaultOffsetUs;

  public MyRenderersFactory(Context context, long defaultDelaySub) {
    super(context);
    textRenderer = null;
    defaultOffsetUs = defaultDelaySub;
  }

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

}