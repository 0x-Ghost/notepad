package com.notepad.app.security

import android.content.Context
import java.io.File

/**
 * Encrypts / decrypts note media under note_images/ and note_audio/ with AES-GCM (DEK).
 */
class MediaCryptoHelper(private val context: Context) {

    private val imagesDir: File
        get() = File(context.filesDir, "note_images").also { it.mkdirs() }

    private val audioDir: File
        get() = File(context.filesDir, "note_audio").also { it.mkdirs() }

    private val decryptCacheDir: File
        get() = File(context.cacheDir, "media_plain").also { it.mkdirs() }

    fun encryptAllMedia(dek: ByteArray) {
        listMediaFiles().forEach { file ->
            runCatching { CryptoManager.encryptFileInPlace(file, dek) }
        }
    }

    fun decryptAllMedia(dek: ByteArray) {
        listMediaFiles().forEach { file ->
            runCatching { CryptoManager.decryptFileInPlace(file, dek) }
        }
        clearDecryptCache()
    }

    /** Encrypt a newly written media file if a DEK is supplied. */
    fun encryptIfNeeded(path: String?, dek: ByteArray?) {
        if (path.isNullOrBlank() || dek == null) return
        val file = File(path)
        if (file.exists()) {
            CryptoManager.encryptFileInPlace(file, dek)
        }
    }

    /**
     * Returns a filesystem path safe for Coil / MediaPlayer.
     * Encrypted files are decrypted into app cache while the session is unlocked.
     */
    fun pathForRead(path: String?, dek: ByteArray?): String? {
        if (path.isNullOrBlank()) return path
        val file = File(path)
        if (!file.exists()) return path
        if (!CryptoManager.isEncryptedMedia(file)) return path
        if (dek == null) return null
        val cached = File(decryptCacheDir, file.name)
        if (!cached.exists() || cached.lastModified() < file.lastModified()) {
            CryptoManager.decryptToFile(file, cached, dek)
        }
        return cached.absolutePath
    }

    fun clearDecryptCache() {
        decryptCacheDir.listFiles()?.forEach { it.delete() }
    }

    private fun listMediaFiles(): List<File> {
        val files = mutableListOf<File>()
        imagesDir.listFiles()?.filter { it.isFile }?.let { files.addAll(it) }
        audioDir.listFiles()?.filter { it.isFile }?.let { files.addAll(it) }
        return files
    }
}
