package com.shelfwise.app.ui.reader.pdf

import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PdfPageAdapter(
    private val pageCount: Int,
    private val viewModel: PdfReaderViewModel,
    private val scope: CoroutineScope
) : RecyclerView.Adapter<PdfPageAdapter.PageViewHolder>() {

    class PageViewHolder(val imageView: ImageView) : RecyclerView.ViewHolder(imageView) {
        var renderJob: Job? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val imageView = ImageView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
        }
        return PageViewHolder(imageView)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        // Cancel any in-flight render for this recycled holder before starting a new one.
        holder.renderJob?.cancel()
        // Clear the stale bitmap immediately so a recycled holder never briefly shows the
        // previous page while the new one renders.
        holder.imageView.setImageBitmap(null)

        val width = holder.imageView.context.resources.displayMetrics.widthPixels
        val boundPosition = position

        holder.renderJob = scope.launch {
            val bitmap = viewModel.renderPage(boundPosition, width)
            ensureActive()
            withContext(Dispatchers.Main) {
                if (!coroutineContext[Job]!!.isActive) return@withContext
                // Holder may have been rebound to a different page while we were rendering.
                if (holder.bindingAdapterPosition != boundPosition) return@withContext
                if (bitmap != null && !bitmap.isRecycled) {
                    holder.imageView.setImageBitmap(bitmap)
                }
            }
        }
    }

    override fun onViewRecycled(holder: PageViewHolder) {
        super.onViewRecycled(holder)
        holder.renderJob?.cancel()
        holder.renderJob = null
        // Do not hold a strong reference to the bitmap; the ViewModel's LRU owns it.
        holder.imageView.setImageBitmap(null)
    }

    override fun getItemCount(): Int = pageCount
}
