package org.schabi.newpipe.cast

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import java.io.InputStream

/**
 * Minimal HTTP server that serves a single local file over the local network so that
 * a Chromecast device (on the same WiFi) can request it.
 *
 * Supports HTTP Range requests so the receiver can seek in the video.
 */
class LocalFileHttpServer(
    private val contentResolver: ContentResolver,
    port: Int = PORT
) : NanoHTTPD(port) {

    private var fileUri: Uri? = null
    private var fileMime: String = "video/mp4"
    private var fileSize: Long = -1L

    fun setFile(uri: Uri, mimeType: String, size: Long) {
        fileUri = uri
        fileMime = mimeType
        fileSize = size
    }

    fun localUrl(localIp: String): String = "http://$localIp:$PORT/video"

    override fun serve(session: IHTTPSession): Response {
        val uri = fileUri ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "no file")

        val rangeHeader = session.headers["range"]
        return if (rangeHeader != null) {
            serveRange(uri, rangeHeader)
        } else {
            serveWhole(uri)
        }
    }

    private fun serveWhole(uri: Uri): Response {
        val stream: InputStream = openStream(uri) ?: return notFound()
        return if (fileSize > 0) {
            newFixedLengthResponse(Response.Status.OK, fileMime, stream, fileSize)
        } else {
            newChunkedResponse(Response.Status.OK, fileMime, stream)
        }
    }

    private fun serveRange(uri: Uri, rangeHeader: String): Response {
        if (fileSize <= 0) return serveWhole(uri)

        val range = parseRange(rangeHeader, fileSize) ?: return serveWhole(uri)
        val (start, end) = range
        val length = end - start + 1

        val stream: InputStream = openStream(uri) ?: return notFound()
        try {
            stream.skip(start)
        } catch (e: Exception) {
            stream.close()
            return serveWhole(uri)
        }

        val response = newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, fileMime, stream, length)
        response.addHeader("Content-Range", "bytes $start-$end/$fileSize")
        response.addHeader("Accept-Ranges", "bytes")
        return response
    }

    private fun openStream(uri: Uri): InputStream? {
        return try {
            contentResolver.openInputStream(uri)
        } catch (e: Exception) {
            Log.e(TAG, "Cannot open $uri", e)
            null
        }
    }

    private fun notFound() = newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "not found")

    private fun parseRange(rangeHeader: String, total: Long): Pair<Long, Long>? {
        val match = Regex("bytes=(\\d*)-(\\d*)").find(rangeHeader) ?: return null
        val startStr = match.groupValues[1]
        val endStr = match.groupValues[2]
        val start = if (startStr.isEmpty()) 0L else startStr.toLongOrNull() ?: return null
        val end = if (endStr.isEmpty()) total - 1 else endStr.toLongOrNull() ?: total - 1
        return if (start > end || start >= total) null else Pair(start, minOf(end, total - 1))
    }

    companion object {
        private const val TAG = "LocalFileHttpServer"
        const val PORT = 8765
    }
}
