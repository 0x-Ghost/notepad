package com.notepad.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entità Room che rappresenta una singola nota nel database locale.
 *
 * @property id Identificatore univoco generato automaticamente.
 * @property title Titolo della nota (può essere vuoto).
 * @property content Corpo testuale della nota.
 * @property color Colore di sfondo ARGB (pastello Material 3).
 * @property isPinned Se true, la nota viene mostrata in cima alla griglia.
 * @property updatedAt Timestamp dell'ultima modifica in millisecondi.
 */
@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String = "",
    val content: String = "",
    val color: Int = NoteColors.DEFAULT,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val isTrashed: Boolean = false,
    val trashedAt: Long? = null,
    val noteType: NoteType = NoteType.TEXT,
    val reminderAt: Long? = null,
    val imageUris: List<String> = emptyList(),
    val audioUri: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
