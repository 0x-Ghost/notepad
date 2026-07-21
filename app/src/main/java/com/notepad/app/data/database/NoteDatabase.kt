package com.notepad.app.data.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import android.content.Context
import com.notepad.app.data.dao.ChecklistItemDao
import com.notepad.app.data.dao.LabelDao
import com.notepad.app.data.dao.NoteDao
import com.notepad.app.data.database.migrations.Migrations
import com.notepad.app.data.model.ChecklistItem
import com.notepad.app.data.model.Label
import com.notepad.app.data.model.Note
import com.notepad.app.data.model.NoteLabelCrossRef

@Database(
    entities = [Note::class, ChecklistItem::class, Label::class, NoteLabelCrossRef::class],
    version = 7,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class NoteDatabase : RoomDatabase() {

    abstract fun noteDao(): NoteDao
    abstract fun checklistItemDao(): ChecklistItemDao
    abstract fun labelDao(): LabelDao

    companion object {
        const val DATABASE_NAME = "notepad_database"

        @Volatile
        private var INSTANCE: NoteDatabase? = null

        /**
         * Returns the open database instance. Prefer [com.notepad.app.NotepadApplication.database]
         * so SQLCipher / lock state is respected.
         */
        fun getInstance(context: Context): NoteDatabase {
            val app = context.applicationContext
            if (app is com.notepad.app.NotepadApplication) {
                return app.requireDatabase()
            }
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(
                    context.applicationContext,
                    context.getDatabasePath(DATABASE_NAME).absolutePath,
                    openHelperFactory = null
                )
                    .also { INSTANCE = it }
            }
        }

        fun buildDatabase(
            context: Context,
            databasePath: String = context.getDatabasePath(DATABASE_NAME).absolutePath,
            openHelperFactory: androidx.sqlite.db.SupportSQLiteOpenHelper.Factory?
        ): NoteDatabase {
            val builder = Room.databaseBuilder(
                context.applicationContext,
                NoteDatabase::class.java,
                databasePath
            )
            // Explicit migrations only from v8 onward (baseline schemas/ v7).
            // Do NOT use fallbackToDestructiveMigration — it would wipe user notes.
            if (Migrations.ALL.isNotEmpty()) {
                builder.addMigrations(*Migrations.ALL)
            }
            if (openHelperFactory != null) {
                builder.openHelperFactory(openHelperFactory)
            }
            return builder.build()
        }

        fun setInstance(database: NoteDatabase?) {
            synchronized(this) {
                if (INSTANCE != null && INSTANCE !== database) {
                    runCatching { INSTANCE?.close() }
                }
                INSTANCE = database
            }
        }

        fun closeInstance() {
            synchronized(this) {
                runCatching { INSTANCE?.close() }
                INSTANCE = null
            }
        }
    }
}
