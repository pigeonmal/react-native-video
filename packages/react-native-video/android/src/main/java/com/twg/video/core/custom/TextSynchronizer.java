package com.twg.video.core.custom;

public interface TextSynchronizer {
  long getTextOffset();
  void setTextOffset(long value);
  void addTextOffset(long value);
}