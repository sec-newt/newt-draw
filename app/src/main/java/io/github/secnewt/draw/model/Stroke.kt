package io.github.secnewt.draw.model

/** A point on the paper, in paper pixels. */
data class Point(val x: Float, val y: Float)

/**
 * One finger-down-to-finger-up line. [width] is in pixels. An [eraser] stroke removes ink
 * instead of adding it. Strokes are never changed after they're made.
 */
class Stroke(val points: List<Point>, val width: Float, val eraser: Boolean = false) {
    init {
        require(points.isNotEmpty()) { "A stroke needs at least one point" }
    }

    /** The area the stroke's ink covers, including its width and a pixel for smoothing. */
    val bounds: Bounds by lazy {
        val pad = width / 2f + 1f
        Bounds(
            points.minOf { it.x } - pad,
            points.minOf { it.y } - pad,
            points.maxOf { it.x } + pad,
            points.maxOf { it.y } + pad,
        )
    }
}

/** A rectangle on the paper. */
data class Bounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun intersects(other: Bounds) =
        left < other.right && other.left < right && top < other.bottom && other.top < bottom

    fun union(other: Bounds) = Bounds(
        minOf(left, other.left),
        minOf(top, other.top),
        maxOf(right, other.right),
        maxOf(bottom, other.bottom),
    )
}
