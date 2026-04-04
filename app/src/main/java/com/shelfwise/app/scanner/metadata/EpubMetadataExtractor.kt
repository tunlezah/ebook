package com.shelfwise.app.scanner.metadata

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

class EpubMetadataExtractor(private val context: Context) {

    suspend fun extract(uri: Uri): MetadataResult? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                parseEpub(inputStream, uri)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseEpub(inputStream: InputStream, uri: Uri): MetadataResult {
        var title: String? = null
        var author: String? = null
        var coverHref: String? = null
        var coverMediaType: String? = null
        val spineItems = mutableListOf<String>()
        val manifestItems = mutableMapOf<String, Pair<String, String>>() // id -> (href, mediaType)
        var opfPath: String? = null

        // First pass: find container.xml and OPF path
        val zipStream = ZipInputStream(inputStream)
        var entry = zipStream.nextEntry
        while (entry != null) {
            val name = entry.name
            if (name == "META-INF/container.xml") {
                opfPath = parseContainerXml(zipStream)
            }
            zipStream.closeEntry()
            entry = zipStream.nextEntry
        }
        zipStream.close()

        if (opfPath == null) return MetadataResult(null, null, null, 0)

        val opfDir = opfPath.substringBeforeLast("/", "")

        // Second pass: parse OPF and extract cover
        context.contentResolver.openInputStream(uri)?.use { is2 ->
            val zipStream2 = ZipInputStream(is2)
            var entry2 = zipStream2.nextEntry
            var coverMetaContent: String? = null

            while (entry2 != null) {
                val name = entry2.name
                if (name == opfPath) {
                    val opfContent = zipStream2.readBytes().decodeToString()
                    val result = parseOpf(opfContent)
                    title = result.title
                    author = result.author
                    coverMetaContent = result.coverMetaContent
                    manifestItems.putAll(result.manifestItems)
                    spineItems.addAll(result.spineItems)

                    // Find cover image
                    if (coverMetaContent != null) {
                        val coverItem = manifestItems[coverMetaContent]
                        if (coverItem != null) {
                            coverHref = if (opfDir.isNotEmpty()) "$opfDir/${coverItem.first}" else coverItem.first
                            coverMediaType = coverItem.second
                        }
                    }

                    // Fallback: look for item with "cover" in properties or id
                    if (coverHref == null) {
                        for ((id, pair) in manifestItems) {
                            if (id.contains("cover", true) && pair.second.startsWith("image/")) {
                                coverHref = if (opfDir.isNotEmpty()) "$opfDir/${pair.first}" else pair.first
                                coverMediaType = pair.second
                                break
                            }
                        }
                    }
                }
                zipStream2.closeEntry()
                entry2 = zipStream2.nextEntry
            }
            zipStream2.close()
        }

        // Third pass: extract cover image if found
        var coverPath: String? = null
        if (coverHref != null) {
            coverPath = extractCoverImage(uri, coverHref!!)
        }

        return MetadataResult(
            title = title,
            author = author,
            coverPath = coverPath,
            chapterCount = spineItems.size
        )
    }

    private fun parseContainerXml(inputStream: InputStream): String? {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(inputStream.bufferedReader())
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "rootfile") {
                return parser.getAttributeValue(null, "full-path")
            }
            eventType = parser.next()
        }
        return null
    }

    private data class OpfResult(
        val title: String?,
        val author: String?,
        val coverMetaContent: String?,
        val manifestItems: Map<String, Pair<String, String>>,
        val spineItems: List<String>
    )

    private fun parseOpf(opfContent: String): OpfResult {
        var title: String? = null
        var author: String? = null
        var coverMetaContent: String? = null
        val manifestItems = mutableMapOf<String, Pair<String, String>>()
        val spineItems = mutableListOf<String>()

        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(opfContent.reader())

        var currentTag = ""
        var inMetadata = false
        var eventType = parser.eventType

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    val tag = parser.name
                    currentTag = tag

                    when {
                        tag.equals("metadata", true) -> inMetadata = true
                        tag.equals("item", true) -> {
                            val id = parser.getAttributeValue(null, "id") ?: ""
                            val href = parser.getAttributeValue(null, "href") ?: ""
                            val mediaType = parser.getAttributeValue(null, "media-type") ?: ""
                            val properties = parser.getAttributeValue(null, "properties") ?: ""
                            manifestItems[id] = Pair(href, mediaType)
                            if (properties.contains("cover-image")) {
                                coverMetaContent = id
                            }
                        }
                        tag.equals("itemref", true) -> {
                            val idref = parser.getAttributeValue(null, "idref") ?: ""
                            spineItems.add(idref)
                        }
                        tag.equals("meta", true) && inMetadata -> {
                            val name = parser.getAttributeValue(null, "name")
                            val content = parser.getAttributeValue(null, "content")
                            if (name == "cover" && content != null) {
                                coverMetaContent = content
                            }
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    val text = parser.text?.trim()
                    if (inMetadata && !text.isNullOrEmpty()) {
                        when {
                            currentTag.equals("title", true) || currentTag.endsWith(":title") -> {
                                if (title == null) title = text
                            }
                            currentTag.equals("creator", true) || currentTag.endsWith(":creator") -> {
                                if (author == null) author = text
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name.equals("metadata", true)) inMetadata = false
                    currentTag = ""
                }
            }
            eventType = parser.next()
        }

        return OpfResult(title, author, coverMetaContent, manifestItems, spineItems)
    }

    private fun extractCoverImage(uri: Uri, coverHref: String): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val zipStream = ZipInputStream(inputStream)
                var entry = zipStream.nextEntry
                while (entry != null) {
                    if (entry.name == coverHref || entry.name.endsWith(coverHref)) {
                        val imageBytes = zipStream.readBytes()
                        val hash = uri.toString().hashCode().toUInt().toString(16)
                        val coverFile = File(context.cacheDir, "covers/cover_$hash.jpg")
                        coverFile.parentFile?.mkdirs()

                        // Decode and re-encode as compressed JPEG thumbnail
                        val options = BitmapFactory.Options().apply {
                            inJustDecodeBounds = true
                        }
                        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)

                        // Calculate sample size for ~240px width thumbnail
                        val targetWidth = 240
                        options.inSampleSize = calculateInSampleSize(options, targetWidth, (targetWidth * 1.5).toInt())
                        options.inJustDecodeBounds = false
                        options.inPreferredConfig = android.graphics.Bitmap.Config.RGB_565

                        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)
                        if (bitmap != null) {
                            coverFile.outputStream().use { out ->
                                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 75, out)
                            }
                            bitmap.recycle()
                            return@use coverFile.absolutePath
                        }
                        return@use null
                    }
                    zipStream.closeEntry()
                    entry = zipStream.nextEntry
                }
                zipStream.close()
                null
            }
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
}
