package com.shelfwise.app.ui.library

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.shelfwise.app.R
import com.shelfwise.app.data.model.Book
import com.shelfwise.app.data.model.BookFormat
import com.shelfwise.app.data.model.SortOrder
import com.shelfwise.app.databinding.FragmentLibraryBinding
import com.shelfwise.app.util.appContainer
import com.shelfwise.app.util.showToast
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class LibraryFragment : Fragment() {

    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: LibraryViewModel by viewModels {
        LibraryViewModelFactory(
            this,
            appContainer.bookRepository,
            appContainer.bookScanner,
            appContainer.preferencesManager
        )
    }

    private lateinit var bookAdapter: BookAdapter

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            // Take persistent permission
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            requireContext().contentResolver.takePersistableUriPermission(uri, flags)
            appContainer.preferencesManager.addTreeUri(uri.toString())
            viewModel.scanFolder(uri)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupToolbar()
        setupSearch()
        setupFab()
        setupSwipeRefresh()
        observeState()
    }

    private fun setupRecyclerView() {
        bookAdapter = BookAdapter(
            isGridMode = viewModel.isGridView,
            onClick = ::onBookClicked,
            onLongClick = ::onBookLongClicked
        )

        binding.recyclerView.apply {
            adapter = bookAdapter
            setHasFixedSize(true)
            itemAnimator = null // No animations for speed
            updateLayoutManager(viewModel.isGridView)
        }
    }

    private fun updateLayoutManager(isGrid: Boolean) {
        binding.recyclerView.layoutManager = if (isGrid) {
            GridLayoutManager(requireContext(), resources.getInteger(R.integer.library_grid_columns))
        } else {
            LinearLayoutManager(requireContext())
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_view_toggle -> {
                    val newMode = !viewModel.isGridView
                    viewModel.isGridView = newMode
                    bookAdapter.setGridMode(newMode)
                    updateLayoutManager(newMode)
                    menuItem.setIcon(
                        if (newMode) android.R.drawable.ic_menu_gallery
                        else android.R.drawable.ic_menu_sort_by_size
                    )
                    true
                }
                R.id.action_sort_title -> { viewModel.setSortOrder(SortOrder.TITLE); true }
                R.id.action_sort_author -> { viewModel.setSortOrder(SortOrder.AUTHOR); true }
                R.id.action_sort_recent -> { viewModel.setSortOrder(SortOrder.RECENT); true }
                R.id.action_transfer -> {
                    findNavController().navigate(R.id.action_library_to_transfer)
                    true
                }
                R.id.action_settings -> {
                    findNavController().navigate(R.id.action_library_to_settings)
                    true
                }
                else -> false
            }
        }
    }

    private fun setupSearch() {
        binding.searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.setSearchQuery(s?.toString() ?: "")
            }
        })
    }

    private fun setupFab() {
        binding.fabAddFolder.setOnClickListener {
            folderPicker.launch(null)
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.rescanAllFolders()
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.books.collectLatest { books ->
                        bookAdapter.submitList(books)
                        binding.emptyView.isVisible = books.isEmpty()
                        binding.bookCountText.isVisible = books.isNotEmpty()
                        if (books.isNotEmpty()) {
                            binding.bookCountText.text = getString(R.string.books_count, books.size)
                        }
                    }
                }

                launch {
                    viewModel.isScanning.collectLatest { scanning ->
                        binding.swipeRefresh.isRefreshing = scanning
                    }
                }

                launch {
                    viewModel.scanResult.collectLatest { result ->
                        if (result != null) {
                            val msg = when {
                                result.added > 0 -> "Found ${result.added} new book(s)"
                                result.removed > 0 -> "Removed ${result.removed} book(s)"
                                result.errors > 0 -> "Scan completed with errors"
                                else -> "Library is up to date"
                            }
                            requireContext().showToast(msg)
                            viewModel.clearScanResult()
                        }
                    }
                }
            }
        }
    }

    private fun onBookClicked(book: Book) {
        when (book.format) {
            BookFormat.EPUB, BookFormat.MOBI -> {
                findNavController().navigate(
                    R.id.action_library_to_epub_reader,
                    bundleOf("bookId" to book.id)
                )
            }
            BookFormat.PDF -> {
                findNavController().navigate(
                    R.id.action_library_to_pdf_reader,
                    bundleOf("bookId" to book.id)
                )
            }
        }
    }

    private fun onBookLongClicked(book: Book) {
        findNavController().navigate(
            R.id.action_library_to_detail,
            bundleOf("bookId" to book.id)
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
