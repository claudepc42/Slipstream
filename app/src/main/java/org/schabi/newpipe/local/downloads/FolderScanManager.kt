package org.schabi.newpipe.local.downloads

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.NewPipeDatabase
import org.schabi.newpipe.database.imported.ImportedFileEntity
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.util.WatchProgressHelper

object FolderScanManager {

    private val TAG = FolderScanManager::class.java.simpleName
    private val MEDIA_EXTENSIONS = setOf("mp4", "mkv", "webm", "mp3", "m4a", "opus", "ogg", "flac", "wav")

    fun scanFolder(context: Context, folderUri: Uri): Single<Int> {
        return Single.fromCallable {
            val docFolder = DocumentFile.fromTreeUri(context, folderUri)
                ?: return@fromCallable 0
            val files = listMediaFiles(docFolder)
            if (files.isEmpty()) return@fromCallable 0

            val entities = files.mapNotNull { file ->
                buildEntity(context, file)
            }
            if (entities.isEmpty()) return@fromCallable 0

            val db = NewPipeDatabase.getInstance(context)
            val inserted = db.importedFileDAO().insertAll(entities)
            inserted.count { it > 0 }
        }.subscribeOn(Schedulers.io())
    }

    private fun listMediaFiles(folder: DocumentFile): List<DocumentFile> {
        return folder.listFiles().filter { file ->
            file.isFile && MEDIA_EXTENSIONS.contains(file.name?.substringAfterLast('.')?.lowercase())
        }
    }

    private fun buildEntity(context: Context, file: DocumentFile): ImportedFileEntity? {
        val name = file.name ?: return null
        val uri = file.uri.toString()
        val videoId = extractYouTubeId(name)

        return if (videoId != null) {
            val metadata = fetchYouTubeMetadata(videoId)
            ImportedFileEntity(
                fileUri = uri,
                title = metadata?.name ?: nameWithoutExtension(name),
                thumbnailUrl = metadata?.let { WatchProgressHelper.thumbnailUrlForId(videoId) },
                uploader = metadata?.uploaderName,
                videoId = videoId,
                durationSeconds = metadata?.duration ?: 0,
                fileName = name
            )
        } else {
            ImportedFileEntity(
                fileUri = uri,
                title = nameWithoutExtension(name),
                thumbnailUrl = null,
                uploader = null,
                videoId = null,
                durationSeconds = 0,
                fileName = name
            )
        }
    }

    private fun extractYouTubeId(filename: String): String? {
        val idRegex = Regex("[A-Za-z0-9_-]{11}")
        val bracketMatch = Regex("\\[([A-Za-z0-9_-]{11})]").find(filename)
        if (bracketMatch != null) return bracketMatch.groupValues[1]
        val parenMatch = Regex("\\(([A-Za-z0-9_-]{11})\\)").find(filename)
        if (parenMatch != null) return parenMatch.groupValues[1]
        return WatchProgressHelper.extractYouTubeVideoId("https://youtu.be/${filename.substringAfterLast(' ').take(11)}")
            ?.takeIf { it.length == 11 && idRegex.matches(it) }
    }

    private fun fetchYouTubeMetadata(videoId: String): StreamInfo? {
        return try {
            val url = "https://www.youtube.com/watch?v=$videoId"
            StreamInfo.getInfo(NewPipe.getService(0), url)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch metadata for $videoId: ${e.message}")
            null
        }
    }

    private fun nameWithoutExtension(name: String): String {
        return name.substringBeforeLast('.').trim()
    }
}
