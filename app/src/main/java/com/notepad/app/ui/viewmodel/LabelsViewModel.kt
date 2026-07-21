package com.notepad.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.notepad.app.data.model.Label
import com.notepad.app.data.model.LabelColors
import com.notepad.app.data.repository.NoteRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LabelsViewModel(private val repository: NoteRepository) : ViewModel() {

    val labels: StateFlow<List<Label>> = repository.allLabels.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    fun addLabel(name: String, color: Int = LabelColors.palette.random()) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.insertLabel(Label(name = name.trim(), color = color))
        }
    }

    fun deleteLabel(label: Label) {
        viewModelScope.launch { repository.deleteLabel(label) }
    }

    fun updateLabel(label: Label, name: String, color: Int) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.updateLabel(label.copy(name = name.trim(), color = color))
        }
    }
}

class LabelsViewModelFactory(private val repository: NoteRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LabelsViewModel::class.java)) {
            return LabelsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
