package com.notepad.app.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.notepad.app.data.model.ChecklistItem
import com.notepad.app.data.model.Label
import com.notepad.app.data.model.Note
import com.notepad.app.data.model.NoteColors
import com.notepad.app.data.model.NoteType
import com.notepad.app.data.preferences.NoteFilter
import com.notepad.app.data.preferences.NoteSortOrder
import com.notepad.app.data.repository.NoteRepository
import com.notepad.app.data.repository.SettingsRepository
import com.notepad.app.reminder.ReminderScheduler
import com.notepad.app.util.NoteListHelper
import com.notepad.app.widget.WidgetUpdateHelper
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

enum class SaveStatus { IDLE, SAVING, SAVED }

data class ChecklistItemUi(
    val key: String = UUID.randomUUID().toString(),
    val id: Long = 0,
    val text: String = "",
    val isChecked: Boolean = false,
    val position: Int = 0
)

data class NoteDetailUiState(
    val id: Long = 0,
    val title: String = "",
    val content: String = "",
    val color: Int = NoteColors.DEFAULT,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val isNewNote: Boolean = true,
    val noteType: NoteType = NoteType.TEXT,
    val checklistItems: List<ChecklistItemUi> = emptyList(),
    val reminderAt: Long? = null,
    val imageUris: List<String> = emptyList(),
    val audioUri: String? = null,
    val selectedLabelIds: Set<Long> = emptySet(),
    val saveStatus: SaveStatus = SaveStatus.IDLE,
    val isLoading: Boolean = false
)

data class UndoSnackbarState(
    val message: String = "",
    val onUndo: () -> Unit = {}
)

private data class PendingTrash(
    val note: Note
)

