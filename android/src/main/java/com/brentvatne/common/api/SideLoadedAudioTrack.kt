package com.brentvatne.common.api

import android.net.Uri
import com.brentvatne.common.toolbox.ReactBridgeUtils
import com.facebook.react.bridge.ReadableMap

/**
* Class representing a sideLoaded audio track from application
*/
class SideLoadedAudioTrack {
   var url: Uri = Uri.EMPTY
   var sampleMimeType: String? = null
   var headers: MutableMap<String, String> = HashMap()

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is SideLoadedAudioTrack) return false
        return url == other.url && sampleMimeType == other.sampleMimeType
    }

   companion object {
       val SIDELOAD_AUDIO_TRACK_URL = "url"
       val SIDELOAD_AUDIO_TRACK_HEADERS = "headers"
       val SIDELOAD_AUDIO_TRACK_SAMPLE_MIME_TYPE = "sampleMimeType"

       fun parse(src: ReadableMap?): SideLoadedAudioTrack {
            val sideLoadedAudioTrack = SideLoadedAudioTrack()
            if (src == null) {
                return sideLoadedAudioTrack
            }
            sideLoadedAudioTrack.url = Uri.parse(ReactBridgeUtils.safeGetString(src, SIDELOAD_AUDIO_TRACK_URL, ""))
            sideLoadedAudioTrack.sampleMimeType = ReactBridgeUtils.safeGetString(src, SIDELOAD_AUDIO_TRACK_SAMPLE_MIME_TYPE, null)
            sideLoadedAudioTrack.headers = ReactBridgeUtils.toStringMap(ReactBridgeUtils.safeGetMap(src, SIDELOAD_AUDIO_TRACK_HEADERS))
            return sideLoadedAudioTrack
        }
    }
}