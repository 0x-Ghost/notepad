package com.notepad.app.data.database

import androidx.room.TypeConverter
import com.notepad.app.data.model.NoteType

class Converters {

    private companion object {
        const val LIST_SEPARATOR = "\u001F"
    }

    @TypeConverter
    fun fromNoteType(value: NoteType): String = value.name

    @TypeConverter
    fun toNoteType(value: String): NoteType = NoteType.valueOf(value)

    @TypeConverter
    fun fromStringList(value: List<String>): String = value.joinToString(LIST_SEPARATOR)

    @TypeConverter
    fun toStringList(value: String): List<String> =
        if (value.isBlank()) emptyList() else value.split(LIST_SEPARATOR)
}
