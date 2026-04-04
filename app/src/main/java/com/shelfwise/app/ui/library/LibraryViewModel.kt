package com.shelfwise.app.ui.library

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shelfwise.app.data.model.Book
import com.shelfwise.app.data.model.SortOrder
import com.shelfwise.app.data.repository.BookRepository
import com.shelfwise.app.scanner.BookScanner
import com.shelfwise.app.util.PreferencesManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LibraryViewModel(
    private val repository: BookRepository,
    private val scanner: BookScanner,
    private val prefs: PreferencesManager,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _sortOrder = MutableStateFlow(prefs.sortOrder)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private val _searchQuery = MutableStateFlow(savedStateHandle.get<String>("search") ?: "")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanResult = MutableStateFlow<BookScanner.ScanResult?>(null)
    val scanResult: StateFlow<BookScanner.ScanResult?> = _scanResult.asStateFlow()

    var isGridView: Boolean
        get() = prefs.isGridView
        set(value) { prefs.isGridView = value }

    @OptIn(ExperimentalCoroutinesApi::class)
    val books: StateFlow<List<Book>> = _searchQuery.flatMapLatest { query ->
        if (query.isBlank()) {
            _sortOrder.flatMapLatest { order -> repository.getBooks(order) }
        } else {
            repository.searchBooks(query)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun setSortOrder(order: SortOrder) {
        prefs.sortOrder = order
        _sortOrder.value = order
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        savedStateHandle["search"] = query
    }

    fun scanFolder(treeUri: Uri) {
        viewModelScope.launch {
            _isScanning.value = true
            try {
                val result = scanner.scanTreeUri(treeUri)
                _scanResult.value = result
            } catch (_: Exception) {
                _scanResult.value = BookScanner.ScanResult(0, 0, 1)
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun rescanAllFolders() {
        viewModelScope.launch {
            _isScanning.value = true
            try {
                val uris = prefs.getTreeUris()
                var totalAdded = 0
                var totalRemoved = 0
                var totalErrors = 0
                for (uriString in uris) {
                    try {
                        val result = scanner.scanTreeUri(Uri.parse(uriString))
                        totalAdded += result.added
                        totalRemoved += result.removed
                        totalErrors += result.errors
                    } catch (_: Exception) {
                        totalErrors++
                    }
                }
                _scanResult.value = BookScanner.ScanResult(totalAdded, totalRemoved, totalErrors)
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun deleteBook(bookId: Long) {
        viewModelScope.launch {
            repository.deleteBook(bookId)
        }
    }

    fun clearScanResult() {
        _scanResult.value = null
    }
}
