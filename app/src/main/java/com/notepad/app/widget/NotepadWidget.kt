package com.notepad.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.notepad.app.MainActivity
import com.notepad.app.NotepadApplication
import com.notepad.app.data.model.Note
import com.notepad.app.data.model.NoteType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class WidgetNoteEntry(
    val note: Note,
    val checkedCount: Int,
    val totalCount: Int
)

class NotepadWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as? NotepadApplication
        val lockRepo = app?.lockRepository
        val locked = lockRepo?.isLockEnabled() == true && lockRepo.isSessionUnlocked().not()

        val entries = if (locked || app?.isDatabaseReady() != true) {
            emptyList()
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    val db = app.requireDatabase()
                    val notes = db.noteDao().getPinnedNotesForWidget(5)
                    notes.map { note ->
                        val items = if (note.noteType == NoteType.CHECKLIST) {
                            db.checklistItemDao().getItemsForNoteOnce(note.id)
                        } else {
                            emptyList()
                        }
                        WidgetNoteEntry(
                            note = note,
                            checkedCount = items.count { it.isChecked },
                            totalCount = items.size
                        )
                    }
                }.getOrDefault(emptyList())
            }
        }
        provideContent { NotepadWidgetContent(context, entries, showLocked = locked) }
    }
}

@Composable
private fun NotepadWidgetContent(
    context: Context,
    entries: List<WidgetNoteEntry>,
    showLocked: Boolean
) {
    GlanceTheme {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .padding(12.dp)
                .clickable(actionStartActivity(Intent(context, MainActivity::class.java)))
        ) {
            Text(
                text = "Notepad",
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = GlanceTheme.colors.onSurface
                )
            )
            Spacer(modifier = GlanceModifier.height(8.dp))
            when {
                showLocked -> {
                    Text(
                        text = "App bloccata",
                        style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.onSurfaceVariant)
                    )
                }
                entries.isEmpty() -> {
                    Text(
                        text = "Nessuna nota fissata",
                        style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.onSurfaceVariant)
                    )
                }
                else -> {
                    entries.forEach { entry ->
                        WidgetNoteRow(context, entry)
                        Spacer(modifier = GlanceModifier.height(6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetNoteRow(context: Context, entry: WidgetNoteEntry) {
    val note = entry.note
    val noteColor = Color(note.color)
    val title = note.title.ifBlank {
        note.content.take(40).ifBlank { "Nota senza titolo" }
    }

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .cornerRadius(12.dp)
            .background(GlanceTheme.colors.surfaceVariant)
            .padding(8.dp)
            .clickable(
                actionStartActivity(
                    Intent(context, MainActivity::class.java).apply {
                        putExtra(MainActivity.EXTRA_OPEN_NOTE_ID, note.id)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                )
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = GlanceModifier
                .size(8.dp)
                .cornerRadius(4.dp)
                .background(ColorProvider(noteColor))
        ) {}
        Spacer(modifier = GlanceModifier.width(8.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = title,
                style = TextStyle(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = GlanceTheme.colors.onSurface
                ),
                maxLines = 1
            )
            if (entry.totalCount > 0) {
                Text(
                    text = "${entry.checkedCount}/${entry.totalCount} completati",
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = GlanceTheme.colors.onSurfaceVariant
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

class NotepadWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NotepadWidget()
}
