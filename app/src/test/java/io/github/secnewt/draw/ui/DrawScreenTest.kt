package io.github.secnewt.draw.ui

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.test.click
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import io.github.secnewt.draw.model.Drawing
import io.github.secnewt.draw.model.PaperView
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
        add(circle(1500f, 1500f, 900f, 40f))
        add(circle(1170f, 1260f, 90f, 40f))
        add(circle(1830f, 1260f, 90f, 40f))
        add(Stroke((0..32).map { i -> val a = i / 32.0 * PI; Point(1500f + 480f * cos(a).toFloat(), 1800f + 270f * sin(a).toFloat()) }, 40f))
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

    /** How much paper, left to right, the same screen swipe covers. */
    private fun Drawing.lastSpan() = strokes.last().points.let { p -> p.maxOf { it.x } - p.minOf { it.x } }

    private fun pinchOut() = paper.performTouchInput {
        pinch(
            start0 = center - Offset(40f, 0f), end0 = center - Offset(400f, 0f),
            start1 = center + Offset(40f, 0f), end1 = center + Offset(400f, 0f),
        )
    }

    @Test
    fun pinchingZoomsInAndDrawsNothing() {
        val drawing = Drawing()
        show(drawing)
        swipeAcross()
        val whole = drawing.lastSpan()
        pinchOut()
        paper.assert(hasStrokes("1 stroke"))
        swipeAcross()
        val zoomed = drawing.lastSpan()
        assertTrue("zoomed in, the same swipe covers less paper ($zoomed vs $whole)", zoomed < whole / 3f)
    }

    @Test
    fun movingPinchKeepsItsAnchorAndStopsWhenThePaperResizes() {
        var paperWidth by mutableStateOf(220.dp)
        var view by mutableStateOf<PaperView?>(null)
        composeRule.setContent {
            DrawingSurface(
                strokes = emptyList(), brushWidth = 10f, eraser = false, moveOnly = false,
                smoothingPx = 0f, view = view,
                onLimits = { view = (view ?: PaperView(0.75f, -100f, -100f)).refitted(it) },
                onViewChange = { view = it }, onMoveEnd = {},
                onStroke = { error("A pinch must not draw") },
                background = Color.White, edge = Color.Black,
                modifier = Modifier.size(paperWidth, 240.dp),
            )
        }
        val before = composeRule.runOnIdle { checkNotNull(view) }
        var anchor = Point(0f, 0f)
        val shift = 30f
        paper.performTouchInput {
            anchor = Point(center.x, center.y)
            down(0, center - Offset(20f, 0f))
            down(1, center + Offset(20f, 0f))
            updatePointerTo(0, center + Offset(shift - 40f, 0f))
            updatePointerTo(1, center + Offset(shift + 40f, 0f))
            move()
        }
        composeRule.runOnIdle {
            val after = checkNotNull(view)
            assertEquals(1.5f, after.scale, 0.01f)
            assertEquals(anchor.x + shift, after.toScreen(before.toPaper(anchor)).x, 0.01f)
            paperWidth = 200.dp
        }

        val resized = composeRule.runOnIdle { view }
        paper.performTouchInput {
            updatePointerTo(0, center - Offset(40f, 0f))
            updatePointerTo(1, center + Offset(40f, 0f))
            move()
            up(1)
            up(0)
        }
        composeRule.runOnIdle { assertEquals(resized, view) }
    }

    @Test
    fun wholePageGoesBackToTheFullView() {
        val drawing = Drawing()
        show(drawing)
        swipeAcross()
        val whole = drawing.lastSpan()
        pinchOut()
        composeRule.onNodeWithText("Whole page").performClick()
        swipeAcross()
        assertEquals(whole, drawing.lastSpan(), whole * 0.05f)
    }

    @Test
    fun undoWhileZoomedKeepsTheZoom() {
        val drawing = Drawing()
        show(drawing)
        swipeAcross()
        val whole = drawing.lastSpan()
        composeRule.onNodeWithText("Zoom in").performClick()
        composeRule.onNodeWithText("Zoom 200%.").assertExists()
        // Undo changes the status text, which changes the paper's height: zoom must stay.
        composeRule.onNodeWithText("Undo").performClick()
        swipeAcross()
        assertEquals(whole / 2f, drawing.lastSpan(), whole * 0.05f)
    }

    @Test
    fun theMoveToolDragsWithOneFingerAndDrawsNothing() {
        val drawing = Drawing()
        show(drawing)
        composeRule.onNodeWithText("Zoom in").performClick()
        composeRule.onNodeWithText("Move").performClick()
        swipeAcross()
        paper.assert(hasStrokes("Blank"))
    }

    @Test
    fun tappingTheMarginMakesNoStroke() {
        val drawing = Drawing()
        show(drawing)
        // At the whole-page view there is grey margin above the square paper.
        paper.performTouchInput { click(Offset(width * 0.5f, 5f)) }
        paper.assert(hasStrokes("Blank"))
    }

    @Test
    fun strokesAreStoredInPaperPositions() {
        val drawing = Drawing()
        show(drawing)
        swipeAcross()
        // A swipe across most of the screen at the whole-page view spans most of the 3000-wide paper.
        assertTrue(drawing.lastSpan() > 1200f)
        assertTrue(drawing.strokes.last().points.all { it.x in 0f..3000f && it.y in 0f..3000f })
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
    fun undoAndRedoSayWhatHappened() {
        show(Drawing())
        swipeAcross()
        swipeAcross()
        composeRule.onNodeWithText("Undo").performClick()
        composeRule.onNodeWithText("Undone. 1 stroke on the paper.").assertExists()
        composeRule.onNodeWithText("Undo").performClick()
        composeRule.onNodeWithText("Undone. The paper is blank.").assertExists()
        composeRule.onNodeWithText("Redo").performClick()
        composeRule.onNodeWithText("Redone. 1 stroke on the paper.").assertExists()
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

    @Test
    fun aNoticeShowsAtTheStart() {
        composeRule.setContent { NewtTheme { DrawScreen(sketch(), notice = "Welcome back. Your drawing is as you left it.") } }
        composeRule.onNodeWithText("Welcome back. Your drawing is as you left it.").assertExists()
        paper.assert(hasStrokes("4 strokes"))
    }

    @Test
    fun everyChangeAsksForASaveAndNothingElseDoes() {
        var changes = 0
        composeRule.setContent { NewtTheme { DrawScreen(Drawing(), onChange = { changes++ }) } }
        composeRule.onNodeWithText("Undo").performClick() // nothing to undo
        composeRule.onNodeWithText("Zoom in").performClick()
        assertEquals(0, changes)
        swipeAcross()
        composeRule.runOnIdle { assertEquals(1, changes) }
        composeRule.onNodeWithText("Undo").performClick()
        composeRule.onNodeWithText("Redo").performClick()
        composeRule.runOnIdle { assertEquals(3, changes) }
    }
}
