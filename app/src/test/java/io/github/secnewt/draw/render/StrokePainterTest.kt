package io.github.secnewt.draw.render

import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.secnewt.draw.model.Fill
import io.github.secnewt.draw.model.Palette
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
    fun savedPictureIsSolidWhiteWhereErased() {
        val eraser = Stroke((40..60).map { Point(it.toFloat(), 50f) }, width = 20f, eraser = true)
        val picture = StrokePainter.picture(100, 100, listOf(line, eraser))
        assertEquals(255, Color.alpha(picture.getPixel(50, 50)))
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

    @Test
    fun penDrawsInItsColor() {
        val red = Palette.Red.argb
        val layer = StrokePainter.inkLayer(100, 100, listOf(Stroke(listOf(Point(10f, 50f), Point(90f, 50f)), 10f, color = red)))
        assertEquals(red, layer.getPixel(50, 50))
    }

    @Test
    fun aFillBehindLeavesTheLineOnTop() {
        val line = Stroke(listOf(Point(50f, 0f), Point(50f, 100f)), 10f)
        // A fill row right across the line, going behind it.
        val fill = Fill(intArrayOf(20, 0, 99), Palette.Red.argb, behind = true)
        val layer = StrokePainter.inkLayer(100, 100, listOf(line, fill))
        assertEquals(Color.BLACK, layer.getPixel(50, 20))
        assertEquals(Palette.Red.argb, layer.getPixel(20, 20))
        assertEquals("only the fill's row", Color.TRANSPARENT, layer.getPixel(20, 21))
    }

    @Test
    fun aFillOverPaintsOnTop() {
        val line = Stroke(listOf(Point(50f, 0f), Point(50f, 100f)), 10f)
        val fill = Fill(intArrayOf(20, 0, 99), Palette.Blue.argb, behind = false)
        val layer = StrokePainter.inkLayer(100, 100, listOf(line, fill))
        assertEquals(Palette.Blue.argb, layer.getPixel(50, 20))
    }

    @Test
    fun theSavedPictureShowsFillsOnWhite() {
        val fill = Fill(intArrayOf(20, 0, 99), Palette.Peach.argb, behind = true)
        val picture = StrokePainter.picture(100, 100, listOf(fill))
        assertEquals(Palette.Peach.argb, picture.getPixel(30, 20))
        assertEquals(Color.WHITE, picture.getPixel(30, 60))
    }
}
