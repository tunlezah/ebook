package com.shelfwise.app.scanner

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.shelfwise.app.data.model.Book
import com.shelfwise.app.data.model.BookFormat
import com.shelfwise.app.data.repository.BookRepository
import com.shelfwise.app.scanner.metadata.EpubMetadataExtractor
import com.shelfwise.app.scanner.metadata.MobiMetadataExtractor
import com.shelfwise.app.scanner.metadata.PdfMetadataExtractor
import com.shelfwise.app.util.isBookFile
import com.shelfwise.app.util.toBookFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BookScanner(
    private val context: Context,
    private val repository: BookRepository
) {
    private val epubExtractor = EpubMetadataExtractor(context)
    private val pdfExtractor = PdfMetadataExtractor(context)
    private val mobiExtractor = MobiMetadataExtractor(context)

    data class ScanResult(
        val added: Int,
        val removed: Int,
        val errors: Int
    )

    suspend fun scanTreeUri(treeUri: Uri): ScanResult = withContext(Dispatchers.IO) {
        val existingPaths = repository.getAllFilePaths().toMutableSet()
        val foundPaths = mutableSetOf<String>()
        var added = 0
        var errors = 0

        try {
            scanDirectory(treeUri, treeUri, foundPaths) { fileUri, displayName, fileSize ->
                val filePath = fileUri.toString()
                foundPaths.add(filePath)

                if (filePath !in existingPaths) {
                    try {
                        val book = extractMetadata(fileUri, displayName, fileSize, treeUri.toString())
                        if (book != null) {
                            repository.addBook(book)
                            added++
                        }
                    } catch (_: Exception) {
                        errors++
                    }
                }
            }
        } catch (_: Exception) {
            errors++
        }

        // Remove books whose files no longer exist
        val removed = existingPaths.count { path ->
            if (path !in foundPaths && isPathUnderTree(path, treeUri)) {
                val book = repository.getBookByPath(path)
                if (book != null) {
                    repository.deleteBook(book.id)
                    true
                } else false
            } else false
        }

        ScanResult(added, removed, errors)
    }

    private fun scanDirectory(
        treeUri: Uri,
        parentUri: Uri,
        foundPaths: MutableSet<String>,
        onBookFound: suspend (Uri, String, Long) -> Unit
    ) {
        val docId = if (parentUri == treeUri) {
            DocumentsContract.getTreeDocumentId(treeUri)
        } else {
            DocumentsContract.getDocumentId(parentUri)
        }

        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE
        )

        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)

            while (cursor.moveToNext()) {
                val childDocId = cursor.getString(idIndex)
                val displayName = cursor.getString(nameIndex) ?: continue
                val mimeType = cursor.getString(mimeIndex) ?: ""
                val fileSize = cursor.getLong(sizeIndex)

                if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                    val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId)
                    scanDirectory(treeUri, childUri, foundPaths, onBookFound)
                } else if (displayName.isBookFile()) {
                    val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId)
                    kotlinx.coroutines.runBlocking {
                        onBookFound(fileUri, displayName, fileSize)
                    }
                }
            }
        }
    }

    private suspend fun extractMetadata(
        fileUri: Uri,
        displayName: String,
        fileSize: Long,
        treeUri: String
    ): Book? {
        val format = when {
            displayName.endsWith(".epub", true) -> BookFormat.EPUB
            displayName.endsWith(".pdf", true) -> BookFormat.PDF
            displayName.endsWith(".mobi", true) || displayName.endsWith(".azw3", true) || displayName.endsWith(".azw", true) -> BookFormat.MOBI
            else -> return null
        }

        val metadata = when (format) {
            BookFormat.EPUB -> epubExtractor.extract(fileUri)
            BookFormat.PDF -> pdfExtractor.extract(fileUri)
            BookFormat.MOBI -> mobiExtractor.extract(fileUri)
        }

        val title = metadata?.title?.takeIf { it.isNotBlank() }
            ?: displayName.substringBeforeLast(".")

        return Book(
            title = title,
            author = metadata?.author ?: "Unknown",
            filePath = fileUri.toString(),
            coverPath = metadata?.coverPath,
            format = format,
            fileSize = fileSize,
            totalChapters = metadata?.chapterCount ?: 0,
            treeUri = treeUri
        )
    }

    private fun isPathUnderTree(path: String, treeUri: Uri): Boolean {
        return path.startsWith("content://") && path.contains(treeUri.authority ?: "")
    }
}
