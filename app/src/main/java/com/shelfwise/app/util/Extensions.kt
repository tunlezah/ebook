package com.shelfwise.app.util

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.shelfwise.app.EBookApp
import com.shelfwise.app.di.AppContainer
import java.text.DecimalFormat

val Context.appContainer: AppContainer
    get() = (applicationContext as EBookApp).container

val Fragment.appContainer: AppContainer
    get() = requireContext().appContainer

fun Context.showToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, message, duration).show()
}

fun Long.formatFileSize(): String {
    if (this <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(this.toDouble()) / Math.log10(1024.0)).toInt()
    val index = digitGroups.coerceIn(0, units.size - 1)
    return DecimalFormat("#,##0.#").format(this / Math.pow(1024.0, index.toDouble())) + " " + units[index]
}

fun Uri.getDocumentDisplayName(context: Context): String? {
    return try {
        val docUri = if (DocumentsContract.isTreeUri(this)) {
            DocumentsContract.buildDocumentUriUsingTree(
                this, DocumentsContract.getTreeDocumentId(this)
            )
        } else {
            this
        }
        context.contentResolver.query(docUri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    } catch (_: Exception) {
        null
    }
}

fun String.toBookFormat(): String {
    return when {
        endsWith(".epub", ignoreCase = true) -> "EPUB"
        endsWith(".pdf", ignoreCase = true) -> "PDF"
        endsWith(".mobi", ignoreCase = true) || endsWith(".azw3", ignoreCase = true) || endsWith(".azw", ignoreCase = true) -> "MOBI"
        else -> ""
    }
}

fun String.isBookFile(): Boolean {
    return endsWith(".epub", ignoreCase = true) ||
            endsWith(".pdf", ignoreCase = true) ||
            endsWith(".mobi", ignoreCase = true) ||
            endsWith(".azw3", ignoreCase = true) ||
            endsWith(".azw", ignoreCase = true)
}
