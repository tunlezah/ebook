package com.shelfwise.app.ui.reader.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shelfwise.app.data.model.Book
import com.shelfwise.app.data.repository.BookRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PdfReaderViewModel(
    private val repository: BookRepository,
    private val context: Context,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    data class PdfState(
        val book: Book? = null,
        val pageCount: Int = 0,
        val currentPage: Int = 0,
        val isLoading: Boolean = true,
        val error: String? = null
    )

    private val _state = MutableStateFlow(PdfState())
    val state: StateFlow<PdfState> = _state.asStateFlow()

    private var pdfRenderer: PdfRenderer? = null
    private var fileDescriptor: android.os.ParcelFileDescriptor? = null

    // Simple LRU cache for rendered pages
    private val pageCache = object : LinkedHashMap<Int, Bitmap>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Bitmap>?): Boolean {
            if (size > 5) {
                eldest?.value?.recycle()
                return true
            }
            return false
        }
    }

    fun loadBook(bookId: Long) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            try {
                val book = repository.getBookById(bookId)
                if (book == null) {
                    _state.value = _state.value.copy(error = "Book not found", isLoading = false)
                    return@launch
                }

                val uri = Uri.parse(book.filePath)
                withContext(Dispatchers.IO) {
                    fileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
                    pdfRenderer = PdfRenderer(fileDescriptor!!)
                }

                val renderer = pdfRenderer!!
                val startPage = savedStateHandle.get<Int>("page") ?: book.currentChapter

                _state.value = PdfState(
                    book = book,
                    pageCount = renderer.pageCount,
                    currentPage = startPage.coerceIn(0, maxOf(0, renderer.pageCount - 1)),
                    isLoading = false
                )

                if (renderer.pageCount != book.totalChapters) {
                    repository.updateTotalChapters(bookId, renderer.pageCount)
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message ?: "Error", isLoading = false)
            }
        }
    }

    suspend fun renderPage(pageIndex: Int, viewWidth: Int): Bitmap? = withContext(Dispatchers.IO) {
        val cached = pageCache[pageIndex]
        if (cached != null && !cached.isRecycled) return@withContext cached

        val renderer = pdfRenderer ?: return@withContext null
        if (pageIndex < 0 || pageIndex >= renderer.pageCount) return@withContext null

        try {
            val page = renderer.openPage(pageIndex)
            val scale = viewWidth.toFloat() / page.width
            val height = (page.height * scale).toInt()

            val bitmap = Bitmap.createBitmap(viewWidth, height, Bitmap.Config.RGB_565)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()

            synchronized(pageCache) {
                pageCache[pageIndex] = bitmap
            }
            bitmap
        } catch (_: Exception) {
            null
        }
    }

    fun saveCurrentPage(page: Int) {
        savedStateHandle["page"] = page
        viewModelScope.launch {
            val book = _state.value.book ?: return@launch
            val pageCount = _state.value.pageCount
            val progress = if (pageCount > 0) (page + 1).toFloat() / pageCount else 0f
            repository.updateReadingProgress(book.id, page, 0f, progress)
        }
        _state.value = _state.value.copy(currentPage = page)
    }

    override fun onCleared() {
        super.onCleared()
        pageCache.values.forEach { it.recycle() }
        pageCache.clear()
        pdfRenderer?.close()
        fileDescriptor?.close()
    }
}
