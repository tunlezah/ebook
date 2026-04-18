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
abstract class BookDao {

    @Query("SELECT * FROM books ORDER BY title ASC")
    abstract fun getAllBooksByTitle(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books ORDER BY author ASC, title ASC")
    abstract fun getAllBooksByAuthor(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books ORDER BY last_read_timestamp DESC")
    abstract fun getAllBooksByRecent(): Flow<List<BookEntity>>

    /**
     * Full-text search over title+author using the FTS4 virtual table.
     * The FTS table's rowid is linked (via content=books) to BookEntity.id.
     * Result is ordered by recency to match the prior UX expectation for search.
     */
    @Query(
        """
        SELECT books.* FROM books
        JOIN books_fts ON books.id = books_fts.rowid
        WHERE books_fts MATCH :ftsQuery
        ORDER BY books.last_read_timestamp DESC
        """
    )
    abstract fun searchBooksFts(ftsQuery: String): Flow<List<BookEntity>>

    /**
     * Public search entry point. Keeps the prior Kotlin signature so repositories
     * don't change. Sanitizes the raw input into an FTS4 MATCH expression:
     *  - blank / punctuation-only input -> falls back to the recent list
     *    (avoids FTS "malformed MATCH" errors on empty queries).
     *  - each whitespace-separated token is stripped of FTS-unfriendly chars
     *    (`"`, `*`, `-`, `:`, `(`, `)`, `^`), wrapped in double quotes and
     *    suffixed with `*` so prefix/substring-like matching still works.
     */
    fun searchBooks(query: String): Flow<List<BookEntity>> {
        val ftsQuery = buildFtsQuery(query)
        return if (ftsQuery == null) {
            getAllBooksByRecent()
        } else {
            searchBooksFts(ftsQuery)
        }
    }

    companion object {
        // Characters that FTS4's default tokenizer rejects or that have
        // special meaning inside a MATCH expression. Stripped from each token
        // before we re-quote it.
        private val FTS_RESERVED = charArrayOf('"', '*', '-', ':', '(', ')', '^')

        /**
         * Returns an FTS4 MATCH string, or null if the input has no usable
         * tokens (empty, whitespace, or punctuation-only).
         */
        internal fun buildFtsQuery(raw: String): String? {
            if (raw.isBlank()) return null
            val tokens = raw.trim().split(Regex("\\s+"))
                .map { token ->
                    token.filterNot { ch -> ch in FTS_RESERVED }
                }
                .filter { it.isNotEmpty() }
            if (tokens.isEmpty()) return null
            return tokens.joinToString(separator = " ") { "\"$it\"*" }
        }
    }

    @Query("SELECT * FROM books WHERE id = :id")
    abstract suspend fun getBookById(id: Long): BookEntity?

    @Query("SELECT * FROM books WHERE file_path = :filePath")
    abstract suspend fun getBookByPath(filePath: String): BookEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insert(book: BookEntity): Long

    @Update
    abstract suspend fun update(book: BookEntity)

    @Delete
    abstract suspend fun delete(book: BookEntity)

    @Query("DELETE FROM books WHERE id = :id")
    abstract suspend fun deleteById(id: Long)

    @Query("UPDATE books SET current_chapter = :chapter, current_position = :position, overall_progress = :progress, last_read_timestamp = :timestamp WHERE id = :id")
    abstract suspend fun updateReadingProgress(id: Long, chapter: Int, position: Float, progress: Float, timestamp: Long)

    @Query("UPDATE books SET cover_path = :coverPath WHERE id = :id")
    abstract suspend fun updateCoverPath(id: Long, coverPath: String)

    @Query("UPDATE books SET total_chapters = :totalChapters WHERE id = :id")
    abstract suspend fun updateTotalChapters(id: Long, totalChapters: Int)

    @Query("SELECT file_path FROM books")
    abstract suspend fun getAllFilePaths(): List<String>

    @Query("SELECT COUNT(*) FROM books")
    abstract suspend fun getBookCount(): Int
}
