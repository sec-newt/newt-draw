package io.github.secnewt.draw.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.secnewt.draw.model.BrushSize
import io.github.secnewt.draw.model.Drawing
import io.github.secnewt.draw.model.PAPER_HEIGHT
import io.github.secnewt.draw.model.PAPER_WIDTH
import io.github.secnewt.draw.model.PaperView
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

private const val HELLO = "One finger draws. Two fingers drag to scroll and pinch to zoom."

/** A message for the status line. Each new one is shown, even if the words are the same. */
class StatusAlert(val text: String)

private enum class Dialog { Size, Smoothing, Clear }

/** "Zoom 300%." — relative to the whole page fitting on screen. */
private fun zoomText(view: PaperView, limits: PaperView.Limits) = "Zoom ${view.percentOf(PaperView.fit(limits))}%."

/** Changes with every undo/redo, so TalkBack reads each one out (it skips repeated text). */
private fun onPage(count: Int) = when (count) {
    0 -> "The paper is blank."
    1 -> "1 stroke on the paper."
    else -> "$count strokes on the paper."
}

/**
 * The whole app: the paper on top, a status line, then the tools.
 * [drawing] outlives this screen (it's held by the activity), so switching dark mode
 * doesn't lose the picture.
 *
 * @param notice shown in the status line at the start instead of the usual hint.
 * @param alert shown in the status line each time a new one arrives (autosave problems).
 * @param onChange called after every change to [drawing], for autosave.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DrawScreen(
    drawing: Drawing,
    notice: String? = null,
    alert: StatusAlert? = null,
    onChange: () -> Unit = {},
) {
    var strokes by remember { mutableStateOf(drawing.strokes) }
    var tool by rememberSaveable { mutableStateOf(Tool.Pen) }
    var size by rememberSaveable { mutableStateOf(BrushSize.Medium) }
    var smoothing by rememberSaveable { mutableStateOf(Smoothing.Medium) }
    var status by remember { mutableStateOf(notice ?: HELLO) }
    var dialog by remember { mutableStateOf<Dialog?>(null) }
    var limits by remember { mutableStateOf<PaperView.Limits?>(null) }
    var view by remember { mutableStateOf<PaperView?>(null) }
    var saving by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(alert) { alert?.let { status = it.text } }

    fun refresh() {
        if (strokes !== drawing.strokes) onChange()
        strokes = drawing.strokes
    }

    fun save() {
        if (saving) return
        if (strokes.isEmpty()) {
            status = "Nothing to save yet. The paper is blank."
            return
        }
        saving = true
        status = "Saving…"
        val snapshot = strokes
        scope.launch {
            try {
                status = withContext(Dispatchers.Default) {
                    try {
                        val picture = StrokePainter.picture(PAPER_WIDTH, PAPER_HEIGHT, snapshot)
                        val saved = try {
                            PictureSaver.save(context, picture)
                        } finally {
                            picture.recycle()
                        }
                        if (saved != null) "Saved to Pictures/${PictureSaver.FOLDER}." else "Couldn't save. Is the phone's storage full?"
                    } catch (_: OutOfMemoryError) {
                        "Couldn't save: the phone ran short of memory. Close other apps and try again."
                    }
                }
            } finally {
                saving = false
            }
        }
    }

    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            DrawingSurface(
                strokes = strokes,
                brushWidth = size.width,
                eraser = tool == Tool.Eraser,
                moveOnly = tool == Tool.Move,
                smoothingPx = with(density) { smoothing.radiusDp.dp.toPx() },
                view = view,
                onLimits = { new ->
                    // First layout shows the whole page. Later size changes (the status line
                    // wrapping differently, say) keep the zoom and place you were at.
                    view = view?.refitted(new) ?: PaperView.fit(new)
                    limits = new
                },
                onViewChange = { view = it },
                onMoveEnd = { view?.let { v -> limits?.let { status = zoomText(v, it) } } },
                onStroke = {
                    drawing.add(it)
                    refresh()
                },
                background = MaterialTheme.colorScheme.surfaceVariant,
                edge = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
            // The paper is white in light mode too, so a line marks where it ends.
            HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.primary)
            // The tools never take more than half the screen, so the paper always has room.
            // With very large text they scroll instead.
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = (LocalConfiguration.current.screenHeightDp / 2).dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
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
                        status = if (drawing.undo()) "Undone. ${onPage(drawing.strokes.size)}" else "Nothing to undo."
                        refresh()
                    })
                    NewtButton("Redo", onClick = {
                        status = if (drawing.redo()) "Redone. ${onPage(drawing.strokes.size)}" else "Nothing to redo."
                        refresh()
                    })
                    NewtChoice(
                        options = Tool.entries,
                        selected = tool,
                        label = { it.label },
                        onSelect = { tool = it },
                        modifier = Modifier.widthIn(min = 196.dp),
                    )
                    NewtButton("Size: ${size.label}", onClick = { dialog = Dialog.Size })
                    NewtButton("Smooth: ${smoothing.label}", onClick = { dialog = Dialog.Smoothing })
                    NewtButton("Zoom in", onClick = {
                        val l = limits ?: return@NewtButton
                        view = view?.zoomedAtCentre(2f, l)?.also { status = zoomText(it, l) }
                    })
                    NewtButton("Zoom out", onClick = {
                        val l = limits ?: return@NewtButton
                        view = view?.zoomedAtCentre(0.5f, l)?.also { status = zoomText(it, l) }
                    })
                    NewtButton("Whole page", onClick = {
                        limits?.let { view = PaperView.fit(it) }
                        status = "Showing the whole page."
                    })
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

/** Shown for the moment it takes to open the saved drawing. */
@Composable
fun OpeningScreen() {
    Scaffold { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Text(
                "Opening your drawing…",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
