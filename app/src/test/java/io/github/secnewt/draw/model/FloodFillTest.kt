package io.github.secnewt.draw.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The fill bucket on small hand-made pictures: see-through paper with black lines. */
class FloodFillTest {
    private val w = 200
    private val h = 200
    private val black = Palette.Black.argb
    private val red = Palette.Red.argb
    private val blue = Palette.Blue.argb

    /** A square outline 4 pixels thick from 50 to 150, with an optional gap in the top edge. */
    private fun box(gap: Int = 0, ink: Int = black) = IntArray(w * h).also { p ->
        for (y in 50 until 150) for (x in 50 until 150) {
            val edge = x < 54 || x >= 146 || y < 54 || y >= 146
            val inGap = y < 54 && x in (100 - gap / 2) until (100 + gap / 2)
            if (edge && !inGap) p[y * w + x] = ink
        }
    }

    private fun filled(result: FloodFill.Result): Fill {
        assertTrue("expected a fill, got $result", result is FloodFill.Result.Filled)
        return (result as FloodFill.Result.Filled).fill
    }

    private fun Fill.covers(x: Int, y: Int): Boolean {
        for (i in 0 until runs) if (spans[3 * i] == y && x in spans[3 * i + 1]..spans[3 * i + 2]) return true
        return false
    }

    @Test
    fun fillsInsideAClosedShapeOnly() {
        val fill = filled(FloodFill.fill(box(), w, h, 100, 100, red))
        assertTrue(fill.covers(100, 100))
        assertTrue("reaches the line", fill.covers(55, 100) && fill.covers(144, 100))
        assertFalse("stays inside", fill.covers(20, 20))
        assertFalse(fill.covers(100, 160))
        assertEquals(red, fill.color)
    }

    @Test
    fun aFillOnEmptyPaperGoesBehindTheLines() {
        val fill = filled(FloodFill.fill(box(), w, h, 100, 100, red))
        assertTrue(fill.behind)
        // One pixel under the line too, so there's no white edge between line and fill.
        assertTrue(fill.covers(53, 100))
        assertFalse(fill.covers(51, 100))
    }

    @Test
    fun aSmallGapInTheLineDoesNotLeak() {
        val fill = filled(FloodFill.fill(box(gap = 16), w, h, 100, 100, red))
        assertFalse("16-pixel gap stays closed", fill.covers(20, 20))
        assertTrue(fill.covers(100, 60))
    }

    @Test
    fun aWideOpeningLetsTheFillOut() {
        // Far wider than any gap the bucket closes: that's a real opening, and it fills out.
        val fill = filled(FloodFill.fill(box(gap = 60), w, h, 100, 100, red))
        assertTrue(fill.covers(20, 20))
    }

    @Test
    fun tappingNextToALineStillFills() {
        val fill = filled(FloodFill.fill(box(), w, h, 56, 100, red))
        assertTrue(fill.covers(100, 100))
        assertFalse(fill.covers(20, 20))
    }

    @Test
    fun outsideTheShapeFillsTheRestOfThePaper() {
        val fill = filled(FloodFill.fill(box(), w, h, 10, 10, blue))
        assertTrue(fill.covers(0, 0) && fill.covers(199, 199))
        assertFalse(fill.covers(100, 100))
    }

    @Test
    fun tappingAColorRecolorsJustThatArea() {
        val pixels = box(ink = red)
        val fill = filled(FloodFill.fill(pixels, w, h, 51, 100, blue))
        assertFalse("painted over, not behind", fill.behind)
        assertTrue(fill.covers(148, 148))
        assertFalse(fill.covers(100, 100))
    }

    @Test
    fun theSameColorAgainChangesNothing() {
        assertSame(FloodFill.Result.AlreadyThatColor, FloodFill.fill(box(), w, h, 51, 100, black))
    }

    @Test
    fun offThePaperFillsNothing() {
        assertSame(FloodFill.Result.OffPaper, FloodFill.fill(box(), w, h, -1, 100, red))
        assertSame(FloodFill.Result.OffPaper, FloodFill.fill(box(), w, h, 100, h, red))
    }

    @Test
    fun aFullSizePageFillsInGoodTime() {
        val size = 3000
        val pixels = IntArray(size * size)
        for (i in 0 until size) pixels[1500 * size + i] = black // a line across the middle
        val start = System.nanoTime()
        val fill = filled(FloodFill.fill(pixels, size, size, 10, 10, red))
        val ms = (System.nanoTime() - start) / 1_000_000
        assertTrue(fill.covers(2999, 1000))
        assertFalse(fill.covers(10, 2000))
        assertTrue("filling took $ms ms", ms < 5000)
        println("FILL 3000x3000 took $ms ms")
    }

    @Test
    fun tappingRightNextToALineStillKeepsGapsClosed() {
        // One pixel inside the left edge, with a 10-pixel opening in the top edge.
        val fill = filled(FloodFill.fill(box(gap = 10), w, h, 54, 100, red))
        assertTrue(fill.covers(100, 100))
        assertFalse("doesn't leak through the opening", fill.covers(20, 20))
    }

    @Test
    fun aClosedThinArmFillsAllTheWay() {
        // All ink, with an empty chamber and a thin empty arm off its side.
        val pixels = IntArray(w * h) { black }
        for (y in 10..89) for (x in 10..69) pixels[y * w + x] = 0
        for (y in 45..54) for (x in 70..139) pixels[y * w + x] = 0
        val fill = filled(FloodFill.fill(pixels, w, h, 40, 50, red))
        assertTrue(fill.covers(40, 50))
        assertTrue("the arm's far end", fill.covers(138, 50))
        assertFalse(fill.covers(150, 50))
    }

    @Test
    fun theFillDoesNotSlipThroughACornerIntoAnotherArea() {
        // The closed arm's end touches a separate area only corner to corner.
        val pixels = IntArray(w * h) { black }
        for (y in 10..89) for (x in 10..69) pixels[y * w + x] = 0
        for (y in 45..54) for (x in 70..139) pixels[y * w + x] = 0
        for (y in 55..199) for (x in 140..199) pixels[y * w + x] = 0
        val fill = filled(FloodFill.fill(pixels, w, h, 40, 50, red))
        assertTrue(fill.covers(138, 50))
        assertFalse("the separate area", fill.covers(145, 60))
    }
}
