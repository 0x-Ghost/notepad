package com.notepad.app.security

import android.content.Context
import com.notepad.app.NotepadApplication

/**
 * Resolves encrypted media paths for UI playback and encrypts newly written media when lock is on.
 */
object SecureMediaAccess {

    fun resolve(context: Context, path: String?): String? {
        if (path.isNullOrBlank()) return path
        val app = context.applicationContext as? NotepadApplication ?: return path
        return app.lockRepository.pathForMediaRead(path) ?: path
    }

    fun protectNewFile(context: Context, path: String?) {
        if (path.isNullOrBlank()) return
        val app = context.applicationContext as? NotepadApplication ?: return
        app.lockRepository.encryptNewMediaIfNeeded(path)
    }
}
