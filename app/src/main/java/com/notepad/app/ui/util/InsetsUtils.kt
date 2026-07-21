package com.notepad.app.ui.util

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Padding per la status bar (edge-to-edge). */
@Composable
fun Modifier.statusBarsTopPadding(): Modifier =
    windowInsetsPadding(WindowInsets.statusBars)

/**
 * Sposta i controlli sopra la barra di navigazione del sistema.
 * Usa un fallback minimo per dispositivi che non riportano gli inset (es. Samsung 3 tasti).
 */
fun Modifier.safeNavigationBarPadding(extra: Dp = 8.dp): Modifier = composed {
    val reportedBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val fallback = 48.dp
    val bottom = maxOf(reportedBottom, fallback) + extra
    Modifier.padding(bottom = bottom)
}

/** Estende lo sfondo fino al bordo inferiore dello schermo. */
@Composable
fun Modifier.edgeToEdgeBottomBar(backgroundColor: Color): Modifier =
    background(backgroundColor)

@Composable
fun Modifier.navigationBarsBottomPadding(): Modifier = safeNavigationBarPadding()
