package io.github.secnewt.draw.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import io.github.secnewt.draw.model.Fill
import io.github.secnewt.draw.model.Mark
import io.github.secnewt.draw.model.Point
import io.github.secnewt.draw.model.Stroke

/** The paper is always white, in light and dark mode alike. */
const val PAPER = Color.WHITE

/**
 * Draws marks onto an Android canvas. Ink lives on its own see-through layer: pen strokes
 * and fills add color, eraser strokes clear it back to transparent, and fills on empty
 * paper go behind the ink already there.
 */
object StrokePainter {

    private fun paintFor(stroke: Stroke, previewOnPaper: Boolean) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = stroke.width
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        when {
            !stroke.eraser -> color = stroke.color
            // While the eraser is still moving, it's drawn in paper color on top of the
            // finished picture; on white paper that looks exactly like the real erase.
            previewOnPaper -> color = PAPER
            else -> xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }
    }

    /** Draws [mark] onto the ink layer [canvas]. */
    fun draw(canvas: Canvas, mark: Mark) = when (mark) {
        is Stroke -> drawStroke(canvas, mark, previewOnPaper = false)
        is Fill -> drawFill(canvas, mark)
    }

    /** Draws a stroke still in progress over the finished picture. */
    fun drawPreview(canvas: Canvas, stroke: Stroke) = drawStroke(canvas, stroke, previewOnPaper = true)

    private fun drawStroke(canvas: Canvas, stroke: Stroke, previewOnPaper: Boolean) {
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

    /** A fill is whole pixels, so it's drawn as rows of rectangles, without smoothing. */
    private fun drawFill(canvas: Canvas, fill: Fill) {
        val paint = Paint().apply {
            color = fill.color
            style = Paint.Style.FILL
            if (fill.behind) xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OVER)
        }
        val spans = fill.spans
        for (i in 0 until fill.runs) {
            val y = spans[3 * i].toFloat()
            canvas.drawRect(spans[3 * i + 1].toFloat(), y, spans[3 * i + 2] + 1f, y + 1f, paint)
        }
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

    /** A see-through ink layer with every mark drawn in order. */
    fun inkLayer(width: Int, height: Int, marks: List<Mark>): Bitmap {
        val layer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(layer)
        marks.forEach { draw(canvas, it) }
        return layer
    }

    /**
     * The finished picture: white paper with the ink on top. This is what gets saved.
     * The ink is drawn first, then white goes in behind it in the same image, so it needs
     * just one image (a 3000 x 3000 image is 36 MB).
     */
    fun picture(width: Int, height: Int, marks: List<Mark>): Bitmap =
        inkLayer(width, height, marks).also { onPaper(Canvas(it)) }

    /** Puts white paper behind whatever is already drawn on [canvas]. */
    fun onPaper(canvas: Canvas) = canvas.drawColor(PAPER, PorterDuff.Mode.DST_OVER)
}
