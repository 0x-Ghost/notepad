package com.notepad.app

import android.app.Application
import android.util.Log
import com.notepad.app.data.database.NoteDatabase
import com.notepad.app.data.preferences.SettingsDataStore
import com.notepad.app.data.repository.NoteRepository
import com.notepad.app.data.repository.SettingsRepository
import com.notepad.app.reminder.NotificationHelper
import com.notepad.app.reminder.ReminderScheduler
import com.notepad.app.security.LockRepository
import com.notepad.app.security.SecureDatabaseFactory
import com.notepad.app.widget.WidgetUpdateHelper
import java.io.File

class NotepadApplication : Application() {

    @Volatile
    private var databaseInstance: NoteDatabase? = null

    @Volatile
    private var repositoryInstance: NoteRepository? = null

    val settingsRepository by lazy {
        SettingsRepository(SettingsDataStore(this))
    }

    val reminderScheduler by lazy { ReminderScheduler(this) }

    val lockRepository: LockRepository by lazy {
        LockRepository(
            context = this,
            onDatabaseOpen = { dek -> openDatabase(dek) },
            onDatabaseClose = { closeDatabase() }
        )
    }

    val database: NoteDatabase
        get() = requireDatabase()

    val repository: NoteRepository
        get() = repositoryInstance
            ?: error("Database not ready — unlock the app first")

    fun isDatabaseReady(): Boolean = databaseInstance != null

    fun requireDatabase(): NoteDatabase =
        databaseInstance ?: error("Database not ready — unlock the app first")

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannel(this)
        lockRepository.initializeOnAppStart()
    }

    private fun openDatabase(dek: ByteArray?) {
        synchronized(this) {
            closeDatabaseLocked()
            if (dek == null) {
                SecureDatabaseFactory.repairPlaintextIfNeeded(this)
            }
            try {
                bindDatabase(SecureDatabaseFactory.open(this, dek))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open database (dek=${dek != null})", e)
                if (dek == null && !lockRepository.isLockEnabled()) {
                    // Last resort for plaintext mode only — never wipe an encrypted database.
                    SecureDatabaseFactory.repairPlaintextIfNeeded(this)
                    val dbFile = getDatabasePath(NoteDatabase.DATABASE_NAME)
                    dbFile.delete()
                    File(dbFile.path + "-shm").delete()
                    File(dbFile.path + "-wal").delete()
                    File(dbFile.path + "-journal").delete()
                    bindDatabase(SecureDatabaseFactory.open(this, null))
                } else {
                    throw e
                }
            }
        }
        runCatching { WidgetUpdateHelper.requestUpdate(this) }
    }

    private fun bindDatabase(db: NoteDatabase) {
        // Touch DB so Room actually opens the connection now (surface errors early)
        db.openHelper.writableDatabase
        databaseInstance = db
        NoteDatabase.setInstance(db)
        repositoryInstance = NoteRepository(
            db.noteDao(),
            db.checklistItemDao(),
            db.labelDao()
        )
    }

    private fun closeDatabase() {
        synchronized(this) {
            closeDatabaseLocked()
        }
        runCatching { WidgetUpdateHelper.requestUpdate(this) }
    }

    private fun closeDatabaseLocked() {
        runCatching { databaseInstance?.close() }
        databaseInstance = null
        repositoryInstance = null
        NoteDatabase.closeInstance()
    }

    companion object {
        private const val TAG = "NotepadApplication"
    }
}
