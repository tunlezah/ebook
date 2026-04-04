package com.shelfwise.app.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "books",
    indices = [
        Index(value = ["file_path"], unique = true),
        Index(value = ["title"]),
        Index(value = ["author"]),
        Index(value = ["last_read_timestamp"])
    ]
)
data class BookEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "author")
    val author: String = "Unknown",

    @ColumnInfo(name = "file_path")
    val filePath: String,

    @ColumnInfo(name = "cover_path")
    val coverPath: String? = null,

    @ColumnInfo(name = "format")
    val format: String, // EPUB, PDF, MOBI

    @ColumnInfo(name = "file_size")
    val fileSize: Long = 0,

    @ColumnInfo(name = "current_chapter")
    val currentChapter: Int = 0,

    @ColumnInfo(name = "current_position")
    val currentPosition: Float = 0f,

    @ColumnInfo(name = "total_chapters")
    val totalChapters: Int = 0,

    @ColumnInfo(name = "overall_progress")
    val overallProgress: Float = 0f,

    @ColumnInfo(name = "last_read_timestamp")
    val lastReadTimestamp: Long = 0,

    @ColumnInfo(name = "added_timestamp")
    val addedTimestamp: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "tree_uri")
    val treeUri: String? = null
)
