package com.notepad.app.data.repository

import com.notepad.app.data.dao.ChecklistItemDao
import com.notepad.app.data.dao.LabelDao
import com.notepad.app.data.dao.NoteDao
import com.notepad.app.data.model.ChecklistItem
import com.notepad.app.data.model.Label
import com.notepad.app.data.model.Note
import com.notepad.app.data.model.NoteLabelCrossRef
import com.notepad.app.data.model.NoteType
import com.notepad.app.util.AudioStorageHelper
import com.notepad.app.util.ImageStorageHelper
import kotlinx.coroutines.flow.Flow

class NoteRepository(
    private val noteDao: NoteDao,
    private val checklistItemDao: ChecklistItemDao,
    private val labelDao: LabelDao
) {

    val allNotes: Flow<List<Note>> = noteDao.getAllNotes()
    val archivedNotes: Flow<List<Note>> = noteDao.getArchivedNotes()
    val trashedNotes: Flow<List<Note>> = noteDao.getTrashedNotes()
    val allLabels: Flow<List<Label>> = labelDao.getAllLabels()

    fun getNoteById(noteId: Long): Flow<Note?> = noteDao.getNoteById(noteId)

    suspend fun getNoteByIdOnce(noteId: Long): Note? = noteDao.getNoteByIdOnce(noteId)

    fun getChecklistItems(noteId: Long): Flow<List<ChecklistItem>> =
        checklistItemDao.getItemsForNote(noteId)

    suspend fun getChecklistItemsOnce(noteId: Long): List<ChecklistItem> =
        checklistItemDao.getItemsForNoteOnce(noteId)

    suspend fun insertNote(note: Note): Long = noteDao.insertNote(note)
    suspend fun updateNote(note: Note) = noteDao.updateNote(note)
    suspend fun deleteNote(note: Note) = noteDao.deleteNote(note)
    suspend fun deleteNoteById(noteId: Long) = noteDao.deleteNoteById(noteId)
    suspend fun archiveNote(noteId: Long) = noteDao.archiveNote(noteId)
    suspend fun unarchiveNote(noteId: Long) = noteDao.unarchiveNote(noteId)
    suspend fun trashNote(noteId: Long) = noteDao.trashNote(noteId)
    suspend fun restoreFromTrash(noteId: Long) = noteDao.restoreFromTrash(noteId)
    suspend fun updateReminder(noteId: Long, reminderAt: Long?) = noteDao.updateReminder(noteId, reminderAt)

    suspend fun getPinnedNotesForWidget(limit: Int = 5): List<Note> =
        noteDao.getPinnedNotesForWidget(limit)

    suspend fun getNotesWithFutureReminders(): List<Note> =
        noteDao.getNotesWithFutureReminders()

    suspend fun purgeTrashedNotesBefore(before: Long) {
        val oldNotes = noteDao.getTrashedNotesBeforeOnce(before)
        oldNotes.forEach { permanentlyDeleteNote(it) }
    }

    suspend fun permanentlyDeleteNote(note: Note) {
        ImageStorageHelper.deleteImages(note.imageUris)
        AudioStorageHelper.deleteAudio(note.audioUri)
        checklistItemDao.deleteItemsForNote(note.id)
        labelDao.deleteCrossRefsForNote(note.id)
        noteDao.deleteNote(note)
    }

    suspend fun duplicateNote(noteId: Long): Long {
        val note = noteDao.getNoteByIdOnce(noteId) ?: return -1L
        val checklistItems = checklistItemDao.getItemsForNoteOnce(noteId)
        val labelIds = labelDao.getLabelsForNote(noteId).map { it.id }

        val newImageUris = note.imageUris.mapNotNull { ImageStorageHelper.duplicateFile(it) }
        val newAudioUri = note.audioUri?.let { AudioStorageHelper.duplicateFile(it) }

        val copyTitle = when {
            note.title.isBlank() -> ""
            else -> "${note.title} (copia)"
        }

        val copy = note.copy(
            id = 0,
            title = copyTitle,
            isPinned = false,
            isArchived = false,
            isTrashed = false,
            trashedAt = null,
            reminderAt = null,
            imageUris = newImageUris,
            audioUri = newAudioUri,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        val newId = noteDao.insertNote(copy)

        if (note.noteType == NoteType.CHECKLIST && checklistItems.isNotEmpty()) {
            checklistItemDao.insertItems(
                checklistItems.map { it.copy(id = 0, noteId = newId) }
            )
        }
        setLabelsForNote(newId, labelIds)
        return newId
    }

    suspend fun saveChecklistItems(noteId: Long, items: List<ChecklistItem>) {
        checklistItemDao.deleteItemsForNote(noteId)
        if (items.isNotEmpty()) {
            checklistItemDao.insertItems(items.map { it.copy(noteId = noteId) })
        }
    }

    suspend fun restoreNote(note: Note, checklistItems: List<ChecklistItem>): Long {
        val newId = noteDao.insertNote(note.copy(id = 0))
        if (checklistItems.isNotEmpty()) {
            checklistItemDao.insertItems(
                checklistItems.map { it.copy(id = 0, noteId = newId) }
            )
        }
        return newId
    }

    suspend fun getAllLabelsOnce(): List<Label> = labelDao.getAllLabelsOnce()
    suspend fun insertLabel(label: Label): Long = labelDao.insertLabel(label)
    suspend fun updateLabel(label: Label) = labelDao.updateLabel(label)
    suspend fun deleteLabel(label: Label) = labelDao.deleteLabel(label)

    suspend fun getLabelsForNote(noteId: Long): List<Label> =
        labelDao.getLabelsForNote(noteId)

    fun getLabelsForNoteFlow(noteId: Long): Flow<List<Label>> =
        labelDao.getLabelsForNoteFlow(noteId)

    fun getNoteIdsForLabel(labelId: Long): Flow<List<Long>> =
        labelDao.getNoteIdsForLabel(labelId)

    suspend fun setLabelsForNote(noteId: Long, labelIds: List<Long>) {
        labelDao.deleteCrossRefsForNote(noteId)
        labelIds.forEach { labelId ->
            labelDao.insertCrossRef(NoteLabelCrossRef(noteId, labelId))
        }
    }
}
