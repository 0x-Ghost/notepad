package com.notepad.app.util

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class AudioRecorderHelper(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null

    val isRecording: Boolean get() = mediaRecorder != null

    fun startRecording(): String? {
        return try {
            val file = AudioStorageHelper.createAudioFile(context)
            outputFile = file
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            mediaRecorder = recorder
            file.absolutePath
        } catch (_: Exception) {
            stopRecording()
            null
        }
    }

    fun stopRecording(): String? {
        return try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            val path = outputFile?.absolutePath
            com.notepad.app.security.SecureMediaAccess.protectNewFile(context, path)
            path
        } catch (_: Exception) {
            mediaRecorder?.release()
            mediaRecorder = null
            outputFile?.delete()
            outputFile = null
            null
        }
    }

    fun cancelRecording() {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (_: Exception) {
            mediaRecorder?.release()
        }
        mediaRecorder = null
        outputFile?.delete()
        outputFile = null
    }
}
