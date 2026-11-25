package io.github.secnewt.draw.model

/** A point on the paper, in screen pixels. */
data class Point(val x: Float, val y: Float)

/**
 * One finger-down-to-finger-up line. [width] is in pixels. An [eraser] stroke removes ink
 * instead of adding it. Strokes are never changed after they're made.
 */
class Stroke(val points: List<Point>, val width: Float, val eraser: Boolean = false) {
    init {
        require(points.isNotEmpty()) { "A stroke needs at least one point" }
    }
}
