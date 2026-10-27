package org.schabi.newpipe.util

import android.content.Context
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.NewPipeDatabase

/**
 * Returns (progressMillis, durationSeconds) for a YouTube video URL, or null if unknown.
 */
object WatchProgressHelper {
    private val YOUTUBE_ID_REGEX = Regex("[?&]v=([A-Za-z0-9_-]{11})")
    private val YOUTU_BE_REGEX = Regex("youtu\\.be/([A-Za-z0-9_-]{11})")
    private val SHORTS_REGEX = Regex("/shorts/([A-Za-z0-9_-]{11})")
    private const val YOUTUBE_SERVICE_ID = 0

    fun extractYouTubeVideoId(url: String?): String? {
        if (url == null) return null
        return YOUTUBE_ID_REGEX.find(url)?.groupValues?.get(1)
            ?: YOUTU_BE_REGEX.find(url)?.groupValues?.get(1)
            ?: SHORTS_REGEX.find(url)?.groupValues?.get(1)
    }

    fun thumbnailUrlForId(videoId: String): String {
        return "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
    }

    /** Returns (progressMillis, durationSeconds) or null. Runs on IO scheduler. */
    fun loadProgress(context: Context, url: String): Maybe<Pair<Long, Long>> {
        return Maybe.fromCallable {
            val db = NewPipeDatabase.getInstance(context)
            val streams = db.streamDAO()
                .getStream(YOUTUBE_SERVICE_ID.toLong(), url)
                .blockingFirst()
            val stream = streams.firstOrNull() ?: return@fromCallable null
            val states = db.streamStateDAO().getState(stream.uid).blockingFirst()
            val state = states.firstOrNull() ?: return@fromCallable null
            if (stream.duration <= 0) return@fromCallable null
            Pair(state.progressMillis, stream.duration)
        }.subscribeOn(Schedulers.io())
    }
}
