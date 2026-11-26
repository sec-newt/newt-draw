package io.github.secnewt.draw.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import io.github.secnewt.draw.model.PAPER_HEIGHT
import io.github.secnewt.draw.model.PAPER_WIDTH
import io.github.secnewt.draw.model.PaperView
import io.github.secnewt.draw.model.Point
import io.github.secnewt.draw.model.Stroke
import io.github.secnewt.draw.model.StringSmoother
import io.github.secnewt.draw.render.PAPER
import io.github.secnewt.draw.render.StrokePainter

/** The ring and string that show where the ink is while smoothing pulls it behind the finger. */
private val GuideColor = Color(0xFF3760BF) // Tokyo Night Day blue: 5.8:1 on white paper

/**
 * The paper, which is bigger than the screen. One finger draws. Two fingers drag to scroll
 * and pinch to zoom; a stroke already started is cancelled when the second finger lands,
 * and nothing draws again until every finger is lifted. With [moveOnly] (the Move tool),
 * one finger drags the paper instead of drawing.
 *
 * Strokes are in paper pixels. Finished strokes live in a cached image of the whole paper,
 * so only the stroke being drawn is redrawn as the finger moves.
 *
 * @param view where the paper sits on screen; null until the first layout, when
 *   [onLimits] reports the screen area and the caller fits the whole page.
 * @param smoothingPx string length in screen pixels (it's about the finger, not the paper).
 * @param edge the outline around the paper, so its edge shows against any background.
 */
@Composable
fun DrawingSurface(
    strokes: List<Stroke>,
    brushWidth: Float,
    eraser: Boolean,
    moveOnly: Boolean,
    smoothingPx: Float,
    view: PaperView?,
    onLimits: (PaperView.Limits) -> Unit,
    onViewChange: (PaperView) -> Unit,
    onMoveEnd: () -> Unit,
    onStroke: (Stroke) -> Unit,
    background: Color,
    edge: Color,
    modifier: Modifier = Modifier,
) {
    val cache = remember { InkCache() }
    val live = remember { mutableStateListOf<Point>() }
    var finger by remember { mutableStateOf<Offset?>(null) }
    var limits by remember { mutableStateOf<PaperView.Limits?>(null) }
    val currentOnStroke by rememberUpdatedState(onStroke)
    val currentOnView by rememberUpdatedState(onViewChange)
    val currentOnMoveEnd by rememberUpdatedState(onMoveEnd)
    val currentView by rememberUpdatedState(view)
    val guideWidth = with(LocalDensity.current) { 2f * density }
    val count = strokes.size

    Canvas(
        modifier
            .clipToBounds()
            .onSizeChanged {
                val new = PaperView.Limits(it.width.toFloat(), it.height.toFloat())
                limits = new
                onLimits(new)
            }
            .semantics {
                contentDescription = "Drawing paper"
                stateDescription = when (count) {
                    0 -> "Blank"
                    1 -> "1 stroke"
                    else -> "$count strokes"
                }
            }
            .pointerInput(brushWidth, eraser, moveOnly, smoothingPx) {
                awaitEachGesture {
                    // If the brush changes mid-stroke this is cancelled; finally still wipes
                    // the half-drawn line so nothing is left on screen that isn't in the drawing.
                    try {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var shown = currentView ?: return@awaitEachGesture
                        val bounds = limits ?: return@awaitEachGesture
                        // The string is a set length on screen, so it's shorter on the paper when zoomed in.
                        val smoother = StringSmoother(smoothingPx / shown.scale)
                        live.clear()
                        live.add(smoother.start(shown.toPaper(down.position.toPoint())))
                        finger = down.position
                        down.consume()
                        var moving = moveOnly
                        if (moving) {
                            live.clear()
                            finger = null
                        }
                        // Fingers needed to move the paper: one with the Move tool, else two.
                        // Lifting down to fewer stops the move (but nothing draws until all lift).
                        val moveFingers = if (moveOnly) 1 else 2
                        while (true) {
                            val event = awaitPointerEvent()
                            // Layout refits the view. Discard this gesture's old transform;
                            // awaitEachGesture waits for all fingers to lift before restarting.
                            if (limits !== bounds) {
                                event.changes.forEach { it.consume() }
                                return@awaitEachGesture
                            }
                            val pressed = event.changes.count { it.pressed }
                            if (pressed > 1 && !moving) {
                                moving = true
                                live.clear()
                                finger = null
                            }
                            if (moving) {
                                val centroid = event.calculateCentroid(useCurrent = false)
                                if (pressed >= moveFingers && centroid != Offset.Unspecified) {
                                    val pan = event.calculatePan()
                                    shown = shown.moved(centroid.toPoint(), event.calculateZoom(), pan.x, pan.y, bounds)
                                    currentOnView(shown)
                                }
                                event.changes.forEach { it.consume() }
                                if (pressed == 0) {
                                    currentOnMoveEnd()
                                    break
                                } else {
                                    continue
                                }
                            }
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            // Fast swipes deliver several samples per frame; use them all.
                            change.historical.forEach { sample ->
                                smoother.move(shown.toPaper(sample.position.toPoint()))?.let(live::add)
                            }
                            smoother.move(shown.toPaper(change.position.toPoint()))?.let(live::add)
                            finger = change.position
                            change.consume()
                        }
                        // A tap or line entirely on the grey margin would be an invisible stroke.
                        if (!moving && live.isNotEmpty() && live.any { it.onPaper(brushWidth) }) {
                            currentOnStroke(Stroke(live.toList(), brushWidth, eraser))
                        }
                    } finally {
                        live.clear()
                        finger = null
                    }
                }
            },
    ) {
        drawRect(background)
        val v = view ?: return@Canvas
        val ink = cache.layer(strokes)
        translate(v.offsetX, v.offsetY) {
            scale(v.scale, v.scale, pivot = Offset.Zero) {
                drawRect(Color(PAPER), size = Size(PAPER_WIDTH.toFloat(), PAPER_HEIGHT.toFloat()))
                drawImage(ink.asImageBitmap(), filterQuality = FilterQuality.High)
                if (live.isNotEmpty()) {
                    // Ink only shows on the paper, never on the margin around it.
                    clipRect(0f, 0f, PAPER_WIDTH.toFloat(), PAPER_HEIGHT.toFloat()) {
                        drawIntoCanvas { StrokePainter.drawPreview(it.nativeCanvas, Stroke(live.toList(), brushWidth, eraser)) }
                    }
                }
            }
        }
        drawRect(
            edge,
            topLeft = Offset(v.offsetX, v.offsetY),
            size = Size(PAPER_WIDTH * v.scale, PAPER_HEIGHT * v.scale),
            style = DrawStroke(guideWidth),
        )
        val ring = live.lastOrNull()
        val touch = finger
        if (smoothingPx > 0f && ring != null && touch != null) {
            val center = v.toScreen(ring).let { Offset(it.x, it.y) }
            drawLine(GuideColor, center, touch, strokeWidth = guideWidth)
            drawCircle(GuideColor, radius = brushWidth * v.scale / 2f + guideWidth * 2f, center = center, style = DrawStroke(guideWidth))
        }
    }
}

