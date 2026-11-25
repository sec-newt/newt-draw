package io.github.secnewt.draw.model

/**
 * The drawing: a list of strokes plus undo/redo history. Undo works on actions, not
 * pictures, so it costs almost no memory however long the history gets.
 *
 * [strokes] is replaced with a new list on every change, so the screen can tell a plain
 * "one stroke added" (draw just that stroke) from anything else (redraw everything).
 */
class Drawing {
    private sealed interface Action {
        class Add(val stroke: Stroke) : Action
        class Clear(val before: List<Stroke>) : Action
    }

    var strokes: List<Stroke> = emptyList()
        private set

    private val undoStack = ArrayDeque<Action>()
    private val redoStack = ArrayDeque<Action>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun add(stroke: Stroke) {
        apply(Action.Add(stroke))
        undoStack.addLast(Action.Add(stroke))
        redoStack.clear()
    }

    /** Clears the paper. Can be undone. Does nothing on blank paper. */
    fun clear() {
        if (strokes.isEmpty()) return
        val action = Action.Clear(strokes)
        apply(action)
        undoStack.addLast(action)
        redoStack.clear()
    }

    /** Returns false when there was nothing to undo. */
    fun undo(): Boolean {
        val action = undoStack.removeLastOrNull() ?: return false
        strokes = when (action) {
            is Action.Add -> strokes.dropLast(1)
            is Action.Clear -> action.before
        }
        redoStack.addLast(action)
        return true
    }

    /** Returns false when there was nothing to redo. */
    fun redo(): Boolean {
        val action = redoStack.removeLastOrNull() ?: return false
        apply(action)
        undoStack.addLast(action)
        return true
    }

    private fun apply(action: Action) {
        strokes = when (action) {
            is Action.Add -> strokes + action.stroke
            is Action.Clear -> emptyList()
        }
    }
}
