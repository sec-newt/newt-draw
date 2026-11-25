package io.github.secnewt.draw.model

import kotlin.math.hypot

/** How much the line is smoothed. [radiusDp] is the length of the "string" in dp. */
enum class Smoothing(val label: String, val radiusDp: Float, val hint: String) {
    Off("Off", 0f, "Ink goes exactly where your finger goes"),
    Low("Low", 8f, "A little steadier"),
    Medium("Medium", 18f, "Good for finger drawing"),
    High("High", 32f, "Very smooth; the line trails further behind"),
}

/**
 * The "string" smoother (often called a lazy brush). The ink sits at the end of a string
 * [radius] pixels long, and the finger pulls it. The ink only moves once the finger is
 * further away than the string, and then only by the extra distance. Small wobbles never
 * pull the string tight, so they never reach the paper.
 *
 * With a radius of 0 the ink follows the finger exactly.
 */
class StringSmoother(private val radius: Float) {
    var ink: Point? = null
        private set

    /** Finger down. The first ink point is exactly under the finger. */
    fun start(finger: Point): Point {
        ink = finger
        return finger
    }

    /** Finger moved. Returns the new ink point, or null if the ink didn't move enough to matter. */
    fun move(finger: Point): Point? {
        val current = checkNotNull(ink) { "Call start() first" }
        val dx = finger.x - current.x
        val dy = finger.y - current.y
        val distance = hypot(dx, dy)
        if (distance <= radius) return null
        val pull = (distance - radius) / distance
        val next = Point(current.x + dx * pull, current.y + dy * pull)
        if (hypot(next.x - current.x, next.y - current.y) < MIN_STEP) return null
        ink = next
        return next
    }

    private companion object {
        /** Ignore moves under half a pixel; they add points without changing the line. */
        const val MIN_STEP = 0.5f
    }
}
