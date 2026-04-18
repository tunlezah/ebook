package com.shelfwise.app.ui.library

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.shelfwise.app.R
import com.shelfwise.app.data.model.Book
import com.shelfwise.app.databinding.ItemBookGridBinding
import com.shelfwise.app.databinding.ItemBookListBinding

class BookAdapter(
    private var isGridMode: Boolean = true,
    private val onClick: (Book) -> Unit,
    private val onLongClick: (Book) -> Unit
) : ListAdapter<Book, RecyclerView.ViewHolder>(BookDiffCallback()) {

    companion object {
        private const val VIEW_TYPE_GRID = 0
        private const val VIEW_TYPE_LIST = 1
    }

    fun setGridMode(grid: Boolean) {
        if (isGridMode != grid) {
            isGridMode = grid
            notifyDataSetChanged()
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (isGridMode) VIEW_TYPE_GRID else VIEW_TYPE_LIST
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_GRID) {
            val binding = ItemBookGridBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            GridViewHolder(binding)
        } else {
            val binding = ItemBookListBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            ListViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val book = getItem(position)
        when (holder) {
            is GridViewHolder -> holder.bind(book)
            is ListViewHolder -> holder.bind(book)
        }
    }

    inner class GridViewHolder(private val binding: ItemBookGridBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(book: Book) {
            binding.titleText.text = book.title
            binding.authorText.text = book.author
            binding.formatBadge.text = book.format.name

            // Load cover image
            val coverPath = book.coverPath?.takeIf { it.isNotBlank() }
            binding.coverImage.load(coverPath) {
                crossfade(false)
                placeholder(R.drawable.book_cover_placeholder)
                error(R.drawable.book_cover_placeholder)
            }

            // Progress bar
            val progress = (book.overallProgress * 100).toInt()
            if (progress > 0) {
                binding.progressBar.visibility = View.VISIBLE
                binding.progressBar.progress = progress
            } else {
                binding.progressBar.visibility = View.GONE
            }

            binding.root.setOnClickListener { onClick(book) }
            binding.root.setOnLongClickListener { onLongClick(book); true }
        }
    }

    inner class ListViewHolder(private val binding: ItemBookListBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(book: Book) {
            binding.titleText.text = book.title
            binding.authorText.text = book.author
            binding.formatBadge.text = book.format.name

            val coverPath = book.coverPath?.takeIf { it.isNotBlank() }
            binding.coverImage.load(coverPath) {
                crossfade(false)
                placeholder(R.drawable.book_cover_placeholder)
                error(R.drawable.book_cover_placeholder)
            }

            val progress = (book.overallProgress * 100).toInt()
            if (progress > 0) {
                binding.progressBar.visibility = View.VISIBLE
                binding.progressBar.progress = progress
                binding.progressText.visibility = View.VISIBLE
                binding.progressText.text = binding.root.context.getString(R.string.book_progress, progress)
            } else {
                binding.progressBar.visibility = View.GONE
                binding.progressText.visibility = View.GONE
            }

            binding.root.setOnClickListener { onClick(book) }
            binding.root.setOnLongClickListener { onLongClick(book); true }
        }
    }

    class BookDiffCallback : DiffUtil.ItemCallback<Book>() {
        override fun areItemsTheSame(oldItem: Book, newItem: Book): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Book, newItem: Book): Boolean = oldItem == newItem
    }
}
