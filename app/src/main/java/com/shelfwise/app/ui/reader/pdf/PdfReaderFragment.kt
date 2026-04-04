package com.shelfwise.app.ui.reader.pdf

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.shelfwise.app.R
import com.shelfwise.app.databinding.FragmentPdfReaderBinding
import com.shelfwise.app.util.appContainer
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PdfReaderFragment : Fragment() {

    private var _binding: FragmentPdfReaderBinding? = null
    private val binding get() = _binding!!

    private val viewModel: PdfReaderViewModel by viewModels {
        PdfReaderViewModelFactory(
            this,
            appContainer.bookRepository,
            requireContext()
        )
    }

    private var uiVisible = false
    private var pdfAdapter: PdfPageAdapter? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPdfReaderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupControls()

        val bookId = arguments?.getLong("bookId") ?: run {
            findNavController().popBackStack()
            return
        }

        viewModel.loadBook(bookId)
        observeState()
    }

    private fun setupControls() {
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        // Toggle UI on tap
        binding.pdfRecyclerView.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: android.view.MotionEvent): Boolean {
                if (e.action == android.view.MotionEvent.ACTION_UP) {
                    val w = rv.width
                    val x = e.x
                    if (x > w * 0.3f && x < w * 0.7f) {
                        toggleUI()
                    }
                }
                return false
            }
        })

        // Track scroll position for page saving
        binding.pdfRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
                    val firstVisible = layoutManager.findFirstCompletelyVisibleItemPosition()
                    if (firstVisible >= 0) {
                        viewModel.saveCurrentPage(firstVisible)
                        updatePageText(firstVisible)
                    }
                }
            }
        })

        binding.blueLightOverlay.isVisible = appContainer.preferencesManager.blueLightFilter
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collectLatest { state ->
                    binding.loadingIndicator.isVisible = state.isLoading

                    state.book?.let { book ->
                        binding.titleText.text = book.title
                    }

                    if (state.pageCount > 0 && pdfAdapter == null) {
                        pdfAdapter = PdfPageAdapter(state.pageCount, viewModel, lifecycleScope)
                        binding.pdfRecyclerView.apply {
                            layoutManager = LinearLayoutManager(requireContext())
                            adapter = pdfAdapter
                            setItemViewCacheSize(2)
                            setHasFixedSize(false)
                        }

                        // Scroll to saved page
                        if (state.currentPage > 0) {
                            binding.pdfRecyclerView.scrollToPosition(state.currentPage)
                        }

                        updatePageText(state.currentPage)
                    }

                    state.error?.let { error ->
                        android.widget.Toast.makeText(requireContext(), error, android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun updatePageText(page: Int) {
        val total = viewModel.state.value.pageCount
        binding.pageText.text = getString(R.string.page_of, page + 1, total)
    }

    private fun toggleUI() {
        uiVisible = !uiVisible
        binding.topBar.isVisible = uiVisible
        binding.bottomBar.isVisible = uiVisible
    }

    override fun onDestroyView() {
        pdfAdapter = null
        _binding = null
        super.onDestroyView()
    }
}
