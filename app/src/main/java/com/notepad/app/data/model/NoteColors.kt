package com.notepad.app.data.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * Palette di colori pastello in stile Google Keep / Material 3.
 * I valori sono salvati nel database come ARGB Int.
 */
object NoteColors {

    /** Colore predefinito: bianco / superficie neutra. */
    val DEFAULT: Int = Color(0xFFFFFFFF).toArgb()

    val palette: List<Int> = listOf(
        Color(0xFFFFFFFF).toArgb(), // Bianco
        Color(0xFFF28B82).toArgb(), // Rosso pastello
        Color(0xFFFBBC04).toArgb(), // Giallo
        Color(0xFFFFF475).toArgb(), // Giallo chiaro
        Color(0xFFCCFF90).toArgb(), // Verde lime
        Color(0xFFA7FFEB).toArgb(), // Teal
        Color(0xFFCBF0F8).toArgb(), // Azzurro
        Color(0xFFAECBFA).toArgb(), // Blu
        Color(0xFFD7AEFB).toArgb(), // Viola
        Color(0xFFFDCFE8).toArgb(), // Rosa
        Color(0xFFE6C9A8).toArgb(), // Marrone chiaro
        Color(0xFFE8EAED).toArgb()  // Grigio
    )

    fun toComposeColor(argb: Int): Color = Color(argb)
}
