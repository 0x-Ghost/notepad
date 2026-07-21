package com.notepad.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.notepad.app.data.model.Label
import com.notepad.app.data.model.LabelColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabelsScreen(
    labels: List<Label>,
    onAddLabel: (String, Int) -> Unit,
    onUpdateLabel: (Label, String, Int) -> Unit,
    onDeleteLabel: (Label) -> Unit,
    onBack: () -> Unit
) {
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var editingLabel by remember { mutableStateOf<Label?>(null) }
    var newLabelName by rememberSaveable { mutableStateOf("") }
    var selectedColor by rememberSaveable { mutableIntStateOf(LabelColors.DEFAULT) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Etichette") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                newLabelName = ""
                selectedColor = LabelColors.palette.random()
                showCreateDialog = true
            }) {
                Icon(Icons.Default.Add, contentDescription = "Aggiungi etichetta")
            }
        }
    ) { innerPadding ->
        if (labels.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Nessuna etichetta", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "Tocca + per crearne una",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(labels, key = { it.id }) { label ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                editingLabel = label
                                newLabelName = label.name
                                selectedColor = label.color
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = LabelColors.toComposeColor(label.color),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            Text(
                                text = label.name,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        IconButton(onClick = {
                            editingLabel = label
                            newLabelName = label.name
                            selectedColor = label.color
                        }) {
                            Icon(Icons.Default.Edit, contentDescription = "Modifica")
                        }
                        IconButton(onClick = { onDeleteLabel(label) }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Elimina",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        LabelDialog(
            title = "Nuova etichetta",
            name = newLabelName,
            selectedColor = selectedColor,
            confirmLabel = "Crea",
            onNameChange = { newLabelName = it },
            onColorChange = { selectedColor = it },
            onConfirm = {
                onAddLabel(newLabelName, selectedColor)
                newLabelName = ""
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false }
        )
    }

    editingLabel?.let { label ->
        LabelDialog(
            title = "Modifica etichetta",
            name = newLabelName,
            selectedColor = selectedColor,
            confirmLabel = "Salva",
            onNameChange = { newLabelName = it },
            onColorChange = { selectedColor = it },
            onConfirm = {
                onUpdateLabel(label, newLabelName, selectedColor)
                editingLabel = null
            },
            onDismiss = { editingLabel = null }
        )
    }
}

@Composable
private fun LabelDialog(
    title: String,
    name: String,
    selectedColor: Int,
    confirmLabel: String,
    onNameChange: (String) -> Unit,
    onColorChange: (Int) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextField(
                    value = name,
                    onValueChange = onNameChange,
                    placeholder = { Text("Nome etichetta") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Colore", style = MaterialTheme.typography.labelMedium)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LabelColors.palette.forEach { colorArgb ->
                        val color = LabelColors.toComposeColor(colorArgb)
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
                                .clickable { onColorChange(colorArgb) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = name.isNotBlank()) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annulla") }
        }
    )
}
