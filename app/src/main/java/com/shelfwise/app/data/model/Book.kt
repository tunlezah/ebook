package com.shelfwise.app.data.model

enum class BookFormat {
    EPUB, PDF, MOBI
}

enum class SortOrder {
    TITLE, AUTHOR, RECENT
}

data class Book(
    val id: Long = 0,
    val title: String,
    val author: String,
    val filePath: String,
    val coverPath: String? = null,
    val format: BookFormat,
    val fileSize: Long = 0,
    val currentChapter: Int = 0,
    val currentPosition: Float = 0f, // 0.0 to 1.0 within chapter
    val totalChapters: Int = 0,
    val overallProgress: Float = 0f, // 0.0 to 1.0 overall
    val lastReadTimestamp: Long = 0,
    val addedTimestamp: Long = System.currentTimeMillis(),
    val treeUri: String? = null // SAF tree URI for accessing the file
)
