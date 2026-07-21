package com.notepad.app.ui.util

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalView

@Composable
fun rememberHapticFeedback(): (Int) -> Unit {
    val view = LocalView.current
    return { feedbackConstant ->
        view.performHapticFeedback(feedbackConstant)
    }
}

fun performLightHaptic(view: View) {
    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
}

fun performConfirmHaptic(view: View) {
    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
}