private fun Offset.toPoint() = Point(x, y)

/** True if a brush of [width] at this point would touch the paper. */
private fun Point.onPaper(width: Float): Boolean {
    val r = width / 2f
    return x >= -r && y >= -r && x <= PAPER_WIDTH + r && y <= PAPER_HEIGHT + r
}

/**
 * Keeps the whole-paper ink image between frames. When the only change is new strokes added
 * on the end, it draws just those; anything else (undo, redo of a clear) wipes the same image
 * and replays every stroke.
 */
private class InkCache {
    private var bitmap: Bitmap? = null
    private var drawn: List<Stroke> = emptyList()

    fun layer(strokes: List<Stroke>): Bitmap {
        val current = bitmap ?: StrokePainter.inkLayer(PAPER_WIDTH, PAPER_HEIGHT, emptyList()).also {
            // Smaller copies for the zoomed-out view, so thin lines don't break up.
            it.setHasMipMap(true)
            bitmap = it
            drawn = emptyList()
        }
        val appended = strokes.size >= drawn.size && (drawn.isEmpty() || strokes[drawn.size - 1] === drawn.last())
        val canvas = android.graphics.Canvas(current)
        if (appended) {
            for (i in drawn.size until strokes.size) StrokePainter.draw(canvas, strokes[i])
        } else {
            current.eraseColor(android.graphics.Color.TRANSPARENT)
            strokes.forEach { StrokePainter.draw(canvas, it) }
        }
        drawn = strokes
        return current
    }
}
