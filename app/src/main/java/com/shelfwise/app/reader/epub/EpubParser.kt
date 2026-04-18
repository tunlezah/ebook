package com.shelfwise.app.reader.epub

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
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

/**
 * Holds an open ZipFile + manifest + parsed [EpubContent] for the current book.
 * Callers should use [getChapterHtml] / [getResource] for O(1) lookups and
 * invoke [close] (typically from ViewModel.onCleared) when done.
 *
 * Thread-safety: ZipFile is safe for concurrent reads; we still serialize
 * getInputStream via [mutex] to be defensive on older Android runtimes.
 */
class EpubSession internal constructor(
    val sourceUri: Uri,
    val cachedFile: File,
    private val zipFile: ZipFile,
    private val entries: Map<String, ZipEntry>,
    val content: EpubContent
) : Closeable {

    @Volatile private var closed = false

    val opfDir: String get() = content.opfDir

    suspend fun getChapterHtml(chapterHref: String): String? = withContext(Dispatchers.IO) {
        val fullPath = joinZipPath(content.opfDir, chapterHref)
        readEntryBytes(fullPath)?.decodeToString()
    }

    suspend fun getResource(resourcePath: String): ByteArray? = withContext(Dispatchers.IO) {
        val normalized = resourcePath.trimStart('/')
        // Try absolute-from-root first; fall back to opfDir-prefixed.
        readEntryBytes(normalized)?.let { return@withContext it }
        if (content.opfDir.isNotEmpty() && !normalized.startsWith(content.opfDir)) {
            readEntryBytes(joinZipPath(content.opfDir, normalized))?.let { return@withContext it }
        }
        // Last-ditch: suffix match (tolerate weird relative refs).
        val suffixHit = entries.keys.firstOrNull { it.endsWith(normalized) }
        suffixHit?.let { readEntryBytes(it) }
    }

    private fun readEntryBytes(path: String): ByteArray? {
        if (closed) return null
        val entry = entries[path] ?: return null
        return try {
            // java.util.zip.ZipFile is documented as safe for concurrent reads
            // across threads; getInputStream returns a fresh stream per call.
            // No mutex needed -- holding one would serialise every WebView
            // worker-thread request and defeat the point of the cache.
            zipFile.getInputStream(entry).use { it.readBytes() }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read zip entry: $path", e)
            null
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        try {
            zipFile.close()
        } catch (_: Exception) {
            // idempotent close
        }
    }

    companion object {
        private const val TAG = "EpubSession"
        private fun joinZipPath(dir: String, rel: String): String {
            val cleanRel = rel.trimStart('/')
            return if (dir.isEmpty()) cleanRel else "$dir/$cleanRel"
        }
    }
}

class EpubParser(private val context: Context) {

    /**
     * Legacy: parse metadata only. Prefer [openSession] for reader flows,
     * which returns both [EpubContent] and a reusable ZipFile handle.
     */
    suspend fun parse(uri: Uri): EpubContent? = withContext(Dispatchers.IO) {
        try {
            val opfPath = findOpfPath(uri) ?: return@withContext null
            val opfDir = opfPath.substringBeforeLast("/", "")
            parseOpf(uri, opfPath, opfDir)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Opens a reusable [EpubSession] for [uri]. Copies the EPUB to the local
     * cache (reused across launches when mtime+size match), opens a ZipFile
     * and precomputes the entry map for O(1) lookups.
     */
    suspend fun openSession(uri: Uri): EpubSession? = withContext(Dispatchers.IO) {
        try {
            val cachedFile = ensureCachedCopy(uri) ?: return@withContext null
            // Best-effort eviction; cheap and caller-thread only.
            runCatching { evictStaleCacheEntries() }

            val zipFile = ZipFile(cachedFile)
            val entries = buildEntryMap(zipFile)

            val opfPath = findOpfPathFromEntries(zipFile, entries) ?: run {
                zipFile.close()
                return@withContext null
            }
            val opfDir = opfPath.substringBeforeLast("/", "")
            val content = parseOpfFromEntries(zipFile, entries, opfPath, opfDir) ?: run {
                zipFile.close()
                return@withContext null
            }
            EpubSession(uri, cachedFile, zipFile, entries, content)
        } catch (e: Exception) {
            Log.w(TAG, "openSession failed for $uri", e)
            null
        }
    }

    // Legacy stateless helpers (still used by scanner/metadata paths).
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

    // -- caching --

    private fun ensureCachedCopy(uri: Uri): File? {
        val cacheDir = File(context.cacheDir, CACHE_SUBDIR).apply { mkdirs() }
        val hash = stableHash(uri.toString())
        val dest = File(cacheDir, "$hash.epub")

        // Look up SAF metadata (size/mtime); if both match exactly, reuse.
        // Using `==` on mtime prevents a stale-cache hit when the source is
        // replaced with an older copy of identical size (metadata edit, etc.).
        val (safSize, safMtime) = safSizeAndMtime(uri)
        if (dest.exists() && safSize != null && safSize == dest.length() &&
            safMtime != null && safMtime == dest.lastModified()) {
            return dest
        }

        return try {
            val tmp = File(cacheDir, "$hash.epub.tmp")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tmp).use { out ->
                    input.copyTo(out)
                }
            } ?: return null
            if (dest.exists()) dest.delete()
            if (!tmp.renameTo(dest)) {
                // Fall back to copy+delete if rename fails (rare on cacheDir).
                tmp.copyTo(dest, overwrite = true)
                tmp.delete()
            }
            if (safMtime != null) dest.setLastModified(safMtime)
            dest
        } catch (e: Exception) {
            Log.w(TAG, "EPUB cache copy failed for $uri", e)
            null
        }
    }

    private fun safSizeAndMtime(uri: Uri): Pair<Long?, Long?> {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (!c.moveToFirst()) return null to null
                val sizeIdx = c.getColumnIndex(android.provider.OpenableColumns.SIZE)
                val size = if (sizeIdx >= 0 && !c.isNull(sizeIdx)) c.getLong(sizeIdx) else null
                // DocumentsContract.Document.COLUMN_LAST_MODIFIED == "last_modified"
                val mtimeIdx = c.getColumnIndex("last_modified")
                val mtime = if (mtimeIdx >= 0 && !c.isNull(mtimeIdx)) c.getLong(mtimeIdx) else null
                size to mtime
            } ?: (null to null)
        } catch (_: Exception) {
            null to null
        }
    }

    private fun stableHash(input: String): String {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            val bytes = md.digest(input.toByteArray())
            buildString(bytes.size * 2) {
                for (b in bytes) append(String.format("%02x", b))
            }.take(32)
        } catch (_: Exception) {
            input.hashCode().toString()
        }
    }

    private fun evictStaleCacheEntries() {
        val cacheDir = File(context.cacheDir, CACHE_SUBDIR)
        if (!cacheDir.isDirectory) return
        val files = cacheDir.listFiles()?.filter { it.isFile && it.name.endsWith(".epub") }
            ?: return
        val now = System.currentTimeMillis()

        // Age-based eviction: drop anything older than 30d.
        for (f in files) {
            if (now - f.lastModified() > MAX_AGE_MS) {
                f.delete()
            }
        }
        // Size-based eviction: LRU by mtime until under budget.
        val remaining = cacheDir.listFiles()?.filter { it.isFile && it.name.endsWith(".epub") }
            ?.sortedBy { it.lastModified() } ?: return
        var total = remaining.sumOf { it.length() }
        val it = remaining.iterator()
        while (total > MAX_CACHE_BYTES && it.hasNext()) {
            val victim = it.next()
            val len = victim.length()
            if (victim.delete()) total -= len
        }
    }

    // -- zip/manifest helpers used by openSession --

    private fun buildEntryMap(zipFile: ZipFile): Map<String, ZipEntry> {
        val map = HashMap<String, ZipEntry>()
        val en = zipFile.entries()
        while (en.hasMoreElements()) {
            val e = en.nextElement()
            if (!e.isDirectory) map[e.name] = e
        }
        return map
    }

    private fun findOpfPathFromEntries(zipFile: ZipFile, entries: Map<String, ZipEntry>): String? {
        val container = entries["META-INF/container.xml"] ?: return null
        return try {
            zipFile.getInputStream(container).use { input ->
                val parser = XmlPullParserFactory.newInstance().newPullParser()
                parser.setInput(input.bufferedReader())
                var eventType = parser.eventType
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG && parser.name == "rootfile") {
                        return@use parser.getAttributeValue(null, "full-path")
                    }
                    eventType = parser.next()
                }
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "findOpfPathFromEntries failed", e)
            null
        }
    }

    private fun parseOpfFromEntries(
        zipFile: ZipFile,
        entries: Map<String, ZipEntry>,
        opfPath: String,
        opfDir: String
    ): EpubContent? {
        val entry = entries[opfPath] ?: return null
        return try {
            val opfContent = zipFile.getInputStream(entry).use { it.readBytes().decodeToString() }
            parseOpfContent(opfContent, opfDir)
        } catch (e: Exception) {
            Log.w(TAG, "parseOpfFromEntries failed", e)
            null
        }
    }

    // -- legacy streaming helpers (parse() path) --

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

    companion object {
        private const val TAG = "EpubParser"
        private const val CACHE_SUBDIR = "epub"
        private const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000 // 30 days
        private const val MAX_CACHE_BYTES = 200L * 1024 * 1024   // 200 MB
    }
}
