package com.notepad.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.notepad.app.data.model.Note

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteContextMenuSheet(
    note: Note,
    onDismiss: () -> Unit,
    onTogglePin: () -> Unit,
    onDuplicate: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = note.title.ifBlank { "Nota senza titolo" },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                maxLines = 2
            )

            ListItem(
                headlineContent = {
                    Text(if (note.isPinned) "Sfissa nota" else "Fissa nota")
                },
                leadingContent = {
                    Icon(
                        imageVector = if (note.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        contentDescription = null
                    )
                },
                modifier = Modifier.clickable {
                    onTogglePin()
                    onDismiss()
                }
            )

            ListItem(
                headlineContent = { Text("Duplica") },
                supportingContent = { Text("Crea una copia della nota") },
                leadingContent = {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                },
                modifier = Modifier.clickable {
                    onDuplicate()
                    onDismiss()
                }
            )

            ListItem(
                headlineContent = { Text("Archivia") },
                leadingContent = {
                    Icon(Icons.Default.Archive, contentDescription = null)
                },
                modifier = Modifier.clickable {
                    onArchive()
                    onDismiss()
                }
            )

            ListItem(
                headlineContent = {
                    Text("Elimina", color = MaterialTheme.colorScheme.error)
                },
                supportingContent = { Text("Sposta nel cestino") },
                leadingContent = {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                modifier = Modifier.clickable {
                    onDelete()
                    onDismiss()
                }
            )
        }
    }
}
