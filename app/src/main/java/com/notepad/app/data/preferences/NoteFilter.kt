package com.notepad.app.data.preferences

enum class NoteFilter(val key: String, val label: String) {
    ALL("all", "Tutte"),
    PINNED("pinned", "Fissate"),
    COLORED("colored", "Con colore"),
    WITH_REMINDER("reminder", "Con promemoria"),
    WITH_IMAGE("image", "Con immagine");

    companion object {
        fun fromKey(key: String): NoteFilter =
            entries.find { it.key == key } ?: ALL
    }
}
