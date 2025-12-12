package com.brentvatne.exoplayer

import android.net.Uri
import androidx.media3.common.util.Util
import androidx.media3.datasource.AssetDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.cronet.CronetDataSource
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import com.facebook.react.bridge.ReactContext
import org.chromium.net.CronetEngine
import com.margelo.nitro.nitrofetch.NitroFetch
import java.util.concurrent.Executors

object DataSourceUtil {

    @JvmStatic
    fun buildHttpDataSourceFactory(
        context: ReactContext,
        bandwidthMeter: DefaultBandwidthMeter?,
        requestHeaders: Map<String, String>?
    ): HttpDataSource.Factory {
        // Get Cronet engine and executor from NitroFetch
        // used before NitroFetch.ioExecutor , but cause blocking thread

        val cronetDataSourceFactory = CronetDataSource.Factory(NitroFetch.getEngine(), Executors.newSingleThreadExecutor())
            .setTransferListener(bandwidthMeter)
            .setConnectionTimeoutMs(10_000)
            .setReadTimeoutMs(10_000)
            .setResetTimeoutOnRedirects(true)
            .setHandleSetCookieRequests(true)

        if (requestHeaders != null) {
            cronetDataSourceFactory.setDefaultRequestProperties(requestHeaders)
            if (!requestHeaders.containsKey("User-Agent")) {
                cronetDataSourceFactory.setUserAgent("ExoPlayer")
            }
        } else {
            cronetDataSourceFactory.setUserAgent("ExoPlayer")
        }

        return cronetDataSourceFactory
    }

    @JvmStatic
    fun buildAssetDataSourceFactory(context: ReactContext): DataSource.Factory {
        // Not tested, i don't use asset but it should work
       return DataSource.Factory { AssetDataSource(context) }
    }
}
