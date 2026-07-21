package com.notepad.app.util

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID

object ImageStorageHelper {

    fun copyToInternalStorage(context: Context, uri: Uri): String? {
        return try {
            val dir = File(context.filesDir, "note_images").apply { mkdirs() }
            val file = File(dir, "${UUID.randomUUID()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            val path = file.absolutePath
            com.notepad.app.security.SecureMediaAccess.protectNewFile(context, path)
            path
        } catch (_: Exception) {
            null
        }
    }

    fun deleteImage(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).delete() }
    }

    fun deleteImages(paths: List<String>) {
        paths.forEach { deleteImage(it) }
    }

    fun duplicateFile(path: String): String? {
        return try {
            val source = File(path)
            if (!source.exists()) return null
            val extension = source.extension.takeIf { it.isNotBlank() }?.let { ".$it" } ?: ""
            val dest = File(source.parentFile, "${UUID.randomUUID()}$extension")
            source.copyTo(dest, overwrite = false)
            dest.absolutePath
        } catch (_: Exception) {
            null
        }
    }
}
