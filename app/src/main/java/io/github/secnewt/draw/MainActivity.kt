package io.github.secnewt.draw

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import io.github.secnewt.draw.model.Drawing
import io.github.secnewt.draw.store.DrawingStore
import io.github.secnewt.draw.ui.DrawScreen
import io.github.secnewt.draw.ui.OpeningScreen
import io.github.secnewt.draw.ui.StatusAlert
import io.github.secnewt.ui.NewtTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** How long after the last change autosave waits, so a burst of strokes is one save. */
private const val SAVE_DELAY_MS = 1500L

private const val SAVE_FAILED =
    "Couldn't autosave. Is the phone's storage full? It will try again with your next change."

class MainActivity : ComponentActivity() {
    /** The drawing once it's been opened, and what to say about it. */
    private class Opened(val drawing: Drawing, val notice: String?)

    /**
     * The picture lives as long as the activity, and autosave keeps it beyond that. The
     * manifest keeps the app upright and tells Android not to rebuild the activity when the
     * theme or text size changes.
     */
    private val opened = mutableStateOf<Opened?>(null)
    private val alert = mutableStateOf<StatusAlert?>(null)
    private lateinit var store: DrawingStore
    private var pendingSave: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        store = DrawingStore(filesDir)
        setContent {
            NewtTheme {
                val ready = opened.value
                if (ready == null) {
                    OpeningScreen()
                } else {
                    DrawScreen(ready.drawing, notice = ready.notice, alert = alert.value, onChange = ::saveSoon)
                }
            }
        }
        // Reading and rebuilding a big drawing takes a moment; do it off the main thread.
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) { store.load() }
            opened.value = when (loaded) {
                is DrawingStore.Loaded.Found -> Opened(
                    loaded.drawing,
                    if (loaded.drawing.strokes.isEmpty()) null else "Welcome back. Your drawing is as you left it.",
                )
                DrawingStore.Loaded.Nothing -> Opened(Drawing(), null)
                is DrawingStore.Loaded.Damaged -> Opened(
                    Drawing(),
                    if (loaded.keptAs != null) {
                        "Your last drawing couldn't be opened, so this is fresh paper. The old file was kept."
                    } else {
                        "Your last drawing couldn't be opened or moved aside, so autosave is off to " +
                            "protect it. Use Save to keep what you draw."
                    },
                )
            }
        }
    }

    /** Saves in the background once the drawing has been still for a moment. */
    private fun saveSoon() {
        val drawing = opened.value?.drawing ?: return
        pendingSave?.cancel()
        pendingSave = lifecycleScope.launch {
            delay(SAVE_DELAY_MS)
            val history = drawing.history()
            val saved = withContext(Dispatchers.IO) { store.save(history) }
            if (!saved) alert.value = StatusAlert(SAVE_FAILED)
        }
    }

    /**
     * Leaving the app saves straight away, and waits for the save to finish: Android may
     * end the app any time after this returns, and a save still running then would be lost.
     * The app is already off screen, so the short wait isn't seen.
     */
    override fun onStop() {
        super.onStop()
        val drawing = opened.value?.drawing ?: return
        pendingSave?.cancel()
        if (!store.save(drawing.history())) alert.value = StatusAlert(SAVE_FAILED)
    }
}
