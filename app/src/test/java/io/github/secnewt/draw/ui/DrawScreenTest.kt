package io.github.secnewt.draw.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import io.github.secnewt.draw.model.Drawing
import io.github.secnewt.draw.model.Point
import io.github.secnewt.draw.model.Stroke
import io.github.secnewt.test.newtScreenshot
import io.github.secnewt.ui.NewtTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
class DrawScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** A simple face, so the screenshots show ink on the paper. */
    private fun sketch() = Drawing().apply {
        fun circle(cx: Float, cy: Float, r: Float, width: Float) =
            Stroke((0..64).map { i -> val a = i / 64.0 * 2 * PI; Point(cx + r * cos(a).toFloat(), cy + r * sin(a).toFloat()) }, width)
        add(circle(540f, 700f, 300f, 16f))
        add(circle(430f, 620f, 30f, 16f))
        add(circle(650f, 620f, 30f, 16f))
        add(Stroke((0..32).map { i -> val a = i / 32.0 * PI; Point(540f + 160f * cos(a).toFloat(), 800f + 90f * sin(a).toFloat()) }, 16f))
    }

    private fun show(drawing: Drawing) = composeRule.setContent { NewtTheme(darkTheme = true) { DrawScreen(drawing) } }

    private val paper get() = composeRule.onNodeWithContentDescription("Drawing paper")

    private fun hasStrokes(text: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text)

    private fun swipeAcross() = paper.performTouchInput {
        swipe(Offset(width * 0.2f, height * 0.5f), Offset(width * 0.8f, height * 0.55f), durationMillis = 400)
    }

    @Test
    fun screenDark() = composeRule.newtScreenshot("draw-dark", dark = true) { DrawScreen(sketch()) }

    @Test
    fun screenLight() = composeRule.newtScreenshot("draw-light", dark = false) { DrawScreen(sketch()) }

    @Test
    fun screenLargeText() = composeRule.newtScreenshot("draw-200pct", dark = true, fontScale = 2f) { DrawScreen(sketch()) }

    @Test
    fun aSwipeDrawsOneStroke() {
        val drawing = Drawing()
        show(drawing)
        paper.assert(hasStrokes("Blank"))
        swipeAcross()
        paper.assert(hasStrokes("1 stroke"))
        assertEquals(1, drawing.strokes.size)
        assertTrue("a long swipe gives the stroke many points", drawing.strokes[0].points.size > 5)
    }

    @Test
    fun undoAndRedoButtons() {
        val drawing = Drawing()
        show(drawing)
        swipeAcross()
        composeRule.onNodeWithText("Undo").performClick()
        paper.assert(hasStrokes("Blank"))
        composeRule.onNodeWithText("Redo").performClick()
        paper.assert(hasStrokes("1 stroke"))
    }

    @Test
    fun undoWithNothingToUndoSaysSo() {
        show(Drawing())
        composeRule.onNodeWithText("Undo").performClick()
        composeRule.onNodeWithText("Nothing to undo.").assertExists()
    }

    @Test
    fun aSecondFingerCancelsTheStroke() {
        val drawing = Drawing()
        show(drawing)
        paper.performTouchInput {
            down(0, Offset(width * 0.2f, height * 0.3f))
            moveTo(0, Offset(width * 0.5f, height * 0.3f))
            down(1, Offset(width * 0.5f, height * 0.7f))
            moveTo(0, Offset(width * 0.7f, height * 0.3f))
            up(1)
            up(0)
        }
        paper.assert(hasStrokes("Blank"))
    }

    @Test
    fun eraserMakesEraserStrokes() {
        val drawing = Drawing()
        show(drawing)
        composeRule.onNodeWithText("Eraser").performClick()
        swipeAcross()
        assertTrue(drawing.strokes.single().eraser)
    }

    @Test
    fun clearAsksFirstAndCanBeUndone() {
        val drawing = Drawing()
        show(drawing)
        swipeAcross()
        composeRule.onNodeWithText("Clear").performClick()
        composeRule.onNodeWithText("Clear the paper?").assertExists()
        composeRule.onNode(hasText("Clear") and hasAnyAncestor(isDialog())).performClick()
        paper.assert(hasStrokes("Blank"))
        composeRule.onNodeWithText("Undo").performClick()
        paper.assert(hasStrokes("1 stroke"))
    }

    @Test
    fun savingBlankPaperSaysSo() {
        show(Drawing())
        composeRule.onNodeWithText("Save").performClick()
        composeRule.onNodeWithText("Nothing to save yet. The paper is blank.").assertExists()
    }

    @Test
    fun smoothingCanBeChanged() {
        show(Drawing())
        composeRule.onNodeWithText("Smooth: Medium").performClick()
        composeRule.onNodeWithText("High").performClick()
        composeRule.onNodeWithText("Smooth: High").assertExists()
    }
}
