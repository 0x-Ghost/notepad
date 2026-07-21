package com.notepad.app.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import com.notepad.app.ui.util.statusBarsTopPadding
import java.io.File
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

private data class DrawPath(val path: Path, val color: Color, val strokeWidth: Float)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawingScreen(
    onDone: (String) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val paths = remember { mutableStateListOf<DrawPath>() }
    var currentPath by remember { mutableStateOf<Path?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var redrawTick by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.statusBarsTopPadding(),
                title = { Text("Disegna") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Annulla")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        currentPath = null
                        paths.clear()
                        redrawTick++
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Cancella tutto")
                    }
                    IconButton(onClick = {
                        val allPaths = buildList {
                            addAll(paths)
                            currentPath?.let { path ->
                                add(DrawPath(path, Color.Black, 4f))
                            }
                        }
                        val savedPath = saveDrawingToFile(
                            context = context,
                            paths = allPaths,
                            canvasWidth = canvasSize.width,
                            canvasHeight = canvasSize.height
                        )
                        if (savedPath != null) onDone(savedPath)
                    }) {
                        Icon(Icons.Default.Check, contentDescription = "Salva")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.White)
                .onSizeChanged { canvasSize = it }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            currentPath = Path().apply { moveTo(offset.x, offset.y) }
                            redrawTick++
                        },
                        onDrag = { change, _ ->
                            val path = currentPath ?: return@detectDragGestures
                            path.lineTo(change.position.x, change.position.y)
                            redrawTick++
                            change.consume()
                        },
                        onDragEnd = {
                            currentPath?.let { path ->
                                paths.add(
                                    DrawPath(
                                        path = Path().apply { addPath(path) },
                                        color = Color.Black,
                                        strokeWidth = 4f
                                    )
                                )
                            }
                            currentPath = null
                            redrawTick++
                        },
                        onDragCancel = {
                            currentPath = null
                            redrawTick++
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                @Suppress("UNUSED_VARIABLE")
                val tick = redrawTick
                paths.forEach { drawPath ->
                    drawPath(
                        path = drawPath.path,
                        color = drawPath.color,
                        style = Stroke(
                            width = drawPath.strokeWidth,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
                currentPath?.let { path ->
                    drawPath(
                        path = path,
                        color = Color.Black,
                        style = Stroke(
                            width = 4f,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }
            }
        }
    }
}

private fun saveDrawingToFile(
    context: Context,
    paths: List<DrawPath>,
    canvasWidth: Int,
    canvasHeight: Int
): String? {
    if (paths.isEmpty() || canvasWidth <= 0 || canvasHeight <= 0) return null
    return try {
        val contentBounds = computeContentBounds(paths) ?: return null
        val padding = 24f
        val left = max(0f, contentBounds.left - padding)
        val top = max(0f, contentBounds.top - padding)
        val right = min(canvasWidth.toFloat(), contentBounds.right + padding)
        val bottom = min(canvasHeight.toFloat(), contentBounds.bottom + padding)

        val bitmapWidth = max(1, ceil(right - left).toInt())
        val bitmapHeight = max(1, ceil(bottom - top).toInt())

        val dir = File(context.filesDir, "note_images").apply { mkdirs() }
        val file = File(dir, "${UUID.randomUUID()}.png")
        val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)

        val matrix = Matrix().apply { setTranslate(-left, -top) }
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 4f
            strokeCap = android.graphics.Paint.Cap.ROUND
            strokeJoin = android.graphics.Paint.Join.ROUND
            isAntiAlias = true
        }

        paths.forEach { drawPath ->
            val androidPath = drawPath.path.asAndroidPath()
            androidPath.transform(matrix)
            canvas.drawPath(androidPath, paint)
        }

        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val path = file.absolutePath
        com.notepad.app.security.SecureMediaAccess.protectNewFile(context, path)
        path
    } catch (_: Exception) {
        null
    }
}

private fun computeContentBounds(paths: List<DrawPath>): RectF? {
    val bounds = RectF()
    var hasBounds = false
    paths.forEach { drawPath ->
        val pathBounds = RectF()
        drawPath.path.asAndroidPath().computeBounds(pathBounds, true)
        if (!pathBounds.isEmpty) {
            val strokePadding = drawPath.strokeWidth / 2f
            pathBounds.inset(-strokePadding, -strokePadding)
            if (!hasBounds) {
                bounds.set(pathBounds)
                hasBounds = true
            } else {
                bounds.union(pathBounds)
            }
        }
    }
    return if (hasBounds) bounds else null
}
