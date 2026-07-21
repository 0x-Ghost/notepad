package com.notepad.app.security

import android.content.Context
import android.os.Build
import android.util.Log
import com.notepad.app.data.database.NoteDatabase
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File
import java.io.IOException

/**
 * Opens Room as plaintext SQLite or SQLCipher depending on lock state.
 * Migrates an existing plaintext DB to/from SQLCipher via sqlcipher_export.
 *
 * SQLCipher passphrase = raw DEK bytes (same bytes passed to [SupportOpenHelperFactory]).
 * Media AES-GCM still uses the raw DEK bytes.
 */
object SecureDatabaseFactory {

    private const val TAG = "SecureDatabaseFactory"

    init {
        System.loadLibrary("sqlcipher")
    }

    fun open(context: Context, dek: ByteArray?): NoteDatabase {
        ensureDatabasesDir(context)
        val dbPath = context.getDatabasePath(NoteDatabase.DATABASE_NAME).absolutePath
        val factory = if (dek != null) {
            SupportOpenHelperFactory(sqlCipherPassphraseBytes(dek))
        } else {
            null
        }
        return NoteDatabase.buildDatabase(context, dbPath, factory)
    }

    private fun sqlCipherPassphraseBytes(dek: ByteArray): ByteArray = dek.copyOf()

    /**
     * If a previous failed migration left a broken file, remove it so Room can recreate.
     * Only safe when app lock is NOT enabled.
     */
    fun repairPlaintextIfNeeded(context: Context) {
        val dbFile = context.getDatabasePath(NoteDatabase.DATABASE_NAME)
        if (!dbFile.exists()) {
            deleteSidecars(dbFile)
            restoreBackupIfPresent(dbFile)
            return
        }
        if (dbFile.length() == 0L) {
            Log.w(TAG, "Removing empty database file")
            deleteDatabaseFiles(dbFile)
            return
        }
        val opened = runCatching {
            openFrameworkDb(dbFile, readOnly = true).use { it.version }
            true
        }.getOrDefault(false)
        if (!opened) {
            Log.w(TAG, "Database unreadable as plaintext — moving aside and restoring backup if any")
            val broken = File(dbFile.parentFile, "${dbFile.name}.broken")
            deleteDatabaseFiles(broken)
            dbFile.renameTo(broken) || run {
                dbFile.copyTo(broken, overwrite = true)
                deleteDatabaseFiles(dbFile)
                true
            }
            deleteSidecars(dbFile)
            restoreBackupIfPresent(dbFile)
        }
    }

    fun rollbackEncryptionMigration(context: Context) {
        val dbFile = context.getDatabasePath(NoteDatabase.DATABASE_NAME)
        val backup = migrationBackupFile(dbFile)
        if (!backup.exists() || backup.length() == 0L) {
            Log.w(TAG, "No migration backup to restore")
            return
        }
        Log.i(TAG, "Restoring database from migration backup")
        deleteDatabaseFiles(dbFile)
        if (!backup.renameTo(dbFile)) {
            backup.copyTo(dbFile, overwrite = true)
            deleteDatabaseFiles(backup)
        }
        deleteSidecars(dbFile)
    }

    fun clearMigrationBackup(context: Context) {
        deleteDatabaseFiles(migrationBackupFile(context.getDatabasePath(NoteDatabase.DATABASE_NAME)))
    }

