package com.shelfwise.app.ui.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import coil.load
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.shelfwise.app.R
import com.shelfwise.app.data.model.Book
import com.shelfwise.app.data.model.BookFormat
import com.shelfwise.app.databinding.FragmentBookDetailBinding
import com.shelfwise.app.util.appContainer
import com.shelfwise.app.util.formatFileSize
import kotlinx.coroutines.launch
import java.io.File

class BookDetailFragment : Fragment() {

    private var _binding: FragmentBookDetailBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBookDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val bookId = arguments?.getLong("bookId") ?: run {
            findNavController().popBackStack()
            return
        }

        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val book = appContainer.bookRepository.getBookById(bookId)
            if (book != null) {
                bindBook(book)
            } else {
                findNavController().popBackStack()
            }
        }
    }

    private fun bindBook(book: Book) {
        binding.titleText.text = book.title
        binding.authorText.text = book.author
        binding.infoText.text = "${book.format.name} · ${book.fileSize.formatFileSize()}"

        if (book.coverPath != null && File(book.coverPath).exists()) {
            binding.coverImage.load(File(book.coverPath)) {
                placeholder(R.drawable.book_cover_placeholder)
            }
        }

        val progress = (book.overallProgress * 100).toInt()
        if (progress > 0) {
            binding.progressContainer.isVisible = true
            binding.progressBar.progress = progress
            binding.progressText.text = "$progress%"
            binding.btnRead.text = getString(R.string.book_detail_continue)
        }

        binding.btnRead.setOnClickListener {
            when (book.format) {
                BookFormat.EPUB, BookFormat.MOBI -> {
                    findNavController().navigate(
                        R.id.epubReaderFragment,
                        bundleOf("bookId" to book.id)
                    )
                }
                BookFormat.PDF -> {
                    findNavController().navigate(
                        R.id.pdfReaderFragment,
                        bundleOf("bookId" to book.id)
                    )
                }
            }
        }

        binding.btnDelete.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Remove book")
                .setMessage("Remove \"${book.title}\" from your library? The file will not be deleted.")
                .setPositiveButton("Remove") { _, _ ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        appContainer.bookRepository.deleteBook(book.id)
                        findNavController().popBackStack()
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
