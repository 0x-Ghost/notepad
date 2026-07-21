package com.notepad.app.util

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

object OcrHelper {

    suspend fun extractTextFromPath(context: Context, imagePath: String): String? {
        val resolved = com.notepad.app.security.SecureMediaAccess.resolve(context, imagePath) ?: imagePath
        val file = File(resolved)
        if (!file.exists()) return null
        val image = InputImage.fromFilePath(context, Uri.fromFile(file))
        return recognizeText(image)
    }

    suspend fun extractTextFromUri(context: Context, uri: Uri): String? {
        val image = InputImage.fromFilePath(context, uri)
        return recognizeText(image)
    }

    private suspend fun recognizeText(image: InputImage): String? =
        suspendCancellableCoroutine { continuation ->
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    val text = result.text.trim().takeIf { it.isNotEmpty() }
                    recognizer.close()
                    continuation.resume(text)
                }
                .addOnFailureListener {
                    recognizer.close()
                    continuation.resume(null)
                }
        }
}