    /**
     * Encrypts an existing plaintext database file in place using [dek].
     * Must be called while Room is closed.
     *
     * Uses the Zetetic-recommended flow: open plaintext as main, attach encrypted target,
     * export with sqlcipher_export('encrypted').
     */
    fun migratePlaintextToEncrypted(context: Context, dek: ByteArray): Int {
        ensureDatabasesDir(context)
        val dbFile = context.getDatabasePath(NoteDatabase.DATABASE_NAME)
        if (!dbFile.exists()) {
            deleteSidecars(dbFile)
            return 0
        }

        checkpointPlaintextOrThrow(dbFile)
        val plaintextNoteCount = countFrameworkRows(dbFile, "notes")
        val version = openFrameworkVersion(dbFile)
        val passphrase = sqlCipherPassphraseBytes(dek)

        val encryptedTemp = File(context.cacheDir, "notepad_enc_${System.currentTimeMillis()}.db")
        deleteDatabaseFiles(encryptedTemp)

        val plainDb = SQLiteDatabase.openDatabase(
            dbFile.absolutePath,
            "",
            null,
            SQLiteDatabase.OPEN_READWRITE,
            null
        )
        try {
            attachEncryptedDatabase(plainDb, encryptedTemp, passphrase)
            plainDb.rawExecSQL("SELECT sqlcipher_export('encrypted')")
            plainDb.rawExecSQL("DETACH DATABASE encrypted")
        } catch (e: Exception) {
            plainDb.close()
            deleteDatabaseFiles(encryptedTemp)
            throw IOException("Crittografia database fallita: ${e.message}", e)
        }
        plainDb.close()

        SQLiteDatabase.openDatabase(
            encryptedTemp.absolutePath,
            passphrase,
            null,
            SQLiteDatabase.OPEN_READWRITE,
            null
        ).use { encrypted ->
            encrypted.version = version
        }

        if (!encryptedTemp.exists() || encryptedTemp.length() == 0L) {
            deleteDatabaseFiles(encryptedTemp)
            throw IOException("Crittografia database fallita: file cifrato non creato")
        }

        val encryptedNoteCount = countSqlCipherRows(encryptedTemp, passphrase, "notes")
        if (plaintextNoteCount > 0 && encryptedNoteCount < plaintextNoteCount) {
            deleteDatabaseFiles(encryptedTemp)
            throw IOException(
                "Crittografia database fallita: note perse durante la migrazione " +
                    "($encryptedNoteCount/$plaintextNoteCount)"
            )
        }

        verifyEncryptedDatabaseReadable(encryptedTemp, passphrase, version)

        replaceDatabaseFile(dbFile, encryptedTemp, keepBackup = true)
        return plaintextNoteCount
    }

    fun verifyEncryptedDatabaseReadable(context: Context, dek: ByteArray): Int {
        val dbFile = context.getDatabasePath(NoteDatabase.DATABASE_NAME)
        val passphrase = sqlCipherPassphraseBytes(dek)
        return countSqlCipherRows(dbFile, passphrase, "notes").also {
            verifyEncryptedDatabaseReadable(dbFile, passphrase, openSqlCipherVersion(dbFile, passphrase))
        }
    }

    fun migrateEncryptedToPlaintext(context: Context, dek: ByteArray) {
        ensureDatabasesDir(context)
        val dbFile = context.getDatabasePath(NoteDatabase.DATABASE_NAME)
        if (!dbFile.exists()) return

        val passphrase = sqlCipherPassphraseBytes(dek)
        val plainTemp = File.createTempFile("notepad_plain_", ".db", context.cacheDir)

        val encDb = SQLiteDatabase.openDatabase(
            dbFile.absolutePath,
            passphrase,
            null,
            SQLiteDatabase.OPEN_READWRITE,
            null
        )
        val version: Int
        try {
            version = encDb.version
            encDb.rawExecSQL("PRAGMA wal_checkpoint(FULL)")

            val attach = encDb.compileStatement("ATTACH DATABASE ? AS plaintext KEY ''")
            attach.bindString(1, plainTemp.absolutePath)
            attach.execute()
            attach.close()

            encDb.rawExecSQL("SELECT sqlcipher_export('plaintext')")
            encDb.rawExecSQL("DETACH DATABASE plaintext")
        } catch (e: Exception) {
            encDb.close()
            plainTemp.delete()
            throw IOException("Decrittografia database fallita: ${e.message}", e)
        }
        encDb.close()

        if (!plainTemp.exists() || plainTemp.length() == 0L) {
            plainTemp.delete()
            throw IOException("Decrittografia database fallita: file in chiaro non creato")
        }

        SQLiteDatabase.openDatabase(
            plainTemp.absolutePath,
            "",
            null,
            SQLiteDatabase.OPEN_READWRITE,
            null
        ).use { plainDb ->
            plainDb.version = version
        }

        replaceDatabaseFile(dbFile, plainTemp, keepBackup = false)
    }

