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

object DataSourceUtil {
private var defaultDataSourceFactory: DataSource.Factory? = null
private var defaultHttpDataSourceFactory: HttpDataSource.Factory? = null
private var userAgent: String? = null


private fun getUserAgent(context: ReactContext): String {
    if (userAgent == null) {
        userAgent = Util.getUserAgent(context, context.packageName)
    }
    return userAgent as String
}

@JvmStatic
fun getDefaultDataSourceFactory(
    context: ReactContext,
    bandwidthMeter: DefaultBandwidthMeter?,
    requestHeaders: Map<String, String>?
): DataSource.Factory {
    if (defaultDataSourceFactory == null || !requestHeaders.isNullOrEmpty()) {
        defaultDataSourceFactory = buildDataSourceFactory(context, bandwidthMeter, requestHeaders)
    }
    return defaultDataSourceFactory as DataSource.Factory
}

@JvmStatic
fun getDefaultHttpDataSourceFactory(
    context: ReactContext,
    bandwidthMeter: DefaultBandwidthMeter?,
    requestHeaders: Map<String, String>?
): HttpDataSource.Factory {
    if (defaultHttpDataSourceFactory == null || !requestHeaders.isNullOrEmpty()) {
        defaultHttpDataSourceFactory = buildHttpDataSourceFactory(context, bandwidthMeter, requestHeaders)
    }
    return defaultHttpDataSourceFactory as HttpDataSource.Factory
}

private fun buildDataSourceFactory(
    context: ReactContext,
    bandwidthMeter: DefaultBandwidthMeter?,
    requestHeaders: Map<String, String>?
): DataSource.Factory = DefaultDataSource.Factory(
    context,
    buildHttpDataSourceFactory(context, bandwidthMeter, requestHeaders)
)

private fun buildHttpDataSourceFactory(
    context: ReactContext,
    bandwidthMeter: DefaultBandwidthMeter?,
    requestHeaders: Map<String, String>?
): HttpDataSource.Factory {
    // Get CronetEngine from NitroFetch
    val engine: CronetEngine = NitroFetch.getEngine()
    val executor = NitroFetch.ioExecutor

    val cronetFactory = CronetDataSource.Factory(engine, executor)
        .setUserAgent(getUserAgent(context))
        .setTransferListener(bandwidthMeter)

    // Apply request headers if provided
    requestHeaders?.let {
        cronetFactory.setDefaultRequestProperties(it)
    }

    return cronetFactory
}

@JvmStatic
fun buildAssetDataSourceFactory(context: ReactContext?, srcUri: Uri?): DataSource.Factory {
    val dataSpec = DataSpec(srcUri!!)
    val assetDataSource = AssetDataSource(context!!)
    assetDataSource.open(dataSpec)
    return DataSource.Factory { assetDataSource }
}


}