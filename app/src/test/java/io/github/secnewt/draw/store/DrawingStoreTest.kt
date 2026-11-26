package io.github.secnewt.draw.store

import io.github.secnewt.draw.model.Drawing
import io.github.secnewt.draw.model.DrawingFile
import io.github.secnewt.draw.model.Point
import io.github.secnewt.draw.model.Stroke
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
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

    private fun Drawing.picture() = strokes.map { s -> Triple(s.width, s.eraser, s.points) }

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
        assertTrue(DrawingStore(folder.root).save(drawing.history()))
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
        val store = DrawingStore(folder.root)
        assertSame(DrawingStore.Loaded.Damaged, store.load())
        store.save(Drawing().apply { add(line(100f)) }.history())
        assertArrayEquals(garbage, File(folder.root, "drawing.ndrw.damaged").readBytes())
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
}
