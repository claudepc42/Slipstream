package org.schabi.newpipe.util

import android.content.Context
import androidx.preference.PreferenceManager
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.schedulers.Schedulers

/**
 * Returns (progressMillis, durationSeconds) for a video, or null if unknown.
 * Reads from SharedPreferences written by LocalPlayerActivity.
 */
object WatchProgressHelper {
    private val YOUTUBE_ID_REGEX = Regex("[?&]v=([A-Za-z0-9_-]{11})")
    private val YOUTU_BE_REGEX = Regex("youtu\\.be/([A-Za-z0-9_-]{11})")
    private val SHORTS_REGEX = Regex("/shorts/([A-Za-z0-9_-]{11})")

    const val PREFS_POSITION_PREFIX = "local_player_progress_"
    const val PREFS_DURATION_PREFIX = "local_player_duration_"

    fun extractYouTubeVideoId(url: String?): String? {
        if (url == null) return null
        return YOUTUBE_ID_REGEX.find(url)?.groupValues?.get(1)
            ?: YOUTU_BE_REGEX.find(url)?.groupValues?.get(1)
            ?: SHORTS_REGEX.find(url)?.groupValues?.get(1)
    }

    fun thumbnailUrlForId(videoId: String): String {
        return "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
    }

    /**
     * Returns (progressMillis, durationMillis) from SharedPreferences keyed by videoId,
     * or null if no progress has been recorded yet. Runs on IO scheduler.
     */
    fun loadProgress(context: Context, videoId: String): Maybe<Pair<Long, Long>> {
        return Maybe.fromCallable {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val position = prefs.getLong(PREFS_POSITION_PREFIX + videoId, 0L)
            val duration = prefs.getLong(PREFS_DURATION_PREFIX + videoId, 0L)
            if (position <= 0L || duration <= 0L) return@fromCallable null
            Pair(position, duration)
        }.subscribeOn(Schedulers.io())
    }
}
