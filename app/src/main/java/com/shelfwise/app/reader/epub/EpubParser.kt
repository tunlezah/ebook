package com.shelfwise.app.reader.epub

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.util.zip.ZipInputStream

data class EpubChapter(
    val id: String,
    val title: String,
    val href: String
)

data class EpubContent(
    val chapters: List<EpubChapter>,
    val opfDir: String,
    val manifest: Map<String, Pair<String, String>> // id -> (href, mediaType)
)

class EpubParser(private val context: Context) {

    suspend fun parse(uri: Uri): EpubContent? = withContext(Dispatchers.IO) {
        try {
            val opfPath = findOpfPath(uri) ?: return@withContext null
            val opfDir = opfPath.substringBeforeLast("/", "")
            parseOpf(uri, opfPath, opfDir)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getChapterHtml(uri: Uri, opfDir: String, chapterHref: String): String? = withContext(Dispatchers.IO) {
        try {
            val fullPath = if (opfDir.isNotEmpty()) "$opfDir/$chapterHref" else chapterHref
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val zipStream = ZipInputStream(inputStream)
                var entry = zipStream.nextEntry
                while (entry != null) {
                    if (entry.name == fullPath) {
                        return@withContext zipStream.readBytes().decodeToString()
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

    suspend fun getResource(uri: Uri, opfDir: String, resourcePath: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val fullPath = if (opfDir.isNotEmpty() && !resourcePath.startsWith(opfDir)) {
                "$opfDir/$resourcePath"
            } else {
                resourcePath
            }
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val zipStream = ZipInputStream(inputStream)
                var entry = zipStream.nextEntry
                while (entry != null) {
                    if (entry.name == fullPath || entry.name.endsWith(resourcePath)) {
                        return@withContext zipStream.readBytes()
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

    private fun findOpfPath(uri: Uri): String? {
        return context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val zipStream = ZipInputStream(inputStream)
            var entry = zipStream.nextEntry
            while (entry != null) {
                if (entry.name == "META-INF/container.xml") {
                    val parser = XmlPullParserFactory.newInstance().newPullParser()
                    parser.setInput(zipStream.bufferedReader())
                    var eventType = parser.eventType
                    while (eventType != XmlPullParser.END_DOCUMENT) {
                        if (eventType == XmlPullParser.START_TAG && parser.name == "rootfile") {
                            return@use parser.getAttributeValue(null, "full-path")
                        }
                        eventType = parser.next()
                    }
                }
                zipStream.closeEntry()
                entry = zipStream.nextEntry
            }
            zipStream.close()
            null
        }
    }

    private fun parseOpf(uri: Uri, opfPath: String, opfDir: String): EpubContent? {
        return context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val zipStream = ZipInputStream(inputStream)
            var entry = zipStream.nextEntry
            while (entry != null) {
                if (entry.name == opfPath) {
                    val opfContent = zipStream.readBytes().decodeToString()
                    return@use parseOpfContent(opfContent, opfDir)
                }
                zipStream.closeEntry()
                entry = zipStream.nextEntry
            }
            zipStream.close()
            null
        }
    }

    private fun parseOpfContent(opfContent: String, opfDir: String): EpubContent {
        val manifestItems = mutableMapOf<String, Pair<String, String>>()
        val spineIds = mutableListOf<String>()
        val tocHref = mutableListOf<String>()

        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(opfContent.reader())

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "item" -> {
                        val id = parser.getAttributeValue(null, "id") ?: ""
                        val href = parser.getAttributeValue(null, "href") ?: ""
                        val mediaType = parser.getAttributeValue(null, "media-type") ?: ""
                        val properties = parser.getAttributeValue(null, "properties") ?: ""
                        manifestItems[id] = Pair(href, mediaType)
                        if (properties.contains("nav")) {
                            tocHref.add(href)
                        }
                    }
                    "itemref" -> {
                        val idref = parser.getAttributeValue(null, "idref") ?: ""
                        spineIds.add(idref)
                    }
                }
            }
            eventType = parser.next()
        }

        // Build chapter list from spine
        val chapters = spineIds.mapIndexedNotNull { index, id ->
            val item = manifestItems[id] ?: return@mapIndexedNotNull null
            if (item.second.contains("html") || item.second.contains("xhtml") || item.second.contains("xml")) {
                EpubChapter(
                    id = id,
                    title = "Chapter ${index + 1}",
                    href = item.first
                )
            } else null
        }

        return EpubContent(
            chapters = chapters,
            opfDir = opfDir,
            manifest = manifestItems
        )
    }
}
