package com.shelfwise.app.ui.reader.epub

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shelfwise.app.data.model.Book
import com.shelfwise.app.data.repository.BookRepository
import com.shelfwise.app.reader.epub.EpubChapter
import com.shelfwise.app.reader.epub.EpubContent
import com.shelfwise.app.reader.epub.EpubParser
import com.shelfwise.app.reader.epub.EpubSession
import com.shelfwise.app.util.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class EpubReaderViewModel(
    private val repository: BookRepository,
    private val epubParser: EpubParser,
    val prefs: PreferencesManager,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    data class ReaderState(
        val book: Book? = null,
        val content: EpubContent? = null,
        val currentChapter: Int = 0,
        val chapterHtml: String? = null,
        val isLoading: Boolean = true,
        val error: String? = null
    )

    private val _state = MutableStateFlow(ReaderState())
    val state: StateFlow<ReaderState> = _state.asStateFlow()

    private var bookUri: Uri? = null
    private var session: EpubSession? = null

    /** Exposed so the Fragment's WebView interceptor can share the same zip handle. */
    fun currentSession(): EpubSession? = session

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
                bookUri = uri

                // Close any prior session (e.g. reloading a different book in the same VM).
                session?.close()
                session = null

                val newSession = epubParser.openSession(uri)
                if (newSession == null) {
                    _state.value = _state.value.copy(error = "Could not parse EPUB", isLoading = false)
                    return@launch
                }
                session = newSession
                val content = newSession.content

                // Update total chapters if changed
                if (content.chapters.size != book.totalChapters) {
                    repository.updateTotalChapters(bookId, content.chapters.size)
                }

                val chapter = savedStateHandle.get<Int>("chapter") ?: book.currentChapter
                _state.value = _state.value.copy(
                    book = book,
                    content = content,
                    currentChapter = chapter.coerceIn(0, maxOf(0, content.chapters.size - 1))
                )

                loadChapter(chapter.coerceIn(0, maxOf(0, content.chapters.size - 1)))
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message ?: "Error loading book", isLoading = false)
            }
        }
    }

    fun loadChapter(chapterIndex: Int) {
        val content = _state.value.content ?: return
        if (chapterIndex < 0 || chapterIndex >= content.chapters.size) return

        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, currentChapter = chapterIndex)
            savedStateHandle["chapter"] = chapterIndex

            try {
                val chapter = content.chapters[chapterIndex]
                val activeSession = session
                val html = if (activeSession != null) {
                    activeSession.getChapterHtml(chapter.href)
                } else {
                    // Fallback (shouldn't happen — loadBook always opens a session first).
                    epubParser.getChapterHtml(bookUri!!, content.opfDir, chapter.href)
                }

                _state.value = _state.value.copy(
                    chapterHtml = html,
                    isLoading = false
                )

                // Save reading progress
                val book = _state.value.book
                if (book != null) {
                    val progress = if (content.chapters.isNotEmpty()) {
                        (chapterIndex + 1).toFloat() / content.chapters.size
                    } else 0f
                    repository.updateReadingProgress(book.id, chapterIndex, 0f, progress)
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = "Error loading chapter", isLoading = false)
            }
        }
    }

    fun nextChapter() {
        val content = _state.value.content ?: return
        val next = _state.value.currentChapter + 1
        if (next < content.chapters.size) {
            loadChapter(next)
        }
    }

    fun previousChapter() {
        val prev = _state.value.currentChapter - 1
        if (prev >= 0) {
            loadChapter(prev)
        }
    }

    fun saveScrollPosition(position: Float) {
        viewModelScope.launch {
            val book = _state.value.book ?: return@launch
            val content = _state.value.content ?: return@launch
            val chapter = _state.value.currentChapter

            val progress = if (content.chapters.isNotEmpty()) {
                (chapter.toFloat() + position) / content.chapters.size
            } else 0f

            repository.updateReadingProgress(book.id, chapter, position, progress)
        }
    }

    fun getChapters(): List<EpubChapter> {
        return _state.value.content?.chapters ?: emptyList()
    }

    override fun onCleared() {
        super.onCleared()
        try {
            session?.close()
        } catch (_: Exception) {
        }
        session = null
    }

    fun buildStyledHtml(rawHtml: String): String {
        val bgColor = when (prefs.readerTheme) {
            "sepia" -> "#F4ECD8"
            "dark" -> "#1A1A1A"
            else -> "#FEFEFE"
        }
        val textColor = when (prefs.readerTheme) {
            "sepia" -> "#3B2F1E"
            "dark" -> "#D4D4D4"
            else -> "#222222"
        }
        val fontFamily = when (prefs.readerFont) {
            "source_serif" -> "'Source Serif 4', serif"
            "noto_serif" -> "'Noto Serif', serif"
            "merriweather" -> "'Merriweather', serif"
            "lora" -> "'Lora', serif"
            "source_sans" -> "'Source Sans 3', sans-serif"
            else -> "'Literata', serif"
        }
        val fontSize = prefs.readerFontSize
        val lineSpacing = prefs.readerLineSpacing
        val margins = prefs.readerMargins

        // Strip existing <html>, <head>, <body> tags and inject our own styling
        val bodyContent = extractBody(rawHtml)

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
                <style>
                    * { box-sizing: border-box; }
                    body {
                        background-color: $bgColor;
                        color: $textColor;
                        font-family: $fontFamily;
                        font-size: ${fontSize}px;
                        line-height: $lineSpacing;
                        margin: 0;
                        padding: ${margins}px;
                        word-wrap: break-word;
                        overflow-wrap: break-word;
                        -webkit-text-size-adjust: none;
                    }
                    img {
                        max-width: 100%;
                        height: auto;
                    }
                    a { color: inherit; }
                    h1, h2, h3 { line-height: 1.3; }
                    p { margin: 0.8em 0; }
                </style>
                <script>
                    // Tap zones for page navigation
                    document.addEventListener('click', function(e) {
                        var w = window.innerWidth;
                        var x = e.clientX;
                        if (x < w * 0.3) {
                            window.shelfwise.previousPage();
                        } else if (x > w * 0.7) {
                            window.shelfwise.nextPage();
                        } else {
                            window.shelfwise.toggleUI();
                        }
                    });

                    // Report scroll position
                    var scrollTimeout;
                    window.addEventListener('scroll', function() {
                        clearTimeout(scrollTimeout);
                        scrollTimeout = setTimeout(function() {
                            var pos = window.scrollY / (document.body.scrollHeight - window.innerHeight);
                            if (isFinite(pos)) {
                                window.shelfwise.onScroll(pos);
                            }
                        }, 300);
                    });
                </script>
            </head>
            <body>
                $bodyContent
            </body>
            </html>
        """.trimIndent()
    }

    private fun extractBody(html: String): String {
        // Try to extract body content
        val bodyStart = html.indexOf("<body", ignoreCase = true)
        val bodyEnd = html.indexOf("</body>", ignoreCase = true)

        return if (bodyStart >= 0 && bodyEnd > bodyStart) {
            val contentStart = html.indexOf('>', bodyStart) + 1
            html.substring(contentStart, bodyEnd)
        } else {
            // No body tags, try removing html/head
            html.replace(Regex("<\\?xml[^>]*>"), "")
                .replace(Regex("<html[^>]*>", RegexOption.IGNORE_CASE), "")
                .replace(Regex("</html>", RegexOption.IGNORE_CASE), "")
                .replace(Regex("<head>.*?</head>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
        }
    }
}
