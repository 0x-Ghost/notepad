package com.notepad.app.ui.components

import android.media.MediaPlayer
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.notepad.app.security.SecureMediaAccess
import kotlinx.coroutines.delay
import kotlin.random.Random

@Composable
fun AudioWaveformPlayer(
    audioUri: String,
    contentColor: Color,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val playableUri = remember(audioUri) {
        SecureMediaAccess.resolve(context, audioUri) ?: audioUri
    }
    var isPlaying by remember(playableUri) { mutableStateOf(false) }
    var progress by remember(playableUri) { mutableFloatStateOf(0f) }
    var durationMs by remember(playableUri) { mutableStateOf(0) }
    var mediaPlayer by remember(playableUri) { mutableStateOf<MediaPlayer?>(null) }

    val barHeights = remember(playableUri) {
        List(28) { Random(playableUri.hashCode() + it).nextFloat() * 0.6f + 0.25f }
    }

    DisposableEffect(playableUri) {
        onDispose {
            mediaPlayer?.release()
            mediaPlayer = null
        }
    }

    LaunchedEffect(isPlaying, mediaPlayer) {
        while (isPlaying) {
            val player = mediaPlayer
            if (player != null && player.isPlaying) {
                progress = player.currentPosition.toFloat() / player.duration.coerceAtLeast(1)
            }
            delay(80)
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = contentColor.copy(alpha = 0.08f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = {
                    if (isPlaying) {
                        mediaPlayer?.pause()
                        isPlaying = false
                    } else {
                        if (mediaPlayer == null) {
                            mediaPlayer = MediaPlayer().apply {
                                setDataSource(playableUri)
                                prepare()
                                durationMs = duration
                                setOnCompletionListener {
                                    isPlaying = false
                                    progress = 0f
                                    seekTo(0)
                                }
                            }
                        }
                        mediaPlayer?.start()
                        isPlaying = true
                    }
                },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pausa" else "Riproduci",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    barHeights.forEachIndexed { index, height ->
                        val barProgress = index.toFloat() / barHeights.size
                        val isActive = barProgress <= progress
                        val animatedHeight by animateFloatAsState(
                            targetValue = if (isActive && isPlaying) height else height * 0.7f,
                            animationSpec = tween(150),
                            label = "bar$index"
                        )
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height((animatedHeight * 32).dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    if (isActive) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        contentColor.copy(alpha = 0.25f)
                                    }
                                )
                        )
                    }
                }
                Text(
                    text = if (durationMs > 0) formatDuration((progress * durationMs).toInt()) else "Nota vocale",
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.7f)
                )
            }

            IconButton(onClick = {
                mediaPlayer?.release()
                mediaPlayer = null
                isPlaying = false
                onRemove()
            }) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Rimuovi audio",
                    tint = contentColor.copy(alpha = 0.6f)
                )
            }
        }
    }
}

private fun formatDuration(ms: Int): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
