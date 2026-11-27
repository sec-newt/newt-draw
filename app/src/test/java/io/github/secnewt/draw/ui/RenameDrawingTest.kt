package io.github.secnewt.draw.ui

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.secnewt.draw.store.Library
import io.github.secnewt.ui.NewtTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renaming, on Robolectric's default screen. On the Pixel 7 screen the other tests use,
 * Robolectric never settles once a text box opens in a dialog; the same box works on a
 * 1080 x 2400 emulator.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class RenameDrawingTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val renamed = mutableListOf<String>()

    private fun show() = composeRule.setContent {
        NewtTheme {
            DrawingsScreen(
                drawings = listOf(Library.Entry("d1", "Drawing 1", 0L)),
                openId = "d1",
                loadPicture = { null },
                message = null,
                onNew = {},
                onOpen = {},
                onBack = {},
                onRename = { e, name -> renamed += "${e.id} $name" },
                onDelete = {},
            )
        }
    }

    private fun type(name: String) {
        composeRule.onAllNodesWithText("Rename")[0].performClick()
        composeRule.onNode(hasSetTextAction() and hasAnyAncestor(isDialog())).performTextReplacement(name)
    }

    private fun confirm() = composeRule.onNode(hasText("Rename") and hasAnyAncestor(isDialog())).performClick()

    @Test
    fun renameTakesTheNewName() {
        show()
        type("Noodle cat")
        confirm()
        assertEquals(listOf("d1 Noodle cat"), renamed)
    }

    @Test
    fun renameWontTakeABlankName() {
        show()
        type("   ")
        composeRule.onNodeWithText("Type a name.").assertExists()
        confirm()
        assertEquals(emptyList<String>(), renamed)
    }
}
