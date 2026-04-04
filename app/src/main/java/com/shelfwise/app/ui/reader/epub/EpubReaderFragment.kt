package com.shelfwise.app.ui.reader.epub

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
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
import com.shelfwise.app.reader.epub.EpubParser
import com.shelfwise.app.util.appContainer
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

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

        viewModel.loadBook(bookId)
        observeState()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        binding.webView.apply {
            settings.javaScriptEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
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
            }
        }

        // Apply blue light filter
        binding.blueLightOverlay.isVisible = appContainer.preferencesManager.blueLightFilter
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
                    }

                    state.chapterHtml?.let { html ->
                        val styledHtml = viewModel.buildStyledHtml(html)
                        binding.webView.loadDataWithBaseURL(
                            null, styledHtml, "text/html", "UTF-8", null
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
                        com.shelfwise.app.util.showToast(requireContext(), error)
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
        binding.webView.destroy()
        _binding = null
        super.onDestroyView()
    }
}

// Utility function used in observer
private fun com.shelfwise.app.util.showToast(context: android.content.Context, message: String) {
    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
}
