package com.notepad.app.data.preferences

enum class NoteSortOrder(val key: String, val label: String) {
    UPDATED_DESC("updated_desc", "Data modifica ↓"),
    UPDATED_ASC("updated_asc", "Data modifica ↑"),
    CREATED_DESC("created_desc", "Data creazione ↓"),
    CREATED_ASC("created_asc", "Data creazione ↑"),
    TITLE_AZ("title_az", "Titolo A-Z");

    companion object {
        fun fromKey(key: String): NoteSortOrder =
            entries.find { it.key == key } ?: UPDATED_DESC
    }
}
