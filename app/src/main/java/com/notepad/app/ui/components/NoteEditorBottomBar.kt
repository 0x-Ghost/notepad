package com.notepad.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import com.notepad.app.ui.util.edgeToEdgeBottomBar
import com.notepad.app.ui.util.safeNavigationBarPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TextFormat
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.notepad.app.data.model.NoteColors
import com.notepad.app.data.model.NoteType
import com.notepad.app.ui.viewmodel.NoteDetailUiState

private enum class EditorSheet { FORMAT, ATTACH, ORGANIZE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorBottomBar(
    uiState: NoteDetailUiState,
    contentColor: Color,
    backgroundColor: Color,
    isRecording: Boolean,
    isPreviewMode: Boolean,
    hasShareableContent: Boolean,
    onNoteTypeChange: (NoteType) -> Unit,
    onTogglePin: () -> Unit,
    onShowReminderPicker: () -> Unit,
    onAttachImage: () -> Unit,
    onOpenDrawing: () -> Unit,
    onToggleAudioRecording: () -> Unit,
    onShare: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onColorChange: (Int) -> Unit,
    onFormatSheetOpen: () -> Unit,
    onBold: () -> Unit,
    onItalic: () -> Unit,
    onStrikethrough: () -> Unit,
    onUnderline: () -> Unit,
    onCode: () -> Unit,
    onHeading: () -> Unit,
    onBullet: () -> Unit,
    onTogglePreview: () -> Unit
) {
    var activeSheet by remember { mutableStateOf<EditorSheet?>(null) }
    var showColorPicker by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .edgeToEdgeBottomBar(backgroundColor)
            .safeNavigationBarPadding(extra = 4.dp)
    ) {
        HorizontalDivider(color = contentColor.copy(alpha = 0.12f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (uiState.noteType == NoteType.TEXT) {
                BottomBarButton(
                    icon = Icons.Default.TextFormat,
                    label = "Formatta",
                    contentColor = contentColor,
                    onClick = {
                        onFormatSheetOpen()
                        activeSheet = EditorSheet.FORMAT
                    }
                )
            }
            BottomBarButton(
                icon = Icons.Default.AttachFile,
                label = "Allega",
                contentColor = contentColor,
                onClick = { activeSheet = EditorSheet.ATTACH }
            )
            BottomBarButton(
                icon = Icons.Default.PushPin,
                label = "Organizza",
                contentColor = if (uiState.isPinned) MaterialTheme.colorScheme.primary else contentColor,
                onClick = { activeSheet = EditorSheet.ORGANIZE }
            )
            BottomBarButton(
                icon = Icons.Default.Palette,
                label = "Colore",
                contentColor = contentColor,
                onClick = { showColorPicker = !showColorPicker }
            )
        }

        AnimatedVisibility(
            visible = showColorPicker,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            NoteColorPickerRow(
                selectedColor = uiState.color,
                onColorSelected = onColorChange,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }

    activeSheet?.let { sheet ->
        ModalBottomSheet(
            onDismissRequest = { activeSheet = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            when (sheet) {
                EditorSheet.FORMAT -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        FormatQuickButton(Icons.Default.FormatBold, "Grassetto", onBold)
                        FormatQuickButton(Icons.Default.FormatItalic, "Corsivo", onItalic)
                        FormatQuickButton(Icons.Default.FormatStrikethrough, "Barrato", onStrikethrough)
                        FormatQuickButton(Icons.Default.FormatUnderlined, "Sottolineato", onUnderline)
                        FormatQuickButton(Icons.Default.Code, "Codice", onCode)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        FormatQuickButton(Icons.Default.Title, "Titolo", onHeading)
                        FormatQuickButton(Icons.Default.FormatListBulleted, "Elenco", onBullet)
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SheetAction(
                        icon = Icons.Default.FormatBold,
                        title = "Grassetto",
                        subtitle = "Avvolge il testo selezionato con **",
                        onClick = { onBold(); activeSheet = null }
                    )
                    SheetAction(
                        icon = Icons.Default.FormatItalic,
                        title = "Corsivo",
                        subtitle = "Avvolge il testo selezionato con *",
                        onClick = { onItalic(); activeSheet = null }
                    )
                    SheetAction(
                        icon = Icons.Default.FormatStrikethrough,
                        title = "Barrato",
                        subtitle = "Avvolge il testo selezionato con ~~",
                        onClick = { onStrikethrough(); activeSheet = null }
                    )
                    SheetAction(
                        icon = Icons.Default.FormatUnderlined,
                        title = "Sottolineato",
                        subtitle = "Avvolge il testo selezionato con __",
                        onClick = { onUnderline(); activeSheet = null }
                    )
                    SheetAction(
                        icon = Icons.Default.Code,
                        title = "Codice",
                        subtitle = "Avvolge il testo selezionato con `",
                        onClick = { onCode(); activeSheet = null }
                    )
                    SheetAction(
                        icon = Icons.Default.Title,
                        title = "Titolo",
                        subtitle = "Aggiunge ## all'inizio della riga",
                        onClick = { onHeading(); activeSheet = null }
                    )
                    SheetAction(
                        icon = Icons.Default.FormatListBulleted,
                        title = "Elenco puntato",
                        subtitle = "Aggiunge - all'inizio della riga",
                        onClick = { onBullet(); activeSheet = null }
                    )
                    SheetAction(
                        icon = if (isPreviewMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        title = if (isPreviewMode) "Modifica" else "Anteprima",
                        subtitle = "Visualizza il markdown formattato",
                        onClick = { onTogglePreview(); activeSheet = null }
                    )
                }
                EditorSheet.ATTACH -> {
                    SheetAction(
                        icon = Icons.Default.Image,
                        title = "Immagine",
                        subtitle = "Allega dalla galleria",
                        onClick = { onAttachImage(); activeSheet = null }
                    )
                    SheetAction(
                        icon = Icons.Default.Brush,
                        title = "Disegno",
                        subtitle = "Apri la tavola da disegno",
                        onClick = { onOpenDrawing(); activeSheet = null }
                    )
                    SheetAction(
                        icon = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                        title = if (isRecording) "Ferma registrazione" else "Registra audio",
                        subtitle = "Aggiungi una nota vocale",
                        onClick = { onToggleAudioRecording(); activeSheet = null }
                    )
                }
                EditorSheet.ORGANIZE -> {
                    SheetAction(
                        icon = Icons.Default.Notes,
                        title = "Nota di testo",
                        subtitle = "Passa al formato testo libero",
                        onClick = { onNoteTypeChange(NoteType.TEXT); activeSheet = null },
                        selected = uiState.noteType == NoteType.TEXT
                    )
                    SheetAction(
                        icon = Icons.Default.FormatListBulleted,
                        title = "Checklist",
                        subtitle = "Passa alla lista di attività",
                        onClick = { onNoteTypeChange(NoteType.CHECKLIST); activeSheet = null },
                        selected = uiState.noteType == NoteType.CHECKLIST
                    )
                    SheetAction(
                        icon = if (uiState.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        title = if (uiState.isPinned) "Sfissa nota" else "Fissa nota",
                        subtitle = "Tieni la nota in cima alla lista",
                        onClick = { onTogglePin(); activeSheet = null },
                        selected = uiState.isPinned
                    )
                    SheetAction(
                        icon = Icons.Default.Notifications,
                        title = "Promemoria",
                        subtitle = "Imposta data e ora",
                        onClick = { onShowReminderPicker(); activeSheet = null },
                        selected = uiState.reminderAt != null
                    )
                    if (hasShareableContent) {
                        SheetAction(
                            icon = Icons.Default.Share,
                            title = "Condividi",
                            subtitle = "Invia il contenuto della nota",
                            onClick = { onShare(); activeSheet = null }
                        )
                    }
                    if (!uiState.isNewNote) {
                        SheetAction(
                            icon = Icons.Default.Archive,
                            title = "Archivia",
                            subtitle = "Sposta nell'archivio",
                            onClick = { onArchive(); activeSheet = null }
                        )
                        SheetAction(
                            icon = Icons.Default.Delete,
                            title = "Elimina",
                            subtitle = "Rimuovi definitivamente",
                            onClick = { onDelete(); activeSheet = null },
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            Box(modifier = Modifier.padding(bottom = 24.dp))
        }
    }
}

@Composable
private fun FormatQuickButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    TextButton(onClick = onClick) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun BottomBarButton(
    icon: ImageVector,
    label: String,
    contentColor: Color,
    onClick: () -> Unit
) {
    TextButton(onClick = onClick) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(imageVector = icon, contentDescription = label, tint = contentColor)
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun SheetAction(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    selected: Boolean = false,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    ListItem(
        headlineContent = {
            Text(
                text = title,
                color = if (selected) MaterialTheme.colorScheme.primary else tint
            )
        },
        supportingContent = { Text(subtitle) },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else tint
            )
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
fun NoteColorPickerRow(
    selectedColor: Int,
    onColorSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)
    ) {
        NoteColors.palette.forEach { colorArgb ->
            val color = NoteColors.toComposeColor(colorArgb)
            val isSelected = colorArgb == selectedColor
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(color)
                    .then(
                        if (isSelected) {
                            Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        } else {
                            Modifier.border(1.dp, Color.Black.copy(alpha = 0.12f), CircleShape)
                        }
                    )
                    .clickable { onColorSelected(colorArgb) }
            )
        }
    }
}
