package com.notepad.app.ui.screens

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.notepad.app.data.preferences.ThemeMode
import com.notepad.app.security.LockRepository
import javax.crypto.Cipher

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentTheme: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    lockEnabled: Boolean,
    biometricEnabled: Boolean,
    securityBusy: Boolean,
    securityMessage: String?,
    onClearSecurityMessage: () -> Unit,
    onSetupPassword: (password: String, confirm: String) -> Unit,
    onChangePassword: (current: String, newPassword: String, confirm: String) -> Unit,
    onRemovePassword: (password: String) -> Unit,
    onCreateBiometricEncryptCipher: () -> Cipher?,
    onEnableBiometric: (Cipher) -> Unit,
    onDisableBiometric: () -> Unit,
    onArchivedNotesClick: () -> Unit,
    onTrashClick: () -> Unit,
    onLabelsClick: () -> Unit,
    onBack: () -> Unit
) {
    var showSetupDialog by remember { mutableStateOf(false) }
    var showChangeDialog by remember { mutableStateOf(false) }
    var showRemoveDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val biometricAvailable = remember {
        BiometricManager.from(context)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    LaunchedEffect(securityMessage) {
        // Message shown inline; auto-clear handled by dismiss button
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Impostazioni") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Indietro"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            SettingsNavRow(
                icon = { Icon(Icons.Default.Archive, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                title = "Note archiviate",
                onClick = onArchivedNotesClick
            )
            HorizontalDivider()
            SettingsNavRow(
                icon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                title = "Cestino",
                onClick = onTrashClick
            )
            HorizontalDivider()
            SettingsNavRow(
                icon = { Icon(Icons.Default.Label, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                title = "Gestisci etichette",
                onClick = onLabelsClick
            )
            HorizontalDivider()

            Text(
                text = "Sicurezza",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
            )
            Text(
                text = "Con il blocco attivo le note sono crittografate sul dispositivo. " +
                    "Screenshot e registrazione schermo sono bloccati.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (!lockEnabled) {
                Button(
                    onClick = { showSetupDialog = true },
                    enabled = !securityBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null)
                    Spacer(modifier = Modifier.padding(4.dp))
                    Text("Imposta password")
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Blocco app", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Attivo — note crittografate",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(Icons.Default.Lock, contentDescription = null)
                }
                TextButton(
                    onClick = { showChangeDialog = true },
                    enabled = !securityBusy
                ) { Text("Cambia password") }
                TextButton(
                    onClick = { showRemoveDialog = true },
                    enabled = !securityBusy
                ) { Text("Rimuovi blocco") }

                if (biometricAvailable) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Sblocco biometrico", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Usa impronta o volto; la password resta disponibile",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = biometricEnabled,
                            enabled = !securityBusy,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    enrollBiometric(
                                        context = context as? FragmentActivity,
                                        createCipher = onCreateBiometricEncryptCipher,
                                        onSuccess = onEnableBiometric
                                    )
                                } else {
                                    onDisableBiometric()
                                }
                            }
                        )
                    }
                }
            }

            if (securityBusy) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.height(28.dp))
                }
            }

            securityMessage?.let { msg ->
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onClearSecurityMessage)
                        .padding(vertical = 8.dp)
                )
            }

            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

            Text(
                text = "Privacy",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
            )
            Text(
                text = "Nessuna telemetria o analytics. I dati restano solo sul dispositivo. " +
                    "L'OCR (se usato) elabora le immagini on-device tramite ML Kit.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            HorizontalDivider()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup()
            ) {
                Text(
                    text = "Tema",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
                ThemeMode.entries.forEach { mode ->
                    ThemeOptionRow(
                        label = mode.label,
                        selected = currentTheme == mode,
                        onClick = { onThemeChange(mode) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showSetupDialog) {
        PasswordSetupDialog(
            title = "Imposta password",
            showCurrent = false,
            onDismiss = { showSetupDialog = false },
            onConfirm = { _, password, confirm ->
                onSetupPassword(password, confirm)
                showSetupDialog = false
            }
        )
    }
    if (showChangeDialog) {
        PasswordSetupDialog(
            title = "Cambia password",
            showCurrent = true,
            onDismiss = { showChangeDialog = false },
            onConfirm = { current, password, confirm ->
                onChangePassword(current, password, confirm)
                showChangeDialog = false
            }
        )
    }
    if (showRemoveDialog) {
        PasswordConfirmDialog(
            title = "Rimuovi blocco",
            message = "Le note torneranno in chiaro sul dispositivo. Conferma con la password.",
            onDismiss = { showRemoveDialog = false },
            onConfirm = { password ->
                onRemovePassword(password)
                showRemoveDialog = false
            }
        )
    }
}

private fun enrollBiometric(
    context: FragmentActivity?,
    createCipher: () -> Cipher?,
    onSuccess: (Cipher) -> Unit
) {
    val activity = context ?: return
    val cipher = createCipher() ?: return
    val executor = ContextCompat.getMainExecutor(activity)
    val prompt = BiometricPrompt(
        activity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                result.cryptoObject?.cipher?.let(onSuccess)
            }
        }
    )
    prompt.authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("Attiva biometria")
            .setSubtitle("Conferma per abilitare lo sblocco biometrico")
            .setNegativeButtonText("Annulla")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build(),
        BiometricPrompt.CryptoObject(cipher)
    )
}

@Composable
private fun PasswordSetupDialog(
    title: String,
    showCurrent: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (current: String, password: String, confirm: String) -> Unit
) {
    var current by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (showCurrent) {
                    OutlinedTextField(
                        value = current,
                        onValueChange = { current = it },
                        label = { Text("Password attuale") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Nuova password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = { Text("Conferma password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(current, password, confirm) },
                enabled = password.length >= LockRepository.MIN_PASSWORD_LENGTH
            ) { Text("Conferma") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annulla") }
        }
    )
}

@Composable
private fun PasswordConfirmDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: (password: String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(message, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(password) },
                enabled = password.isNotEmpty()
            ) { Text("Rimuovi") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annulla") }
        }
    )
}

@Composable
private fun SettingsNavRow(
    icon: @Composable () -> Unit,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            icon()
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ThemeOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton
            )
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = null)
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}
