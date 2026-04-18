package com.shelfwise.app

import com.shelfwise.app.server.HttpFileServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class HttpFileServerTest {

    private lateinit var server: HttpFileServer
    private lateinit var uploadDir: File
    private val port = 18080
    private val uploadedFiles = mutableListOf<String>()

    /** Base URL including the per-instance token prefix. */
    private val base: String get() = "http://localhost:$port/t/${server.token}"

    @Before
    fun setup() {
        uploadDir = File(System.getProperty("java.io.tmpdir"), "shelfwise_test_${System.currentTimeMillis()}")
        uploadDir.mkdirs()
        server = HttpFileServer(port, uploadDir) { uploadedFiles.add(it) }
        server.start()
    }

    @After
    fun teardown() {
        server.stop()
        uploadDir.deleteRecursively()
    }

    @Test
    fun `unauthenticated request returns 403`() {
        val url = URL("http://localhost:$port/files")
        val conn = url.openConnection() as HttpURLConnection
        assertEquals(403, conn.responseCode)
        conn.disconnect()
    }

    @Test
    fun `unauthenticated root does not leak token via redirect`() {
        val url = URL("http://localhost:$port/")
        val conn = url.openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = false
        val code = conn.responseCode
        val location = conn.getHeaderField("Location")
        conn.disconnect()
        assertEquals(403, code)
        // Any Location header must NOT contain the token.
        assertTrue(location == null || !location.contains(server.token))
    }

    @Test
    fun `main page returns 200`() {
        val url = URL("$base/")
        val conn = url.openConnection() as HttpURLConnection
        assertEquals(200, conn.responseCode)
        val body = conn.inputStream.bufferedReader().readText()
        assertTrue(body.contains("ShelfWise"))
        conn.disconnect()
    }

    @Test
    fun `file list returns empty array`() {
        val url = URL("$base/files")
        val conn = url.openConnection() as HttpURLConnection
        assertEquals(200, conn.responseCode)
        val body = conn.inputStream.bufferedReader().readText()
        assertEquals("[]", body)
        conn.disconnect()
    }

    @Test
    fun `file list returns files`() {
        File(uploadDir, "test.epub").writeText("dummy")
        File(uploadDir, "test.pdf").writeText("dummy")

        val url = URL("$base/files")
        val conn = url.openConnection() as HttpURLConnection
        val body = conn.inputStream.bufferedReader().readText()
        assertTrue(body.contains("test.epub"))
        assertTrue(body.contains("test.pdf"))
        conn.disconnect()
    }

    @Test
    fun `download nonexistent file returns 404`() {
        val url = URL("$base/download/nonexistent.epub")
        val conn = url.openConnection() as HttpURLConnection
        assertEquals(404, conn.responseCode)
        conn.disconnect()
    }

    @Test
    fun `download existing file returns content`() {
        val testContent = "test epub content"
        File(uploadDir, "test.epub").writeText(testContent)

        val url = URL("$base/download/test.epub")
        val conn = url.openConnection() as HttpURLConnection
        assertEquals(200, conn.responseCode)
        val body = conn.inputStream.bufferedReader().readText()
        assertEquals(testContent, body)
        conn.disconnect()
    }

    @Test
    fun `delete removes file`() {
        File(uploadDir, "test.epub").writeText("content")
        assertTrue(File(uploadDir, "test.epub").exists())

        val url = URL("$base/delete/test.epub")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        assertEquals(200, conn.responseCode)
        conn.disconnect()

        assertTrue(!File(uploadDir, "test.epub").exists())
    }

    @Test
    fun `cookie-only auth is accepted`() {
        val url = URL("http://localhost:$port/files")
        val conn = url.openConnection() as HttpURLConnection
        conn.setRequestProperty("Cookie", "shelfwise_auth=${server.token}")
        assertEquals(200, conn.responseCode)
        conn.disconnect()
    }

    @Test
    fun `wrong cookie is rejected`() {
        val url = URL("http://localhost:$port/files")
        val conn = url.openConnection() as HttpURLConnection
        conn.setRequestProperty("Cookie", "shelfwise_auth=wrongvalue")
        assertEquals(403, conn.responseCode)
        conn.disconnect()
    }
}
