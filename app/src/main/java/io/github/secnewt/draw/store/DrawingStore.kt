package io.github.secnewt.draw.store

import io.github.secnewt.draw.model.Drawing
import io.github.secnewt.draw.model.DrawingFile
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Keeps the drawing in one file in the app's private storage, so it survives Android
 * closing the app. No permission is needed and no other app can read it.
 *
 * Saving writes a new file and then swaps it in, so a crash halfway through a save leaves
 * the last good save in place. A file that can't be read is moved aside, never overwritten,
 * so a bug in loading can't silently cost a drawing.
 */
class DrawingStore(dir: File) {
    private val file = File(dir, "drawing.ndrw")
    private val temp = File(dir, "drawing.ndrw.new")
    private val damaged = File(dir, "drawing.ndrw.damaged")

    /** Version of the last save, so saving an unchanged drawing does nothing. */
    @Volatile
    private var savedVersion = -1L

    sealed interface Loaded {
        /** First run, or the last save was of blank paper with no history. */
        data object Nothing : Loaded
        class Found(val drawing: Drawing) : Loaded
        /** The file couldn't be read. It's been kept as drawing.ndrw.damaged. */
        data object Damaged : Loaded
    }

    fun load(): Loaded {
        if (!file.exists()) return Loaded.Nothing
        return try {
            val history = file.inputStream().use(DrawingFile::read)
            savedVersion = history.version
            Loaded.Found(Drawing.from(history))
        } catch (e: IOException) {
            moveAside()
            Loaded.Damaged
        } catch (e: IllegalArgumentException) {
            moveAside()
            Loaded.Damaged
        }
    }

    /**
     * Saves [history] unless it's already saved. Safe to call from any thread; saves never
     * overlap, and an older history never replaces a newer one.
     * Returns false if the save failed (the previous save is still there).
     */
    @Synchronized
    fun save(history: Drawing.History): Boolean {
        if (history.version <= savedVersion) return true
        return try {
            FileOutputStream(temp).use { out ->
                DrawingFile.write(history, out)
                out.fd.sync()
            }
            if (!temp.renameTo(file)) throw IOException("Couldn't replace the saved drawing")
            savedVersion = history.version
            true
        } catch (e: IOException) {
            temp.delete()
            false
        }
    }

    private fun moveAside() {
        damaged.delete()
        file.renameTo(damaged)
    }
}
