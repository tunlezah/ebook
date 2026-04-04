package com.shelfwise.app.data.repository

import android.content.Context
import com.shelfwise.app.data.db.BookDao
import com.shelfwise.app.data.db.entity.BookEntity
import com.shelfwise.app.data.model.Book
import com.shelfwise.app.data.model.BookFormat
import com.shelfwise.app.data.model.SortOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class BookRepository(
    private val bookDao: BookDao,
    private val context: Context
) {

    fun getBooks(sortOrder: SortOrder): Flow<List<Book>> {
        val flow = when (sortOrder) {
            SortOrder.TITLE -> bookDao.getAllBooksByTitle()
            SortOrder.AUTHOR -> bookDao.getAllBooksByAuthor()
            SortOrder.RECENT -> bookDao.getAllBooksByRecent()
        }
        return flow.map { entities -> entities.map { it.toBook() } }
    }

    fun searchBooks(query: String): Flow<List<Book>> {
        return bookDao.searchBooks(query).map { entities -> entities.map { it.toBook() } }
    }

    suspend fun getBookById(id: Long): Book? {
        return bookDao.getBookById(id)?.toBook()
    }

    suspend fun getBookByPath(filePath: String): Book? {
        return bookDao.getBookByPath(filePath)?.toBook()
    }

    suspend fun addBook(book: Book): Long {
        return bookDao.insert(book.toEntity())
    }

    suspend fun updateBook(book: Book) {
        bookDao.update(book.toEntity())
    }

    suspend fun deleteBook(id: Long) {
        bookDao.deleteById(id)
    }

    suspend fun updateReadingProgress(
        bookId: Long,
        chapter: Int,
        position: Float,
        progress: Float
    ) {
        bookDao.updateReadingProgress(
            id = bookId,
            chapter = chapter,
            position = position,
            progress = progress,
            timestamp = System.currentTimeMillis()
        )
    }

    suspend fun updateCoverPath(bookId: Long, coverPath: String) {
        bookDao.updateCoverPath(bookId, coverPath)
    }

    suspend fun updateTotalChapters(bookId: Long, totalChapters: Int) {
        bookDao.updateTotalChapters(bookId, totalChapters)
    }

    suspend fun getAllFilePaths(): List<String> {
        return bookDao.getAllFilePaths()
    }

    suspend fun getBookCount(): Int {
        return bookDao.getBookCount()
    }

    private fun BookEntity.toBook(): Book {
        return Book(
            id = id,
            title = title,
            author = author,
            filePath = filePath,
            coverPath = coverPath,
            format = try { BookFormat.valueOf(format) } catch (_: Exception) { BookFormat.EPUB },
            fileSize = fileSize,
            currentChapter = currentChapter,
            currentPosition = currentPosition,
            totalChapters = totalChapters,
            overallProgress = overallProgress,
            lastReadTimestamp = lastReadTimestamp,
            addedTimestamp = addedTimestamp,
            treeUri = treeUri
        )
    }

    private fun Book.toEntity(): BookEntity {
        return BookEntity(
            id = id,
            title = title,
            author = author,
            filePath = filePath,
            coverPath = coverPath,
            format = format.name,
            fileSize = fileSize,
            currentChapter = currentChapter,
            currentPosition = currentPosition,
            totalChapters = totalChapters,
            overallProgress = overallProgress,
            lastReadTimestamp = lastReadTimestamp,
            addedTimestamp = addedTimestamp,
            treeUri = treeUri
        )
    }
}
