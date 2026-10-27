package org.schabi.newpipe.local.downloads

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers

data class ScanResult(val total: Int, val withYouTubeId: Int)

object FolderScanManager {

    private val TAG = FolderScanManager::class.java.simpleName
    private val MEDIA_EXTENSIONS = setOf("mp4", "mkv", "webm", "mp3", "m4a", "opus", "ogg", "flac", "wav")
    private val BRACKET_ID_REGEX = Regex("\\[([A-Za-z0-9_-]{11})]")
    private val PAREN_ID_REGEX = Regex("\\(([A-Za-z0-9_-]{11})\\)")

    fun scanFolder(context: Context, folderUri: Uri): Single<ScanResult> {
        return Single.fromCallable {
            val docFolder = DocumentFile.fromTreeUri(context, folderUri)
                ?: return@fromCallable ScanResult(0, 0)
            val files = listMediaFiles(docFolder)
            val withId = files.count { extractYouTubeId(it.name ?: "") != null }
            Log.d(TAG, "Scanned ${files.size} media files, $withId have a YouTube ID in filename")
            ScanResult(files.size, withId)
        }.subscribeOn(Schedulers.io())
    }

    private fun listMediaFiles(folder: DocumentFile): List<DocumentFile> {
        return folder.listFiles().filter { file ->
            file.isFile && MEDIA_EXTENSIONS.contains(
                file.name?.substringAfterLast('.')?.lowercase()
            )
        }
    }

    fun extractYouTubeId(filename: String): String? {
        BRACKET_ID_REGEX.find(filename)?.let { return it.groupValues[1] }
        PAREN_ID_REGEX.find(filename)?.let { return it.groupValues[1] }
        return null
    }

    fun nameWithoutExtension(name: String): String = name.substringBeforeLast('.').trim()
}
