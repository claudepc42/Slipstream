package org.schabi.newpipe.database.imported

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = ImportedFileEntity.IMPORTED_FILES_TABLE,
    indices = [Index(value = ["file_uri"], unique = true)]
)
data class ImportedFileEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "uid")
    val uid: Long = 0,

    @ColumnInfo(name = "file_uri")
    val fileUri: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "thumbnail_url")
    val thumbnailUrl: String?,

    @ColumnInfo(name = "uploader")
    val uploader: String?,

    @ColumnInfo(name = "video_id")
    val videoId: String?,

    @ColumnInfo(name = "duration_seconds")
    val durationSeconds: Long = 0,

    @ColumnInfo(name = "file_name")
    val fileName: String
) {
    companion object {
        const val IMPORTED_FILES_TABLE = "imported_files"
    }
}
