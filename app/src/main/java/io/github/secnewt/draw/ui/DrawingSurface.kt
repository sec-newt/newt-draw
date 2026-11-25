package io.github.secnewt.draw.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import io.github.secnewt.draw.model.Point
import io.github.secnewt.draw.model.Stroke
import io.github.secnewt.draw.model.StringSmoother
import io.github.secnewt.draw.render.PAPER
import io.github.secnewt.draw.render.StrokePainter

/** The ring and string that show where the ink is while smoothing pulls it behind the finger. */
private val GuideColor = Color(0xFF3760BF) // Tokyo Night Day blue: 5.8:1 on white paper

/**
 * The paper. One finger draws; a second finger cancels the stroke so a resting finger or
 * palm doesn't leave a stray line. Finished strokes live in a cached image, so only the
 * stroke being drawn is redrawn as the finger moves.
 *
 * @param smoothingPx string length in pixels; 0 means no smoothing.
 * @param brushPx brush width in pixels.
 */
@Composable
fun DrawingSurface(
    strokes: List<Stroke>,
    brushPx: Float,
    eraser: Boolean,
    smoothingPx: Float,
    onStroke: (Stroke) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cache = remember { InkCache() }
    val live = remember { mutableStateListOf<Point>() }
    var finger by remember { mutableStateOf<Offset?>(null) }
    val currentOnStroke by rememberUpdatedState(onStroke)
    val guideWidth = with(LocalDensity.current) { 2f * density }
    val count = strokes.size

    Canvas(
        modifier
            .semantics {
                contentDescription = "Drawing paper"
                stateDescription = when (count) {
                    0 -> "Blank"
                    1 -> "1 stroke"
                    else -> "$count strokes"
                }
            }
            .pointerInput(brushPx, eraser, smoothingPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val smoother = StringSmoother(smoothingPx)
                    live.clear()
                    live.add(smoother.start(down.position.toPoint()))
                    finger = down.position
                    down.consume()
                    var cancelled = false
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.count { it.pressed } > 1) cancelled = true
                        if (cancelled) {
                            live.clear()
                            finger = null
                            event.changes.forEach { it.consume() }
                            if (event.changes.none { it.pressed }) break else continue
                        }
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        // Fast swipes deliver several samples per frame; use them all.
                        change.historical.forEach { sample ->
                            smoother.move(sample.position.toPoint())?.let(live::add)
                        }
                        smoother.move(change.position.toPoint())?.let(live::add)
                        finger = change.position
                        change.consume()
                    }
                    if (!cancelled && live.isNotEmpty()) {
                        currentOnStroke(Stroke(live.toList(), brushPx, eraser))
                    }
                    live.clear()
                    finger = null
                }
            },
    ) {
        drawRect(Color(PAPER))
        val ink = cache.layer(size.width.toInt(), size.height.toInt(), strokes)
        if (ink != null) drawImage(ink.asImageBitmap())
        if (live.isNotEmpty()) {
            drawIntoCanvas { StrokePainter.drawPreview(it.nativeCanvas, Stroke(live.toList(), brushPx, eraser)) }
        }
        val ring = live.lastOrNull()
        val touch = finger
        if (smoothingPx > 0f && ring != null && touch != null) {
            val center = Offset(ring.x, ring.y)
            drawLine(GuideColor, center, touch, strokeWidth = guideWidth)
            drawCircle(GuideColor, radius = brushPx / 2f + guideWidth * 2f, center = center, style = DrawStroke(guideWidth))
        }
    }
}

private fun Offset.toPoint() = Point(x, y)

/**
 * Keeps the ink layer between frames. When the only change is new strokes added on the end,
 * it draws just those; anything else (undo, redo of a clear, a new size) rebuilds it by
 * replaying every stroke.
 */
private class InkCache {
    private var bitmap: Bitmap? = null
    private var drawn: List<Stroke> = emptyList()

    fun layer(width: Int, height: Int, strokes: List<Stroke>): Bitmap? {
        if (width <= 0 || height <= 0) return null
        val current = bitmap
        val appended = current != null &&
            current.width == width && current.height == height &&
            strokes.size >= drawn.size &&
            (drawn.isEmpty() || strokes[drawn.size - 1] === drawn.last())
        if (appended) {
            val canvas = android.graphics.Canvas(current)
            for (i in drawn.size until strokes.size) StrokePainter.draw(canvas, strokes[i])
        } else {
            // No recycle(): the screen may still be showing the old image this frame.
            bitmap = StrokePainter.inkLayer(width, height, strokes)
        }
        drawn = strokes
        return bitmap
    }
}
