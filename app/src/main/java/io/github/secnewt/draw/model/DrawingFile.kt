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
 *     action: 0 = add (width, eraser, point count, x y x y ...), 1 = clear
 *
 * Anything that doesn't add up is an [IOException], never a half-loaded drawing.
 */
object DrawingFile {
    private const val MAGIC = 0x4E445257 // "NDRW"
    private const val FORMAT = 1
    private const val ADD: Byte = 0
    private const val CLEAR: Byte = 1

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
        if (format != FORMAT) throw IOException("Unknown file format $format")
        val version = data.readLong()
        val done = readActions(data)
        val undone = readActions(data)
        if (data.read() != -1) throw IOException("Extra data at the end of the file")
        return Drawing.History(done, undone, version)
    }

    private fun writeActions(out: DataOutputStream, actions: List<Drawing.Action>) {
        out.writeInt(actions.size)
        for (action in actions) {
            when (action) {
                is Drawing.Action.Add -> {
                    out.writeByte(ADD.toInt())
                    val stroke = action.stroke
                    out.writeFloat(stroke.width)
                    out.writeBoolean(stroke.eraser)
                    out.writeInt(stroke.points.size)
                    for (p in stroke.points) {
                        out.writeFloat(p.x)
                        out.writeFloat(p.y)
                    }
                }
                is Drawing.Action.Clear -> out.writeByte(CLEAR.toInt())
            }
        }
    }

    private fun readActions(data: DataInputStream): List<Drawing.Action> {
        val count = readCount(data, allowZero = true)
        return List(count) {
            when (val type = data.readByte()) {
                ADD -> {
                    val width = data.readFloat()
                    if (!width.isFinite() || width <= 0f) throw IOException("Bad brush width $width")
                    val eraser = data.readBoolean()
                    val points = List(readCount(data, allowZero = false)) { Point(data.readFloat(), data.readFloat()) }
                    Drawing.Action.Add(Stroke(points, width, eraser))
                }
                CLEAR -> Drawing.Action.Clear()
                else -> throw IOException("Unknown action $type")
            }
        }
    }

    private fun readCount(data: DataInputStream, allowZero: Boolean): Int {
        val count = data.readInt()
        if (count < (if (allowZero) 0 else 1) || count > MAX_COUNT) throw IOException("Bad count $count")
        return count
    }
}
