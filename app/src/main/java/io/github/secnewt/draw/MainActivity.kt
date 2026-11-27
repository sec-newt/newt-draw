package io.github.secnewt.draw

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.lifecycleScope
import io.github.secnewt.draw.model.Drawing
import io.github.secnewt.draw.render.Thumbnail
import io.github.secnewt.draw.store.DrawingStore
import io.github.secnewt.draw.store.Library
import io.github.secnewt.draw.ui.DrawScreen
import io.github.secnewt.draw.ui.DrawingsScreen
import io.github.secnewt.draw.ui.OpeningScreen
import io.github.secnewt.draw.ui.StatusAlert
import io.github.secnewt.ui.NewtTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** How long after the last change autosave waits, so a burst of strokes is one save. */
private const val SAVE_DELAY_MS = 1500L

private const val SAVE_FAILED =
    "Couldn't autosave. Is the phone's storage full? It will try again with your next change."

class MainActivity : ComponentActivity() {
    /** A drawing open on the paper. */
    private data class Editor(val entry: Library.Entry, val drawing: Drawing, val notice: String?) {
        /** Made and never drawn on: not worth keeping if you move on to another drawing. */
        val untouched get() = drawing.version == 0L
    }

    private sealed interface Screen {
        data object Opening : Screen
        data object Paper : Screen
        class Drawings(val entries: List<Library.Entry>, val message: String?) : Screen
    }

    private val screen = mutableStateOf<Screen>(Screen.Opening)

    /**
     * The open drawing. It stays open behind the list of drawings, so "Back to drawing"
     * returns to it as it was. The manifest tells Android not to rebuild the activity when
     * the theme or text size changes, so those keep it too; autosave keeps it beyond that.
     */
    private val editor = mutableStateOf<Editor?>(null)
    private val alert = mutableStateOf<StatusAlert?>(null)
    private lateinit var library: Library
    private var pendingSave: Job? = null

    /** All file work runs here, one job at a time, in order. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val files = Dispatchers.IO.limitedParallelism(1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        library = Library(filesDir)
        setContent {
            NewtTheme {
                val open = editor.value
                when (val now = screen.value) {
                    Screen.Opening -> OpeningScreen()
                    Screen.Paper -> if (open == null) {
                        OpeningScreen()
                    } else {
                        // A different drawing starts with a fresh view and status line.
                        key(open.entry.id) {
                            DrawScreen(
                                open.drawing,
                                notice = open.notice,
                                alert = alert.value,
                                onChange = ::saveSoon,
                                onDrawings = { showDrawings(null) },
                            )
                        }
                    }
                    is Screen.Drawings -> DrawingsScreen(
                        drawings = now.entries,
                        openId = open?.entry?.id,
                        loadPicture = { entry ->
                            withContext(files) { Thumbnail.read(library.thumbnail(entry.id))?.asImageBitmap() }
                        },
                        message = now.message,
                        onNew = ::newDrawing,
                        onOpen = { open(it.id, "Opened ${it.name}.") },
                        onBack = { screen.value = Screen.Paper },
                        onRename = ::rename,
                        onDelete = ::delete,
                    )
                }
            }
        }
        lifecycleScope.launch {
            val id = withContext(files) {
                library.bringInOldDrawing()
                library.current ?: library.list().firstOrNull()?.id ?: library.create().id
            }
            open(id, welcome = true)
        }
    }

    /** Opens drawing [id] on the paper, saving the one open now first. */
    private fun open(id: String, notice: String? = null, welcome: Boolean = false) {
        lifecycleScope.launch {
            val leaving = putAway()
            screen.value = Screen.Opening
            editor.value = withContext(files) {
                // A drawing made and never drawn on isn't kept when you go to another.
                if (leaving != null && leaving.untouched && leaving.entry.id != id) library.delete(leaving.entry.id)
                library.current = id
                val entry = library.entry(id)
                when (val loaded = library.store(id).load()) {
                    is DrawingStore.Loaded.Found -> Editor(
                        entry,
                        loaded.drawing,
                        when {
                            !welcome -> notice
                            loaded.drawing.strokes.isEmpty() -> null
                            else -> "Welcome back. ${entry.name} is as you left it."
                        },
                    )
                    DrawingStore.Loaded.Nothing -> Editor(entry, Drawing(), notice)
                    is DrawingStore.Loaded.Damaged -> Editor(
                        entry,
                        Drawing(),
                        if (loaded.keptAs != null) {
                            "${entry.name} couldn't be opened, so this is fresh paper. The old file was kept."
                        } else {
                            "${entry.name} couldn't be opened or moved aside, so autosave is off to " +
                                "protect it. Use Save to keep what you draw."
                        },
                    )
                }
            }
            screen.value = Screen.Paper
        }
    }

