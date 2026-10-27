package org.schabi.newpipe.database.imported

import androidx.room3.Dao
import androidx.room3.DaoReturnTypeConverters
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.rxjava3.RxDaoReturnTypeConverters
import io.reactivex.rxjava3.core.Flowable

@Dao
@DaoReturnTypeConverters(RxDaoReturnTypeConverters::class)
interface ImportedFileDAO {

    @Query("SELECT * FROM ${ImportedFileEntity.IMPORTED_FILES_TABLE} ORDER BY uid DESC")
    fun getAll(): Flowable<List<ImportedFileEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertAll(entities: List<ImportedFileEntity>): List<Long>

    @Query("DELETE FROM ${ImportedFileEntity.IMPORTED_FILES_TABLE} WHERE uid = :uid")
    fun delete(uid: Long)

    @Query("SELECT COUNT(*) FROM ${ImportedFileEntity.IMPORTED_FILES_TABLE}")
    fun count(): Long
}
