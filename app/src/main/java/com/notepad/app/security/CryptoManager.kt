package com.notepad.app.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * PBKDF2 key derivation, DEK wrap/unwrap, and AES-GCM helpers for DB passphrase and media.
 */
object CryptoManager {

    const val PBKDF2_ITERATIONS = 600_000
    private const val DEK_BYTES = 32
    private const val SALT_BYTES = 16
    private const val GCM_IV_BYTES = 12
    private const val GCM_TAG_BITS = 128
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val BIOMETRIC_KEY_ALIAS = "notepad_biometric_dek_wrap"

    /** Magic header for encrypted media files: NENC + version. */
    val MEDIA_MAGIC: ByteArray = byteArrayOf('N'.code.toByte(), 'E'.code.toByte(), 'N'.code.toByte(), 'C'.code.toByte(), 1)

    fun generateDek(): ByteArray = ByteArray(DEK_BYTES).also { SecureRandom().nextBytes(it) }

    fun generateSalt(): ByteArray = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }

    fun deriveKek(password: CharArray, salt: ByteArray, iterations: Int = PBKDF2_ITERATIONS): ByteArray {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password, salt, iterations, DEK_BYTES * 8)
        return try {
            factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    /** AES-GCM wrap: IV || ciphertext+tag */
    fun wrapKey(plaintextKey: ByteArray, wrappingKey: ByteArray): ByteArray {
        val iv = ByteArray(GCM_IV_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(wrappingKey, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        val encrypted = cipher.doFinal(plaintextKey)
        return iv + encrypted
    }

    fun unwrapKey(wrapped: ByteArray, wrappingKey: ByteArray): ByteArray {
        require(wrapped.size > GCM_IV_BYTES) { "Invalid wrapped key" }
        val iv = wrapped.copyOfRange(0, GCM_IV_BYTES)
        val ciphertext = wrapped.copyOfRange(GCM_IV_BYTES, wrapped.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(wrappingKey, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    fun toBase64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    fun fromBase64(value: String): ByteArray = Base64.decode(value, Base64.NO_WRAP)

    fun isEncryptedMedia(file: File): Boolean {
        if (!file.exists() || file.length() < MEDIA_MAGIC.size) return false
        val header = ByteArray(MEDIA_MAGIC.size)
        FileInputStream(file).use { it.read(header) }
        return header.contentEquals(MEDIA_MAGIC)
    }

    /** Encrypt file in place using AES-GCM; output = magic || IV || ciphertext+tag. */
    fun encryptFileInPlace(file: File, dek: ByteArray) {
        if (!file.exists() || isEncryptedMedia(file)) return
        val plain = file.readBytes()
        val iv = ByteArray(GCM_IV_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(dek, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        val encrypted = cipher.doFinal(plain)
        val temp = File(file.parentFile, "${file.name}.tmp")
        FileOutputStream(temp).use { out ->
            out.write(MEDIA_MAGIC)
            out.write(iv)
            out.write(encrypted)
        }
        if (!temp.renameTo(file)) {
            temp.copyTo(file, overwrite = true)
            temp.delete()
        }
        plain.fill(0)
    }

    fun decryptFileInPlace(file: File, dek: ByteArray) {
        if (!file.exists() || !isEncryptedMedia(file)) return
        val all = file.readBytes()
        val offset = MEDIA_MAGIC.size
        val iv = all.copyOfRange(offset, offset + GCM_IV_BYTES)
        val ciphertext = all.copyOfRange(offset + GCM_IV_BYTES, all.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(dek, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        val plain = cipher.doFinal(ciphertext)
        val temp = File(file.parentFile, "${file.name}.tmp")
        FileOutputStream(temp).use { it.write(plain) }
        if (!temp.renameTo(file)) {
            temp.copyTo(file, overwrite = true)
            temp.delete()
        }
        plain.fill(0)
    }

    /** Decrypt encrypted media to a cache file for Coil / MediaPlayer. */
    fun decryptToFile(encrypted: File, destination: File, dek: ByteArray): File {
        require(isEncryptedMedia(encrypted)) { "Not an encrypted media file" }
        val all = encrypted.readBytes()
        val offset = MEDIA_MAGIC.size
        val iv = all.copyOfRange(offset, offset + GCM_IV_BYTES)
        val ciphertext = all.copyOfRange(offset + GCM_IV_BYTES, all.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(dek, "AES"), GCMParameterSpec(GCM_TAG_BITS, iv))
        val plain = cipher.doFinal(ciphertext)
        destination.parentFile?.mkdirs()
        FileOutputStream(destination).use { it.write(plain) }
        plain.fill(0)
        return destination
    }

    // --- Biometric Keystore wrap ---

    fun getOrCreateBiometricSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val existing = keyStore.getKey(BIOMETRIC_KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        val builder = KeyGenParameterSpec.Builder(
            BIOMETRIC_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(
                0,
                KeyProperties.AUTH_BIOMETRIC_STRONG
            )
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(-1)
        }

        keyGenerator.init(builder.build())
        return keyGenerator.generateKey()
    }

    fun createBiometricEncryptCipher(): Cipher {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateBiometricSecretKey())
        return cipher
    }

    fun createBiometricDecryptCipher(iv: ByteArray): Cipher {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateBiometricSecretKey(),
            GCMParameterSpec(GCM_TAG_BITS, iv)
        )
        return cipher
    }

    fun wrapDekWithBiometricCipher(cipher: Cipher, dek: ByteArray): Pair<ByteArray, ByteArray> {
        val encrypted = cipher.doFinal(dek)
        val iv = cipher.iv
        return iv to encrypted
    }

    fun unwrapDekWithBiometricCipher(cipher: Cipher, encryptedDek: ByteArray): ByteArray {
        return cipher.doFinal(encryptedDek)
    }

    fun deleteBiometricKey() {
        runCatching {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
            if (keyStore.containsAlias(BIOMETRIC_KEY_ALIAS)) {
                keyStore.deleteEntry(BIOMETRIC_KEY_ALIAS)
            }
        }
    }

    fun wipe(bytes: ByteArray?) {
        bytes?.fill(0)
    }
}
