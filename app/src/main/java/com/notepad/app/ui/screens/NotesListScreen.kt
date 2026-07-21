package com.notepad.app.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import com.notepad.app.ui.util.safeNavigationBarPadding
import com.notepad.app.ui.util.statusBarsTopPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.notepad.app.data.model.ChecklistItem
import com.notepad.app.data.model.Label
import com.notepad.app.data.model.LabelColors
import com.notepad.app.data.model.Note
import com.notepad.app.data.model.NoteColors
import com.notepad.app.data.model.NoteType
import com.notepad.app.data.preferences.NoteFilter
import com.notepad.app.data.preferences.NoteSortOrder
import com.notepad.app.security.SecureMediaAccess
import com.notepad.app.ui.components.EmptyStateType
import com.notepad.app.ui.components.NotesEmptyState
import com.notepad.app.ui.components.NoteContextMenuSheet
import com.notepad.app.ui.components.QuickNoteBar
import com.notepad.app.ui.components.SectionHeader
import com.notepad.app.ui.util.contentColorFor
import com.notepad.app.util.TextFormatHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Home screen in stile Google Keep: griglia masonry, sezioni fissate, ricerca e card animate.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesListScreen(
    notes: List<Note>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    noteFilter: NoteFilter,
    onFilterChange: (NoteFilter) -> Unit,
    noteSortOrder: NoteSortOrder,
    onSortOrderChange: (NoteSortOrder) -> Unit,
    allLabels: List<Label>,
    selectedLabelId: Long?,
    onLabelSelect: (Long?) -> Unit,
    onAddNote: () -> Unit,
    onNoteClick: (Long) -> Unit,
    onDeleteNote: (Note) -> Unit,
    onArchiveNote: (Note) -> Unit,
    onTogglePinNote: (Note) -> Unit,
    onDuplicateNote: (Note) -> Unit,
    onSettingsClick: () -> Unit,
    getChecklistPreview: (Long) -> List<ChecklistItem>,
    getNoteLabels: (Long) -> List<Label>
) {
    var searchActive by rememberSaveable { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var contextMenuNote by remember { mutableStateOf<Note?>(null) }
    val view = LocalView.current

    val pinnedNotes = remember(notes) { notes.filter { it.isPinned } }
    val otherNotes = remember(notes) { notes.filter { !it.isPinned } }
    val isFiltered = noteFilter != NoteFilter.ALL || selectedLabelId != null
    val emptyStateType = when {
        searchQuery.isNotBlank() -> EmptyStateType.NO_RESULTS
        isFiltered -> EmptyStateType.FILTERED
        else -> EmptyStateType.NO_NOTES
    }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsTopPadding(),
                title = {
                    Text(
                        text = "Notepad",
                        style = MaterialTheme.typography.headlineMedium
                    )
                },
                actions = {
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.Default.Sort, contentDescription = "Ordina")
                    }
                    DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                        NoteSortOrder.entries.forEach { order ->
                            DropdownMenuItem(
                                text = { Text(order.label) },
                                onClick = {
                                    onSortOrderChange(order)
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Impostazioni")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                modifier = Modifier.safeNavigationBarPadding(extra = 12.dp),
                onClick = onAddNote,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Nota") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            SearchBar(
                inputField = {
                    SearchBarDefaults.InputField(
                        query = searchQuery,
                        onQueryChange = onSearchQueryChange,
                        onSearch = { searchActive = false },
                        expanded = searchActive,
                        onExpandedChange = { searchActive = it },
                        placeholder = { Text("Cerca nelle note...") }
                    )
                },
                expanded = searchActive,
                onExpandedChange = { searchActive = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {}

            QuickNoteBar(
                onClick = onAddNote,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NoteFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = noteFilter == filter,
                        onClick = { onFilterChange(filter) },
                        label = { Text(filter.label) }
                    )
                }
            }

            if (allLabels.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedLabelId == null,
                        onClick = { onLabelSelect(null) },
                        label = { Text("Tutte le etichette") }
                    )
                    allLabels.forEach { label ->
                        FilterChip(
                            selected = selectedLabelId == label.id,
                            onClick = {
                                onLabelSelect(if (selectedLabelId == label.id) null else label.id)
                            },
                            label = { Text(label.name) }
                        )
                    }
                }
            }

            if (notes.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    NotesEmptyState(type = emptyStateType)
                }
            } else {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalItemSpacing = 10.dp
                ) {
                    if (pinnedNotes.isNotEmpty()) {
                        item(span = StaggeredGridItemSpan.FullLine) {
                            SectionHeader(title = "Fissate")
                        }
                        items(pinnedNotes, key = { "pinned-${it.id}" }) { note ->
                            AnimatedNoteItem(
                                note = note,
                                checklistItems = getChecklistPreview(note.id),
                                labels = getNoteLabels(note.id),
                                onClick = { onNoteClick(note.id) },
                                onLongClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    contextMenuNote = note
                                },
                                onDelete = { onDeleteNote(note) },
                                onArchive = { onArchiveNote(note) }
                            )
                        }
                    }
                    if (otherNotes.isNotEmpty()) {
                        if (pinnedNotes.isNotEmpty()) {
                            item(span = StaggeredGridItemSpan.FullLine) {
                                SectionHeader(title = "Altre note")
                            }
                        }
                        items(otherNotes, key = { "other-${it.id}" }) { note ->
                            AnimatedNoteItem(
                                note = note,
                                checklistItems = getChecklistPreview(note.id),
                                labels = getNoteLabels(note.id),
                                onClick = { onNoteClick(note.id) },
                                onLongClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    contextMenuNote = note
                                },
                                onDelete = { onDeleteNote(note) },
                                onArchive = { onArchiveNote(note) }
                            )
                        }
                    }
                }
            }
        }
    }

    contextMenuNote?.let { note ->
        NoteContextMenuSheet(
            note = note,
            onDismiss = { contextMenuNote = null },
            onTogglePin = { onTogglePinNote(note) },
            onDuplicate = { onDuplicateNote(note) },
            onArchive = { onArchiveNote(note) },
            onDelete = { onDeleteNote(note) }
        )
    }
}

