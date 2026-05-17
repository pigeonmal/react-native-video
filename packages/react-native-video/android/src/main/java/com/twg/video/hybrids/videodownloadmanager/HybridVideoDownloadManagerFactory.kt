package com.margelo.nitro.video

import com.facebook.proguard.annotations.DoNotStrip

@DoNotStrip
class HybridVideoDownloadManagerFactory : HybridVideoDownloadManagerFactorySpec() {
  override fun createDownloadManager(): HybridVideoDownloadManagerSpec {
    return HybridVideoDownloadManager()
  }

  override val memorySize: Long
    get() = 0
}
