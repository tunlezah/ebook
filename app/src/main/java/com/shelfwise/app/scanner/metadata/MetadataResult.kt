package com.shelfwise.app.scanner.metadata

data class MetadataResult(
    val title: String?,
    val author: String?,
    val coverPath: String?,
    val chapterCount: Int,
    val language: String? = null,
    val description: String? = null
)
