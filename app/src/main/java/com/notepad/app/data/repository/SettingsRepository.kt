package com.notepad.app.data.repository

import com.notepad.app.data.preferences.NoteFilter
import com.notepad.app.data.preferences.NoteSortOrder
import com.notepad.app.data.preferences.SettingsDataStore
import com.notepad.app.data.preferences.ThemeMode
import kotlinx.coroutines.flow.Flow

class SettingsRepository(private val dataStore: SettingsDataStore) {

    val themeMode: Flow<ThemeMode> = dataStore.themeMode
    val noteFilter: Flow<NoteFilter> = dataStore.noteFilter
    val noteSortOrder: Flow<NoteSortOrder> = dataStore.noteSortOrder
    val selectedLabelId: Flow<Long?> = dataStore.selectedLabelId

    suspend fun setThemeMode(mode: ThemeMode) = dataStore.setThemeMode(mode)
    suspend fun setNoteFilter(filter: NoteFilter) = dataStore.setNoteFilter(filter)
    suspend fun setNoteSortOrder(order: NoteSortOrder) = dataStore.setNoteSortOrder(order)
    suspend fun setSelectedLabelId(labelId: Long?) = dataStore.setSelectedLabelId(labelId)
}