private data class PendingArchive(
    val note: Note
)

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class NoteViewModel(
    private val repository: NoteRepository,
    private val settingsRepository: SettingsRepository,
    private val reminderScheduler: ReminderScheduler,
    private val appContext: Context
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _checklistPreviews = MutableStateFlow<Map<Long, List<ChecklistItem>>>(emptyMap())
    private val _noteLabels = MutableStateFlow<Map<Long, List<Label>>>(emptyMap())

    val allLabels: StateFlow<List<Label>> = repository.allLabels.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val noteFilter: StateFlow<NoteFilter> = settingsRepository.noteFilter.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = NoteFilter.ALL
    )

    val noteSortOrder: StateFlow<NoteSortOrder> = settingsRepository.noteSortOrder.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = NoteSortOrder.UPDATED_DESC
    )

    val selectedLabelId: StateFlow<Long?> = settingsRepository.selectedLabelId.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null
    )

    private val noteIdsForSelectedLabel = selectedLabelId.flatMapLatest { labelId ->
        if (labelId == null) flowOf(emptyList())
        else repository.getNoteIdsForLabel(labelId)
    }

    val notes: StateFlow<List<Note>> = combine(
        repository.allNotes,
        _searchQuery,
        _checklistPreviews,
        noteFilter,
        noteSortOrder,
        selectedLabelId,
        noteIdsForSelectedLabel
    ) { values ->
        val allNotes = values[0] as List<Note>
        val query = values[1] as String
        val previews = values[2] as Map<Long, List<ChecklistItem>>
        val filter = values[3] as NoteFilter
        val sortOrder = values[4] as NoteSortOrder
        val labelId = values[5] as Long?
        val noteIds = values[6] as List<Long>

        NoteListHelper.applyFiltersAndSort(
            notes = allNotes,
            query = query,
            filter = filter,
            sortOrder = sortOrder,
            labelFilterId = labelId,
            noteIdsForLabel = noteIds.toSet(),
            checklistPreviews = previews
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val archivedNotes: StateFlow<List<Note>> = repository.archivedNotes.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val trashedNotes: StateFlow<List<Note>> = repository.trashedNotes.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    private val _detailUiState = MutableStateFlow(NoteDetailUiState())
    val detailUiState: StateFlow<NoteDetailUiState> = _detailUiState.asStateFlow()

    private val _saveTrigger = MutableStateFlow(0L)
    private var autoSaveJob: Job? = null
    private var pendingTrash: PendingTrash? = null
    private var pendingTrashJob: Job? = null
    private var pendingArchive: PendingArchive? = null
    private var pendingArchiveJob: Job? = null

    private val _undoSnackbarState = MutableStateFlow<UndoSnackbarState?>(null)
    val undoSnackbarState: StateFlow<UndoSnackbarState?> = _undoSnackbarState.asStateFlow()

    private val _sharedTextIntent = MutableStateFlow<String?>(null)
    val sharedTextIntent: StateFlow<String?> = _sharedTextIntent.asStateFlow()

    init {
        viewModelScope.launch {
            val thirtyDaysAgo = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
            repository.purgeTrashedNotesBefore(thirtyDaysAgo)
        }
        viewModelScope.launch {
            _saveTrigger.debounce(500).collect {
                if (it > 0L) autoSave()
            }
        }
        viewModelScope.launch {
            repository.allNotes.collect { allNotes ->
                allNotes.forEach { note ->
                    if (note.noteType == NoteType.CHECKLIST && !_checklistPreviews.value.containsKey(note.id)) {
                        loadChecklistPreview(note.id)
                    }
                    if (!_noteLabels.value.containsKey(note.id)) {
                        loadNoteLabels(note.id)
                    }
                }
            }
        }
    }

    fun getChecklistPreview(noteId: Long) = _checklistPreviews.value[noteId].orEmpty()
    fun getNoteLabels(noteId: Long) = _noteLabels.value[noteId].orEmpty()

    private fun loadChecklistPreview(noteId: Long) {
        viewModelScope.launch {
            val items = repository.getChecklistItemsOnce(noteId)
            _checklistPreviews.update { it + (noteId to items) }
        }
    }

    private fun loadNoteLabels(noteId: Long) {
        viewModelScope.launch {
            val labels = repository.getLabelsForNote(noteId)
            _noteLabels.update { it + (noteId to labels) }
        }
    }

    fun onSearchQueryChange(query: String) { _searchQuery.value = query }
    fun setNoteFilter(filter: NoteFilter) { viewModelScope.launch { settingsRepository.setNoteFilter(filter) } }
    fun setNoteSortOrder(order: NoteSortOrder) { viewModelScope.launch { settingsRepository.setNoteSortOrder(order) } }
    fun setSelectedLabelId(labelId: Long?) { viewModelScope.launch { settingsRepository.setSelectedLabelId(labelId) } }

    fun handleShareIntent(text: String) { _sharedTextIntent.value = text }
    fun clearSharedTextIntent() { _sharedTextIntent.value = null }

    fun prepareNoteFromSharedText(text: String) {
        _detailUiState.value = NoteDetailUiState(content = text.trim(), isNewNote = true)
    }

    fun loadNote(noteId: Long) {
        viewModelScope.launch {
            _detailUiState.update { it.copy(isLoading = true) }
            val note = repository.getNoteById(noteId).first() ?: return@launch
            val checklistItems = if (note.noteType == NoteType.CHECKLIST) {
                ensureTrailingEmptyRow(repository.getChecklistItemsOnce(noteId).map { it.toUi() })
            } else emptyList()
            val labels = repository.getLabelsForNote(noteId)
            _detailUiState.value = NoteDetailUiState(
                id = note.id,
                title = note.title,
                content = note.content,
                color = note.color,
                isPinned = note.isPinned,
                isArchived = note.isArchived,
                isNewNote = false,
                noteType = note.noteType,
                checklistItems = checklistItems,
                reminderAt = note.reminderAt,
                imageUris = note.imageUris,
                audioUri = note.audioUri,
                selectedLabelIds = labels.map { it.id }.toSet(),
                saveStatus = SaveStatus.SAVED,
                isLoading = false
            )
        }
    }

    fun prepareNewNote() { _detailUiState.value = NoteDetailUiState() }

    private fun triggerAutoSave() {
        _detailUiState.update {
            if (it.saveStatus == SaveStatus.SAVED) it.copy(saveStatus = SaveStatus.IDLE) else it
        }
        _saveTrigger.value = System.currentTimeMillis()
    }

    fun onTitleChange(title: String) { _detailUiState.update { it.copy(title = title) }; triggerAutoSave() }
    fun onContentChange(content: String) { _detailUiState.update { it.copy(content = content) }; triggerAutoSave() }
    fun onColorChange(color: Int) { _detailUiState.update { it.copy(color = color) }; triggerAutoSave() }
    fun togglePin() { _detailUiState.update { it.copy(isPinned = !it.isPinned) }; triggerAutoSave() }

    fun onReminderChange(reminderAt: Long?) {
        _detailUiState.update { it.copy(reminderAt = reminderAt) }
        triggerAutoSave()
    }

    fun onAddImage(imageUri: String) {
        _detailUiState.update { it.copy(imageUris = it.imageUris + imageUri) }
        triggerAutoSave()
    }

    fun onRemoveImage(imageUri: String) {
        _detailUiState.update { it.copy(imageUris = it.imageUris - imageUri) }
        triggerAutoSave()
    }

    fun onAudioChange(audioUri: String?) {
        _detailUiState.update { it.copy(audioUri = audioUri) }
        triggerAutoSave()
    }

    fun toggleLabel(labelId: Long) {
        _detailUiState.update { state ->
            val ids = state.selectedLabelIds.toMutableSet()
            if (labelId in ids) ids.remove(labelId) else ids.add(labelId)
            state.copy(selectedLabelIds = ids)
        }
        triggerAutoSave()
    }

    fun onNoteTypeChange(noteType: NoteType) {
        _detailUiState.update { state ->
            if (state.noteType == noteType) state
            else if (noteType == NoteType.CHECKLIST && state.checklistItems.isEmpty()) {
                state.copy(noteType = noteType, checklistItems = listOf(newChecklistItem(0)))
            } else state.copy(noteType = noteType)
        }
        triggerAutoSave()
    }

    fun onChecklistItemTextChange(index: Int, text: String) {
        _detailUiState.update { state ->
            val items = state.checklistItems.toMutableList()
            if (index in items.indices) items[index] = items[index].copy(text = text)
            val withTrailing = if (index == items.lastIndex && text.isNotBlank()) {
                items + newChecklistItem(items.size)
            } else items
            state.copy(checklistItems = withTrailing)
        }
        triggerAutoSave()
    }

    fun onChecklistItemCheckedChange(index: Int, isChecked: Boolean) {
        _detailUiState.update { state ->
            val items = state.checklistItems.toMutableList()
            if (index in items.indices) items[index] = items[index].copy(isChecked = isChecked)
            state.copy(checklistItems = items)
        }
        triggerAutoSave()
    }

    fun addChecklistItem() {
        _detailUiState.update { it.copy(checklistItems = it.checklistItems + newChecklistItem(it.checklistItems.size)) }
        triggerAutoSave()
    }

    fun removeChecklistItem(index: Int) {
        _detailUiState.update { state ->
            val items = state.checklistItems.toMutableList()
            if (index in items.indices && items.size > 1) items.removeAt(index)
            state.copy(checklistItems = ensureTrailingEmptyRow(items.mapIndexed { i, item -> item.copy(position = i) }))
        }
        triggerAutoSave()
    }

    fun saveNote(onSaved: (Long) -> Unit = {}) {
        viewModelScope.launch {
            performSave()?.let { onSaved(it) }
        }
    }

    private suspend fun autoSave() {
        val state = _detailUiState.value
        if (state.isLoading || !hasSaveableContent(state)) return
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            _detailUiState.update { it.copy(saveStatus = SaveStatus.SAVING) }
            performSave()
        }
    }

    private suspend fun performSave(): Long? {
        val state = _detailUiState.value
        if (!hasSaveableContent(state)) return null

        val existing = if (state.id > 0) repository.getNoteById(state.id).first() else null
        val note = Note(
            id = state.id,
            title = state.title.trim(),
            content = if (state.noteType == NoteType.TEXT) state.content.trim() else "",
            color = state.color,
            isPinned = state.isPinned,
            isArchived = existing?.isArchived ?: false,
            isTrashed = existing?.isTrashed ?: false,
            trashedAt = existing?.trashedAt,
            noteType = state.noteType,
            reminderAt = state.reminderAt,
            imageUris = state.imageUris,
            audioUri = state.audioUri,
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        val savedId = if (state.isNewNote) repository.insertNote(note) else {
            repository.updateNote(note)
            note.id
        }

        if (state.noteType == NoteType.CHECKLIST) {
            val items = state.checklistItems.filter { it.text.isNotBlank() }.mapIndexed { index, item ->
                ChecklistItem(id = item.id, noteId = savedId, text = item.text.trim(), isChecked = item.isChecked, position = index)
            }
            repository.saveChecklistItems(savedId, items)
            _checklistPreviews.update { it + (savedId to items) }
        }

        repository.setLabelsForNote(savedId, state.selectedLabelIds.toList())
        _noteLabels.update { it + (savedId to repository.getLabelsForNote(savedId)) }

        val savedNote = note.copy(id = savedId)
        if (savedNote.reminderAt != null && savedNote.reminderAt > System.currentTimeMillis()) {
            reminderScheduler.schedule(savedNote)
        } else {
            reminderScheduler.cancel(savedId)
        }

        _detailUiState.update { it.copy(id = savedId, isNewNote = false, saveStatus = SaveStatus.SAVED) }
        refreshWidget()
        return savedId
    }

    private fun hasSaveableContent(state: NoteDetailUiState) =
        state.title.isNotBlank() || state.content.isNotBlank() ||
            state.checklistItems.any { it.text.isNotBlank() } ||
            state.imageUris.isNotEmpty() || !state.audioUri.isNullOrBlank()

    private fun refreshWidget() {
        viewModelScope.launch { WidgetUpdateHelper.updateAll(appContext) }
    }

    fun deleteCurrentNote(onDeleted: () -> Unit = {}) {
        val state = _detailUiState.value
        if (state.isNewNote || state.id == 0L) { onDeleted(); return }
        viewModelScope.launch {
            repository.getNoteById(state.id).first()?.let { deleteNoteWithUndo(it, onDeleted) }
        }
    }

    fun deleteNote(note: Note) { deleteNoteWithUndo(note) }

    private fun deleteNoteWithUndo(note: Note, onDeleted: (() -> Unit)? = null) {
        viewModelScope.launch {
            reminderScheduler.cancel(note.id)
            repository.trashNote(note.id)
            _checklistPreviews.update { it - note.id }
            _noteLabels.update { it - note.id }
            pendingTrashJob?.cancel()
            pendingTrash = PendingTrash(note)
            showUndoSnackbar("Nota spostata nel cestino") { undoTrash() }
            pendingTrashJob = viewModelScope.launch { delay(5_000); dismissUndoSnackbar() }
            refreshWidget()
            onDeleted?.invoke()
        }
    }

    fun undoTrash() {
        val pending = pendingTrash ?: return
        pendingTrashJob?.cancel()
        pendingTrash = null
        _undoSnackbarState.value = null
        viewModelScope.launch {
            repository.restoreFromTrash(pending.note.id)
            if (pending.note.noteType == NoteType.CHECKLIST) {
                loadChecklistPreview(pending.note.id)
            }
            loadNoteLabels(pending.note.id)
            if (pending.note.reminderAt != null && pending.note.reminderAt > System.currentTimeMillis()) {
                reminderScheduler.schedule(pending.note)
            }
            refreshWidget()
        }
    }

    fun restoreFromTrash(note: Note) {
        viewModelScope.launch {
            repository.restoreFromTrash(note.id)
            if (note.noteType == NoteType.CHECKLIST) {
                loadChecklistPreview(note.id)
            }
            loadNoteLabels(note.id)
            if (note.reminderAt != null && note.reminderAt > System.currentTimeMillis()) {
                reminderScheduler.schedule(note)
            }
            refreshWidget()
        }
    }

    fun permanentlyDeleteNote(note: Note) {
        viewModelScope.launch {
            reminderScheduler.cancel(note.id)
            repository.permanentlyDeleteNote(note)
            refreshWidget()
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            trashedNotes.value.forEach { repository.permanentlyDeleteNote(it) }
            refreshWidget()
        }
    }

    fun duplicateNote(note: Note) {
        viewModelScope.launch {
            val newId = repository.duplicateNote(note.id)
            if (newId > 0L) {
                val duplicated = repository.getNoteByIdOnce(newId)
                if (duplicated?.noteType == NoteType.CHECKLIST) {
                    loadChecklistPreview(newId)
                }
                loadNoteLabels(newId)
                refreshWidget()
            }
        }
    }

    fun togglePinNote(note: Note) {
        viewModelScope.launch {
            val updated = note.copy(
                isPinned = !note.isPinned,
                updatedAt = System.currentTimeMillis()
            )
            repository.updateNote(updated)
            refreshWidget()
        }
    }

    fun undoDelete() {
        undoTrash()
    }

    fun dismissUndoSnackbar() {
        pendingTrashJob?.cancel()
        pendingTrash = null
        pendingArchiveJob?.cancel()
        pendingArchive = null
        _undoSnackbarState.value = null
    }

    private fun showUndoSnackbar(message: String, onUndo: () -> Unit) {
        _undoSnackbarState.value = UndoSnackbarState(message = message, onUndo = onUndo)
    }

    fun archiveNote(note: Note) {
        viewModelScope.launch {
            reminderScheduler.cancel(note.id)
            repository.archiveNote(note.id)
            repository.updateReminder(note.id, null)
            pendingArchiveJob?.cancel()
            pendingArchive = PendingArchive(note)
            showUndoSnackbar("Nota archiviata") { undoArchive() }
            pendingArchiveJob = viewModelScope.launch { delay(5_000); dismissUndoSnackbar() }
            refreshWidget()
        }
    }

    fun archiveCurrentNote(onArchived: () -> Unit = {}) {
        val state = _detailUiState.value
        if (state.isNewNote || state.id == 0L) return
        viewModelScope.launch {
            performSave()
            repository.getNoteById(state.id).first()?.let { note ->
                archiveNote(note)
                onArchived()
            }
        }
    }

    fun undoArchive() {
        val pending = pendingArchive ?: return
        pendingArchiveJob?.cancel()
        pendingArchive = null
        _undoSnackbarState.value = null
        viewModelScope.launch {
            repository.unarchiveNote(pending.note.id)
            if (pending.note.reminderAt != null && pending.note.reminderAt > System.currentTimeMillis()) {
                reminderScheduler.schedule(pending.note)
            }
            refreshWidget()
        }
    }

    fun unarchiveNote(note: Note) {
        viewModelScope.launch {
            repository.unarchiveNote(note.id)
            refreshWidget()
        }
    }

    fun getShareText(): String {
        val state = _detailUiState.value
        val builder = StringBuilder()
        if (state.title.isNotBlank()) builder.append(state.title)
        if (state.noteType == NoteType.TEXT) {
            if (builder.isNotEmpty() && state.content.isNotBlank()) builder.append("\n\n")
            builder.append(state.content)
        } else {
            val items = state.checklistItems.filter { it.text.isNotBlank() }
            if (items.isNotEmpty()) {
                if (builder.isNotEmpty()) builder.append("\n\n")
                items.forEach { item ->
                    builder.append(if (item.isChecked) "☑ " else "☐ ").append(item.text).append('\n')
                }
            }
        }
        return builder.toString().trim()
    }

    private fun ChecklistItem.toUi() = ChecklistItemUi(id = id, text = text, isChecked = isChecked, position = position)
    private fun newChecklistItem(position: Int) = ChecklistItemUi(position = position)
    private fun ensureTrailingEmptyRow(items: List<ChecklistItemUi>): List<ChecklistItemUi> {
        if (items.isEmpty()) return listOf(newChecklistItem(0))
        return if (items.last().text.isNotBlank()) items + newChecklistItem(items.size) else items
    }
}

class NoteViewModelFactory(
    private val repository: NoteRepository,
    private val settingsRepository: SettingsRepository,
    private val reminderScheduler: ReminderScheduler,
    private val appContext: Context
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(NoteViewModel::class.java)) {
            return NoteViewModel(repository, settingsRepository, reminderScheduler, appContext) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
