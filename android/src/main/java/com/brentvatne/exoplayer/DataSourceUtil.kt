package com.brentvatne.exoplayer

import android.net.Uri
import androidx.media3.common.util.Util
import androidx.media3.datasource.AssetDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import com.facebook.react.bridge.ReactContext
import com.facebook.react.modules.network.CookieJarContainer
import com.facebook.react.modules.network.ForwardingCookieHandler
import com.facebook.react.modules.network.OkHttpClientProvider
import okhttp3.Call
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.net.InetAddress

object DataSourceUtil {
    private var defaultDataSourceFactory: DataSource.Factory? = null
    private var defaultHttpDataSourceFactory: HttpDataSource.Factory? = null
    private var userAgent: String? = null
    private var dnsOverHttps: DnsOverHttps? = null

    private fun getUserAgent(context: ReactContext): String {
        if (userAgent == null) {
            userAgent = Util.getUserAgent(context, context.packageName)
        }
        return userAgent as String
    }

    private fun buildCloudflareDoH(client: OkHttpClient): DnsOverHttps {
        if (dnsOverHttps == null) {
            dnsOverHttps = DnsOverHttps.Builder()
                .client(client)
                .url("https://cloudflare-dns.com/dns-query".toHttpUrl())
                .bootstrapDnsHosts(
                    InetAddress.getByName("1.1.1.1"),
                    InetAddress.getByName("1.0.0.1"),
                    InetAddress.getByName("2606:4700:4700::1111"),
                    InetAddress.getByName("2606:4700:4700::1001")
                )
                .build()
        }
        return dnsOverHttps as DnsOverHttps
    }

    @JvmStatic
    fun getDefaultDataSourceFactory(context: ReactContext, bandwidthMeter: DefaultBandwidthMeter?, requestHeaders: Map<String, String>?): DataSource.Factory {
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
    ): DataSource.Factory = DefaultDataSource.Factory(context, buildHttpDataSourceFactory(context, bandwidthMeter, requestHeaders))

    private fun buildHttpDataSourceFactory(
        context: ReactContext,
        bandwidthMeter: DefaultBandwidthMeter?,
        requestHeaders: Map<String, String>?
    ): HttpDataSource.Factory {
        val baseClient = OkHttpClientProvider.getOkHttpClient()
        
        val clientWithDoH = baseClient.newBuilder()
            .dns(buildCloudflareDoH(baseClient))
            .build()
        
        val container = clientWithDoH.cookieJar as CookieJarContainer
        val handler = ForwardingCookieHandler(context)
        container.setCookieJar(JavaNetCookieJar(handler))
        
        val okHttpDataSourceFactory = OkHttpDataSource.Factory(clientWithDoH as Call.Factory)
            .setTransferListener(bandwidthMeter)

        if (requestHeaders != null) {
            okHttpDataSourceFactory.setDefaultRequestProperties(requestHeaders)
            if (!requestHeaders.containsKey("User-Agent")) {
                okHttpDataSourceFactory.setUserAgent(getUserAgent(context))
            }
        } else {
            okHttpDataSourceFactory.setUserAgent(getUserAgent(context))
        }

        return okHttpDataSourceFactory
    }

    @JvmStatic
    fun buildAssetDataSourceFactory(context: ReactContext?, srcUri: Uri?): DataSource.Factory {
        val dataSpec = DataSpec(srcUri!!)
        val rawResourceDataSource = AssetDataSource(context!!)
        rawResourceDataSource.open(dataSpec)
        return DataSource.Factory { rawResourceDataSource }
    }
}
