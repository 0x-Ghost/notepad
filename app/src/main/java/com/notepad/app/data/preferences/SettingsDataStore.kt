package com.notepad.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "notepad_settings"
)

class SettingsDataStore(private val context: Context) {

    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val noteFilterKey = stringPreferencesKey("note_filter")
    private val noteSortOrderKey = stringPreferencesKey("note_sort_order")
    private val selectedLabelIdKey = longPreferencesKey("selected_label_id")

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        ThemeMode.fromKey(prefs[themeModeKey] ?: ThemeMode.SYSTEM.key)
    }

    val noteFilter: Flow<NoteFilter> = context.dataStore.data.map { prefs ->
        NoteFilter.fromKey(prefs[noteFilterKey] ?: NoteFilter.ALL.key)
    }

    val noteSortOrder: Flow<NoteSortOrder> = context.dataStore.data.map { prefs ->
        NoteSortOrder.fromKey(prefs[noteSortOrderKey] ?: NoteSortOrder.UPDATED_DESC.key)
    }

    val selectedLabelId: Flow<Long?> = context.dataStore.data.map { prefs ->
        prefs[selectedLabelIdKey]?.takeIf { it >= 0 }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs -> prefs[themeModeKey] = mode.key }
    }

    suspend fun setNoteFilter(filter: NoteFilter) {
        context.dataStore.edit { prefs -> prefs[noteFilterKey] = filter.key }
    }

    suspend fun setNoteSortOrder(order: NoteSortOrder) {
        context.dataStore.edit { prefs -> prefs[noteSortOrderKey] = order.key }
    }

    suspend fun setSelectedLabelId(labelId: Long?) {
        context.dataStore.edit { prefs ->
            if (labelId != null) {
                prefs[selectedLabelIdKey] = labelId
            } else {
                prefs.remove(selectedLabelIdKey)
            }
        }
    }
}
