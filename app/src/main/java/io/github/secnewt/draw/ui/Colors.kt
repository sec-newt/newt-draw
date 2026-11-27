package io.github.secnewt.draw.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.secnewt.draw.model.Palette
import io.github.secnewt.ui.NewtButton

/** A round patch of [color], outlined so white and pale colors show on any background. */
@Composable
private fun Swatch(color: Palette, size: Int, chosen: Boolean = false) {
    val ring = if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Box(
        Modifier
            .size(size.dp)
            .border(if (chosen) 4.dp else 2.dp, ring, CircleShape)
            .padding(if (chosen) 6.dp else 3.dp)
            .clip(CircleShape)
            .background(Color(color.argb)),
    )
}

/**
 * The Color button under the paper: the color's patch and its name, outlined like the
 * other buttons. TalkBack hears "Color: Red".
 */
@Composable
fun ColorButton(color: Palette, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .heightIn(min = 58.dp)
            .clip(shape)
            .border(2.5.dp, MaterialTheme.colorScheme.primary, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = "Color: ${color.label}"
                role = Role.Button
                onClick { onClick(); true }
            }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Swatch(color, size = 28)
        Text(color.label, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 19.sp)
    }
}

/** Every color as a patch with its name under it; the chosen one has a thick ring. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorDialog(selected: Palette, onPick: (Palette) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Color") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.selectableGroup(),
                ) {
                    Palette.entries.forEach { color ->
                        val chosen = color == selected
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .width(76.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .selectable(selected = chosen, role = Role.RadioButton, onClick = { onPick(color) })
                                .padding(vertical = 6.dp),
                        ) {
                            Swatch(color, size = 52, chosen = chosen)
                            Text(
                                color.label,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { NewtButton("Close", onClick = onDismiss) },
    )
}
