package io.github.secnewt.draw.store

import io.github.secnewt.draw.model.Drawing
import io.github.secnewt.draw.model.DrawingFile
import io.github.secnewt.draw.model.Fill
import io.github.secnewt.draw.model.Palette
import io.github.secnewt.draw.model.Point
import io.github.secnewt.draw.model.Stroke
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

class DrawingStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun line(y: Float, width: Float = 16f, eraser: Boolean = false) =
        Stroke(listOf(Point(10f, y), Point(200f, y + 5f), Point(400f, y)), width, eraser)

    private fun found(store: DrawingStore): Drawing {
        val loaded = store.load()
        assertTrue("expected a saved drawing, got $loaded", loaded is DrawingStore.Loaded.Found)
        return (loaded as DrawingStore.Loaded.Found).drawing
    }

    private fun Drawing.picture() = strokes.map { s -> (s as Stroke).let { Triple(it.width, it.eraser, it.points) } }

    @Test
    fun firstRunHasNothingSaved() {
        assertSame(DrawingStore.Loaded.Nothing, DrawingStore(folder.root).load())
    }

    @Test
    fun aSavedDrawingComesBackExactly() {
        val drawing = Drawing().apply {
            add(line(100f))
            add(line(300f, width = 64f, eraser = true))
            add(Stroke(listOf(Point(1500f, 1500f)), 8f)) // a single dot
        }
        assertEquals(DrawingStore.Saved.Written, DrawingStore(folder.root).save(drawing.history()))
        assertEquals(drawing.picture(), found(DrawingStore(folder.root)).picture())
    }

    @Test
    fun aClearCanStillBeUndoneAfterReopening() {
        val drawing = Drawing().apply {
            add(line(100f))
            add(line(200f))
            clear()
        }
        DrawingStore(folder.root).save(drawing.history())
        val reopened = found(DrawingStore(folder.root))
        assertTrue(reopened.strokes.isEmpty())
        assertTrue(reopened.undo())
        assertEquals(2, reopened.strokes.size)
        assertTrue(reopened.undo())
        assertEquals(1, reopened.strokes.size)
    }

    @Test
    fun redoStillWorksAfterReopening() {
        val drawing = Drawing().apply {
            add(line(100f))
            add(line(200f))
            clear()
            undo() // back to two strokes; the clear can be redone
            undo() // one stroke; line 200 can be redone
        }
        DrawingStore(folder.root).save(drawing.history())
        val reopened = found(DrawingStore(folder.root))
        assertEquals(1, reopened.strokes.size)
        assertTrue(reopened.redo())
        assertEquals(2, reopened.strokes.size)
        assertTrue(reopened.redo())
        assertTrue(reopened.strokes.isEmpty())
        // And that redone clear can be undone again.
        assertTrue(reopened.undo())
        assertEquals(2, reopened.strokes.size)
        assertTrue(reopened.canRedo)
    }

    @Test
    fun savingAnUnchangedDrawingDoesNotWriteAgain() {
        val store = DrawingStore(folder.root)
        val drawing = Drawing().apply { add(line(100f)) }
        store.save(drawing.history())
        val file = File(folder.root, "drawing.ndrw")
        file.setLastModified(0)
        store.save(drawing.history())
        assertEquals(0, file.lastModified())
    }

    @Test
    fun anOlderSaveNeverReplacesANewerOne() {
        val store = DrawingStore(folder.root)
        val drawing = Drawing().apply { add(line(100f)) }
        val older = drawing.history()
        drawing.add(line(200f))
        store.save(drawing.history())
        store.save(older) // a slow background save finishing late
        assertEquals(2, found(DrawingStore(folder.root)).strokes.size)
    }

    @Test
    fun aDamagedFileIsKeptAsideNotOverwritten() {
        val file = File(folder.root, "drawing.ndrw")
        val garbage = "not a drawing".toByteArray()
        file.writeBytes(garbage)
        val store = DrawingStore(folder.root, clock = { 1234L })
        val loaded = store.load()
        assertTrue(loaded is DrawingStore.Loaded.Damaged)
        assertEquals("drawing-damaged-1234.ndrw", (loaded as DrawingStore.Loaded.Damaged).keptAs)
        assertEquals(DrawingStore.Saved.Written, store.save(Drawing().apply { add(line(100f)) }.history()))
        assertArrayEquals(garbage, File(folder.root, "drawing-damaged-1234.ndrw").readBytes())
    }

    @Test
    fun everyDamagedFileIsKept() {
        val file = File(folder.root, "drawing.ndrw")
        file.writeBytes(byteArrayOf(1))
        DrawingStore(folder.root, clock = { 1234L }).load()
        file.writeBytes(byteArrayOf(2))
        DrawingStore(folder.root, clock = { 1234L }).load() // same moment: still its own name
        assertArrayEquals(byteArrayOf(1), File(folder.root, "drawing-damaged-1234.ndrw").readBytes())
        assertArrayEquals(byteArrayOf(2), File(folder.root, "drawing-damaged-1234-1.ndrw").readBytes())
    }

    @Test
    fun aDamagedFileThatCantBeMovedIsNeverSavedOver() {
        val file = File(folder.root, "drawing.ndrw")
        file.writeBytes(byteArrayOf(9, 9, 9))
        folder.root.setWritable(false)
        try {
            assumeTrue("needs a folder that can be made read-only", !folder.root.canWrite())
            val store = DrawingStore(folder.root)
            val loaded = store.load() as DrawingStore.Loaded.Damaged
            assertEquals(null, loaded.keptAs)
            folder.root.setWritable(true)
            // Even once the folder is writable again, this run never saves over the file.
            assertEquals(DrawingStore.Saved.Failed, store.save(Drawing().apply { add(line(100f)) }.history()))
            assertArrayEquals(byteArrayOf(9, 9, 9), file.readBytes())
        } finally {
            folder.root.setWritable(true)
        }
    }

    @Test
    fun aPositionThatIsNotANumberMeansDamaged() {
        val bytes = ByteArrayOutputStream().also {
            DrawingFile.write(Drawing().apply { add(line(100f)) }.history(), it)
        }.toByteArray()
        // The first point's x is right after: magic, format, version, count, type, width, eraser, points.
        val firstX = 4 + 4 + 8 + 4 + 1 + 4 + 1 + 4
        java.nio.ByteBuffer.wrap(bytes).putFloat(firstX, Float.NaN)
        File(folder.root, "drawing.ndrw").writeBytes(bytes)
        assertTrue(DrawingStore(folder.root).load() is DrawingStore.Loaded.Damaged)
    }

    @Test
    fun aHalfWrittenSaveIsIgnored() {
        // A crash mid-save leaves the new file unfinished; the last good save still loads.
        DrawingStore(folder.root).save(Drawing().apply { add(line(100f)) }.history())
        File(folder.root, "drawing.ndrw.new").writeBytes(byteArrayOf(1, 2, 3))
        assertEquals(1, found(DrawingStore(folder.root)).strokes.size)
    }

    @Test
    fun aCutOffFileIsDamagedNotHalfLoaded() {
        val bytes = ByteArrayOutputStream().also {
            DrawingFile.write(Drawing().apply { add(line(100f)); add(line(200f)) }.history(), it)
        }.toByteArray()
        for (cut in listOf(3, 12, bytes.size / 2, bytes.size - 1)) {
            try {
                DrawingFile.read(ByteArrayInputStream(bytes.copyOf(cut)))
                throw AssertionError("a file cut to $cut bytes loaded")
            } catch (_: IOException) {
                // expected
            }
        }
    }

    @Test
    fun aBigDrawingLoadsQuickly() {
        // 2,000 strokes of 200 points: far more than a sketch, and still well under a second.
        val drawing = Drawing().apply {
            repeat(2000) { i -> add(Stroke(List(200) { Point(it * 10f, i.toFloat()) }, 16f)) }
        }
        DrawingStore(folder.root).save(drawing.history())
        val start = System.nanoTime()
        val reopened = found(DrawingStore(folder.root))
        val ms = (System.nanoTime() - start) / 1_000_000
        assertEquals(2000, reopened.strokes.size)
        assertTrue("loading took $ms ms", ms < 1000)
    }

    @Test
    fun colorsAndFillsComeBack() {
        val fill = Fill(intArrayOf(10, 5, 20, 11, 6, 19), Palette.Red.argb, behind = true)
        val drawing = Drawing().apply {
            add(Stroke(listOf(Point(1f, 2f), Point(3f, 4f)), 16f, color = Palette.Blue.argb))
            add(fill)
        }
        DrawingStore(folder.root).save(drawing.history())
        val back = found(DrawingStore(folder.root)).strokes
        assertEquals(Palette.Blue.argb, (back[0] as Stroke).color)
        val fillBack = back[1] as Fill
        assertArrayEquals(fill.spans, fillBack.spans)
        assertEquals(Palette.Red.argb, fillBack.color)
        assertTrue(fillBack.behind)
    }

    @Test
    fun aDrawingFromBeforeColorsLoadsInBlack() {
        // Format 1, written by hand: one stroke of two points, no color.
        val bytes = ByteArrayOutputStream().also { out ->
            java.io.DataOutputStream(out).apply {
                writeInt(0x4E445257); writeInt(1); writeLong(1)
                writeInt(1); writeByte(0); writeFloat(16f); writeBoolean(false)
                writeInt(2); writeFloat(1f); writeFloat(2f); writeFloat(3f); writeFloat(4f)
                writeInt(0)
            }
        }.toByteArray()
        File(folder.root, "drawing.ndrw").writeBytes(bytes)
        val stroke = found(DrawingStore(folder.root)).strokes.single() as Stroke
        assertEquals(Palette.Black.argb, stroke.color)
        assertEquals(2, stroke.points.size)
    }

    @Test
    fun aFillRunOffThePaperMeansDamaged() {
        val drawing = Drawing().apply { add(Fill(intArrayOf(10, 5, 20), Palette.Red.argb, behind = false)) }
        val bytes = ByteArrayOutputStream().also { DrawingFile.write(drawing.history(), it) }.toByteArray()
        // The run's last column is the last int before the undone-actions count.
        java.nio.ByteBuffer.wrap(bytes).putInt(bytes.size - 8, 99_999)
        File(folder.root, "drawing.ndrw").writeBytes(bytes)
        assertTrue(DrawingStore(folder.root).load() is DrawingStore.Loaded.Damaged)
    }
}
