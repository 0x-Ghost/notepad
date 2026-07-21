package com.notepad.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.notepad.app.NotepadApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as? NotepadApplication ?: return@launch
                // Encrypted DB cannot be opened without unlock; reminders are rescheduled after unlock.
                if (app.lockRepository.isLockEnabled() && !app.isDatabaseReady()) return@launch
                if (!app.isDatabaseReady()) return@launch
                val notes = app.requireDatabase().noteDao().getNotesWithFutureReminders()
                ReminderScheduler(context).rescheduleAll(notes)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
