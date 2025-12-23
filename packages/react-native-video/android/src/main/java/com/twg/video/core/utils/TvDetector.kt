package com.twg.video.core.utils

import android.content.Context
import android.content.pm.PackageManager

object TvDetector {
    @Volatile private var cached = false
    
    fun isTv(context: Context): Boolean {
        if (cached) return true
        cached = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        return cached
    }
}