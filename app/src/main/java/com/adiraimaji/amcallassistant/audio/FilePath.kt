package com.adiraimaji.amcallassistant.audio

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.io.File

/**
 * The filesystem path behind a file picked through Android's document picker.
 *
 * A picker hands back a content URI, which is fine for opening but unhelpful
 * to show and impossible to type into a Tasker %variable. Where Android gives
 * a real path - a file URI, an external-storage document, or a MediaStore id -
 * it is turned into one here, so both the app and the plugin show and store
 * something like /storage/emulated/0/Music/greeting.mp3.
 */
object FilePath {

    /** A real path, or null when this provider only has a document URI. */
    fun fromUri(context: Context, uri: Uri): String? {
        if (uri.scheme == ContentResolver.SCHEME_FILE) return uri.path
        val id = when {
            DocumentsContract.isDocumentUri(context, uri) -> DocumentsContract.getDocumentId(uri)
            uri.scheme == ContentResolver.SCHEME_CONTENT -> uri.lastPathSegment
            else -> return uri.toString()
        } ?: return null

        return runCatching {
            when {
                id.startsWith("raw:") -> id.removePrefix("raw:")
                id.startsWith("primary:") -> {
                    File(Environment.getExternalStorageDirectory(), id.removePrefix("primary:")).absolutePath
                }
                id.contains(':') -> mediaPath(context, id)
                else -> displayPath(context, uri)
            }
        }.getOrNull()
    }

    /** A MediaStore document, e.g. "audio:123", by its id and type. */
    private fun mediaPath(context: Context, id: String): String? {
        val parts = id.split(':', limit = 2)
        val root = when (parts[0]) {
            "audio" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            "video" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            "image" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            else -> MediaStore.Files.getContentUri("external")
        }
        val projection = arrayOf(
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH,
        )
        context.contentResolver.query(
            root,
            projection,
            "${MediaStore.MediaColumns._ID}=?",
            arrayOf(parts[1]),
            null,
        )?.use { rows ->
            if (rows.moveToFirst()) {
                val data = rows.getColumnIndex(MediaStore.MediaColumns.DATA)
                if (data >= 0) rows.getString(data)?.takeIf { it.isNotBlank() }?.let { return it }
                val name = rows.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val relative = rows.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                val folder = if (relative >= 0) rows.getString(relative) else null
                if (name >= 0) rows.getString(name)?.let { return storagePath(folder, it) }
            }
        }
        return null
    }

    /** Last resort for a provider we do not know: its name, in Downloads. */
    private fun displayPath(context: Context, uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { rows ->
            if (rows.moveToFirst()) {
                val name = rows.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (name >= 0) rows.getString(name)?.let { return musicPath(it) }
            }
        }
        return null
    }

    /** RELATIVE_PATH is where MediaStore keeps the file, e.g. "Music/". */
    private fun storagePath(folder: String?, name: String): String {
        val relative = folder?.takeIf { it.isNotBlank() } ?: "${Environment.DIRECTORY_MUSIC}/"
        return File(Environment.getExternalStorageDirectory(), relative.trimEnd('/') + "/" + name).absolutePath
    }

    private fun musicPath(name: String): String =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), name).absolutePath
}