    private fun attachEncryptedDatabase(
        plainDb: SQLiteDatabase,
        encryptedFile: File,
        passphrase: ByteArray
    ) {
        try {
            val attach = plainDb.compileStatement("ATTACH DATABASE ? AS encrypted KEY ?")
            attach.bindString(1, encryptedFile.absolutePath)
            attach.bindBlob(2, passphrase)
            attach.execute()
            attach.close()
        } catch (blobError: Exception) {
            Log.w(TAG, "ATTACH with bindBlob failed, retrying with hex key", blobError)
            val hexKey = passphrase.joinToString("") { byte -> "%02x".format(byte) }
            plainDb.execSQL(
                "ATTACH DATABASE '${encryptedFile.absolutePath.replace("'", "''")}' " +
                    "AS encrypted KEY \"x'$hexKey'\""
            )
        }
    }

    private fun verifyEncryptedDatabaseReadable(dbFile: File, passphrase: ByteArray, expectedVersion: Int) {
        SQLiteDatabase.openDatabase(
            dbFile.absolutePath,
            passphrase,
            null,
            SQLiteDatabase.OPEN_READONLY,
            null
        ).use { db ->
            if (db.version != expectedVersion) {
                throw IOException("Versione database cifrato non valida")
            }
            db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='notes'", null).use { cursor ->
                if (!cursor.moveToFirst()) {
                    throw IOException("Schema database cifrato non valido")
                }
            }
        }
    }

    private fun openFrameworkVersion(dbFile: File): Int =
        openFrameworkDb(dbFile, readOnly = true).use { it.version }

    private fun openSqlCipherVersion(dbFile: File, passphrase: ByteArray): Int =
        SQLiteDatabase.openDatabase(
            dbFile.absolutePath,
            passphrase,
            null,
            SQLiteDatabase.OPEN_READONLY,
            null
        ).use { it.version }

    /**
     * Room uses Android framework SQLite (WAL). Merge WAL into the main file with the same engine
     * before SQLCipher reads the database for export. Never delete WAL files unless this succeeds.
     */
    private fun checkpointPlaintextOrThrow(dbFile: File) {
        openFrameworkDb(dbFile, readOnly = false).use { db ->
            db.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { cursor ->
                if (!cursor.moveToFirst()) {
                    throw IOException("Checkpoint WAL fallito")
                }
                val busy = cursor.getInt(0)
                val log = cursor.getInt(1)
                val checkpointed = cursor.getInt(2)
                Log.d(TAG, "wal_checkpoint(FULL): busy=$busy log=$log checkpointed=$checkpointed")
                if (busy != 0) {
                    throw IOException("Database occupato durante il checkpoint WAL")
                }
            }
        }
        deleteSidecars(dbFile)
    }

    private fun countFrameworkRows(dbFile: File, table: String): Int {
        if (!dbFile.exists() || dbFile.length() == 0L) return 0
        return openFrameworkDb(dbFile, readOnly = true).use { db ->
            if (!tableExistsFramework(db, table)) return 0
            db.rawQuery("SELECT COUNT(*) FROM $table", null).use { cursor ->
                if (!cursor.moveToFirst()) {
                    throw IOException("Impossibile contare le righe in $table")
                }
                cursor.getInt(0)
            }
        }
    }

    private fun countSqlCipherRows(dbFile: File, passphrase: ByteArray, table: String): Int {
        if (!dbFile.exists() || dbFile.length() == 0L) return 0
        return SQLiteDatabase.openDatabase(
            dbFile.absolutePath,
            passphrase,
            null,
            SQLiteDatabase.OPEN_READONLY,
            null
        ).use { db ->
            if (!tableExistsSqlCipher(db, table)) return 0
            db.rawQuery("SELECT COUNT(*) FROM $table", null).use { cursor ->
                if (!cursor.moveToFirst()) {
                    throw IOException("Impossibile contare le righe cifrate in $table")
                }
                cursor.getInt(0)
            }
        }
    }

    private fun tableExistsFramework(
        db: android.database.sqlite.SQLiteDatabase,
        table: String
    ): Boolean = db.rawQuery(
        "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
        arrayOf(table)
    ).use { it.moveToFirst() }

    private fun tableExistsSqlCipher(db: SQLiteDatabase, table: String): Boolean = db.rawQuery(
        "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
        arrayOf(table)
    ).use { it.moveToFirst() }

    private fun openFrameworkDb(
        dbFile: File,
        readOnly: Boolean
    ): android.database.sqlite.SQLiteDatabase {
        val flags = if (readOnly) {
            android.database.sqlite.SQLiteDatabase.OPEN_READONLY
        } else {
            android.database.sqlite.SQLiteDatabase.OPEN_READWRITE
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            android.database.sqlite.SQLiteDatabase.openDatabase(
                dbFile,
                android.database.sqlite.SQLiteDatabase.OpenParams.Builder()
                    .setOpenFlags(flags)
                    .build()
            )
        } else {
            @Suppress("DEPRECATION")
            android.database.sqlite.SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                null,
                flags
            )
        }
    }

    private fun migrationBackupFile(dbFile: File): File =
        File(dbFile.parentFile, "${dbFile.name}.bak")

    private fun restoreBackupIfPresent(dbFile: File) {
        val backup = migrationBackupFile(dbFile)
        if (backup.exists() && backup.length() > 0L) {
            runCatching {
                backup.copyTo(dbFile, overwrite = true)
                Log.i(TAG, "Restored database from backup")
            }
        }
    }

    private fun ensureDatabasesDir(context: Context) {
        val dir = context.getDatabasePath(NoteDatabase.DATABASE_NAME).parentFile ?: return
        if (!dir.exists()) dir.mkdirs()
    }

    private fun replaceDatabaseFile(dbFile: File, newFile: File, keepBackup: Boolean) {
        val parent = dbFile.parentFile
            ?: throw IOException("Directory database non disponibile")
        val backup = migrationBackupFile(dbFile)
        deleteDatabaseFiles(backup)
        backup.delete()

        val movedAside = if (dbFile.exists()) {
            if (dbFile.renameTo(backup)) {
                true
            } else {
                dbFile.copyTo(backup, overwrite = true)
                deleteDatabaseFiles(dbFile)
                true
            }
        } else {
            false
        }
        deleteSidecars(dbFile)

        try {
            if (!newFile.renameTo(dbFile)) {
                newFile.copyTo(dbFile, overwrite = true)
                newFile.delete()
            }
            if (!dbFile.exists() || dbFile.length() == 0L) {
                throw IOException("Sostituzione database fallita")
            }
            if (movedAside && !keepBackup) {
                deleteDatabaseFiles(backup)
            }
        } catch (e: Exception) {
            if (movedAside && backup.exists()) {
                deleteDatabaseFiles(dbFile)
                if (!backup.renameTo(dbFile)) {
                    backup.copyTo(dbFile, overwrite = true)
                }
            }
            throw e
        }
    }

    private fun deleteDatabaseFiles(dbFile: File) {
        dbFile.delete()
        deleteSidecars(dbFile)
    }

    private fun deleteSidecars(dbFile: File) {
        File(dbFile.path + "-shm").delete()
        File(dbFile.path + "-wal").delete()
        File(dbFile.path + "-journal").delete()
    }
}
