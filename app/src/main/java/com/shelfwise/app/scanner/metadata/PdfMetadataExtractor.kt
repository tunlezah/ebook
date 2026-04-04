package com.shelfwise.app.scanner.metadata

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class PdfMetadataExtractor(private val context: Context) {

    suspend fun extract(uri: Uri): MetadataResult? = withContext(Dispatchers.IO) {
        try {
            // PdfRenderer requires a seekable file descriptor
            val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return@withContext null
            val renderer = PdfRenderer(pfd)

            val pageCount = renderer.pageCount
            val coverPath = extractCoverPage(renderer, uri)

            renderer.close()
            pfd.close()

            // PDF metadata (title/author) is not easily accessible via PdfRenderer
            // We use the filename as title
            val displayName = getDisplayName(uri)
            val title = displayName?.substringBeforeLast(".")

            MetadataResult(
                title = title,
                author = null,
                coverPath = coverPath,
                chapterCount = pageCount
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun extractCoverPage(renderer: PdfRenderer, uri: Uri): String? {
        return try {
            if (renderer.pageCount == 0) return null

            val page = renderer.openPage(0)

            // Render at thumbnail size - 240px width
            val targetWidth = 240
            val scale = targetWidth.toFloat() / page.width
            val targetHeight = (page.height * scale).toInt()

            val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.RGB_565)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()

            val hash = uri.toString().hashCode().toUInt().toString(16)
            val coverFile = File(context.cacheDir, "covers/cover_$hash.jpg")
            coverFile.parentFile?.mkdirs()

            coverFile.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 75, out)
            }
            bitmap.recycle()

            coverFile.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    private fun getDisplayName(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (_: Exception) {
            uri.lastPathSegment
        }
    }
}
