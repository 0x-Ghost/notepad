package com.notepad.app.data.model

import androidx.compose.ui.graphics.Color

object LabelColors {
    const val DEFAULT = 0xFF1E88E5.toInt()

    val palette = listOf(
        0xFF1E88E5.toInt(),
        0xFF43A047.toInt(),
        0xFFE53935.toInt(),
        0xFF8E24AA.toInt(),
        0xFFFB8C00.toInt(),
        0xFF00ACC1.toInt(),
        0xFF6D4C41.toInt(),
        0xFF546E7A.toInt()
    )

    fun toComposeColor(argb: Int): Color = Color(argb)
}
