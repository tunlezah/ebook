package com.shelfwise.app.ui.reader.epub

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.SeekBar
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.shelfwise.app.databinding.FragmentEpubReaderBinding
import com.shelfwise.app.util.showToast
import com.shelfwise.app.reader.epub.EpubParser
import com.shelfwise.app.reader.epub.EpubSession
import com.shelfwise.app.util.appContainer
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream

class EpubReaderFragment : Fragment() {

    private var _binding: FragmentEpubReaderBinding? = null
    private val binding get() = _binding!!

    private val viewModel: EpubReaderViewModel by viewModels {
        EpubReaderViewModelFactory(
            this,
            appContainer.bookRepository,
            EpubParser(requireContext()),
            appContainer.preferencesManager
        )
    }

    private var uiVisible = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentEpubReaderBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupWebView()
        setupControls()

        val bookId = arguments?.getLong("bookId") ?: run {
            findNavController().popBackStack()
            return
        }

        // Guard against reloading on configuration changes (rotation etc.): if the
        // VM already holds parsed content for this book, don't re-parse.
        if (viewModel.state.value.book == null) {
            viewModel.loadBook(bookId)
        }
        observeState()
    }

    override fun onResume() {
        super.onResume()
        // Settings may have changed while we were away; re-apply blue-light overlay.
        applyBlueLightOverlay()
    }

    /**
     * Applies [PreferencesManager.blueLightFilter] + [PreferencesManager.blueLightIntensity]
     * to the overlay View. Intensity (0..100) is mapped onto an alpha of 0.0..0.6 so
     * the screen is never completely blocked.
     */
    private fun applyBlueLightOverlay() {
        val binding = _binding ?: return
        val prefs = appContainer.preferencesManager
        val enabled = prefs.blueLightFilter
        val intensity = prefs.blueLightIntensity.coerceIn(0, 100)
        val alpha = (intensity / 100f) * MAX_BLUE_LIGHT_ALPHA
        binding.blueLightOverlay.alpha = alpha
        binding.blueLightOverlay.isVisible = enabled && intensity > 0
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        binding.webView.apply {
            settings.javaScriptEnabled = true
            // Local file/content schemes are locked down; EPUB resources flow through
            // our synthetic https://shelfwise.local origin via shouldInterceptRequest.
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.allowFileAccessFromFileURLs = false
            settings.allowUniversalAccessFromFileURLs = false
            // Must be false so the interceptor is actually invoked for <img>/<link>/fonts.
            settings.blockNetworkImage = false
            settings.blockNetworkLoads = false
            settings.domStorageEnabled = false
            settings.setSupportZoom(false)
            settings.builtInZoomControls = false
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false

            addJavascriptInterface(WebAppInterface(), "shelfwise")

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    // Restore scroll position
                    val state = viewModel.state.value
                    val book = state.book
                    if (book != null && book.currentPosition > 0f && state.currentChapter == book.currentChapter) {
                        val scrollY = "(document.body.scrollHeight - window.innerHeight) * ${book.currentPosition}"
                        view?.evaluateJavascript("window.scrollTo(0, $scrollY)", null)
                    }
                }

                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    val url = request?.url ?: return null
                    if (url.host != EPUB_ASSET_HOST) return null
                    return interceptEpubResource(url)
                }
            }
        }

        // Apply blue-light overlay initially; kept in sync on each onResume().
        applyBlueLightOverlay()
    }

    private fun interceptEpubResource(url: Uri): WebResourceResponse? {
        // Capture everything the worker thread needs under the UI thread snapshot.
        val snapshot = assetSnapshot ?: return null
        // Normalize: strip leading '/', collapse any residual '../' segments.
        val rawPath = (url.path ?: return null).trimStart('/')
        val normalized = normalizeZipPath(rawPath) ?: return null
        val bytes = try {
            runBlocking {
                // Resolve through the shared EpubSession (cached ZipFile + O(1)
                // manifest lookup) so worker-thread reads don't re-open the SAF
                // stream per resource.
                snapshot.session.getResource(normalized)
            }
        } catch (_: Exception) {
            null
        } ?: return null
        val mime = mimeTypeForPath(normalized)
        return WebResourceResponse(mime, null, ByteArrayInputStream(bytes))
    }

    private fun normalizeZipPath(path: String): String? {
        val segments = path.split('/').toMutableList()
        val out = ArrayList<String>(segments.size)
        for (s in segments) {
            when (s) {
                "", "." -> { /* skip */ }
                ".." -> if (out.isNotEmpty()) out.removeAt(out.size - 1) else return null
                else -> out.add(s)
            }
        }
        return out.joinToString("/")
    }

    private fun mimeTypeForPath(path: String): String {
        val ext = path.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "svg" -> "image/svg+xml"
            "css" -> "text/css"
            "js" -> "application/javascript"
            "html", "htm", "xhtml" -> "text/html"
            "woff" -> "application/font-woff"
            "woff2" -> "application/font-woff2"
            "ttf" -> "font/ttf"
            "otf" -> "font/otf"
            "xml" -> "application/xml"
            // EPUB3 media overlays / embedded media
            "mp3" -> "audio/mpeg"
            "m4a", "aac" -> "audio/mp4"
            "ogg", "opus" -> "audio/ogg"
            "wav" -> "audio/wav"
            "mp4" -> "video/mp4"
            "webm" -> "video/webm"
            else -> "application/octet-stream"
        }
    }

    // Snapshot of data needed by the WebView worker thread for interception.
    // Volatile: written on UI thread, read on WebView worker thread.
    private data class AssetSnapshot(
        val session: EpubSession,
        val bookUri: Uri
    )

    @Volatile
    private var assetSnapshot: AssetSnapshot? = null

    private fun buildChapterBaseUrl(state: EpubReaderViewModel.ReaderState): String {
        val content = state.content
        val chapters = content?.chapters
        val idx = state.currentChapter
        if (content == null || chapters == null || idx < 0 || idx >= chapters.size) {
            return "https://$EPUB_ASSET_HOST/"
        }
        val chapterHref = chapters[idx].href
        val chapterDir = chapterHref.substringBeforeLast('/', "")
        // Anchor relative references at <opfDir>/<chapterDir>/ so WebView normalizes
        // '../' refs against the correct starting point inside the zip.
        val prefix = buildString {
            if (content.opfDir.isNotEmpty()) {
                append(content.opfDir.trim('/'))
                append('/')
            }
            if (chapterDir.isNotEmpty()) {
                append(chapterDir.trim('/'))
                append('/')
            }
        }
        return "https://$EPUB_ASSET_HOST/$prefix"
    }

    companion object {
        private const val EPUB_ASSET_HOST = "shelfwise.local"
        // Cap blue-light overlay alpha so the screen is never fully blocked.
        private const val MAX_BLUE_LIGHT_ALPHA = 0.6f
    }

    private fun setupControls() {
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnChapters.setOnClickListener {
            showChapterList()
        }

        binding.btnSettings.setOnClickListener {
            showReaderSettings()
        }

        binding.progressSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {}
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val progress = seekBar?.progress ?: return
                val chapters = viewModel.getChapters()
                if (chapters.isNotEmpty()) {
                    val targetChapter = (progress * chapters.size / 100).coerceIn(0, chapters.size - 1)
                    viewModel.loadChapter(targetChapter)
                }
            }
        })
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collectLatest { state ->
                    binding.loadingIndicator.isVisible = state.isLoading

                    state.book?.let { book ->
                        binding.titleText.text = book.title
                        // Refresh the worker-thread snapshot whenever the book (or
                        // backing session) changes. The snapshot carries the same
                        // EpubSession the VM uses, so WebView worker-thread reads
                        // hit the cached ZipFile + manifest rather than re-opening SAF.
                        val activeSession = viewModel.currentSession()
                        val currentSnap = assetSnapshot
                        if (activeSession != null &&
                            (currentSnap == null ||
                                currentSnap.session !== activeSession ||
                                currentSnap.bookUri.toString() != book.filePath)) {
                            assetSnapshot = AssetSnapshot(
                                session = activeSession,
                                bookUri = Uri.parse(book.filePath)
                            )
                        }
                    }

                    state.chapterHtml?.let { html ->
                        val styledHtml = viewModel.buildStyledHtml(html)
                        val baseUrl = buildChapterBaseUrl(state)
                        binding.webView.loadDataWithBaseURL(
                            baseUrl, styledHtml, "text/html", "UTF-8", null
                        )
                    }

                    val chapters = state.content?.chapters
                    if (chapters != null && chapters.isNotEmpty()) {
                        val progress = ((state.currentChapter + 1).toFloat() / chapters.size * 100).toInt()
                        binding.progressSeekBar.progress = progress
                        binding.progressText.text = "$progress%"
                        binding.chapterText.text = "Chapter ${state.currentChapter + 1} of ${chapters.size}"
                    }

                    state.error?.let { error ->
                        requireContext().showToast(error)
                    }
                }
            }
        }
    }

    private fun showChapterList() {
        val chapters = viewModel.getChapters()
        if (chapters.isEmpty()) return

        val titles = chapters.mapIndexed { index, ch ->
            ch.title.ifBlank { "Chapter ${index + 1}" }
        }.toTypedArray()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Chapters")
            .setItems(titles) { _, which ->
                viewModel.loadChapter(which)
            }
            .show()
    }

    private fun showReaderSettings() {
        val themes = arrayOf("Light", "Sepia", "Dark")
        val currentTheme = when (viewModel.prefs.readerTheme) {
            "sepia" -> 1
            "dark" -> 2
            else -> 0
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Reader Theme")
            .setSingleChoiceItems(themes, currentTheme) { dialog, which ->
                val theme = when (which) {
                    1 -> "sepia"
                    2 -> "dark"
                    else -> "light"
                }
                viewModel.prefs.readerTheme = theme
                // Reload current chapter with new theme
                viewModel.loadChapter(viewModel.state.value.currentChapter)
                dialog.dismiss()
            }
            .show()
    }

    private fun toggleUI() {
        uiVisible = !uiVisible
        binding.topBar.isVisible = uiVisible
        binding.bottomBar.isVisible = uiVisible
    }

    inner class WebAppInterface {
        @JavascriptInterface
        fun nextPage() {
            activity?.runOnUiThread {
                viewModel.nextChapter()
            }
        }

        @JavascriptInterface
        fun previousPage() {
            activity?.runOnUiThread {
                viewModel.previousChapter()
            }
        }

        @JavascriptInterface
        fun toggleUI() {
            activity?.runOnUiThread {
                this@EpubReaderFragment.toggleUI()
            }
        }

        @JavascriptInterface
        fun onScroll(position: Float) {
            viewModel.saveScrollPosition(position)
        }
    }

    override fun onDestroyView() {
        assetSnapshot = null
        binding.webView.destroy()
        _binding = null
        super.onDestroyView()
    }
}
