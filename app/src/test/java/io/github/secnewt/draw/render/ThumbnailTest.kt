package io.github.secnewt.draw.render

import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.secnewt.draw.model.Point
import io.github.secnewt.draw.model.Stroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ThumbnailTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun theWholePaperShrunkOnWhite() {
        val line = Stroke(listOf(Point(0f, 1500f), Point(3000f, 1500f)), 64f)
        val eraser = Stroke(listOf(Point(1500f, 1300f), Point(1500f, 1700f)), 200f, eraser = true)
        val picture = Thumbnail.render(listOf(line, eraser))
        assertEquals(Thumbnail.SIZE, picture.width)
        assertEquals(Color.BLACK, picture.getPixel(Thumbnail.SIZE / 4, Thumbnail.SIZE / 2))
        assertEquals(Color.WHITE, picture.getPixel(Thumbnail.SIZE / 4, Thumbnail.SIZE / 4))
        assertEquals("erased ink shows white paper", Color.WHITE, picture.getPixel(Thumbnail.SIZE / 2, Thumbnail.SIZE / 2))
    }

    @Test
    fun writtenAndReadBack() {
        val file = File(folder.root, "d1.png")
        assertNull(Thumbnail.read(file))
        assertTrue(Thumbnail.write(file, listOf(Stroke(listOf(Point(1500f, 1500f)), 300f))))
        assertNotNull(Thumbnail.read(file))
        assertEquals(listOf("d1.png"), folder.root.list()!!.toList())
    }
}
