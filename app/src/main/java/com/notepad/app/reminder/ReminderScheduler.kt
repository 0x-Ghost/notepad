package com.notepad.app.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.notepad.app.data.model.Note

class ReminderScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = android.net.Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun schedule(note: Note) {
        val reminderAt = note.reminderAt ?: return
        if (reminderAt <= System.currentTimeMillis()) {
            cancel(note.id)
            return
        }
        val pendingIntent = createPendingIntent(note) ?: return

        try {
            if (canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    reminderAt,
                    pendingIntent
                )
            } else {
                // Fallback senza permesso exact alarm: promemoria con leggero ritardo possibile
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    reminderAt,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact alarm denied, using inexact fallback", e)
            runCatching {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    reminderAt,
                    pendingIntent
                )
            }
        }
    }

    fun cancel(noteId: Long) {
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            noteId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    fun rescheduleAll(notes: List<Note>) {
        notes.forEach { note ->
            if (note.reminderAt != null && note.reminderAt > System.currentTimeMillis()) {
                schedule(note)
            } else {
                cancel(note.id)
            }
        }
    }

    private fun createPendingIntent(note: Note): PendingIntent? {
        val redact = NotificationHelper.shouldRedactContent(context) ||
            (context.applicationContext as? com.notepad.app.NotepadApplication)
                ?.lockRepository?.isLockEnabled() == true
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_NOTE_ID, note.id)
            // Never embed note body in alarms when app lock is enabled (PendingIntent leak).
            if (!redact) {
                putExtra(ReminderReceiver.EXTRA_NOTE_TITLE, note.title)
                putExtra(ReminderReceiver.EXTRA_NOTE_CONTENT, note.content)
            }
        }
        return PendingIntent.getBroadcast(
            context,
            note.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val TAG = "ReminderScheduler"
    }
}
