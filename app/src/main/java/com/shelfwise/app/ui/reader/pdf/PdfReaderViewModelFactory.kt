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
    // IMPORTANT: must be an application context. ViewModels outlive Activities
    // across config changes; holding an Activity here would leak it. Callers
    // must pass `requireContext().applicationContext`.
    private val appContext: Context
) : AbstractSavedStateViewModelFactory(owner, null) {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(key: String, modelClass: Class<T>, handle: SavedStateHandle): T {
        return PdfReaderViewModel(repository, appContext, handle) as T
    }
}
