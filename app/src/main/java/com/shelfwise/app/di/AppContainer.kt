package com.shelfwise.app.di

import android.content.Context
import com.shelfwise.app.data.db.AppDatabase
import com.shelfwise.app.data.repository.BookRepository
import com.shelfwise.app.scanner.BookScanner
import com.shelfwise.app.util.PreferencesManager

class AppContainer(private val context: Context) {

    val database: AppDatabase by lazy {
        AppDatabase.getInstance(context)
    }

    val bookRepository: BookRepository by lazy {
        BookRepository(database.bookDao(), context)
    }

    val bookScanner: BookScanner by lazy {
        BookScanner(context, bookRepository)
    }

    val preferencesManager: PreferencesManager by lazy {
        PreferencesManager(context)
    }
}
