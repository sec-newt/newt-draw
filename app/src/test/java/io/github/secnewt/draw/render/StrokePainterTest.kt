package io.github.secnewt.draw.render

import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.secnewt.draw.model.Point
import io.github.secnewt.draw.model.Stroke
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Real pixel checks, using Android's own drawing code. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class StrokePainterTest {
    private val line = Stroke((10..90).map { Point(it.toFloat(), 50f) }, width = 10f)

    @Test
    fun penLeavesBlackInkOnWhitePaper() {
        val picture = StrokePainter.picture(100, 100, listOf(line))
        assertEquals(Color.BLACK, picture.getPixel(50, 50))
        assertEquals(Color.WHITE, picture.getPixel(50, 10))
    }

    @Test
    fun eraserRemovesInk() {
        val eraser = Stroke((40..60).map { Point(it.toFloat(), 50f) }, width = 20f, eraser = true)
        val picture = StrokePainter.picture(100, 100, listOf(line, eraser))
        assertEquals(Color.WHITE, picture.getPixel(50, 50))
        assertEquals("ink outside the eraser stays", Color.BLACK, picture.getPixel(20, 50))
    }

    @Test
    fun aTapMakesADot() {
        val dot = Stroke(listOf(Point(50f, 50f)), width = 12f)
        val picture = StrokePainter.picture(100, 100, listOf(dot))
        assertEquals(Color.BLACK, picture.getPixel(50, 50))
        assertEquals(Color.WHITE, picture.getPixel(50, 60))
    }

    @Test
    fun theInkLayerIsSeeThroughWhereNothingIsDrawn() {
        val layer = StrokePainter.inkLayer(100, 100, listOf(line))
        assertEquals(0, Color.alpha(layer.getPixel(50, 10)))
    }

    @Test
    fun savedFileNamesAreReadableAndSortByTime() {
        val name = PictureSaver.fileName(java.time.LocalDateTime.of(2026, 11, 25, 14, 3, 22))
        assertEquals("Newt Draw 2026-11-25 14-03-22.png", name)
    }
}
