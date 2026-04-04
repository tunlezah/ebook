package com.shelfwise.app.ui.reader.epub

import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.savedstate.SavedStateRegistryOwner
import com.shelfwise.app.data.repository.BookRepository
import com.shelfwise.app.reader.epub.EpubParser
import com.shelfwise.app.util.PreferencesManager

class EpubReaderViewModelFactory(
    owner: SavedStateRegistryOwner,
    private val repository: BookRepository,
    private val epubParser: EpubParser,
    private val prefs: PreferencesManager
) : AbstractSavedStateViewModelFactory(owner, null) {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(key: String, modelClass: Class<T>, handle: SavedStateHandle): T {
        return EpubReaderViewModel(repository, epubParser, prefs, handle) as T
    }
}
