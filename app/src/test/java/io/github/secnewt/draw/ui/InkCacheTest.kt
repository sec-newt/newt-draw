package io.github.secnewt.draw.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.secnewt.draw.model.Drawing
import io.github.secnewt.draw.model.PAPER_HEIGHT
import io.github.secnewt.draw.model.PAPER_WIDTH
import io.github.secnewt.draw.model.Point
import io.github.secnewt.draw.model.Stroke
import io.github.secnewt.draw.render.StrokePainter
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Undo only redraws a patch of the paper; the result must match redrawing everything. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class InkCacheTest {
    private val drawing = Drawing().apply {
        add(Stroke(listOf(Point(100f, 1500f), Point(2900f, 1500f)), 64f)) // long line across
        add(Stroke(listOf(Point(1500f, 100f), Point(1500f, 2900f)), 32f)) // crossing it
        add(Stroke(listOf(Point(1400f, 1400f), Point(1600f, 1600f)), 64f, eraser = true)) // erases the cross
        add(Stroke(listOf(Point(1450f, 1520f), Point(1700f, 1480f)), 16f)) // over the erased spot
        add(Stroke(listOf(Point(1500f, 1500f)), 8f)) // a dot
    }

    /**
     * Skia smooths a line's edge a little differently when it's drawn through a clip, so a
     * few edge pixels inside the patch can differ by a faint shade. That's invisible and
     * doesn't build up (each undo redraws its patch from scratch). Anything more is a bug:
     * a missing or stale stroke differs by full ink (255/255).
     */
    private fun assertSameAsFullRedraw(cache: InkCache, strokes: List<Stroke>) {
        val expected = StrokePainter.inkLayer(PAPER_WIDTH, PAPER_HEIGHT, strokes)
        val actual = cache.layer(strokes)
        var differing = 0
        var worst = 0
        for (y in 0 until PAPER_HEIGHT) for (x in 0 until PAPER_WIDTH) {
            val a = actual.getPixel(x, y) ushr 24
            val e = expected.getPixel(x, y) ushr 24
            if (a != e) {
                differing++
                worst = maxOf(worst, abs(a - e))
            }
        }
        val after = "after going to ${strokes.size} strokes"
        // Measured: at most 151 edge pixels, at most 50/255 apart.
        assertTrue("$differing pixels differ by up to $worst/255 $after", worst <= 64)
    }

    @Test
    fun everyUndoMatchesAFullRedraw() {
        val cache = InkCache()
        cache.layer(drawing.strokes)
        while (drawing.undo()) assertSameAsFullRedraw(cache, drawing.strokes)
        while (drawing.redo()) assertSameAsFullRedraw(cache, drawing.strokes)
    }

    @Test
    fun clearAndItsUndoMatchAFullRedraw() {
        val cache = InkCache()
        cache.layer(drawing.strokes)
        drawing.clear()
        assertSameAsFullRedraw(cache, drawing.strokes)
        drawing.undo()
        assertSameAsFullRedraw(cache, drawing.strokes)
    }
}
