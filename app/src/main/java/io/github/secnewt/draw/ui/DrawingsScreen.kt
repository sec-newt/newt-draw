package io.github.secnewt.draw.ui

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import io.github.secnewt.draw.render.PAPER
import io.github.secnewt.draw.store.Library
import io.github.secnewt.ui.NewtButton
import io.github.secnewt.ui.NewtPrimaryButton
import io.github.secnewt.ui.NewtTone

/**
 * The list of drawings: a small picture of each, newest first, with Open, Rename and Delete.
 *
 * @param openId the drawing open in the editor, if any; "Back to drawing" returns to it.
 * @param loadPicture reads a drawing's small picture (null when it has none yet). Only rows
 *   on screen load theirs, so a long list doesn't fill memory.
 * @param message what just happened ("Deleted Drawing 2."), read out by TalkBack.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DrawingsScreen(
    drawings: List<Library.Entry>,
    openId: String?,
    loadPicture: suspend (Library.Entry) -> ImageBitmap?,
    message: String?,
    onNew: () -> Unit,
    onOpen: (Library.Entry) -> Unit,
    onBack: () -> Unit,
    onRename: (Library.Entry, String) -> Unit,
    onDelete: (Library.Entry) -> Unit,
    now: Long = System.currentTimeMillis(),
) {
    var renaming by remember { mutableStateOf<Library.Entry?>(null) }
    var deleting by remember { mutableStateOf<Library.Entry?>(null) }
    val canGoBack = openId != null && drawings.any { it.id == openId }
    BackHandler(enabled = canGoBack, onBack = onBack)

    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Your drawings",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NewtPrimaryButton("New drawing", onClick = onNew)
                if (canGoBack) NewtButton("Back to drawing", onClick = onBack)
            }
            Text(
                message ?: when (drawings.size) {
                    0 -> "No drawings yet."
                    1 -> "1 drawing."
                    else -> "${drawings.size} drawings."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.primary)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(drawings, key = { it.id }) { entry ->
                    DrawingRow(
                        entry = entry,
                        isOpen = entry.id == openId,
                        picture = produceState<ImageBitmap?>(null, entry.id, entry.changed) {
                            value = loadPicture(entry)
                        }.value,
                        now = now,
                        onOpen = { if (entry.id == openId) onBack() else onOpen(entry) },
                        onRename = { renaming = entry },
                        onDelete = { deleting = entry },
                    )
                }
            }
        }
    }

    renaming?.let { entry ->
        RenameDialog(
            entry = entry,
            onRename = { name ->
                onRename(entry, name)
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }
    deleting?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${entry.name}?") },
            text = { Text("It's gone for good; Undo can't bring it back. Pictures you saved to your gallery stay.") },
            confirmButton = {
                NewtPrimaryButton("Delete", tone = NewtTone.Danger, onClick = {
                    onDelete(entry)
                    deleting = null
                })
            },
            dismissButton = { NewtButton("Keep it", onClick = { deleting = null }) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DrawingRow(
    entry: Library.Entry,
    isOpen: Boolean,
    picture: ImageBitmap?,
    now: Long,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        val frame = Modifier
            .size(96.dp)
            .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
            .padding(2.dp)
        if (picture != null) {
            // The name next to it says what it is, so TalkBack skips the picture.
            Image(picture, contentDescription = null, modifier = frame)
        } else {
            Box(frame.background(Color(PAPER)))
        }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                entry.name,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            val changed = if (now - entry.changed in 0 until DateUtils.MINUTE_IN_MILLIS) {
                "just now"
            } else {
                DateUtils.getRelativeTimeSpanString(entry.changed, now, DateUtils.MINUTE_IN_MILLIS)
            }
            Text(
                if (isOpen) "Open now. Changed $changed." else "Changed $changed.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // TalkBack hears which drawing each button is for ("Drawing 2, Delete"),
                // not a run of identical "Delete"s.
                NewtButton("Open", onClick = onOpen, modifier = Modifier.semantics { contentDescription = entry.name })
                NewtButton("Rename", onClick = onRename, modifier = Modifier.semantics { contentDescription = entry.name })
                NewtButton("Delete", onClick = onDelete, modifier = Modifier.semantics { contentDescription = entry.name })
            }
        }
    }
}

@Composable
private fun RenameDialog(entry: Library.Entry, onRename: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(entry.name) }
    val clean = Library.cleanName(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename ${entry.name}") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(Library.MAX_NAME) },
                label = { Text("Name") },
                singleLine = true,
                supportingText = { Text(if (clean == null) "Type a name." else "Up to ${Library.MAX_NAME} letters.") },
                isError = clean == null,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            )
        },
        confirmButton = {
            NewtPrimaryButton("Rename", onClick = { clean?.let(onRename) })
        },
        dismissButton = { NewtButton("Cancel", onClick = onDismiss) },
    )
}
