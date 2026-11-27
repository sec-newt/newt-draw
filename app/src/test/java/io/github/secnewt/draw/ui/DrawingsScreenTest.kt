package io.github.secnewt.draw.ui

import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import io.github.secnewt.draw.model.Point
import io.github.secnewt.draw.model.Stroke
import io.github.secnewt.draw.render.Thumbnail
import io.github.secnewt.draw.store.Library
import io.github.secnewt.test.newtScreenshot
import io.github.secnewt.ui.NewtTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class DrawingsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val now = 1_800_000_000_000L
    private val entries = listOf(
        Library.Entry("d3", "Rubber hose hero", now - 5 * 60_000L),
        Library.Entry("d2", "Drawing 2", now - 3 * 3_600_000L),
        Library.Entry("d1", "Drawing 1", now - 2 * 86_400_000L),
        Library.Entry("d0", "Drawing 0", now - 20_000L),
    )

    private val face = Thumbnail.render(
        listOf(Stroke((0..64).map { i -> val a = i / 64.0 * 2 * PI; Point(1500f + 900f * cos(a).toFloat(), 1500f + 900f * sin(a).toFloat()) }, 60f)),
    ).asImageBitmap()

    private val calls = mutableListOf<String>()

    @androidx.compose.runtime.Composable
    private fun screen(openId: String? = "d3", message: String? = null) = DrawingsScreen(
        drawings = entries,
        openId = openId,
        loadPicture = { if (it.id == "d1") null else face },
        message = message,
        onNew = { calls += "new" },
        onOpen = { calls += "open ${it.id}" },
        onBack = { calls += "back" },
        onRename = { e, name -> calls += "rename ${e.id} $name" },
        onDelete = { calls += "delete ${it.id}" },
        now = now,
    )

    private fun show(openId: String? = "d3") = composeRule.setContent { NewtTheme(darkTheme = true) { screen(openId) } }

    @Test
    fun screenDark() = composeRule.newtScreenshot("drawings-dark", dark = true) { screen() }

    @Test
    fun screenLight() = composeRule.newtScreenshot("drawings-light", dark = false) { screen() }

    @Test
    fun screenLargeText() = composeRule.newtScreenshot("drawings-200pct", dark = true, fontScale = 2f) { screen() }

    @Test
    fun showsWhichIsOpenAndWhenEachChanged() {
        show()
        composeRule.onNodeWithText("Open now. Changed 5 minutes ago.").assertExists()
        composeRule.onNodeWithText("Changed 3 hours ago.").assertExists()
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("Changed just now."))
        composeRule.onNodeWithText("4 drawings.").assertExists()
    }

    @Test
    fun openingTheOpenDrawingJustGoesBack() {
        show()
        composeRule.onAllNodesWithText("Open")[0].performClick()
        composeRule.onAllNodesWithText("Open")[1].performClick()
        assertEquals(listOf("back", "open d2"), calls)
    }

    @Test
    fun newAndBack() {
        show()
        composeRule.onNodeWithText("New drawing").performClick()
        composeRule.onNodeWithText("Back to drawing").performClick()
        assertEquals(listOf("new", "back"), calls)
    }

    @Test
    fun noBackButtonWhenNothingIsOpen() {
        show(openId = null)
        composeRule.onNodeWithText("Back to drawing").assertDoesNotExist()
    }

    @Test
    fun deleteAsksFirst() {
        show()
        composeRule.onAllNodesWithText("Delete")[1].performClick()
        composeRule.onNodeWithText("Delete Drawing 2?").assertExists()
        composeRule.onNodeWithText("Keep it").performClick()
        assertEquals(emptyList<String>(), calls)
        composeRule.onAllNodesWithText("Delete")[1].performClick()
        composeRule.onNode(hasText("Delete") and hasAnyAncestor(isDialog())).performClick()
        assertEquals(listOf("delete d2"), calls)
    }
}
