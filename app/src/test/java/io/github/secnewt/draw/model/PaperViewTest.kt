package io.github.secnewt.draw.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaperViewTest {
    /** A phone-sized drawing area: 1080 x 1500 screen pixels. */
    private val limits = PaperView.Limits(1080f, 1500f)
    private val fit = PaperView.fit(limits)

    private fun assertNear(expected: Point, actual: Point) {
        assertEquals(expected.x, actual.x, 0.01f)
        assertEquals(expected.y, actual.y, 0.01f)
    }

    @Test
    fun wholePageFitsAndIsCentred() {
        assertEquals(1080f / 3000f, fit.scale, 0.0001f)
        assertNear(Point(0f, 210f), fit.toScreen(Point(0f, 0f)))
        assertNear(Point(1080f, 1290f), fit.toScreen(Point(3000f, 3000f)))
    }

    @Test
    fun screenAndPaperPositionsRoundTrip() {
        val view = PaperView(2.5f, -400f, -900f)
        val paper = Point(1234f, 567f)
        assertNear(paper, view.toPaper(view.toScreen(paper)))
    }

    @Test
    fun pinchKeepsTheSpotBetweenTheFingersStill() {
        val between = Point(500f, 700f)
        val under = fit.toPaper(between)
        val zoomed = fit.moved(between, zoom = 3f, panX = 0f, panY = 0f, limits = limits)
        assertEquals(fit.scale * 3f, zoomed.scale, 0.0001f)
        assertNear(between, zoomed.toScreen(under))
    }

    @Test
    fun dragSlidesThePaper() {
        val zoomed = fit.moved(Point(540f, 750f), 4f, 0f, 0f, limits)
        val dragged = zoomed.moved(Point(540f, 750f), 1f, panX = 100f, panY = -50f, limits = limits)
        assertEquals(zoomed.offsetX + 100f, dragged.offsetX, 0.01f)
        assertEquals(zoomed.offsetY - 50f, dragged.offsetY, 0.01f)
    }

    @Test
    fun zoomStopsAtTheLimits() {
        val tooFar = fit.moved(Point(0f, 0f), 1000f, 0f, 0f, limits)
        assertEquals(limits.maxScale, tooFar.scale, 0.0001f)
        val tooSmall = fit.moved(Point(0f, 0f), 0.01f, 0f, 0f, limits)
        assertEquals(limits.minScale, tooSmall.scale, 0.0001f)
    }

    @Test
    fun wideDrawingAreaStopsAtTwoScreenPixelsPerPaperPixel() {
        val wide = PaperView.Limits(2400f, 1800f)
        assertEquals(2f, PaperView.fit(wide).zoomedAtCentre(10f, wide).scale, 0.0001f)
    }

    @Test
    fun wholePageStillFitsWhenItNeedsMoreThanTwoScreenPixelsPerPaperPixel() {
        val huge = PaperView.Limits(9000f, 7500f)
        val whole = PaperView.fit(huge)
        assertEquals(2.5f, whole.scale, 0.0001f)
        assertEquals(whole, whole.zoomedAtCentre(10f, huge))
    }

    @Test
    fun thePaperCanNeverBeDraggedAway() {
        val zoomed = fit.moved(Point(540f, 750f), 4f, 0f, 0f, limits)
        val flung = zoomed.moved(Point(540f, 750f), 1f, panX = 99999f, panY = 99999f, limits = limits)
        // The paper's top-left corner can't come further in than the screen's top-left.
        assertNear(Point(0f, 0f), flung.toScreen(Point(0f, 0f)))
        val other = zoomed.moved(Point(540f, 750f), 1f, panX = -99999f, panY = -99999f, limits = limits)
        assertNear(Point(1080f, 1500f), other.toScreen(Point(3000f, 3000f)))
    }

    @Test
    fun aSizeChangeKeepsTheZoom() {
        val zoomed = fit.moved(Point(540f, 750f), 3f, 0f, 0f, limits)
        val taller = PaperView.Limits(1080f, 1560f)
        assertEquals(zoomed.scale, zoomed.refitted(taller).scale, 0.0001f)
    }

    @Test
    fun zoomButtonsZoomAroundTheMiddle() {
        val middle = Point(540f, 750f)
        val under = fit.toPaper(middle)
        val zoomed = fit.zoomedAtCentre(2f, limits)
        assertNear(middle, zoomed.toScreen(under))
    }

    @Test
    fun zoomPercentIsRelativeToWholePage() {
        assertEquals(100, fit.percentOf(fit))
        assertEquals(300, fit.moved(Point(0f, 0f), 3f, 0f, 0f, limits).percentOf(fit))
    }

    @Test
    fun zoomingInMakesTheFingerMorePrecise() {
        // The same 30-pixel finger wobble covers a third as much paper at 3x zoom.
        val zoomed = fit.moved(Point(540f, 750f), 3f, 0f, 0f, limits)
        fun paperSpan(view: PaperView) = view.toPaper(Point(530f, 750f)).x - view.toPaper(Point(500f, 750f)).x
        assertTrue(paperSpan(zoomed) < paperSpan(fit) / 2.9f)
    }
}
