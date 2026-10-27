package org.schabi.newpipe.local.downloads

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.database.imported.ImportedFileEntity

data class ScanResult(val total: Int, val withYouTubeId: Int)

object FolderScanManager {

    private val TAG = FolderScanManager::class.java.simpleName
    private val MEDIA_EXTENSIONS = setOf("mp4", "mkv", "webm", "mp3", "m4a", "opus", "ogg", "flac", "wav")
    private val BRACKET_ID_REGEX = Regex("[\\[(]([A-Za-z0-9_-]{11})[\\])]")
    private val BARE_ID_REGEX = Regex("(?<![A-Za-z0-9_-])([A-Za-z0-9_-]{11})(?![A-Za-z0-9_-])")

    fun scanFolder(context: Context, folderUri: Uri): Single<ScanResult> {
        return Single.fromCallable {
            val docFolder = DocumentFile.fromTreeUri(context, folderUri)
                ?: return@fromCallable ScanResult(0, 0)
            val files = listMediaFiles(docFolder)
            val entities = mutableListOf<ImportedFileEntity>()
            for (file in files) {
                val name = file.name ?: continue
                val videoId = extractYouTubeId(name)
                val thumbnailUrl = if (videoId != null)
                    "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                else null
                entities.add(
                    ImportedFileEntity(
                        fileUri = file.uri.toString(),
                        title = nameWithoutExtension(name),
                        thumbnailUrl = thumbnailUrl,
                        uploader = null,
                        videoId = videoId,
                        durationSeconds = 0,
                        fileName = name
                    )
                )
            }
            val withId = entities.count { it.videoId != null }
            if (entities.isNotEmpty()) {
                NewPipeDatabase.getInstance(context).importedFileDAO().insertAll(entities)
            }
            Log.d(TAG, "Scanned ${files.size} media files, $withId have a YouTube ID")
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
        // Strip extension and try bare 11-char match
        val nameNoExt = filename.substringBeforeLast('.')
        BARE_ID_REGEX.find(nameNoExt)?.let { return it.groupValues[1] }
        return null
    }

    fun nameWithoutExtension(name: String): String = name.substringBeforeLast('.').trim()
}
