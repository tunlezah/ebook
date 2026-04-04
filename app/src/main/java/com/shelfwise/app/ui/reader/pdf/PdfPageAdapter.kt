package com.shelfwise.app.ui.reader.pdf

import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PdfPageAdapter(
    private val pageCount: Int,
    private val viewModel: PdfReaderViewModel,
    private val scope: CoroutineScope
) : RecyclerView.Adapter<PdfPageAdapter.PageViewHolder>() {

    class PageViewHolder(val imageView: ImageView) : RecyclerView.ViewHolder(imageView)

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
        val width = holder.imageView.context.resources.displayMetrics.widthPixels

        scope.launch {
            val bitmap = viewModel.renderPage(position, width)
            withContext(Dispatchers.Main) {
                if (bitmap != null && !bitmap.isRecycled) {
                    holder.imageView.setImageBitmap(bitmap)
                }
            }
        }
    }

    override fun getItemCount(): Int = pageCount
}
