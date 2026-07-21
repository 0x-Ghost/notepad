package com.notepad.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.notepad.app.data.model.Note
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object per le operazioni CRUD sulle note.
 * Le note fissate (isPinned) vengono mostrate per prime.
 */
@Dao
interface NoteDao {

    @Query("SELECT * FROM notes WHERE isArchived = 0 AND isTrashed = 0 ORDER BY isPinned DESC, updatedAt DESC")
    fun getAllNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE isArchived = 1 AND isTrashed = 0 ORDER BY updatedAt DESC")
    fun getArchivedNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE isTrashed = 1 ORDER BY trashedAt DESC")
    fun getTrashedNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE isTrashed = 1 AND trashedAt IS NOT NULL AND trashedAt < :before")
    suspend fun getTrashedNotesBeforeOnce(before: Long): List<Note>

    @Query("SELECT * FROM notes WHERE id = :noteId")
    fun getNoteById(noteId: Long): Flow<Note?>

    @Query("SELECT * FROM notes WHERE id = :noteId")
    suspend fun getNoteByIdOnce(noteId: Long): Note?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: Note): Long

    @Update
    suspend fun updateNote(note: Note)

    @Delete
    suspend fun deleteNote(note: Note)

    @Query("DELETE FROM notes WHERE id = :noteId")
    suspend fun deleteNoteById(noteId: Long)

    @Query("UPDATE notes SET isArchived = 1, updatedAt = :updatedAt WHERE id = :noteId")
    suspend fun archiveNote(noteId: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE notes SET isArchived = 0, updatedAt = :updatedAt WHERE id = :noteId")
    suspend fun unarchiveNote(noteId: Long, updatedAt: Long = System.currentTimeMillis())

    @Query(
        "UPDATE notes SET isTrashed = 1, trashedAt = :trashedAt, isPinned = 0, " +
            "reminderAt = NULL, updatedAt = :trashedAt WHERE id = :noteId"
    )
    suspend fun trashNote(noteId: Long, trashedAt: Long = System.currentTimeMillis())

    @Query("UPDATE notes SET isTrashed = 0, trashedAt = NULL, updatedAt = :updatedAt WHERE id = :noteId")
    suspend fun restoreFromTrash(noteId: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE notes SET reminderAt = :reminderAt, updatedAt = :updatedAt WHERE id = :noteId")
    suspend fun updateReminder(noteId: Long, reminderAt: Long?, updatedAt: Long = System.currentTimeMillis())

    @Query(
        "SELECT * FROM notes WHERE isArchived = 0 AND isTrashed = 0 AND isPinned = 1 " +
            "ORDER BY updatedAt DESC LIMIT :limit"
    )
    suspend fun getPinnedNotesForWidget(limit: Int): List<Note>

    @Query(
        "SELECT * FROM notes WHERE reminderAt IS NOT NULL AND reminderAt > :now " +
            "AND isArchived = 0 AND isTrashed = 0"
    )
    suspend fun getNotesWithFutureReminders(now: Long = System.currentTimeMillis()): List<Note>
}
