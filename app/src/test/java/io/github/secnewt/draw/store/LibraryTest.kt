package io.github.secnewt.draw.store

import io.github.secnewt.draw.model.Drawing
import io.github.secnewt.draw.model.Point
import io.github.secnewt.draw.model.Stroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LibraryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private var time = 1_000L
    private fun library() = Library(folder.root, clock = { time++ })

    private fun line(y: Float) = Stroke(listOf(Point(10f, y), Point(400f, y)), 16f)

    private fun DrawingStore.drawing() = (load() as DrawingStore.Loaded.Found).drawing

    @Test
    fun newDrawingsAreNumberedAndListedNewestFirst() {
        val library = library()
        val first = library.create()
        val second = library.create()
        assertEquals("Drawing 1", first.name)
        assertEquals("Drawing 2", second.name)
        library.store(first.id).file.setLastModified(5_000_000)
        library.store(second.id).file.setLastModified(4_000_000)
        assertEquals(listOf("Drawing 1", "Drawing 2"), library.list().map { it.name })
    }

    @Test
    fun numberingCarriesOnAfterTheHighest() {
        val library = library()
        val first = library.create()
        library.create()
        library.delete(first.id)
        assertEquals("Drawing 3", library.create().name)
    }

    @Test
    fun eachDrawingKeepsItsOwnPictureAndUndo() {
        val library = library()
        val a = library.create()
        val b = library.create()
        library.store(a.id).save(Drawing().apply { add(line(100f)); add(line(200f)) }.history())
        library.store(b.id).save(Drawing().apply { add(line(300f)); clear() }.history())
        val reopened = library()
        assertEquals(2, reopened.store(a.id).drawing().strokes.size)
        val other = reopened.store(b.id).drawing()
        assertTrue(other.strokes.isEmpty())
        assertTrue(other.undo())
        assertEquals(1, other.strokes.size)
    }

    @Test
    fun renameIsKeptAndTidied() {
        val library = library()
        val entry = library.create()
        library.rename(entry.id, "  Rubber   hose\nhero  ")
        assertEquals("Rubber hose hero", library().entry(entry.id).name)
        library.rename(entry.id, "   ")
        assertEquals("Rubber hose hero", library.entry(entry.id).name)
        library.rename(entry.id, "x".repeat(200))
        assertEquals(Library.MAX_NAME, library.entry(entry.id).name.length)
    }

    @Test
    fun deleteRemovesEverythingAndForgetsItWasOpen() {
        val library = library()
        val entry = library.create()
        library.current = entry.id
        library.thumbnail(entry.id).writeBytes(byteArrayOf(1))
        library.delete(entry.id)
        assertTrue(library.list().isEmpty())
        assertNull(library.current)
        assertEquals(listOf<String>(), File(folder.root, "drawings").list()!!.filter { it.startsWith(entry.id) })
    }

    @Test
    fun theOpenDrawingIsRemembered() {
        val library = library()
        library.create()
        val second = library.create()
        library.current = second.id
        assertEquals(second.id, library().current)
    }

    @Test
    fun theDrawingFromBeforeTheListComesInAsDrawing1() {
        // Phase 2b kept one drawing at files/drawing.ndrw.
        DrawingStore(folder.root).save(Drawing().apply { add(line(100f)); add(line(200f)) }.history())
        val library = library()
        library.bringInOldDrawing()
        val entries = library.list()
        assertEquals(listOf("Drawing 1"), entries.map { it.name })
        assertEquals(entries.single().id, library.current)
        assertEquals(2, library.store(entries.single().id).drawing().strokes.size)
        assertFalse(File(folder.root, "drawing.ndrw").exists())
        library.bringInOldDrawing() // nothing left to bring in
        assertEquals(1, library.list().size)
    }

    @Test
    fun onlyRealDrawingsAreListed() {
        val library = library()
        val entry = library.create()
        val dir = File(folder.root, "drawings")
        File(dir, "${entry.id}-damaged-5.ndrw").writeBytes(byteArrayOf(1))
        File(dir, "${entry.id}.ndrw.new").writeBytes(byteArrayOf(1))
        File(dir, "notes.txt").writeText("hello")
        assertEquals(listOf(entry.id), library.list().map { it.id })
    }

    @Test
    fun aCurrentPointingNowhereIsIgnored() {
        val library = library()
        File(folder.root, "drawings").mkdirs()
        File(folder.root, "drawings/current").writeText("../../somewhere")
        assertNull(library.current)
    }
}
