package io.github.secnewt.draw.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.secnewt.draw.model.BrushSize
import io.github.secnewt.draw.model.Drawing
import io.github.secnewt.draw.model.Smoothing
import io.github.secnewt.draw.model.Tool
import io.github.secnewt.draw.render.PictureSaver
import io.github.secnewt.draw.render.StrokePainter
import io.github.secnewt.ui.NewtButton
import io.github.secnewt.ui.NewtChoice
import io.github.secnewt.ui.NewtPickList
import io.github.secnewt.ui.NewtPrimaryButton
import io.github.secnewt.ui.NewtTone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val HELLO = "One finger draws. A second finger cancels the line."

private enum class Dialog { Size, Smoothing, Clear }

/**
 * The whole app in Phase 1: the paper on top, a status line, then the tools.
 * [drawing] outlives this screen (it's held by the activity), so turning the phone or
 * switching dark mode doesn't lose the picture.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DrawScreen(drawing: Drawing) {
    var strokes by remember { mutableStateOf(drawing.strokes) }
    var tool by rememberSaveable { mutableStateOf(Tool.Pen) }
    var size by rememberSaveable { mutableStateOf(BrushSize.Medium) }
    var smoothing by rememberSaveable { mutableStateOf(Smoothing.Medium) }
    var status by remember { mutableStateOf(HELLO) }
    var dialog by remember { mutableStateOf<Dialog?>(null) }
    var paperSize by remember { mutableStateOf(IntSize.Zero) }
    var saving by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun refresh() {
        strokes = drawing.strokes
    }

    fun save() {
        if (saving) return
        if (strokes.isEmpty()) {
            status = "Nothing to save yet. The paper is blank."
            return
        }
        if (paperSize.width <= 0 || paperSize.height <= 0) return
        saving = true
        status = "Saving…"
        val snapshot = strokes
        val (w, h) = paperSize
        scope.launch {
            val saved = withContext(Dispatchers.Default) {
                val picture = StrokePainter.picture(w, h, snapshot)
                try {
                    PictureSaver.save(context, picture)
                } finally {
                    picture.recycle()
                }
            }
            status = if (saved != null) "Saved to Pictures/${PictureSaver.FOLDER}." else "Couldn't save. Is the phone's storage full?"
            saving = false
        }
    }

    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            DrawingSurface(
                strokes = strokes,
                brushPx = with(density) { size.widthDp.dp.toPx() },
                eraser = tool == Tool.Eraser,
                smoothingPx = with(density) { smoothing.radiusDp.dp.toPx() },
                onStroke = {
                    drawing.add(it)
                    refresh()
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onSizeChanged { paperSize = it },
            )
            // The paper is white in light mode too, so a line marks where it ends.
            HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.primary)
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    status,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                // Buttons wrap onto more rows rather than scrolling, so none hide off screen.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    NewtButton("Undo", onClick = {
                        status = if (drawing.undo()) HELLO else "Nothing to undo."
                        refresh()
                    })
                    NewtButton("Redo", onClick = {
                        status = if (drawing.redo()) HELLO else "Nothing to redo."
                        refresh()
                    })
                    NewtChoice(
                        options = Tool.entries,
                        selected = tool,
                        label = { it.label },
                        onSelect = { tool = it },
                        modifier = Modifier.width(196.dp),
                    )
                    NewtButton("Size: ${size.label}", onClick = { dialog = Dialog.Size })
                    NewtButton("Smooth: ${smoothing.label}", onClick = { dialog = Dialog.Smoothing })
                    NewtButton("Save", onClick = ::save)
                    NewtButton("Clear", onClick = {
                        if (strokes.isEmpty()) status = "The paper is already blank." else dialog = Dialog.Clear
                    })
                }
            }
        }
    }

    when (dialog) {
        Dialog.Size -> ChoiceDialog(
            title = "Brush size",
            options = BrushSize.entries,
            selected = size,
            label = { it.label },
            hint = { null },
            onPick = { size = it; dialog = null },
            onDismiss = { dialog = null },
        )
        Dialog.Smoothing -> ChoiceDialog(
            title = "Smoothing",
            options = Smoothing.entries,
            selected = smoothing,
            label = { it.label },
            hint = { it.hint },
            onPick = { smoothing = it; dialog = null },
            onDismiss = { dialog = null },
        )
        Dialog.Clear -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Clear the paper?") },
            text = { Text("Everything you've drawn goes away. Undo brings it back.") },
            confirmButton = {
                NewtPrimaryButton("Clear", tone = NewtTone.Danger, onClick = {
                    drawing.clear()
                    refresh()
                    status = "Cleared. Undo brings it back."
                    dialog = null
                })
            },
            dismissButton = { NewtButton("Keep drawing", onClick = { dialog = null }) },
        )
        null -> Unit
    }
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    hint: (T) -> String?,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                NewtPickList(options = options, selected = selected, label = label, hint = hint, onSelect = onPick)
            }
        },
        confirmButton = { NewtButton("Close", onClick = onDismiss) },
    )
}
