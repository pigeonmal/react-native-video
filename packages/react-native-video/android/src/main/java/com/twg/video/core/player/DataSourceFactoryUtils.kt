package com.twg.video.core.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.facebook.react.bridge.ReactContext
import com.facebook.react.modules.network.CookieJarContainer
import com.facebook.react.modules.network.ForwardingCookieHandler
import com.facebook.react.modules.network.OkHttpClientProvider
import com.margelo.nitro.video.HybridVideoPlayerSourceSpec
import okhttp3.JavaNetCookieJar
import androidx.media3.datasource.cronet.CronetDataSource
import com.margelo.nitro.nitrofetch.NitroFetch
import java.util.concurrent.Executors

const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36 Edg/143.0.0.0" 

fun buildBaseDataSourceFactory(context: Context, source: HybridVideoPlayerSourceSpec): DefaultDataSource.Factory {
  return if (source.uri.startsWith("http")) {
  DefaultDataSource.Factory(
    context,
    if (source.config.forceOkhttp == true) {
      buildHttpDataSourceFactory(context, source)
    } else {
      buildCronetHttpDataSourceFactory(source)
    }
  )
  } else {
    DefaultDataSource.Factory(context)
  }
}

@OptIn(UnstableApi::class)
fun buildHttpDataSourceFactory(context: Context, source: HybridVideoPlayerSourceSpec): OkHttpDataSource.Factory {
  val client = OkHttpClientProvider.getOkHttpClient()

  if (context is ReactContext) {
    val handler = ForwardingCookieHandler(context)
    (client.cookieJar as CookieJarContainer).setCookieJar(JavaNetCookieJar(handler))
  }

  val factory = OkHttpDataSource.Factory(client)

  val headers: Map<String, String>? = source.config.headers

  if (headers != null) {
    factory.setDefaultRequestProperties(headers)
  }

  if (headers == null || !headers.containsKey("User-Agent")) {
    factory.setUserAgent(DEFAULT_USER_AGENT)
  }

  return factory
}

@OptIn(UnstableApi::class)
fun buildCronetHttpDataSourceFactory(source: HybridVideoPlayerSourceSpec): CronetDataSource.Factory {
  // Get Cronet engine and executor from NitroFetch
  // used before NitroFetch.ioExecutor , but cause blocking thread
  val factory = CronetDataSource.Factory(NitroFetch.getEngine(), NitroFetch.ioExecutor)
    .setConnectionTimeoutMs(10_000)
    .setReadTimeoutMs(10_000)
    .setResetTimeoutOnRedirects(true)
    .setHandleSetCookieRequests(true)

  val headers: Map<String, String>? = source.config.headers

  if (headers != null) {
    factory.setDefaultRequestProperties(headers)
  }

  if (headers == null || !headers.containsKey("User-Agent")) {
    factory.setUserAgent(DEFAULT_USER_AGENT)
  }

  return factory
}
