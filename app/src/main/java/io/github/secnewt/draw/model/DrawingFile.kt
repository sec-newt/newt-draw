package io.github.secnewt.draw.model

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * The autosave file format: a drawing's whole [Drawing.History] as plain numbers.
 *
 *     "NDRW", format version
 *     version, done actions, undone actions
 *     action: 0 = stroke (width, eraser, [color], point count, x y x y ...)
 *             1 = clear
 *             2 = fill (color, behind, run count, row first last ...)
 *
 * Format 1 (before colors) has no stroke color and no fills; its strokes load as black.
 *
 * Anything that doesn't add up is an [IOException], never a half-loaded drawing.
 */
object DrawingFile {
    private const val MAGIC = 0x4E445257 // "NDRW"
    private const val FORMAT = 2
    private const val ADD: Byte = 0
    private const val CLEAR: Byte = 1
    private const val FILL: Byte = 2

    /** Far more than anyone draws; a count above this means the file is damaged. */
    private const val MAX_COUNT = 1_000_000

    fun write(history: Drawing.History, output: OutputStream) {
        val out = DataOutputStream(output.buffered())
        out.writeInt(MAGIC)
        out.writeInt(FORMAT)
        out.writeLong(history.version)
        writeActions(out, history.done)
        writeActions(out, history.undone)
        out.flush()
    }

    fun read(input: InputStream): Drawing.History {
        val data = DataInputStream(input.buffered())
        if (data.readInt() != MAGIC) throw IOException("Not a Newt Draw file")
        val format = data.readInt()
        if (format !in 1..FORMAT) throw IOException("Unknown file format $format")
        val version = data.readLong()
        val done = readActions(data, format)
        val undone = readActions(data, format)
        if (data.read() != -1) throw IOException("Extra data at the end of the file")
        return Drawing.History(done, undone, version)
    }

    private fun writeActions(out: DataOutputStream, actions: List<Drawing.Action>) {
        out.writeInt(actions.size)
        for (action in actions) {
            when (action) {
                is Drawing.Action.Add -> when (val mark = action.mark) {
                    is Stroke -> {
                        out.writeByte(ADD.toInt())
                        out.writeFloat(mark.width)
                        out.writeBoolean(mark.eraser)
                        out.writeInt(mark.color)
                        out.writeInt(mark.points.size)
                        for (p in mark.points) {
                            out.writeFloat(p.x)
                            out.writeFloat(p.y)
                        }
                    }
                    is Fill -> {
                        out.writeByte(FILL.toInt())
                        out.writeInt(mark.color)
                        out.writeBoolean(mark.behind)
                        out.writeInt(mark.runs)
                        for (v in mark.spans) out.writeInt(v)
                    }
                }
                is Drawing.Action.Clear -> out.writeByte(CLEAR.toInt())
            }
        }
    }

    private fun readActions(data: DataInputStream, format: Int): List<Drawing.Action> {
        val count = readCount(data, allowZero = true)
        return List(count) {
            when (val type = data.readByte()) {
                ADD -> {
                    val width = data.readFloat()
                    if (!width.isFinite() || width <= 0f) throw IOException("Bad brush width $width")
                    val eraser = data.readBoolean()
                    val color = if (format >= 2) data.readInt() else Palette.Black.argb
                    val points = List(readCount(data, allowZero = false)) { Point(readCoordinate(data), readCoordinate(data)) }
                    Drawing.Action.Add(Stroke(points, width, eraser, color))
                }
                FILL -> if (format >= 2) Drawing.Action.Add(readFill(data)) else throw IOException("Unknown action $type")
                CLEAR -> Drawing.Action.Clear()
                else -> throw IOException("Unknown action $type")
            }
        }
    }

    private fun readFill(data: DataInputStream): Fill {
        val color = data.readInt()
        val behind = data.readBoolean()
        val spans = IntArray(3 * readCount(data, allowZero = false))
        for (i in spans.indices step 3) {
            val y = data.readInt()
            val first = data.readInt()
            val last = data.readInt()
            if (y !in 0 until PAPER_HEIGHT || first !in 0 until PAPER_WIDTH || last !in first until PAPER_WIDTH) {
                throw IOException("Bad fill run $y $first $last")
            }
            spans[i] = y
            spans[i + 1] = first
            spans[i + 2] = last
        }
        return Fill(spans, color, behind)
    }

    private fun readCoordinate(data: DataInputStream): Float {
        val value = data.readFloat()
        if (!value.isFinite()) throw IOException("Bad position $value")
        return value
    }

    private fun readCount(data: DataInputStream, allowZero: Boolean): Int {
        val count = data.readInt()
        if (count < (if (allowZero) 0 else 1) || count > MAX_COUNT) throw IOException("Bad count $count")
        return count
    }
}
