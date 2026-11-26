package io.github.secnewt.draw

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import io.github.secnewt.draw.model.Drawing
import io.github.secnewt.draw.store.DrawingStore
import io.github.secnewt.draw.ui.DrawScreen
import io.github.secnewt.ui.NewtTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How long after the last change autosave waits, so a burst of strokes is one save. */
private const val SAVE_DELAY_MS = 1500L

class MainActivity : ComponentActivity() {
    /**
     * The picture lives as long as the activity, and autosave keeps it beyond that. The
     * manifest keeps the app upright and tells Android not to rebuild the activity when the
     * theme or text size changes.
     */
    private lateinit var drawing: Drawing
    private lateinit var store: DrawingStore
    private var pendingSave: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        store = DrawingStore(filesDir)
        val notice = when (val loaded = store.load()) {
            is DrawingStore.Loaded.Found -> {
                drawing = loaded.drawing
                if (drawing.strokes.isEmpty()) null else "Welcome back. Your drawing is as you left it."
            }
            DrawingStore.Loaded.Nothing -> {
                drawing = Drawing()
                null
            }
            DrawingStore.Loaded.Damaged -> {
                drawing = Drawing()
                "Your last drawing couldn't be opened, so this is fresh paper. The old file was kept."
            }
        }
        setContent {
            NewtTheme {
                DrawScreen(drawing, notice = notice, onChange = ::saveSoon)
            }
        }
    }

    /** Saves in the background once the drawing has been still for a moment. */
    private fun saveSoon() {
        pendingSave?.cancel()
        pendingSave = lifecycleScope.launch {
            delay(SAVE_DELAY_MS)
            val history = drawing.history()
            launch(Dispatchers.IO) { store.save(history) }
        }
    }

    /** Leaving the app saves straight away: Android may close it any time after this. */
    override fun onStop() {
        super.onStop()
        pendingSave?.cancel()
        store.save(drawing.history())
    }
}
