package com.shelfwise.app

import com.shelfwise.app.data.model.Book
import com.shelfwise.app.data.model.BookFormat
import com.shelfwise.app.data.model.SortOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookModelTest {

    @Test
    fun `book format enum values`() {
        assertEquals(3, BookFormat.entries.size)
        assertEquals(BookFormat.EPUB, BookFormat.valueOf("EPUB"))
        assertEquals(BookFormat.PDF, BookFormat.valueOf("PDF"))
        assertEquals(BookFormat.MOBI, BookFormat.valueOf("MOBI"))
    }

    @Test
    fun `sort order enum values`() {
        assertEquals(3, SortOrder.entries.size)
        assertEquals(SortOrder.TITLE, SortOrder.valueOf("TITLE"))
        assertEquals(SortOrder.AUTHOR, SortOrder.valueOf("AUTHOR"))
        assertEquals(SortOrder.RECENT, SortOrder.valueOf("RECENT"))
    }

    @Test
    fun `create book with defaults`() {
        val book = Book(
            title = "Test",
            author = "Author",
            filePath = "/path",
            format = BookFormat.EPUB
        )

        assertEquals(0L, book.id)
        assertEquals("Test", book.title)
        assertEquals("Author", book.author)
        assertEquals("/path", book.filePath)
        assertEquals(BookFormat.EPUB, book.format)
        assertEquals(0f, book.overallProgress, 0.001f)
        assertTrue(book.addedTimestamp > 0)
    }

    @Test
    fun `book progress calculation`() {
        val book = Book(
            title = "Test",
            author = "Author",
            filePath = "/path",
            format = BookFormat.PDF,
            currentChapter = 5,
            totalChapters = 10,
            overallProgress = 0.5f
        )

        assertEquals(0.5f, book.overallProgress, 0.001f)
        assertEquals(5, book.currentChapter)
        assertEquals(10, book.totalChapters)
    }

    @Test
    fun `book copy preserves values`() {
        val original = Book(
            id = 1,
            title = "Original",
            author = "Author",
            filePath = "/path",
            format = BookFormat.MOBI,
            overallProgress = 0.75f
        )

        val copy = original.copy(title = "Modified")
        assertEquals("Modified", copy.title)
        assertEquals(original.id, copy.id)
        assertEquals(original.author, copy.author)
        assertEquals(original.overallProgress, copy.overallProgress, 0.001f)
    }
}
