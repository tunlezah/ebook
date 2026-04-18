package com.shelfwise.app.data.db.entity

import androidx.room.Entity
import androidx.room.Fts4

@Entity(tableName = "books_fts")
@Fts4(contentEntity = BookEntity::class)
data class BookFtsEntity(
    val title: String,
    val author: String
)
