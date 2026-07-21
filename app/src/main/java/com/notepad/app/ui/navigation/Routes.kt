package com.notepad.app.ui.navigation

object Routes {
    const val NOTES_LIST = "notes_list"
    const val NOTE_DETAIL = "note_detail"
    const val NOTE_DETAIL_WITH_ID = "note_detail/{noteId}"
    const val SETTINGS = "settings"
    const val ARCHIVED_NOTES = "archived_notes"
    const val LABELS = "labels"
    const val TRASH = "trash"
    const val DRAWING = "drawing"

    fun noteDetail(noteId: Long) = "note_detail/$noteId"
}
