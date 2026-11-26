package io.github.secnewt.draw.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random

class StringSmootherTest {

    /** A left-to-right finger line along y = 500 with up to ±[wobble] px of shake. */
    private fun shakyLine(wobble: Float, seed: Int = 7): List<Point> {
        val random = Random(seed)
        return (0..300).map { i -> Point(100f + i * 3f, 500f + (random.nextFloat() * 2f - 1f) * wobble) }
    }

    private fun smooth(finger: List<Point>, radius: Float): List<Point> {
        val smoother = StringSmoother(radius)
        return listOf(smoother.start(finger.first())) + finger.drop(1).mapNotNull { smoother.move(it) }
    }

    /** How far the line strays from y = 500, on average. */
    private fun averageWobble(points: List<Point>) = points.map { abs(it.y - 500f) }.average()

    /** Total length drawn: shake makes a line longer than the straight distance it covers. */
    private fun length(points: List<Point>) = points.zipWithNext { a, b -> hypot(b.x - a.x, b.y - a.y) }.sum()

    @Test
    fun offFollowsTheFingerExactly() {
        val finger = shakyLine(wobble = 6f)
        assertEquals(finger, smooth(finger, radius = 0f))
    }

    @Test
    fun aShakyLineComesOutSmooth() {
        val finger = shakyLine(wobble = 8f)
        val ink = smooth(finger, radius = 40f)
        // Skip the start: the ink begins exactly under the finger, before the string is tight.
        val settled = ink.drop(ink.size / 5)
        val before = averageWobble(finger)
        val after = averageWobble(settled)
        assertTrue("wobble should drop to under a quarter: before $before, after $after", after < before / 4)
        assertTrue("the line should be shorter, not zig-zagging", length(ink) < length(finger))
    }

    @Test
    fun moreSmoothingIsSmoother() {
        val finger = shakyLine(wobble = 8f)
        val low = averageWobble(smooth(finger, 10f).drop(60))
        val high = averageWobble(smooth(finger, 40f).drop(60))
        assertTrue("high ($high) should be steadier than low ($low)", high < low)
    }

    @Test
    fun inkStaysWithinTheStringOfTheFinger() {
        val finger = shakyLine(wobble = 8f)
        val smoother = StringSmoother(30f)
        smoother.start(finger.first())
        finger.drop(1).forEach { f ->
            smoother.move(f)
            val ink = smoother.ink!!
            assertTrue(hypot(f.x - ink.x, f.y - ink.y) <= 30.01f)
        }
    }

    @Test
    fun smallWigglesInsideTheStringDrawNothing() {
        val smoother = StringSmoother(20f)
        smoother.start(Point(0f, 0f))
        assertNull(smoother.move(Point(10f, 5f)))
        assertNull(smoother.move(Point(-12f, 3f)))
        assertEquals(Point(0f, 0f), smoother.ink)
    }

    @Test
    fun aStraightLineStaysStraight() {
        val finger = (0..100).map { Point(it * 5f, 200f) }
        val ink = smooth(finger, 25f)
        assertTrue(ink.all { it.y == 200f })
        assertTrue("ink trails the finger by the string length", abs(ink.last().x - (500f - 25f)) < 0.01f)
    }
}
