package com.statussaver.vault.model

import android.net.Uri

data class StatusItem(
    val uri: Uri,
    val name: String,
    val isVideo: Boolean,
    val size: Long,
    val dateModified: Long,
    val isSaved: Boolean = false,
    val duration: String = ""   // Populated for video items
)