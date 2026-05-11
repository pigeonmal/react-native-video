package com.twg.video.core.custom

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.ByteArrayOutputStream

@UnstableApi
class IvInjectingDataSource(private val upstream: DataSource) : DataSource {

    private var patchedBytes: ByteArray? = null
    private var readPosition = 0
    private var isPlaylist = false
    private var uri: Uri? = null

    override fun addTransferListener(p0: TransferListener) {
        upstream.addTransferListener(p0)
    }

    override fun open(p0: DataSpec): Long {
        uri = p0.uri
        isPlaylist = p0.uri.toString().contains(".m3u8")

        if (!isPlaylist) {
            // Completely transparent — zero overhead for MP4, DASH, TS segments, key files
            return upstream.open(p0)
        }

        // Playlist: drain, patch, serve from memory
        upstream.open(p0)
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (true) {
            val n = upstream.read(buf, 0, buf.size)
            if (n == -1) break
            if (n > 0) out.write(buf, 0, n)
        }
        upstream.close()

        val raw = out.toByteArray().toString(Charsets.UTF_8)
        val patched = if (raw.contains("#EXTINF")) patchPlaylist(raw) else raw
        patchedBytes = patched.toByteArray(Charsets.UTF_8)
        readPosition = 0
        return patchedBytes!!.size.toLong()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (!isPlaylist) return upstream.read(buffer, offset, length)
        val bytes = patchedBytes ?: return -1
        if (readPosition >= bytes.size) return -1
        val toRead = minOf(length, bytes.size - readPosition)
        System.arraycopy(bytes, readPosition, buffer, offset, toRead)
        readPosition += toRead
        return toRead
    }

    override fun getUri(): Uri = uri ?: Uri.EMPTY

    override fun close() {
        patchedBytes = null
        readPosition = 0
        if (!isPlaylist) upstream.close()
        // playlist upstream already closed in open()
    }

    private fun patchPlaylist(playlist: String): String {
        val lines = playlist.lines()
        val keyLine = lines.firstOrNull { it.startsWith("#EXT-X-KEY:") } ?: return playlist
        if (keyLine.contains("IV=")) return playlist // already correct, do nothing
        val keyAttributes = keyLine.removePrefix("#EXT-X-KEY:").trimEnd()
        val segRegex = Regex("""seg-(\d+)-""")
        val result = StringBuilder()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            when {
                line.startsWith("#EXT-X-KEY:") -> result.appendLine(line)
                line.startsWith("#EXTINF:") -> {
                    result.appendLine(line)
                    i++
                    if (i < lines.size) {
                        val segUri = lines[i].trim()
                        val segNum = segRegex.find(segUri)?.groupValues?.get(1)?.toLongOrNull()
                        if (segNum != null) {
                            result.appendLine("#EXT-X-KEY:$keyAttributes,IV=${"0x%032X".format(segNum)}")
                        }
                        result.appendLine(segUri)
                    }
                }
                else -> result.appendLine(line)
            }
            i++
        }
        return result.toString()
    }

    class Factory(private val upstreamFactory: DataSource.Factory) : DataSource.Factory {
        override fun createDataSource() = IvInjectingDataSource(upstreamFactory.createDataSource())
    }
}