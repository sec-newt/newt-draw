package io.github.secnewt.draw.model

/**
 * The drawing: a list of strokes plus undo/redo history. Undo works on actions, not
 * pictures, so it costs almost no memory however long the history gets.
 *
 * [strokes] is replaced with a new list on every change, so the screen can tell a plain
 * "one stroke added" (draw just that stroke) from anything else (redraw everything).
 *
 * The history is the whole drawing: replaying the done actions from blank paper gives
 * [strokes]. That's what autosave stores (see [History]), so undo still works after the
 * app is reopened.
 */
class Drawing {
    /** Something that was done. Immutable, so a [History] can be saved on another thread. */
    sealed interface Action {
        class Add(val mark: Mark) : Action

        /** Clearing. [before] is what was on the paper, filled in when the clear happens. */
        class Clear internal constructor(internal val before: List<Mark>) : Action {
            constructor() : this(emptyList())
        }
    }

    /** Everything needed to rebuild the drawing: what's been done, and what can be redone. */
    class History(val done: List<Action>, val undone: List<Action>, val version: Long)

    var strokes: List<Mark> = emptyList()
        private set

    /** Goes up by one with every change, so autosave can tell if there's anything new. */
    var version: Long = 0
        private set

    private val undoStack = ArrayDeque<Action>()
    private val redoStack = ArrayDeque<Action>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun add(mark: Mark) {
        undoStack.addLast(perform(Action.Add(mark)))
        redoStack.clear()
        version++
    }

    /** Clears the paper. Can be undone. Does nothing on blank paper. */
    fun clear() {
        if (strokes.isEmpty()) return
        undoStack.addLast(perform(Action.Clear()))
        redoStack.clear()
        version++
    }

    /** Returns false when there was nothing to undo. */
    fun undo(): Boolean {
        val action = undoStack.removeLastOrNull() ?: return false
        strokes = when (action) {
            is Action.Add -> strokes.dropLast(1)
            is Action.Clear -> action.before
        }
        redoStack.addLast(action)
        version++
        return true
    }

    /** Returns false when there was nothing to redo. */
    fun redo(): Boolean {
        val action = redoStack.removeLastOrNull() ?: return false
        undoStack.addLast(perform(action))
        version++
        return true
    }

    /** A copy of the history that later changes don't affect. Cheap: actions are shared. */
    fun history() = History(undoStack.toList(), redoStack.toList(), version)

    /** Does [action] and returns it as it should sit on the undo stack. */
    private fun perform(action: Action): Action = when (action) {
        is Action.Add -> action.also { strokes = strokes + it.mark }
        is Action.Clear -> Action.Clear(strokes).also { strokes = emptyList() }
    }

    companion object {
        /** Rebuilds a drawing from a saved [history], with the same undo and redo. */
        fun from(history: History) = Drawing().apply {
            // One growing list rather than a new list per stroke: a big drawing loads fast.
            val paper = ArrayList<Mark>()
            history.done.forEach { action ->
                when (action) {
                    is Action.Add -> undoStack.addLast(action).also { paper.add(action.mark) }
                    is Action.Clear -> undoStack.addLast(Action.Clear(paper.toList())).also { paper.clear() }
                }
            }
            strokes = paper.toList()
            redoStack.addAll(history.undone)
            version = history.version
        }
    }
}
