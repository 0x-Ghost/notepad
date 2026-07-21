package com.notepad.app.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.notepad.app.data.model.Label
import com.notepad.app.data.model.LabelColors
import com.notepad.app.data.model.NoteColors
import com.notepad.app.ui.components.NoteAttachedImage
import com.notepad.app.ui.components.AudioWaveformPlayer
import com.notepad.app.ui.components.ImageLightbox
import com.notepad.app.ui.components.NoteEditorBottomBar
import com.notepad.app.ui.util.contentColorFor
import com.notepad.app.ui.util.statusBarsTopPadding
import com.notepad.app.data.model.NoteType
import com.notepad.app.ui.viewmodel.NoteDetailUiState
import com.notepad.app.ui.viewmodel.SaveStatus
import com.notepad.app.util.AudioRecorderHelper
import com.notepad.app.util.AudioStorageHelper
import com.notepad.app.util.ImageStorageHelper
import com.notepad.app.util.MarkdownVisualTransformation
import com.notepad.app.util.TextFormatHelper
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Editor seamless in stile Google Keep: sfondo colorato, campi senza bordi,
 * toolbar azioni scrollabile in basso e color picker sotto.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditNoteScreen(
    uiState: NoteDetailUiState,
    allLabels: List<Label>,
    onTitleChange: (String) -> Unit,
    onContentChange: (String) -> Unit,
    onColorChange: (Int) -> Unit,
    onTogglePin: () -> Unit,
    onNoteTypeChange: (NoteType) -> Unit,
    onChecklistItemTextChange: (Int, String) -> Unit,
    onChecklistItemCheckedChange: (Int, Boolean) -> Unit,
    onAddChecklistItem: () -> Unit,
    onRemoveChecklistItem: (Int) -> Unit,
    onReminderChange: (Long?) -> Unit,
    onAddImage: (String) -> Unit,
    onRemoveImage: (String) -> Unit,
    onAudioChange: (String?) -> Unit,
    onToggleLabel: (Long) -> Unit,
    onOpenDrawing: () -> Unit,
    onOcrExtract: suspend (String) -> String?,
    onShare: () -> String,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onArchive: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var pickedDateMillis by remember { mutableStateOf<Long?>(null) }
    var isPreviewMode by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var isOcrRunning by remember { mutableStateOf(false) }
    var showImageLightbox by remember { mutableStateOf<String?>(null) }
    val audioRecorder = remember { AudioRecorderHelper(context) }

    DisposableEffect(Unit) {
        onDispose {
            if (isRecording) audioRecorder.cancelRecording()
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            audioRecorder.startRecording()
            isRecording = true
        }
    }

    // Conserva l'ultima selezione non vuota: al tap su Formatta il TextField
    // perde il focus e collassa la selezione PRIMA di applicare lo stile.
    var lastNonEmptySelection by remember(uiState.id) {
        mutableStateOf(TextRange.Zero)
    }

    var contentField by remember(uiState.id) {
        mutableStateOf(TextFieldValue(uiState.content))
    }
    val contentFocusRequester = remember { FocusRequester() }

    fun resolveFormatSelection(): TextRange {
        val current = contentField.selection
        if (current.start != current.end) return current
        val remembered = lastNonEmptySelection
        if (remembered.start != remembered.end &&
            remembered.start >= 0 &&
            remembered.end <= contentField.text.length
        ) {
            return remembered
        }
        return current
    }

    fun applyFormat(marker: String) {
        val selection = resolveFormatSelection()
        val (newText, newSelection) = TextFormatHelper.wrapSelection(
            contentField.text,
            selection.start,
            selection.end,
            marker
        )
        contentField = TextFieldValue(newText, newSelection)
        if (newSelection.start != newSelection.end) {
            lastNonEmptySelection = newSelection
        }
        onContentChange(newText)
    }

    fun applyLinePrefix(prefix: String) {
        val selection = resolveFormatSelection()
        val (newText, newSelection) = TextFormatHelper.toggleLinePrefix(
            contentField.text,
            selection.start,
            selection.end,
            prefix
        )
        contentField = TextFieldValue(newText, newSelection)
        if (newSelection.start != newSelection.end) {
            lastNonEmptySelection = newSelection
        }
        onContentChange(newText)
    }

    LaunchedEffect(uiState.content, uiState.id) {
        if (contentField.text != uiState.content) {
            contentField = TextFieldValue(uiState.content, contentField.selection)
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            ImageStorageHelper.copyToInternalStorage(context, it)?.let(onAddImage)
        }
    }
    val backgroundColor = NoteColors.toComposeColor(uiState.color)
    val contentColor = contentColorFor(backgroundColor)
    val transparentFieldColors = TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
        cursorColor = MaterialTheme.colorScheme.primary,
        focusedTextColor = contentColor,
        unfocusedTextColor = contentColor,
        focusedPlaceholderColor = contentColor.copy(alpha = 0.5f),
        unfocusedPlaceholderColor = contentColor.copy(alpha = 0.5f)
    )

    val markdownTransformation = remember { MarkdownVisualTransformation() }

    Scaffold(
        containerColor = backgroundColor,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsTopPadding(),
                title = {
                    Text(
                        text = when (uiState.saveStatus) {
                            SaveStatus.SAVING -> "Salvataggio..."
                            SaveStatus.SAVED -> "Salvato"
                            SaveStatus.IDLE -> ""
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = contentColor.copy(alpha = 0.7f)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Indietro",
                            tint = contentColor
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onSave) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Salva nota",
                            tint = contentColor
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = backgroundColor,
                    titleContentColor = contentColor
                )
            )
        },
        bottomBar = {
            NoteEditorBottomBar(
                uiState = uiState,
                contentColor = contentColor,
                backgroundColor = backgroundColor,
                isRecording = isRecording,
                isPreviewMode = isPreviewMode,
                hasShareableContent = hasShareableContent(uiState),
                onNoteTypeChange = onNoteTypeChange,
                onTogglePin = onTogglePin,
                onShowReminderPicker = { showDatePicker = true },
                onAttachImage = {
                    imagePicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onOpenDrawing = onOpenDrawing,
                onToggleAudioRecording = {
                    if (isRecording) {
                        val path = audioRecorder.stopRecording()
                        isRecording = false
                        if (path != null) onAudioChange(path)
                    } else {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                            == PackageManager.PERMISSION_GRANTED
                        ) {
                            audioRecorder.startRecording()
                            isRecording = true
                        } else {
                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                },
                onShare = {
                    val text = onShare()
                    if (text.isNotBlank()) {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                            if (uiState.title.isNotBlank()) {
                                putExtra(Intent.EXTRA_SUBJECT, uiState.title)
                            }
                        }
                        context.startActivity(Intent.createChooser(intent, "Condividi nota"))
                    }
                },
                onArchive = onArchive,
                onDelete = onDelete,
                onColorChange = onColorChange,
                onFormatSheetOpen = {
                    val selection = contentField.selection
                    if (selection.start != selection.end) {
                        lastNonEmptySelection = selection
                    }
                },
                onBold = { applyFormat("**") },
                onItalic = { applyFormat("*") },
                onStrikethrough = { applyFormat("~~") },
                onUnderline = { applyFormat("__") },
                onCode = { applyFormat("`") },
                onHeading = { applyLinePrefix("## ") },
                onBullet = { applyLinePrefix("- ") },
                onTogglePreview = { isPreviewMode = !isPreviewMode }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
                .padding(innerPadding)
                .padding(horizontal = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            TextField(
                value = uiState.title,
                onValueChange = onTitleChange,
                placeholder = { Text("Titolo", style = MaterialTheme.typography.headlineMedium) },
                textStyle = MaterialTheme.typography.headlineMedium.copy(color = contentColor),
                singleLine = false,
                maxLines = 3,
                colors = transparentFieldColors,
                modifier = Modifier.fillMaxWidth()
            )

            if (allLabels.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    allLabels.forEach { label ->
                        FilterChip(
                            selected = label.id in uiState.selectedLabelIds,
                            onClick = { onToggleLabel(label.id) },
                            label = { Text(label.name) },
                            leadingIcon = if (label.id in uiState.selectedLabelIds) {
                                {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(LabelColors.toComposeColor(label.color))
                                    )
                                }
                            } else null
                        )
                    }
                }
            }

            if (uiState.reminderAt != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Promemoria: ${formatReminder(uiState.reminderAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.8f)
                    )
                    IconButton(onClick = { onReminderChange(null) }) {
                        Icon(Icons.Default.Close, contentDescription = "Rimuovi promemoria", tint = contentColor)
                    }
                }
            }

            if (uiState.imageUris.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    uiState.imageUris.forEach { imageUri ->
                        NoteAttachedImage(
                            imageUri = imageUri,
                            onClick = { showImageLightbox = imageUri },
                            modifier = Modifier.fillMaxWidth(),
                            overlay = {
                                Row(modifier = Modifier.align(Alignment.TopEnd)) {
                                    IconButton(
                                        onClick = {
                                            if (isOcrRunning) return@IconButton
                                            isOcrRunning = true
                                            scope.launch {
                                                val text = onOcrExtract(imageUri)
                                                isOcrRunning = false
                                                if (!text.isNullOrBlank()) {
                                                    val separator = if (uiState.content.isNotBlank()) "\n\n" else ""
                                                    onContentChange(uiState.content + separator + text)
                                                }
                                            }
                                        },
                                        enabled = !isOcrRunning
                                    ) {
                                        Icon(Icons.Default.TextFields, contentDescription = "Estrai testo (OCR)", tint = Color.White)
                                    }
                                    IconButton(
                                        onClick = {
                                            ImageStorageHelper.deleteImage(imageUri)
                                            onRemoveImage(imageUri)
                                        }
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Rimuovi immagine", tint = Color.White)
                                    }
                                }
                            }
                        )
                    }
                }
            }

            if (!uiState.audioUri.isNullOrBlank()) {
                AudioWaveformPlayer(
                    audioUri = uiState.audioUri!!,
                    contentColor = contentColor,
                    onRemove = {
                        AudioStorageHelper.deleteAudio(uiState.audioUri)
                        onAudioChange(null)
                    },
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else if (isRecording) {
                Text(
                    text = "Registrazione in corso...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            if (uiState.noteType == NoteType.TEXT) {
                if (isPreviewMode) {
                    BasicText(
                        text = TextFormatHelper.parseMarkdownLite(uiState.content),
                        style = MaterialTheme.typography.bodyLarge.copy(color = contentColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    )
                } else {
                    // BasicTextField: applica correttamente SpanStyle dalla VisualTransformation
                    // (Material3 TextField spesso ignora grassetto/corsivo del markdown).
                    val bodyStyle = MaterialTheme.typography.bodyLarge.copy(color = contentColor)
                    Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                        if (contentField.text.isEmpty()) {
                            Text(
                                text = "Scrivi una nota...",
                                style = bodyStyle.copy(color = contentColor.copy(alpha = 0.5f))
                            )
                        }
                        BasicTextField(
                            value = contentField,
                            onValueChange = { newValue ->
                                contentField = newValue
                                if (newValue.selection.start != newValue.selection.end) {
                                    lastNonEmptySelection = newValue.selection
                                }
                                onContentChange(newValue.text)
                            },
                            textStyle = bodyStyle,
                            visualTransformation = markdownTransformation,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(contentFocusRequester),
                            minLines = 8,
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            } else {
                ChecklistEditor(
                    items = uiState.checklistItems,
                    contentColor = contentColor,
                    transparentFieldColors = transparentFieldColors,
                    onItemTextChange = onChecklistItemTextChange,
                    onItemCheckedChange = onChecklistItemCheckedChange,
                    onAddItem = onAddChecklistItem,
                    onRemoveItem = onRemoveChecklistItem,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
        }
    }

    showImageLightbox?.let { imageUri ->
        ImageLightbox(
            imageUri = imageUri,
            onDismiss = { showImageLightbox = null }
        )
    }

    if (showDatePicker) {
        val dateState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickedDateMillis = dateState.selectedDateMillis
                    showDatePicker = false
                    showTimePicker = true
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Annulla") }
            }
        ) { DatePicker(state = dateState) }
    }

    if (showTimePicker) {
        val timeState = rememberTimePickerState()
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val base = pickedDateMillis ?: System.currentTimeMillis()
                    val cal = Calendar.getInstance().apply { timeInMillis = base }
                    cal.set(Calendar.HOUR_OF_DAY, timeState.hour)
                    cal.set(Calendar.MINUTE, timeState.minute)
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                    onReminderChange(cal.timeInMillis)
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Annulla") }
            },
            text = { TimePicker(state = timeState) }
        )
    }
}

@Composable
private fun ChecklistEditor(
    items: List<com.notepad.app.ui.viewmodel.ChecklistItemUi>,
    contentColor: Color,
    transparentFieldColors: androidx.compose.material3.TextFieldColors,
    onItemTextChange: (Int, String) -> Unit,
    onItemCheckedChange: (Int, Boolean) -> Unit,
    onAddItem: () -> Unit,
    onRemoveItem: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    Column(modifier = modifier.fillMaxWidth()) {
        items.forEachIndexed { index, item ->
            key(item.key) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = item.isChecked,
                        onCheckedChange = { checked ->
                            view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                            onItemCheckedChange(index, checked)
                        }
                    )
                    TextField(
                        value = item.text,
                        onValueChange = { onItemTextChange(index, it) },
                        placeholder = {
                            Text(
                                text = "Elemento lista",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = contentColor,
                            textDecoration = if (item.isChecked) {
                                TextDecoration.LineThrough
                            } else {
                                TextDecoration.None
                            }
                        ),
                        colors = transparentFieldColors,
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    val canRemove = items.size > 1 && (item.text.isNotBlank() || index < items.lastIndex)
                    if (canRemove) {
                        IconButton(onClick = { onRemoveItem(index) }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Rimuovi elemento",
                                tint = contentColor.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onAddItem)
                .padding(vertical = 8.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CheckBoxOutlineBlank,
                contentDescription = null,
                tint = contentColor.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "Aggiungi elemento",
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor.copy(alpha = 0.7f)
            )
        }
    }
}

private fun formatReminder(timestamp: Long): String {
    val formatter = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    return formatter.format(Date(timestamp))
}

private fun hasShareableContent(uiState: NoteDetailUiState): Boolean {
    return uiState.title.isNotBlank() ||
        uiState.content.isNotBlank() ||
        uiState.checklistItems.any { it.text.isNotBlank() } ||
        uiState.imageUris.isNotEmpty() ||
        !uiState.audioUri.isNullOrBlank()
}

