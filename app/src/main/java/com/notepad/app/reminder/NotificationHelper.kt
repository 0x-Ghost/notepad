package com.notepad.app.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.notepad.app.MainActivity
import com.notepad.app.NotepadApplication
import com.notepad.app.R

object NotificationHelper {

    private const val CHANNEL_ID = "notepad_reminders"
    private const val CHANNEL_NAME = "Promemoria note"
    private const val LOCKED_TITLE = "App bloccata"
    private const val LOCKED_TEXT = "Sblocca Notepad per vedere il promemoria"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    fun showReminderNotification(
        context: Context,
        noteId: Long,
        title: String,
        content: String
    ) {
        createNotificationChannel(context)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_NOTE_ID, noteId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            noteId.toInt(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val redact = shouldRedactContent(context)
        val displayTitle = if (redact) LOCKED_TITLE else title.ifBlank { "Promemoria nota" }
        val displayText = if (redact) {
            LOCKED_TEXT
        } else {
            content.take(120).ifBlank { "Hai un promemoria per questa nota" }
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(displayTitle)
            .setContentText(displayText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(displayText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(noteId.toInt(), notification)
    }

    /** Hide note content while app lock is on and session is not unlocked. */
    fun shouldRedactContent(context: Context): Boolean {
        val app = context.applicationContext as? NotepadApplication ?: return false
        val lock = app.lockRepository
        return lock.isLockEnabled() && !lock.isSessionUnlocked()
    }
}
