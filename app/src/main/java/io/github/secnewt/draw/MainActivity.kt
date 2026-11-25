package io.github.secnewt.draw

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.secnewt.draw.model.Drawing
import io.github.secnewt.draw.ui.DrawScreen
import io.github.secnewt.ui.NewtTheme

class MainActivity : ComponentActivity() {
    /**
     * The picture lives as long as the activity. The manifest keeps the app upright (strokes
     * are in screen pixels, so turning would cut part of the drawing off) and tells Android
     * not to rebuild the activity when the theme or text size changes, so those keep it.
     * (Android closing the app in the background still loses it; autosave is Phase 2.)
     */
    private val drawing = Drawing()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NewtTheme {
                DrawScreen(drawing)
            }
        }
    }
}
