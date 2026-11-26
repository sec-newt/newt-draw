package io.github.secnewt.draw.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import io.github.secnewt.draw.model.Point
import io.github.secnewt.draw.model.Stroke

/** Paper is always white and ink always black in Phase 1, in light and dark mode alike. */
const val PAPER = Color.WHITE
const val INK = Color.BLACK

/**
 * Draws strokes onto an Android canvas. Pen strokes add black ink; eraser strokes clear
 * the ink back to transparent, so ink lives on its own see-through layer above the paper.
 */
object StrokePainter {

    private fun paintFor(stroke: Stroke, previewOnPaper: Boolean) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = stroke.width
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        when {
            !stroke.eraser -> color = INK
            // While the eraser is still moving, it's drawn in paper color on top of the
            // finished picture; on white paper that looks exactly like the real erase.
            previewOnPaper -> color = PAPER
            else -> xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }
    }

    /** Draws [stroke] onto the ink layer [canvas]. */
    fun draw(canvas: Canvas, stroke: Stroke) = draw(canvas, stroke, previewOnPaper = false)

    /** Draws a stroke still in progress over the finished picture. */
    fun drawPreview(canvas: Canvas, stroke: Stroke) = draw(canvas, stroke, previewOnPaper = true)

    private fun draw(canvas: Canvas, stroke: Stroke, previewOnPaper: Boolean) {
        val paint = paintFor(stroke, previewOnPaper)
        val points = stroke.points
        if (points.size == 1) {
            // A tap: a round dot the width of the brush.
            paint.style = Paint.Style.FILL
            canvas.drawCircle(points[0].x, points[0].y, stroke.width / 2f, paint)
            return
        }
        paint.style = Paint.Style.STROKE
        canvas.drawPath(smoothPath(points), paint)
    }

    /**
     * Curves through the midpoints between finger samples, using each sample as the bend.
     * This rounds off the corners a straight join-the-dots line would have.
     */
    fun smoothPath(points: List<Point>): Path = Path().apply {
        moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) {
            val bend = points[i - 1]
            val mid = Point((bend.x + points[i].x) / 2f, (bend.y + points[i].y) / 2f)
            if (i == 1) lineTo(mid.x, mid.y) else quadTo(bend.x, bend.y, mid.x, mid.y)
        }
        val last = points.last()
        lineTo(last.x, last.y)
    }

    /** A see-through ink layer with every stroke drawn in order. */
    fun inkLayer(width: Int, height: Int, strokes: List<Stroke>): Bitmap {
        val layer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(layer)
        strokes.forEach { draw(canvas, it) }
        return layer
    }

    /** The finished picture: white paper with the ink on top. This is what gets saved. */
    fun picture(width: Int, height: Int, strokes: List<Stroke>): Bitmap {
        val picture = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(picture)
        canvas.drawColor(PAPER)
        val ink = inkLayer(width, height, strokes)
        canvas.drawBitmap(ink, 0f, 0f, null)
        ink.recycle()
        return picture
    }
}
