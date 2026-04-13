package com.statussaver.vault.repository

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.statussaver.vault.model.StatusItem
import com.statussaver.vault.utils.MediaUtils
import java.io.File
import java.io.FileOutputStream

class StatusRepository(private val context: Context) {

    companion object {
        private const val PREF_NAME = "status_vault_prefs"
        private const val KEY_URI   = "whatsapp_tree_uri"
        private const val SAVE_PICTURES_DIR = "StatusVault"
        private const val SAVE_MOVIES_DIR   = "StatusVault"
    }

    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    // ─── URI Persistence ────────────────────────────────────────────────────────

    fun getPersistedUri(): Uri? {
        val s = prefs.getString(KEY_URI, null) ?: return null
        return Uri.parse(s)
    }

    fun persistUri(uri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        prefs.edit().putString(KEY_URI, uri.toString()).apply()
    }

    fun clearPersistedUri() {
        prefs.edit().remove(KEY_URI).apply()
    }

    // ─── Load WhatsApp Statuses (via SAF) ────────────────────────────────────────

    fun loadStatuses(treeUri: Uri): List<StatusItem> {
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: return emptyList()

        if (!root.canRead()) return emptyList()

        val savedNames = getSavedFileNames()
        val items = mutableListOf<StatusItem>()

        root.listFiles().forEach { file ->
            if (!file.isFile) return@forEach
            val name = file.name ?: return@forEach

            val isImage = name.matches(Regex("(?i).*\\.(jpg|jpeg|png|webp|gif)$"))
            val isVideo = name.matches(Regex("(?i).*\\.(mp4|3gp|mkv|avi|mov)$"))

            if (!isImage && !isVideo) return@forEach

            val duration = if (isVideo) {
                MediaUtils.getVideoDuration(context, file.uri)
            } else ""

            items.add(
                StatusItem(
                    uri          = file.uri,
                    name         = name,
                    isVideo      = isVideo,
                    size         = file.length(),
                    dateModified = file.lastModified(),
                    isSaved      = savedNames.contains(name),
                    duration     = duration
                )
            )
        }

        return items.sortedByDescending { it.dateModified }
    }

    // ─── Save Status to Device ───────────────────────────────────────────────────

    fun saveStatus(sourceUri: Uri, fileName: String, isVideo: Boolean): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveViaMediaStore(sourceUri, fileName, isVideo)
            } else {
                saveViaFileSystem(sourceUri, fileName, isVideo)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun saveViaMediaStore(sourceUri: Uri, fileName: String, isVideo: Boolean): Boolean {
        val collection = if (isVideo)
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        else
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

        val mimeType = resolveMimeType(fileName, isVideo)
        val relativePath = if (isVideo) "Movies/$SAVE_MOVIES_DIR" else "Pictures/$SAVE_PICTURES_DIR"

        val cv = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
        }

        val destUri = context.contentResolver.insert(collection, cv) ?: return false
        context.contentResolver.openOutputStream(destUri)?.use { out ->
            context.contentResolver.openInputStream(sourceUri)?.use { inp ->
                inp.copyTo(out)
            }
        }
        return true
    }

    private fun saveViaFileSystem(sourceUri: Uri, fileName: String, isVideo: Boolean): Boolean {
        val parent = if (isVideo)
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), SAVE_MOVIES_DIR)
        else
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), SAVE_PICTURES_DIR)

        if (!parent.exists() && !parent.mkdirs()) return false
        val dest = File(parent, fileName)

        context.contentResolver.openInputStream(sourceUri)?.use { inp ->
            FileOutputStream(dest).use { out -> inp.copyTo(out) }
        }

        MediaScannerConnection.scanFile(context, arrayOf(dest.absolutePath), null, null)
        return true
    }

    private fun resolveMimeType(fileName: String, isVideo: Boolean): String {
        if (isVideo) {
            return when {
                fileName.endsWith(".3gp",  true) -> "video/3gpp"
                fileName.endsWith(".mkv",  true) -> "video/x-matroska"
                else -> "video/mp4"
            }
        }
        return when {
            fileName.endsWith(".png",  true) -> "image/png"
            fileName.endsWith(".webp", true) -> "image/webp"
            fileName.endsWith(".gif",  true) -> "image/gif"
            else -> "image/jpeg"
        }
    }

    // ─── Saved Statuses ──────────────────────────────────────────────────────────

    fun getSavedStatuses(): List<StatusItem> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getSavedViaMediaStore()
        } else {
            getSavedViaFileSystem()
        }
    }

    private fun getSavedViaMediaStore(): List<StatusItem> {
        val items = mutableListOf<StatusItem>()
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED
        )
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"

        // Images
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection, selection,
            arrayOf("Pictures/$SAVE_PICTURES_DIR/"),
            "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
        )?.use { c ->
            while (c.moveToNext()) {
                val id   = c.getLong(c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                val name = c.getString(c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME))
                val size = c.getLong(c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE))
                val date = c.getLong(c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED))
                val uri  = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                items.add(StatusItem(uri, name, false, size, date, isSaved = true))
            }
        }

        // Videos
        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection, selection,
            arrayOf("Movies/$SAVE_MOVIES_DIR/"),
            "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
        )?.use { c ->
            while (c.moveToNext()) {
                val id   = c.getLong(c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                val name = c.getString(c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME))
                val size = c.getLong(c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE))
                val date = c.getLong(c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED))
                val uri  = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                val dur  = MediaUtils.getVideoDuration(context, uri)
                items.add(StatusItem(uri, name, true, size, date, isSaved = true, duration = dur))
            }
        }

        return items.sortedByDescending { it.dateModified }
    }

    private fun getSavedViaFileSystem(): List<StatusItem> {
        val items = mutableListOf<StatusItem>()
        val picDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), SAVE_PICTURES_DIR)
        val vidDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), SAVE_MOVIES_DIR)

        picDir.listFiles()?.forEach { f ->
            items.add(StatusItem(Uri.fromFile(f), f.name, false, f.length(), f.lastModified(), isSaved = true))
        }
        vidDir.listFiles()?.forEach { f ->
            val dur = MediaUtils.getVideoDuration(context, Uri.fromFile(f))
            items.add(StatusItem(Uri.fromFile(f), f.name, true, f.length(), f.lastModified(), isSaved = true, duration = dur))
        }

        return items.sortedByDescending { it.dateModified }
    }

    private fun getSavedFileNames(): Set<String> {
        val names = mutableSetOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val proj = arrayOf(MediaStore.MediaColumns.DISPLAY_NAME)
            val sel  = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"

            listOf(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI to "Pictures/$SAVE_PICTURES_DIR/",
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI  to "Movies/$SAVE_MOVIES_DIR/"
            ).forEach { (collection, path) ->
                context.contentResolver.query(collection, proj, sel, arrayOf(path), null)?.use { c ->
                    while (c.moveToNext()) names.add(c.getString(0))
                }
            }
        } else {
            val picDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), SAVE_PICTURES_DIR)
            val vidDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),  SAVE_MOVIES_DIR)
            picDir.listFiles()?.forEach { names.add(it.name) }
            vidDir.listFiles()?.forEach { names.add(it.name) }
        }

        return names
    }
}