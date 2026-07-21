package com.notepad.app.data.preferences

/** Modalità tema disponibili nelle impostazioni. */
enum class ThemeMode(val key: String, val label: String) {
    LIGHT("light", "Chiaro"),
    DARK("dark", "Scuro"),
    SYSTEM("system", "Predefinito di sistema");

    companion object {
        fun fromKey(key: String): ThemeMode =
            entries.find { it.key == key } ?: SYSTEM
    }
}
