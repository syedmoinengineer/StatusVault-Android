package com.statussaver.vault.utils

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri

object MediaUtils {

    fun getVideoDuration(context: Context, uri: Uri): String {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val ms = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            retriever.release()
            formatMs(ms)
        } catch (e: Exception) {
            "0:00"
        }
    }

    private fun formatMs(ms: Long): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return "%d:%02d".format(min, sec)
    }
}