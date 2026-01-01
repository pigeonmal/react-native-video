package com.twg.video.core.utils

import android.content.Context
import android.content.pm.PackageManager

object TvDetector {

    @Volatile
    private var cachedIsTv: Boolean? = null

    fun isTv(context: Context): Boolean {
        return cachedIsTv ?: synchronized(this) {
            cachedIsTv ?: context.applicationContext
                .packageManager
                .hasSystemFeature(PackageManager.FEATURE_LEANBACK)
                .also { cachedIsTv = it }
        }
    }
}