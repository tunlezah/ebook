package com.shelfwise.app

import com.shelfwise.app.util.formatFileSize
import com.shelfwise.app.util.isBookFile
import com.shelfwise.app.util.toBookFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionsTest {

    @Test
    fun `formatFileSize handles zero`() {
        assertEquals("0 B", 0L.formatFileSize())
    }

    @Test
    fun `formatFileSize handles bytes`() {
        assertEquals("512 B", 512L.formatFileSize())
    }

    @Test
    fun `formatFileSize handles kilobytes`() {
        val result = (2048L).formatFileSize()
        assertTrue(result.contains("KB"))
    }

    @Test
    fun `formatFileSize handles megabytes`() {
        val result = (5 * 1024 * 1024L).formatFileSize()
        assertTrue(result.contains("MB"))
    }

    @Test
    fun `isBookFile detects epub`() {
        assertTrue("book.epub".isBookFile())
        assertTrue("book.EPUB".isBookFile())
    }

    @Test
    fun `isBookFile detects pdf`() {
        assertTrue("book.pdf".isBookFile())
        assertTrue("book.PDF".isBookFile())
    }

    @Test
    fun `isBookFile detects mobi`() {
        assertTrue("book.mobi".isBookFile())
        assertTrue("book.azw3".isBookFile())
        assertTrue("book.azw".isBookFile())
    }

    @Test
    fun `isBookFile rejects non-book files`() {
        assertFalse("file.txt".isBookFile())
        assertFalse("image.png".isBookFile())
        assertFalse("doc.docx".isBookFile())
    }

    @Test
    fun `toBookFormat returns correct format`() {
        assertEquals("EPUB", "file.epub".toBookFormat())
        assertEquals("PDF", "file.pdf".toBookFormat())
        assertEquals("MOBI", "file.mobi".toBookFormat())
        assertEquals("MOBI", "file.azw3".toBookFormat())
    }

    @Test
    fun `toBookFormat returns empty for unknown`() {
        assertEquals("", "file.txt".toBookFormat())
    }
}
