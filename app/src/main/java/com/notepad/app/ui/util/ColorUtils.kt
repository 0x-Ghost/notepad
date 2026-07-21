package com.notepad.app.ui.util

import androidx.compose.ui.graphics.Color

fun contentColorFor(background: Color): Color {
    val luminance = 0.299f * background.red + 0.587f * background.green + 0.114f * background.blue
    return if (luminance > 0.6f) Color(0xFF1A1C1E) else Color(0xFFE3E2E6)
}
