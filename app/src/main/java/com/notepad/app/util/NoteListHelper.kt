package com.notepad.app.util

import com.notepad.app.data.model.ChecklistItem
import com.notepad.app.data.model.Note
import com.notepad.app.data.model.NoteColors
import com.notepad.app.data.preferences.NoteFilter
import com.notepad.app.data.preferences.NoteSortOrder

object NoteListHelper {

    fun applyFiltersAndSort(
        notes: List<Note>,
        query: String,
        filter: NoteFilter,
        sortOrder: NoteSortOrder,
        labelFilterId: Long?,
        noteIdsForLabel: Set<Long>,
        checklistPreviews: Map<Long, List<ChecklistItem>>
    ): List<Note> {
        var result = notes

        result = when (filter) {
            NoteFilter.ALL -> result
            NoteFilter.PINNED -> result.filter { it.isPinned }
            NoteFilter.COLORED -> result.filter { it.color != NoteColors.DEFAULT }
            NoteFilter.WITH_REMINDER -> result.filter { it.reminderAt != null }
            NoteFilter.WITH_IMAGE -> result.filter { it.imageUris.isNotEmpty() }
        }

        if (labelFilterId != null) {
            result = result.filter { it.id in noteIdsForLabel }
        }

        if (query.isNotBlank()) {
            result = result.filter { note ->
                note.title.contains(query, ignoreCase = true) ||
                    note.content.contains(query, ignoreCase = true) ||
                    checklistPreviews[note.id]?.any {
                        it.text.contains(query, ignoreCase = true)
                    } == true
            }
        }

        val secondary = when (sortOrder) {
            NoteSortOrder.UPDATED_DESC -> compareByDescending<Note> { it.updatedAt }
            NoteSortOrder.UPDATED_ASC -> compareBy<Note> { it.updatedAt }
            NoteSortOrder.CREATED_DESC -> compareByDescending<Note> { it.createdAt }
            NoteSortOrder.CREATED_ASC -> compareBy<Note> { it.createdAt }
            NoteSortOrder.TITLE_AZ -> compareBy<Note> { it.title.lowercase() }
        }
        return result.sortedWith(compareByDescending<Note> { it.isPinned }.then(secondary))
    }
}
