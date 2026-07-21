package com.notepad.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.notepad.app.NotepadApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val noteId = intent.getLongExtra(EXTRA_NOTE_ID, -1L)
        if (noteId == -1L) return

        val redact = NotificationHelper.shouldRedactContent(context)
        val title = if (redact) "" else intent.getStringExtra(EXTRA_NOTE_TITLE).orEmpty()
        val content = if (redact) "" else intent.getStringExtra(EXTRA_NOTE_CONTENT).orEmpty()
        NotificationHelper.showReminderNotification(context, noteId, title, content)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as? NotepadApplication
                if (app != null && app.isDatabaseReady()) {
                    app.requireDatabase().noteDao().updateReminder(noteId, null)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val EXTRA_NOTE_ID = "note_id"
        const val EXTRA_NOTE_TITLE = "note_title"
        const val EXTRA_NOTE_CONTENT = "note_content"
    }
}
