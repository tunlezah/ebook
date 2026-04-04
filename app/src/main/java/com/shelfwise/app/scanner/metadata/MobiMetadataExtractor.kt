package com.shelfwise.app.scanner.metadata

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset

class MobiMetadataExtractor(private val context: Context) {

    suspend fun extract(uri: Uri): MetadataResult? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                parseMobiHeader(inputStream, uri)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseMobiHeader(inputStream: InputStream, uri: Uri): MetadataResult {
        val headerBytes = ByteArray(78)
        val bytesRead = inputStream.read(headerBytes)
        if (bytesRead < 78) return fallbackResult(uri)

        // PalmDOC header: bytes 0-31 = name (null-terminated)
        val palmName = String(headerBytes, 0, 32, Charset.forName("ISO-8859-1")).trim('\u0000').trim()

        // Check for MOBI magic at offset 60 (relative to PalmDOC record 0)
        // For basic metadata, the PalmDOC name is often the book title
        val title = palmName.takeIf { it.isNotBlank() } ?: getDisplayName(uri)?.substringBeforeLast(".")

        val coverPath = extractMobiCover(uri)

        return MetadataResult(
            title = title,
            author = null, // MOBI author requires deeper parsing of EXTH header
            coverPath = coverPath,
            chapterCount = 0
        )
    }

    private fun extractMobiCover(uri: Uri): String? {
        // MOBI cover extraction is complex - attempt to find embedded image
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val allBytes = inputStream.readBytes()
                // Look for JPEG or PNG magic bytes in the file
                val jpegMagic = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
                val pngMagic = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)

                var imageStart = findBytes(allBytes, jpegMagic, allBytes.size / 2)
                var isJpeg = true
                if (imageStart < 0) {
                    imageStart = findBytes(allBytes, pngMagic, allBytes.size / 2)
                    isJpeg = false
                }

                if (imageStart >= 0) {
                    // Find end of image
                    val imageEnd = if (isJpeg) {
                        findJpegEnd(allBytes, imageStart)
                    } else {
                        findPngEnd(allBytes, imageStart)
                    }

                    if (imageEnd > imageStart) {
                        val imageBytes = allBytes.copyOfRange(imageStart, minOf(imageEnd, allBytes.size))
                        saveCoverImage(imageBytes, uri)
                    } else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun findBytes(data: ByteArray, pattern: ByteArray, startFrom: Int = 0): Int {
        val start = maxOf(0, startFrom)
        for (i in start until data.size - pattern.size) {
            var found = true
            for (j in pattern.indices) {
                if (data[i + j] != pattern[j]) {
                    found = false
                    break
                }
            }
            if (found) return i
        }
        return -1
    }

    private fun findJpegEnd(data: ByteArray, start: Int): Int {
        // JPEG ends with FF D9
        for (i in start + 2 until data.size - 1) {
            if (data[i] == 0xFF.toByte() && data[i + 1] == 0xD9.toByte()) {
                return i + 2
            }
        }
        return minOf(start + 500_000, data.size) // Cap at 500KB
    }

    private fun findPngEnd(data: ByteArray, start: Int): Int {
        // PNG ends with IEND chunk
        val iend = byteArrayOf(0x49, 0x45, 0x4E, 0x44)
        val pos = findBytes(data, iend, start)
        return if (pos >= 0) pos + 12 else minOf(start + 500_000, data.size)
    }

    private fun saveCoverImage(imageBytes: ByteArray, uri: Uri): String? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)

            val targetWidth = 240
            options.inSampleSize = calculateInSampleSize(options, targetWidth, (targetWidth * 1.5).toInt())
            options.inJustDecodeBounds = false
            options.inPreferredConfig = android.graphics.Bitmap.Config.RGB_565

            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options) ?: return null

            val hash = uri.toString().hashCode().toUInt().toString(16)
            val coverFile = File(context.cacheDir, "covers/cover_$hash.jpg")
            coverFile.parentFile?.mkdirs()

            coverFile.outputStream().use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 75, out)
            }
            bitmap.recycle()

            coverFile.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height, width) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun fallbackResult(uri: Uri): MetadataResult {
        val displayName = getDisplayName(uri)
        return MetadataResult(
            title = displayName?.substringBeforeLast("."),
            author = null,
            coverPath = null,
            chapterCount = 0
        )
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
