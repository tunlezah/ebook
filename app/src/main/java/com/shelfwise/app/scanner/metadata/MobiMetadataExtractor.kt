package com.shelfwise.app.scanner.metadata

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
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
        // MOBI cover extraction: stream the file with a bounded sliding-window scan
        // for JPEG/PNG magic bytes. Covers in MOBI/AZW3 typically live within the
        // first few MB (image records are near the start of the PDB record section).
        // We cap the total scanned region so a 100 MB AZW3 cannot blow up the heap.
        return try {
            context.contentResolver.openInputStream(uri)?.use { rawStream ->
                BufferedInputStream(rawStream).use { input ->
                    scanForCover(input, uri)
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun scanForCover(input: InputStream, uri: Uri): String? {
        val jpegMagic = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
        val pngMagic = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)

        val chunkSize = 64 * 1024
        val overlap = 8 // enough to catch any 3-4 byte magic straddling a chunk boundary
        val scanCap = 4 * 1024 * 1024 // 4 MB hard cap on header/scan region

        val buffer = ByteArray(chunkSize + overlap)
        var bufferFilled = 0 // valid bytes currently in buffer
        var totalConsumed = 0L // absolute bytes read from the stream (approx file offset)

        while (totalConsumed < scanCap) {
            val readInto = chunkSize
            val remainingCap = (scanCap - totalConsumed).toInt().coerceAtMost(readInto)
            if (remainingCap <= 0) break

            val n = input.read(buffer, bufferFilled, remainingCap)
            if (n <= 0) {
                // EOF; still scan whatever is left in the buffer.
                if (bufferFilled < 3) return null
                return findAndExtract(buffer, bufferFilled, input, jpegMagic, pngMagic, uri)
                    // even if no image, we're done
                    ?: null
            }
            bufferFilled += n
            totalConsumed += n

            // Try to find an image start within the currently-filled buffer.
            val jpegIdx = indexOf(buffer, bufferFilled, jpegMagic)
            val pngIdx = indexOf(buffer, bufferFilled, pngMagic)

            val (imageStart, isJpeg) = when {
                jpegIdx >= 0 && (pngIdx < 0 || jpegIdx < pngIdx) -> jpegIdx to true
                pngIdx >= 0 -> pngIdx to false
                else -> -1 to false
            }

            if (imageStart >= 0) {
                return extractImage(buffer, bufferFilled, imageStart, isJpeg, input, uri)
            }

            // No match; retain the last `overlap` bytes so magic spanning the
            // boundary is still caught on the next iteration.
            if (bufferFilled > overlap) {
                System.arraycopy(buffer, bufferFilled - overlap, buffer, 0, overlap)
                bufferFilled = overlap
            }
        }
        return null
    }

    private fun findAndExtract(
        buffer: ByteArray,
        filled: Int,
        input: InputStream,
        jpegMagic: ByteArray,
        pngMagic: ByteArray,
        uri: Uri
    ): String? {
        val jpegIdx = indexOf(buffer, filled, jpegMagic)
        val pngIdx = indexOf(buffer, filled, pngMagic)
        val (imageStart, isJpeg) = when {
            jpegIdx >= 0 && (pngIdx < 0 || jpegIdx < pngIdx) -> jpegIdx to true
            pngIdx >= 0 -> pngIdx to false
            else -> return null
        }
        return extractImage(buffer, filled, imageStart, isJpeg, input, uri)
    }

    private fun extractImage(
        buffer: ByteArray,
        filled: Int,
        imageStart: Int,
        isJpeg: Boolean,
        input: InputStream,
        uri: Uri
    ): String? {
        val maxImageBytes = 2 * 1024 * 1024 // 2 MB cap on a single cover
        val out = ByteArrayOutputStream(minOf(maxImageBytes, 256 * 1024))

        // 1) Write what we already have from `imageStart`..`filled`, searching for
        //    the image end marker in that slice first.
        val available = filled - imageStart
        val inBufferEnd = if (isJpeg) {
            findJpegEndInRange(buffer, imageStart, filled)
        } else {
            findPngEndInRange(buffer, imageStart, filled)
        }

        if (inBufferEnd > 0) {
            val len = (inBufferEnd - imageStart).coerceAtMost(maxImageBytes)
            out.write(buffer, imageStart, len)
            val bytes = out.toByteArray()
            return if (bytes.isNotEmpty()) saveCoverImage(bytes, uri) else null
        }

        // 2) No end marker yet — flush what we have, then keep reading in chunks
        //    until we hit the end marker or the cap.
        val initialLen = available.coerceAtMost(maxImageBytes)
        out.write(buffer, imageStart, initialLen)

        val readChunk = ByteArray(64 * 1024)
        // Small trailing window so end markers (FF D9 or IEND + 4 bytes length) spanning
        // reads are still detected. 16 bytes comfortably covers IEND + CRC.
        var prevTail = ByteArray(0)

        while (out.size() < maxImageBytes) {
            val want = (maxImageBytes - out.size()).coerceAtMost(readChunk.size)
            val n = input.read(readChunk, 0, want)
            if (n <= 0) break

            // Build a view that includes the previous tail for boundary-spanning end detection.
            val view = ByteArray(prevTail.size + n)
            System.arraycopy(prevTail, 0, view, 0, prevTail.size)
            System.arraycopy(readChunk, 0, view, prevTail.size, n)

            val endIdx = if (isJpeg) {
                findJpegEndInRange(view, 0, view.size)
            } else {
                findPngEndInRange(view, 0, view.size)
            }

            if (endIdx > 0) {
                // We need to write only the NEW portion up to endIdx (prevTail was already
                // written on the previous iteration).
                val newPortionEnd = endIdx - prevTail.size
                if (newPortionEnd > 0) {
                    val toWrite = newPortionEnd.coerceAtMost(maxImageBytes - out.size())
                    if (toWrite > 0) out.write(readChunk, 0, toWrite)
                }
                val bytes = out.toByteArray()
                return if (bytes.isNotEmpty()) saveCoverImage(bytes, uri) else null
            }

            out.write(readChunk, 0, n)

            // Keep last 16 bytes (or fewer) of the just-written data as the next tail.
            val tailSize = minOf(16, n)
            prevTail = readChunk.copyOfRange(n - tailSize, n)
        }

        val bytes = out.toByteArray()
        return if (bytes.isNotEmpty()) saveCoverImage(bytes, uri) else null
    }

    private fun indexOf(data: ByteArray, length: Int, pattern: ByteArray): Int {
        if (pattern.isEmpty() || length < pattern.size) return -1
        val end = length - pattern.size
        outer@ for (i in 0..end) {
            for (j in pattern.indices) {
                if (data[i + j] != pattern[j]) continue@outer
            }
            return i
        }
        return -1
    }

    private fun findJpegEndInRange(data: ByteArray, start: Int, endExclusive: Int): Int {
        // JPEG ends with FF D9. Return the index just past the marker, or -1 if not found.
        var i = start + 2
        val limit = endExclusive - 1
        while (i < limit) {
            if (data[i] == 0xFF.toByte() && data[i + 1] == 0xD9.toByte()) {
                return i + 2
            }
            i++
        }
        return -1
    }

    private fun findPngEndInRange(data: ByteArray, start: Int, endExclusive: Int): Int {
        // PNG ends with IEND chunk type; include the following 4-byte CRC.
        val iend = byteArrayOf(0x49, 0x45, 0x4E, 0x44)
        var i = start
        val limit = endExclusive - iend.size
        while (i <= limit) {
            var match = true
            for (j in iend.indices) {
                if (data[i + j] != iend[j]) { match = false; break }
            }
            if (match) {
                // IEND + 4-byte CRC = 8 bytes total after the type starts.
                val endPos = i + iend.size + 4
                return if (endPos <= endExclusive) endPos else endExclusive
            }
            i++
        }
        return -1
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
