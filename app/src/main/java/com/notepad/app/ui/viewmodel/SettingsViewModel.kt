package com.notepad.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.notepad.app.data.preferences.ThemeMode
import com.notepad.app.data.repository.SettingsRepository
import com.notepad.app.security.LockRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.crypto.Cipher

/** ViewModel per la schermata Impostazioni (tema + sicurezza). */
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val lockRepository: LockRepository
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = settingsRepository.themeMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ThemeMode.SYSTEM
        )

    val lockEnabled: StateFlow<Boolean> = lockRepository.lockEnabled
    val biometricEnabled: StateFlow<Boolean> = lockRepository.biometricEnabled

    private val _securityMessage = MutableStateFlow<String?>(null)
    val securityMessage: StateFlow<String?> = _securityMessage.asStateFlow()

    private val _securityBusy = MutableStateFlow(false)
    val securityBusy: StateFlow<Boolean> = _securityBusy.asStateFlow()

    private val _restartRequired = MutableStateFlow(false)
    val restartRequired: StateFlow<Boolean> = _restartRequired.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(mode)
        }
    }

    fun consumeRestartRequired() {
        _restartRequired.value = false
    }

    fun clearSecurityMessage() {
        _securityMessage.value = null
    }

    fun setupPassword(password: String, confirm: String) {
        if (password != confirm) {
            _securityMessage.value = "Le password non coincidono"
            return
        }
        if (password.length < LockRepository.MIN_PASSWORD_LENGTH) {
            _securityMessage.value = "Password troppo corta (min. ${LockRepository.MIN_PASSWORD_LENGTH})"
            return
        }
        viewModelScope.launch {
            _securityBusy.value = true
            try {
                lockRepository.setupPassword(password)
                _securityMessage.value = "Blocco app attivato"
            } catch (e: Exception) {
                _securityMessage.value = e.message ?: "Errore durante l'attivazione del blocco"
            } finally {
                _securityBusy.value = false
            }
        }
    }

    fun changePassword(current: String, newPassword: String, confirm: String) {
        if (newPassword != confirm) {
            _securityMessage.value = "Le password non coincidono"
            return
        }
        if (newPassword.length < LockRepository.MIN_PASSWORD_LENGTH) {
            _securityMessage.value = "Password troppo corta (min. ${LockRepository.MIN_PASSWORD_LENGTH})"
            return
        }
        viewModelScope.launch {
            _securityBusy.value = true
            try {
                lockRepository.changePassword(current, newPassword)
                _securityMessage.value = "Password aggiornata"
            } catch (e: Exception) {
                _securityMessage.value = e.message ?: "Password attuale non corretta"
            } finally {
                _securityBusy.value = false
            }
        }
    }

    fun removePassword(password: String) {
        viewModelScope.launch {
            _securityBusy.value = true
            try {
                lockRepository.removePassword(password)
                _securityMessage.value = "Blocco app disattivato"
                _restartRequired.value = true
            } catch (e: Exception) {
                _securityMessage.value = e.message ?: "Password non corretta"
            } finally {
                _securityBusy.value = false
            }
        }
    }

    fun createBiometricEncryptCipher(): Cipher? = try {
        lockRepository.createBiometricEncryptCipher()
    } catch (_: Exception) {
        null
    }

    fun enableBiometric(cipher: Cipher) {
        viewModelScope.launch {
            _securityBusy.value = true
            val ok = lockRepository.enableBiometric(cipher)
            _securityMessage.value = if (ok) {
                "Biometria attivata"
            } else {
                "Impossibile attivare la biometria"
            }
            _securityBusy.value = false
        }
    }

    fun disableBiometric() {
        lockRepository.disableBiometric()
        _securityMessage.value = "Biometria disattivata"
    }

    fun encryptNewMedia(path: String?) {
        lockRepository.encryptNewMediaIfNeeded(path)
    }

    fun pathForMediaRead(path: String?): String? =
        lockRepository.pathForMediaRead(path)
}

class SettingsViewModelFactory(
    private val settingsRepository: SettingsRepository,
    private val lockRepository: LockRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            return SettingsViewModel(settingsRepository, lockRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
