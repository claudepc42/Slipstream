package org.schabi.newpipe.util

import android.content.Context
import android.net.Uri
import androidx.preference.PreferenceManager

object SubtitleHelper {

    private const val PREFS_SUBTITLE_PREFIX = "subtitle_uri_"

    fun storeSubtitleUri(context: Context, videoKey: String, uri: Uri) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit().putString(PREFS_SUBTITLE_PREFIX + videoKey, uri.toString()).apply()
    }

    fun getSubtitleUri(context: Context, videoKey: String): Uri? {
        val str = PreferenceManager.getDefaultSharedPreferences(context)
            .getString(PREFS_SUBTITLE_PREFIX + videoKey, null) ?: return null
        return Uri.parse(str)
    }

    /** Infer ExoPlayer MIME type from file extension. */
    fun mimeTypeFor(uri: Uri): String {
        val path = uri.path ?: uri.toString()
        return when {
            path.endsWith(".vtt", ignoreCase = true) -> "text/vtt"
            path.endsWith(".ttml", ignoreCase = true) -> "application/ttml+xml"
            else -> "application/x-subrip" // .srt default
        }
    }
}
