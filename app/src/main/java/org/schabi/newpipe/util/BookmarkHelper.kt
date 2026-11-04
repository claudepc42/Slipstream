package org.schabi.newpipe.util

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

object BookmarkHelper {

    private const val PREFS_BOOKMARKS_PREFIX = "bookmarks_"

    fun addBookmark(context: Context, videoKey: String, positionMs: Long) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val key = PREFS_BOOKMARKS_PREFIX + videoKey
        val list = loadList(prefs, key).toMutableList()
        if (!list.contains(positionMs)) {
            list.add(positionMs)
            list.sort()
            saveList(prefs, key, list)
        }
    }

    fun getBookmarks(context: Context, videoKey: String): List<Long> {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return loadList(prefs, PREFS_BOOKMARKS_PREFIX + videoKey)
    }

    fun removeBookmark(context: Context, videoKey: String, positionMs: Long) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val key = PREFS_BOOKMARKS_PREFIX + videoKey
        val list = loadList(prefs, key).toMutableList()
        list.remove(positionMs)
        saveList(prefs, key, list)
    }

    fun prevBookmark(context: Context, videoKey: String, currentMs: Long): Long? =
        getBookmarks(context, videoKey).filter { it < currentMs - 2000 }.maxOrNull()

    fun nextBookmark(context: Context, videoKey: String, currentMs: Long): Long? =
        getBookmarks(context, videoKey).filter { it > currentMs + 2000 }.minOrNull()

    private fun loadList(prefs: SharedPreferences, key: String): List<Long> {
        val str = prefs.getString(key, "") ?: return emptyList()
        if (str.isEmpty()) return emptyList()
        return str.split(",").mapNotNull { it.trim().toLongOrNull() }.sorted()
    }

    private fun saveList(prefs: SharedPreferences, key: String, list: List<Long>) {
        prefs.edit().putString(key, list.joinToString(",")).apply()
    }
}
