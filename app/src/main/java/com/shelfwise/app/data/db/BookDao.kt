package com.shelfwise.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.shelfwise.app.data.db.entity.BookEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    @Query("SELECT * FROM books ORDER BY title ASC")
    fun getAllBooksByTitle(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books ORDER BY author ASC, title ASC")
    fun getAllBooksByAuthor(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books ORDER BY last_read_timestamp DESC")
    fun getAllBooksByRecent(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE title LIKE '%' || :query || '%' OR author LIKE '%' || :query || '%' ORDER BY title ASC")
    fun searchBooks(query: String): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getBookById(id: Long): BookEntity?

    @Query("SELECT * FROM books WHERE file_path = :filePath")
    suspend fun getBookByPath(filePath: String): BookEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(book: BookEntity): Long

    @Update
    suspend fun update(book: BookEntity)

    @Delete
    suspend fun delete(book: BookEntity)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE books SET current_chapter = :chapter, current_position = :position, overall_progress = :progress, last_read_timestamp = :timestamp WHERE id = :id")
    suspend fun updateReadingProgress(id: Long, chapter: Int, position: Float, progress: Float, timestamp: Long)

    @Query("UPDATE books SET cover_path = :coverPath WHERE id = :id")
    suspend fun updateCoverPath(id: Long, coverPath: String)

    @Query("UPDATE books SET total_chapters = :totalChapters WHERE id = :id")
    suspend fun updateTotalChapters(id: Long, totalChapters: Int)

    @Query("SELECT file_path FROM books")
    suspend fun getAllFilePaths(): List<String>

    @Query("SELECT COUNT(*) FROM books")
    suspend fun getBookCount(): Int
}
