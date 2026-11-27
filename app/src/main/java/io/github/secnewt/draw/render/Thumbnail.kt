package io.github.secnewt.draw.render

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import io.github.secnewt.draw.model.PAPER_HEIGHT
import io.github.secnewt.draw.model.PAPER_WIDTH
import io.github.secnewt.draw.model.Mark
import java.io.File

/** The small picture of a drawing shown in the list of drawings. */
object Thumbnail {
    /** Pixels across. Big enough to recognise a drawing at 96dp on a sharp phone screen. */
    const val SIZE = 300

    /** The whole paper, shrunk, on white. Erased ink shows as white paper. */
    fun render(marks: List<Mark>): Bitmap {
        val bitmap = Bitmap.createBitmap(SIZE, SIZE * PAPER_HEIGHT / PAPER_WIDTH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.save()
        canvas.scale(SIZE.toFloat() / PAPER_WIDTH, SIZE.toFloat() / PAPER_WIDTH)
        marks.forEach { StrokePainter.draw(canvas, it) }
        canvas.restore()
        StrokePainter.onPaper(canvas)
        return bitmap
    }

    /** Writes [marks]' picture to [file], replacing it whole. Returns false if it couldn't. */
    @Synchronized
    fun write(file: File, marks: List<Mark>): Boolean {
        val bitmap = render(marks)
        val temp = File(file.parentFile, file.name + ".new")
        return try {
            val written = temp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            written && temp.renameTo(file)
        } catch (e: java.io.IOException) {
            false
        } finally {
            temp.delete()
            bitmap.recycle()
        }
    }

    /** Null if there's no picture yet (a drawing from before pictures, say). */
    fun read(file: File): Bitmap? = if (file.exists()) BitmapFactory.decodeFile(file.path) else null
}
