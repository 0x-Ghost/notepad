package com.notepad.app.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object WidgetUpdateHelper {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun requestUpdate(context: Context) {
        val appContext = context.applicationContext
        scope.launch { updateAll(appContext) }
    }

    suspend fun updateAll(context: Context) {
        withContext(Dispatchers.IO) {
            val manager = GlanceAppWidgetManager(context)
            manager.getGlanceIds(NotepadWidget::class.java).forEach { glanceId ->
                NotepadWidget().update(context, glanceId)
            }
        }
    }
}
