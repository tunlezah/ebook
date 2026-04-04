package com.shelfwise.app

import com.shelfwise.app.data.db.entity.BookEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class BookEntityTest {

    @Test
    fun `create book entity with defaults`() {
        val entity = BookEntity(
            title = "Test Book",
            filePath = "/path/to/book.epub",
            format = "EPUB"
        )

        assertEquals("Test Book", entity.title)
        assertEquals("Unknown", entity.author)
        assertEquals("/path/to/book.epub", entity.filePath)
        assertEquals("EPUB", entity.format)
        assertEquals(0L, entity.id)
        assertEquals(0, entity.currentChapter)
        assertEquals(0f, entity.currentPosition, 0.001f)
        assertEquals(0f, entity.overallProgress, 0.001f)
    }

    @Test
    fun `create book entity with all fields`() {
        val entity = BookEntity(
            id = 1,
            title = "Full Book",
            author = "John Author",
            filePath = "/path/book.pdf",
            coverPath = "/cache/cover.jpg",
            format = "PDF",
            fileSize = 1024000,
            currentChapter = 5,
            currentPosition = 0.5f,
            totalChapters = 20,
            overallProgress = 0.25f,
            lastReadTimestamp = 1000L,
            addedTimestamp = 500L,
            treeUri = "content://tree/uri"
        )

        assertEquals(1L, entity.id)
        assertEquals("Full Book", entity.title)
        assertEquals("John Author", entity.author)
        assertEquals("/path/book.pdf", entity.filePath)
        assertEquals("/cache/cover.jpg", entity.coverPath)
        assertEquals("PDF", entity.format)
        assertEquals(1024000L, entity.fileSize)
        assertEquals(5, entity.currentChapter)
        assertEquals(0.5f, entity.currentPosition, 0.001f)
        assertEquals(20, entity.totalChapters)
        assertEquals(0.25f, entity.overallProgress, 0.001f)
    }

    @Test
    fun `book entities with same data are equal`() {
        val entity1 = BookEntity(id = 1, title = "Book", filePath = "/path", format = "EPUB")
        val entity2 = BookEntity(id = 1, title = "Book", filePath = "/path", format = "EPUB")
        assertEquals(entity1, entity2)
    }

    @Test
    fun `book entities with different ids are not equal`() {
        val entity1 = BookEntity(id = 1, title = "Book", filePath = "/path", format = "EPUB")
        val entity2 = BookEntity(id = 2, title = "Book", filePath = "/path", format = "EPUB")
        assertNotEquals(entity1, entity2)
    }
}
