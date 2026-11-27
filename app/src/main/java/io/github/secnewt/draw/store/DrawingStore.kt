package io.github.secnewt.draw.store

import io.github.secnewt.draw.model.Drawing
import io.github.secnewt.draw.model.DrawingFile
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Keeps one drawing in a file, `[id].ndrw`, in the app's private storage, so it survives
 * Android closing the app. No permission is needed and no other app can read it.
 *
 * Saving writes a new file and then swaps it in, so a crash halfway through a save leaves
 * the last good save in place. A file that can't be read is moved aside under its own name,
 * never overwritten, so a bug in loading can't silently cost a drawing. If it can't even be
 * moved aside, saving stops rather than write over it.
 */
class DrawingStore(
    private val dir: File,
    private val id: String = "drawing",
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** The saved drawing. Exists from the first save on. */
    val file = File(dir, "$id.ndrw")
    private val temp = File(dir, "$id.ndrw.new")

    enum class Saved { Written, AlreadySaved, Failed }

    /** Version of the last save, so saving an unchanged drawing does nothing. */
    @Volatile
    private var savedVersion = -1L

    /** Set when a damaged file couldn't be moved aside: saving would overwrite it. */
    @Volatile
    private var blocked = false

    sealed interface Loaded {
        /** First run, or the last save was of blank paper with no history. */
        data object Nothing : Loaded
        class Found(val drawing: Drawing) : Loaded

        /**
         * The file couldn't be read. [keptAs] is the name it was moved to; null if even
         * that failed, in which case it's left where it was and autosave is off.
         */
        class Damaged(val keptAs: String?) : Loaded
    }

    fun load(): Loaded {
        if (!file.exists()) return Loaded.Nothing
        return try {
            val history = file.inputStream().use(DrawingFile::read)
            savedVersion = history.version
            Loaded.Found(Drawing.from(history))
        } catch (e: IOException) {
            Loaded.Damaged(moveAside())
        } catch (e: IllegalArgumentException) {
            Loaded.Damaged(moveAside())
        }
    }

    /**
     * Saves [history] unless it's already saved. Safe to call from any thread; saves never
     * overlap, and an older history never replaces a newer one.
     * [Saved.Failed] leaves the previous save in place, and the next call tries again.
     */
    @Synchronized
    fun save(history: Drawing.History): Saved {
        if (blocked) return Saved.Failed
        if (history.version <= savedVersion) return Saved.AlreadySaved
        return try {
            FileOutputStream(temp).use { out ->
                DrawingFile.write(history, out)
                out.fd.sync()
            }
            if (!temp.renameTo(file)) throw IOException("Couldn't replace the saved drawing")
            savedVersion = history.version
            Saved.Written
        } catch (e: IOException) {
            temp.delete()
            Saved.Failed
        }
    }

    /** Moves the unreadable file to a name of its own. Returns that name, or null if it couldn't. */
    private fun moveAside(): String? {
        val stamp = clock()
        val target = generateSequence(0) { it + 1 }
            .map { n -> File(dir, if (n == 0) "$id-damaged-$stamp.ndrw" else "$id-damaged-$stamp-$n.ndrw") }
            .first { !it.exists() }
        return if (file.renameTo(target)) {
            target.name
        } else {
            blocked = true
            null
        }
    }
}
