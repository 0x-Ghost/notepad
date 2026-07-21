package com.notepad.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.notepad.app.data.model.Label
import com.notepad.app.data.model.NoteLabelCrossRef
import kotlinx.coroutines.flow.Flow

@Dao
interface LabelDao {

    @Query("SELECT * FROM labels ORDER BY name ASC")
    fun getAllLabels(): Flow<List<Label>>

    @Query("SELECT * FROM labels ORDER BY name ASC")
    suspend fun getAllLabelsOnce(): List<Label>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLabel(label: Label): Long

    @Update
    suspend fun updateLabel(label: Label)

    @Delete
    suspend fun deleteLabel(label: Label)

    @Query("SELECT l.* FROM labels l INNER JOIN note_label_cross_ref r ON l.id = r.labelId WHERE r.noteId = :noteId")
    suspend fun getLabelsForNote(noteId: Long): List<Label>

    @Query("SELECT l.* FROM labels l INNER JOIN note_label_cross_ref r ON l.id = r.labelId WHERE r.noteId = :noteId")
    fun getLabelsForNoteFlow(noteId: Long): Flow<List<Label>>

    @Query("SELECT noteId FROM note_label_cross_ref WHERE labelId = :labelId")
    fun getNoteIdsForLabel(labelId: Long): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCrossRef(crossRef: NoteLabelCrossRef)

    @Query("DELETE FROM note_label_cross_ref WHERE noteId = :noteId")
    suspend fun deleteCrossRefsForNote(noteId: Long)

    @Query("DELETE FROM note_label_cross_ref WHERE noteId = :noteId AND labelId = :labelId")
    suspend fun deleteCrossRef(noteId: Long, labelId: Long)
}
