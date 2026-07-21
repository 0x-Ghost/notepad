package com.notepad.app.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * Persists lock metadata (salt, wrapped DEK, biometric wrap) in EncryptedSharedPreferences.
 */
class LockPreferences(context: Context) {

    private val prefs: SharedPreferences

    init {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        prefs = EncryptedSharedPreferences.create(
            PREFS_NAME,
            masterKeyAlias,
            context.applicationContext,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    var isLockEnabled: Boolean
        get() = prefs.getBoolean(KEY_LOCK_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_LOCK_ENABLED, value).apply()

    var saltBase64: String?
        get() = prefs.getString(KEY_SALT, null)
        set(value) = prefs.edit().putString(KEY_SALT, value).apply()

    var wrappedDekBase64: String?
        get() = prefs.getString(KEY_WRAPPED_DEK, null)
        set(value) = prefs.edit().putString(KEY_WRAPPED_DEK, value).apply()

    var pbkdf2Iterations: Int
        get() = prefs.getInt(KEY_ITERATIONS, CryptoManager.PBKDF2_ITERATIONS)
        set(value) = prefs.edit().putInt(KEY_ITERATIONS, value).apply()

    var isBiometricEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, value).apply()

    var biometricIvBase64: String?
        get() = prefs.getString(KEY_BIOMETRIC_IV, null)
        set(value) = prefs.edit().putString(KEY_BIOMETRIC_IV, value).apply()

    var biometricWrappedDekBase64: String?
        get() = prefs.getString(KEY_BIOMETRIC_WRAPPED_DEK, null)
        set(value) = prefs.edit().putString(KEY_BIOMETRIC_WRAPPED_DEK, value).apply()

    /**
     * Persists lock credentials synchronously. Must complete before the app restarts.
     */
    fun saveLockCredentials(
        saltBase64: String,
        wrappedDekBase64: String,
        iterations: Int
    ): Boolean {
        return prefs.edit()
            .putString(KEY_SALT, saltBase64)
            .putString(KEY_WRAPPED_DEK, wrappedDekBase64)
            .putInt(KEY_ITERATIONS, iterations)
            .putBoolean(KEY_LOCK_ENABLED, true)
            .putBoolean(KEY_BIOMETRIC_ENABLED, false)
            .remove(KEY_BIOMETRIC_IV)
            .remove(KEY_BIOMETRIC_WRAPPED_DEK)
            .commit()
    }

    fun savePasswordWrap(
        saltBase64: String,
        wrappedDekBase64: String,
        iterations: Int
    ): Boolean {
        return prefs.edit()
            .putString(KEY_SALT, saltBase64)
            .putString(KEY_WRAPPED_DEK, wrappedDekBase64)
            .putInt(KEY_ITERATIONS, iterations)
            .commit()
    }

    fun clearAll() {
        prefs.edit().clear().commit()
    }

    companion object {
        private const val PREFS_NAME = "notepad_lock_prefs"
        private const val KEY_LOCK_ENABLED = "lock_enabled"
        private const val KEY_SALT = "salt"
        private const val KEY_WRAPPED_DEK = "wrapped_dek"
        private const val KEY_ITERATIONS = "pbkdf2_iterations"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_BIOMETRIC_IV = "biometric_iv"
        private const val KEY_BIOMETRIC_WRAPPED_DEK = "biometric_wrapped_dek"
    }
}
