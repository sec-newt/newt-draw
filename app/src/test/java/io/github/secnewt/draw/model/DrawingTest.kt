package io.github.secnewt.draw.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawingTest {
    private fun stroke(n: Int) = Stroke(listOf(Point(n.toFloat(), n.toFloat())), width = 5f)

    @Test
    fun undoAndRedoMoveStrokes() {
        val drawing = Drawing()
        val a = stroke(1)
        val b = stroke(2)
        drawing.add(a)
        drawing.add(b)
        assertTrue(drawing.undo())
        assertEquals(listOf(a), drawing.strokes)
        assertTrue(drawing.redo())
        assertEquals(listOf(a, b), drawing.strokes)
    }

    @Test
    fun aNewStrokeAfterUndoClearsRedo() {
        val drawing = Drawing()
        drawing.add(stroke(1))
        drawing.undo()
        drawing.add(stroke(2))
        assertFalse(drawing.canRedo)
        assertFalse(drawing.redo())
    }

    @Test
    fun nothingToUndoOrRedoIsHarmless() {
        val drawing = Drawing()
        assertFalse(drawing.undo())
        assertFalse(drawing.redo())
        assertTrue(drawing.strokes.isEmpty())
    }

    @Test
    fun undoFiftyTimesInARow() {
        val drawing = Drawing()
        val all = (1..60).map(::stroke)
        all.forEach(drawing::add)
        repeat(50) { assertTrue(drawing.undo()) }
        assertEquals(all.take(10), drawing.strokes)
        repeat(50) { assertTrue(drawing.redo()) }
        assertEquals(all, drawing.strokes)
    }

    @Test
    fun clearCanBeUndone() {
        val drawing = Drawing()
        val all = (1..3).map(::stroke)
        all.forEach(drawing::add)
        drawing.clear()
        assertTrue(drawing.strokes.isEmpty())
        drawing.undo()
        assertEquals(all, drawing.strokes)
        drawing.redo()
        assertTrue(drawing.strokes.isEmpty())
    }

    @Test
    fun clearingBlankPaperAddsNoHistory() {
        val drawing = Drawing()
        drawing.clear()
        assertFalse(drawing.canUndo)
    }

    @Test
    fun addingKeepsTheEarlierStrokesSoTheScreenOnlyDrawsTheNewOne() {
        val drawing = Drawing()
        val a = stroke(1)
        drawing.add(a)
        val before = drawing.strokes
        drawing.add(stroke(2))
        assertSame(before.last(), drawing.strokes[before.size - 1])
    }
}
