package io.github.secnewt.draw.model

import kotlin.math.max
import kotlin.math.min

/** The paper's size in paper pixels. Strokes are stored in these units. */
const val PAPER_WIDTH = 3000
const val PAPER_HEIGHT = 3000

/**
 * Where the paper sits on screen: screen = paper * [scale] + ([offsetX], [offsetY]).
 * Immutable; every move makes a new one.
 */
data class PaperView(val scale: Float, val offsetX: Float, val offsetY: Float) {

    fun toPaper(screen: Point) = Point((screen.x - offsetX) / scale, (screen.y - offsetY) / scale)

    fun toScreen(paper: Point) = Point(paper.x * scale + offsetX, paper.y * scale + offsetY)

    /** Scale as a percentage of "whole page fits on screen", for showing the zoom level. */
    fun percentOf(fit: PaperView) = (scale / fit.scale * 100f).toInt()

    /**
     * Two fingers moved: zoom by [zoom] around [centroid] (the spot between the fingers stays
     * under them), then slide by [panX], [panY]. The result is kept within [limits].
     */
    fun moved(centroid: Point, zoom: Float, panX: Float, panY: Float, limits: Limits): PaperView {
        val newScale = (scale * zoom).coerceIn(limits.minScale, limits.maxScale)
        val applied = newScale / scale
        val zoomed = PaperView(
            newScale,
            centroid.x - (centroid.x - offsetX) * applied + panX,
            centroid.y - (centroid.y - offsetY) * applied + panY,
        )
        return zoomed.keptOnScreen(limits)
    }

    /**
     * The screen area changed size (for example the tools grew): keep the same spot and
     * zoom where possible, just brought back within the new [limits].
     */
    fun refitted(limits: Limits) = copy(scale = scale.coerceIn(limits.minScale, limits.maxScale)).keptOnScreen(limits)

    /** Zoom by [zoom] around the middle of the screen (for the Zoom in / Zoom out buttons). */
    fun zoomedAtCentre(zoom: Float, limits: Limits) =
        moved(Point(limits.viewWidth / 2f, limits.viewHeight / 2f), zoom, 0f, 0f, limits)

    /**
     * Never lets the paper slide fully away. When it's smaller than the screen it stays
     * centred; when bigger, its edges can't come further in than the screen's edges.
     */
    fun keptOnScreen(limits: Limits): PaperView {
        fun clamp(offset: Float, paperSize: Int, viewSize: Float): Float {
            val shown = paperSize * scale
            return if (shown <= viewSize) (viewSize - shown) / 2f else offset.coerceIn(viewSize - shown, 0f)
        }
        return copy(
            offsetX = clamp(offsetX, PAPER_WIDTH, limits.viewWidth),
            offsetY = clamp(offsetY, PAPER_HEIGHT, limits.viewHeight),
        )
    }

    /** The screen area the paper is shown in, and how far zoom may go. */
    data class Limits(val viewWidth: Float, val viewHeight: Float) {
        /** Whole page fits on screen. */
        val minScale: Float = min(viewWidth / PAPER_WIDTH, viewHeight / PAPER_HEIGHT)

        /**
         * At most 2 screen pixels per paper pixel (closer than that, the finished ink would
         * look soft), unless the whole-page view already needs a higher scale.
         */
        val maxScale: Float = max(2f, minScale)
    }

    companion object {
        /** The whole page, centred in the view. */
        fun fit(limits: Limits) = PaperView(limits.minScale, 0f, 0f).keptOnScreen(limits)
    }
}
