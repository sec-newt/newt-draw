package io.github.secnewt.draw.model

/** A point on the paper, in paper pixels. */
data class Point(val x: Float, val y: Float)

/** Something on the paper: a [Stroke] or a [Fill]. Never changed after it's made. */
sealed interface Mark {
    /** The area it covers, so undo only has to redraw there. */
    val bounds: Bounds
}

/**
 * One finger-down-to-finger-up line. [width] is in paper pixels; [color] is ARGB. An
 * [eraser] stroke removes ink instead of adding it (its color doesn't matter).
 */
class Stroke(
    val points: List<Point>,
    val width: Float,
    val eraser: Boolean = false,
    val color: Int = Palette.Black.argb,
) : Mark {
    init {
        require(points.isNotEmpty()) { "A stroke needs at least one point" }
    }

    /** The area the stroke's ink covers, including its width and a pixel for smoothing. */
    override val bounds: Bounds by lazy {
        val pad = width / 2f + 1f
        Bounds(
            points.minOf { it.x } - pad,
            points.minOf { it.y } - pad,
            points.maxOf { it.x } + pad,
            points.maxOf { it.y } + pad,
        )
    }
}

/**
 * A fill-bucket fill: the area it worked out when you tapped, painted in [color].
 * [spans] are runs of pixels, three numbers each: row, first column, last column.
 * [behind] fills go under the ink already there (filling empty paper inside lines, so the
 * lines stay crisp on top); the others paint over (recoloring an area already filled).
 */
class Fill(val spans: IntArray, val color: Int, val behind: Boolean) : Mark {
    init {
        require(spans.isNotEmpty() && spans.size % 3 == 0) { "A fill needs at least one run of pixels" }
    }

    val runs: Int get() = spans.size / 3

    override val bounds: Bounds by lazy {
        var left = Int.MAX_VALUE
        var top = Int.MAX_VALUE
        var right = Int.MIN_VALUE
        var bottom = Int.MIN_VALUE
        for (i in 0 until runs) {
            val y = spans[3 * i]
            top = minOf(top, y)
            bottom = maxOf(bottom, y)
            left = minOf(left, spans[3 * i + 1])
            right = maxOf(right, spans[3 * i + 2])
        }
        Bounds(left.toFloat(), top.toFloat(), right + 1f, bottom + 1f)
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
