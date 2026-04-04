package com.shelfwise.app.ui.reader.pdf

import android.content.Context
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.savedstate.SavedStateRegistryOwner
import com.shelfwise.app.data.repository.BookRepository

class PdfReaderViewModelFactory(
    owner: SavedStateRegistryOwner,
    private val repository: BookRepository,
    private val context: Context
) : AbstractSavedStateViewModelFactory(owner, null) {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(key: String, modelClass: Class<T>, handle: SavedStateHandle): T {
        return PdfReaderViewModel(repository, context, handle) as T
    }
}