@Composable
private fun AnimatedNoteItem(
    note: Note,
    checklistItems: List<ChecklistItem>,
    labels: List<Label>,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit,
    onArchive: () -> Unit
) {
    var visible by remember(note.id) { mutableStateOf(false) }
    LaunchedEffect(note.id) { visible = true }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 3 }
    ) {
        SwipeableNoteItem(
            note = note,
            checklistItems = checklistItems,
            labels = labels,
            onClick = onClick,
            onLongClick = onLongClick,
            onDelete = onDelete,
            onArchive = onArchive
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableNoteItem(
    note: Note,
    checklistItems: List<ChecklistItem>,
    labels: List<Label>,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit,
    onArchive: () -> Unit
) {
    val view = LocalView.current
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> {
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    onDelete()
                    true
                }
                SwipeToDismissBoxValue.StartToEnd -> {
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    onArchive()
                    true
                }
                else -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp),
                contentAlignment = when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                    SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                    else -> Alignment.CenterEnd
                }
            ) {
                Icon(
                    imageVector = when (direction) {
                        SwipeToDismissBoxValue.StartToEnd -> Icons.Default.Archive
                        else -> Icons.Default.Delete
                    },
                    contentDescription = when (direction) {
                        SwipeToDismissBoxValue.StartToEnd -> "Archivia"
                        else -> "Elimina"
                    },
                    tint = when (direction) {
                        SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    ) {
        NoteGridItem(
            note = note,
            checklistItems = checklistItems,
            labels = labels,
            onClick = onClick,
            onLongClick = onLongClick
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun NoteGridItem(
    note: Note,
    checklistItems: List<ChecklistItem>,
    labels: List<Label>,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val backgroundColor = NoteColors.toComposeColor(note.color)
    val textColor = contentColorFor(backgroundColor)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = tween(120),
        label = "noteCardScale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (note.isPinned) 4.dp else 2.dp,
            pressedElevation = 6.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            if (note.isPinned || note.reminderAt != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (note.reminderAt != null) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Promemoria",
                            modifier = Modifier
                                .size(16.dp)
                                .padding(end = 4.dp),
                            tint = textColor.copy(alpha = 0.6f)
                        )
                    }
                    if (note.isPinned) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = "Fissata",
                            modifier = Modifier.size(16.dp),
                            tint = textColor.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            if (note.imageUris.isNotEmpty()) {
                val context = LocalContext.current
                val thumb = remember(note.imageUris.first()) {
                    SecureMediaAccess.resolve(context, note.imageUris.first()) ?: note.imageUris.first()
                }
                AsyncImage(
                    model = thumb,
                    contentDescription = "Immagine allegata",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .padding(bottom = 8.dp),
                    contentScale = ContentScale.Crop
                )
            }

            if (labels.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    labels.take(3).forEach { label ->
                        Text(
                            text = label.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = LabelColors.toComposeColor(label.color),
                            maxLines = 1,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .padding(horizontal = 2.dp)
                        )
                    }
                }
            }

            if (note.title.isNotBlank()) {
                Text(
                    text = note.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = textColor
                )
            }

            if (note.noteType == NoteType.CHECKLIST && checklistItems.isNotEmpty()) {
                val checkedCount = checklistItems.count { it.isChecked }
                val totalCount = checklistItems.size

                LinearProgressIndicator(
                    progress = { checkedCount.toFloat() / totalCount },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = if (note.title.isNotBlank()) 8.dp else 0.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = textColor.copy(alpha = 0.2f)
                )
                Text(
                    text = "$checkedCount/$totalCount completati",
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp)
                )

                checklistItems.take(3).forEach { item ->
                    Text(
                        text = item.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor.copy(alpha = if (item.isChecked) 0.5f else 0.85f),
                        textDecoration = if (item.isChecked) TextDecoration.LineThrough else null,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            } else if (note.content.isNotBlank()) {
                Text(
                    text = TextFormatHelper.stripMarkdown(note.content),
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.85f),
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = if (note.title.isNotBlank()) 6.dp else 0.dp)
                )
            }

            Text(
                text = formatDate(note.updatedAt),
                style = MaterialTheme.typography.labelSmall,
                color = textColor.copy(alpha = 0.55f),
                modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}

private fun formatDate(timestamp: Long): String {
    val formatter = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    return formatter.format(Date(timestamp))
}
