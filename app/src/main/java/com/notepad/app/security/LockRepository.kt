package com.notepad.app.security

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.crypto.Cipher

/**
 * Manages app lock state: password setup/change/remove, biometric wrap, session unlock, DEK.
 *
 * Callbacks [onDatabaseOpen] / [onDatabaseClose] let the Application open/close the Room database.
 */
class LockRepository(
    context: Context,
    private val preferences: LockPreferences = LockPreferences(context),
    private val mediaCryptoHelper: MediaCryptoHelper = MediaCryptoHelper(context),
    private val onDatabaseOpen: (dek: ByteArray?) -> Unit,
    private val onDatabaseClose: () -> Unit
) {
    private val appContext = context.applicationContext

    private val _lockEnabled = MutableStateFlow(preferences.isLockEnabled)
    val lockEnabled: StateFlow<Boolean> = _lockEnabled.asStateFlow()

    private val _biometricEnabled = MutableStateFlow(preferences.isBiometricEnabled)
    val biometricEnabled: StateFlow<Boolean> = _biometricEnabled.asStateFlow()

    private val _isUnlocked = MutableStateFlow(!preferences.isLockEnabled)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private val _databaseMigrating = MutableStateFlow(false)
    val databaseMigrating: StateFlow<Boolean> = _databaseMigrating.asStateFlow()

    private var sessionDek: ByteArray? = null

    /** Background timestamp for relock timeout (0 = not in background). */
    var wentToBackgroundAt: Long = 0L

    fun currentDek(): ByteArray? = sessionDek

    fun isLockEnabled(): Boolean = preferences.isLockEnabled

    fun isSessionUnlocked(): Boolean = _isUnlocked.value

    /**
     * Opens DB for cold start: plaintext if no lock; otherwise leaves locked until unlock.
     */
    fun initializeOnAppStart() {
        if (!preferences.isLockEnabled) {
            sessionDek = null
            _isUnlocked.value = true
            onDatabaseOpen(null)
        } else {
            sessionDek = null
            _isUnlocked.value = false
            // DB stays closed until unlock
        }
    }

    suspend fun setupPassword(password: String) = withContext(Dispatchers.IO) {
        require(password.length >= 4) { "Password too short" }
        require(!preferences.isLockEnabled) { "Lock already enabled" }

        _databaseMigrating.value = true
        try {
            onDatabaseClose()

            val dek = CryptoManager.generateDek()
            val salt = CryptoManager.generateSalt()
            val kek = CryptoManager.deriveKek(password.toCharArray(), salt)
            val wrapped = CryptoManager.wrapKey(dek, kek)

            try {
                val notesBefore = SecureDatabaseFactory.migratePlaintextToEncrypted(appContext, dek)
                mediaCryptoHelper.encryptAllMedia(dek)

                sessionDek = dek
                onDatabaseOpen(dek)

                val notesAfter = SecureDatabaseFactory.verifyEncryptedDatabaseReadable(appContext, dek)
                if (notesBefore > 0 && notesAfter < notesBefore) {
                    throw IllegalStateException(
                        "Verifica database fallita: note mancanti ($notesAfter/$notesBefore)"
                    )
                }

                val saved = preferences.saveLockCredentials(
                    saltBase64 = CryptoManager.toBase64(salt),
                    wrappedDekBase64 = CryptoManager.toBase64(wrapped),
                    iterations = CryptoManager.PBKDF2_ITERATIONS
                )
                if (!saved) {
                    throw IllegalStateException("Impossibile salvare le credenziali di blocco")
                }

                SecureDatabaseFactory.clearMigrationBackup(appContext)
                _lockEnabled.value = true
                _biometricEnabled.value = false
                _isUnlocked.value = true
            } catch (e: Exception) {
                SecureDatabaseFactory.rollbackEncryptionMigration(appContext)
                sessionDek = null
                runCatching { onDatabaseOpen(null) }
                throw IllegalStateException(
                    e.message?.takeIf { it.isNotBlank() }
                        ?: "Impossibile attivare il blocco. Riprova.",
                    e
                )
            } finally {
                CryptoManager.wipe(kek)
            }
        } finally {
            _databaseMigrating.value = false
        }
    }

    suspend fun changePassword(currentPassword: String, newPassword: String) = withContext(Dispatchers.IO) {
        require(newPassword.length >= 4) { "Password too short" }
        val dek = unwrapWithPassword(currentPassword)
        val salt = CryptoManager.generateSalt()
        val kek = CryptoManager.deriveKek(newPassword.toCharArray(), salt)
        try {
            val wrapped = CryptoManager.wrapKey(dek, kek)
            val saved = preferences.savePasswordWrap(
                saltBase64 = CryptoManager.toBase64(salt),
                wrappedDekBase64 = CryptoManager.toBase64(wrapped),
                iterations = CryptoManager.PBKDF2_ITERATIONS
            )
            if (!saved) {
                throw IllegalStateException("Impossibile salvare la nuova password")
            }
            // Invalidate biometric wrap — must re-enroll
            if (preferences.isBiometricEnabled) {
                preferences.isBiometricEnabled = false
                preferences.biometricIvBase64 = null
                preferences.biometricWrappedDekBase64 = null
                CryptoManager.deleteBiometricKey()
                _biometricEnabled.value = false
            }
            if (!_isUnlocked.value) {
                activateSession(dek)
            } else {
                CryptoManager.wipe(dek)
            }
        } finally {
            CryptoManager.wipe(kek)
        }
    }

    suspend fun removePassword(password: String) = withContext(Dispatchers.IO) {
        val dek = unwrapWithPassword(password)
        _databaseMigrating.value = true
        try {
            onDatabaseClose()
            SecureDatabaseFactory.migrateEncryptedToPlaintext(appContext, dek)
            mediaCryptoHelper.decryptAllMedia(dek)

            preferences.clearAll()
            CryptoManager.deleteBiometricKey()

            sessionDek = null
            onDatabaseOpen(null)
            _lockEnabled.value = false
            _biometricEnabled.value = false
            _isUnlocked.value = true
        } finally {
            CryptoManager.wipe(dek)
            _databaseMigrating.value = false
        }
    }

    suspend fun unlockWithPassword(password: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val dek = unwrapWithPassword(password)
            activateSession(dek)
            true
        } catch (e: Exception) {
            Log.e(TAG, "unlockWithPassword failed", e)
            false
        }
    }

    fun prepareBiometricDecryptCipher(): Cipher? {
        if (!preferences.isBiometricEnabled) return null
        val ivB64 = preferences.biometricIvBase64 ?: return null
        return try {
            CryptoManager.createBiometricDecryptCipher(CryptoManager.fromBase64(ivB64))
        } catch (_: Exception) {
            null
        }
    }

    suspend fun unlockWithBiometricCipher(cipher: Cipher): Boolean = withContext(Dispatchers.IO) {
        try {
            val wrappedB64 = preferences.biometricWrappedDekBase64 ?: return@withContext false
            val dek = CryptoManager.unwrapDekWithBiometricCipher(
                cipher,
                CryptoManager.fromBase64(wrappedB64)
            )
            activateSession(dek)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Enables biometric unlock by wrapping the current session DEK.
     * [authenticatedEncryptCipher] must come from BiometricPrompt CryptoObject after user auth.
     */
    suspend fun enableBiometric(authenticatedEncryptCipher: Cipher): Boolean = withContext(Dispatchers.IO) {
        val dek = sessionDek ?: return@withContext false
        if (!preferences.isLockEnabled) return@withContext false
        try {
            val (iv, encrypted) = CryptoManager.wrapDekWithBiometricCipher(authenticatedEncryptCipher, dek)
            preferences.biometricIvBase64 = CryptoManager.toBase64(iv)
            preferences.biometricWrappedDekBase64 = CryptoManager.toBase64(encrypted)
            preferences.isBiometricEnabled = true
            _biometricEnabled.value = true
            true
        } catch (_: Exception) {
            false
        }
    }

    fun disableBiometric() {
        preferences.isBiometricEnabled = false
        preferences.biometricIvBase64 = null
        preferences.biometricWrappedDekBase64 = null
        CryptoManager.deleteBiometricKey()
        _biometricEnabled.value = false
    }

    fun lockSession() {
        if (!preferences.isLockEnabled) return
        if (!_isUnlocked.value) return
        mediaCryptoHelper.clearDecryptCache()
        onDatabaseClose()
        CryptoManager.wipe(sessionDek)
        sessionDek = null
        _isUnlocked.value = false
        wentToBackgroundAt = 0L
    }

    fun shouldRelock(now: Long = System.currentTimeMillis(), timeoutMs: Long = RELOCK_TIMEOUT_MS): Boolean {
        if (!preferences.isLockEnabled || !_isUnlocked.value) return false
        val bg = wentToBackgroundAt
        if (bg <= 0L) return false
        return now - bg >= timeoutMs
    }

    fun pathForMediaRead(path: String?): String? =
        mediaCryptoHelper.pathForRead(path, sessionDek)

    fun encryptNewMediaIfNeeded(path: String?) {
        mediaCryptoHelper.encryptIfNeeded(path, sessionDek)
    }

    private fun activateSession(dek: ByteArray) {
        _databaseMigrating.value = true
        try {
            onDatabaseClose()
            sessionDek = dek
            onDatabaseOpen(dek)
            _isUnlocked.value = true
            wentToBackgroundAt = 0L
        } finally {
            _databaseMigrating.value = false
        }
        // Reschedule reminders now that DB is readable (e.g. after boot with lock).
        runCatching {
            val app = appContext as? com.notepad.app.NotepadApplication ?: return@runCatching
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                val notes = app.requireDatabase().noteDao().getNotesWithFutureReminders()
                app.reminderScheduler.rescheduleAll(notes)
            }
        }
    }

    private fun unwrapWithPassword(password: String): ByteArray {
        val saltB64 = preferences.saltBase64 ?: error("Missing salt")
        val wrappedB64 = preferences.wrappedDekBase64 ?: error("Missing wrapped DEK")
        val salt = CryptoManager.fromBase64(saltB64)
        val wrapped = CryptoManager.fromBase64(wrappedB64)
        val kek = CryptoManager.deriveKek(
            password.toCharArray(),
            salt,
            preferences.pbkdf2Iterations
        )
        return try {
            CryptoManager.unwrapKey(wrapped, kek)
        } finally {
            CryptoManager.wipe(kek)
        }
    }

    fun createBiometricEncryptCipher(): Cipher =
        CryptoManager.createBiometricEncryptCipher()

    companion object {
        private const val TAG = "LockRepository"
        const val RELOCK_TIMEOUT_MS = 15_000L
        const val MIN_PASSWORD_LENGTH = 4
    }
}
