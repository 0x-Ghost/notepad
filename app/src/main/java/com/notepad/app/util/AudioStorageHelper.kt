package com.notepad.app.util

import android.content.Context
import java.io.File
import java.util.UUID

object AudioStorageHelper {

    fun createAudioFile(context: Context): File {
        val dir = File(context.filesDir, "note_audio").apply { mkdirs() }
        return File(dir, "${UUID.randomUUID()}.m4a")
    }

    fun deleteAudio(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).delete() }
    }

    fun duplicateFile(path: String): String? = ImageStorageHelper.duplicateFile(path)
}
