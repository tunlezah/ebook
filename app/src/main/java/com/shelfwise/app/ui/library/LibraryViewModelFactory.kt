package com.shelfwise.app.ui.library

import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.savedstate.SavedStateRegistryOwner
import com.shelfwise.app.data.repository.BookRepository
import com.shelfwise.app.scanner.BookScanner
import com.shelfwise.app.util.PreferencesManager

class LibraryViewModelFactory(
    owner: SavedStateRegistryOwner,
    private val repository: BookRepository,
    private val scanner: BookScanner,
    private val prefs: PreferencesManager
) : AbstractSavedStateViewModelFactory(owner, null) {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(key: String, modelClass: Class<T>, handle: SavedStateHandle): T {
        return LibraryViewModel(repository, scanner, prefs, handle) as T
    }
}