    private fun newDrawing() {
        lifecycleScope.launch {
            val current = editor.value
            // Already on blank paper nobody has drawn on: that's the new drawing.
            if (current != null && current.untouched) {
                editor.value = current.copy(notice = "Here's ${current.entry.name}, a new drawing.")
                screen.value = Screen.Paper
                return@launch
            }
            val made = withContext(files) { runCatching { library.create() }.getOrNull() }
            if (made == null) {
                showDrawings("Couldn't make a new drawing. Is the phone's storage full?")
            } else {
                open(made.id, "Here's ${made.name}, a new drawing.")
            }
        }
    }

    private fun showDrawings(message: String?) {
        lifecycleScope.launch {
            putAway()
            val entries = withContext(files) { library.list() }
            screen.value = Screen.Drawings(entries, message)
        }
    }

    private fun rename(entry: Library.Entry, name: String) {
        lifecycleScope.launch {
            val renamed = withContext(files) {
                runCatching { library.rename(entry.id, name) }.isSuccess
            }
            editor.value?.takeIf { it.entry.id == entry.id }?.let {
                editor.value = it.copy(entry = withContext(files) { library.entry(entry.id) })
            }
            val newName = Library.cleanName(name)
            showDrawings(if (renamed) "Renamed to $newName." else "Couldn't rename ${entry.name}.")
        }
    }

    private fun delete(entry: Library.Entry) {
        lifecycleScope.launch {
            if (editor.value?.entry?.id == entry.id) {
                pendingSave?.cancel()
                editor.value = null
            }
            withContext(files) { library.delete(entry.id) }
            showDrawings("Deleted ${entry.name}.")
        }
    }

    /** Saves the open drawing and waits for it. Returns it (it stays open). */
    private suspend fun putAway(): Editor? {
        val open = editor.value ?: return null
        pendingSave?.cancel()
        val snapshot = Snapshot(open)
        if (!withContext(files) { snapshot.save() }) alert.value = StatusAlert(SAVE_FAILED)
        return open
    }

    /** Saves in the background once the drawing has been still for a moment. */
    private fun saveSoon() {
        val open = editor.value ?: return
        pendingSave?.cancel()
        pendingSave = lifecycleScope.launch {
            delay(SAVE_DELAY_MS)
            val snapshot = Snapshot(open)
            if (!withContext(files) { snapshot.save() }) alert.value = StatusAlert(SAVE_FAILED)
        }
    }

    /**
     * The drawing as it is now, taken on the main thread so it can be written on another.
     * Writes the drawing, then its small picture if that changed anything or there's no
     * picture yet (a drawing from before pictures).
     */
    private inner class Snapshot(open: Editor) {
        private val id = open.entry.id
        private val history = open.drawing.history()
        private val strokes = open.drawing.strokes

        /** False if the drawing couldn't be saved. A missing picture doesn't count. */
        fun save(): Boolean = when (library.store(id).save(history)) {
            DrawingStore.Saved.Written -> true.also { Thumbnail.write(library.thumbnail(id), strokes) }
            DrawingStore.Saved.AlreadySaved -> true.also {
                val picture = library.thumbnail(id)
                if (!picture.exists()) Thumbnail.write(picture, strokes)
            }
            DrawingStore.Saved.Failed -> false
        }
    }

    /**
     * Leaving the app saves straight away, and waits for the save to finish: Android may
     * end the app any time after this returns, and a save still running then would be lost.
     * The app is already off screen, so the short wait isn't seen.
     */
    override fun onStop() {
        super.onStop()
        val open = editor.value ?: return
        pendingSave?.cancel()
        if (!Snapshot(open).save()) alert.value = StatusAlert(SAVE_FAILED)
    }
}
