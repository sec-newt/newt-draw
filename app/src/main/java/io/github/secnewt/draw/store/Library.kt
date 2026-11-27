package io.github.secnewt.draw.store

import io.github.secnewt.draw.model.Drawing
import java.io.File
import java.io.IOException

/**
 * All the drawings, in the app's private `drawings` folder. Each drawing has an id and:
 * - `[id].ndrw`: the drawing and its undo history ([DrawingStore]);
 * - `[id].name`: its name, as plain text;
 * - `[id].png`: a small picture of it for the list (made by the app; optional).
 *
 * Which drawing was open last is kept in `current`, so the app reopens it.
 * The app does its file work on one background thread; [store] can also be called from
 * the main thread (the save when leaving the app).
 */
class Library(root: File, private val clock: () -> Long = System::currentTimeMillis) {
    private val dir = File(root, "drawings")
    private val currentFile = File(dir, "current")

    /** The one-drawing file from before there was a list of drawings. */
    private val oldFile = File(root, "drawing.ndrw")

    private val stores = mutableMapOf<String, DrawingStore>()

    data class Entry(val id: String, val name: String, val changed: Long)

    /** The store for drawing [id]; the same one every time, so it remembers what it saved. */
    fun store(id: String): DrawingStore = synchronized(stores) { stores.getOrPut(id) { DrawingStore(dir, id, clock) } }

    fun thumbnail(id: String) = File(dir, "$id.png")

    /** Every drawing, most recently changed first. */
    fun list(): List<Entry> = (dir.listFiles() ?: emptyArray())
        .mapNotNull { f -> ID_FILE.matchEntire(f.name)?.groupValues?.get(1) }
        .map { entry(it) }
        .sortedWith(compareByDescending<Entry> { it.changed }.thenByDescending { it.id })

    fun entry(id: String) = Entry(id, name(id), store(id).file.lastModified())

    /** The drawing open last, if it still exists. */
    var current: String?
        get() = currentFile.takeIf { it.exists() }?.readText()?.trim()?.takeIf { exists(it) }
        set(id) {
            if (id == null) currentFile.delete() else writeAtomically(currentFile, id)
        }

    fun exists(id: String) = ID.matches(id) && store(id).file.exists()

    /** Makes a new blank drawing, named "Drawing N", saved so it shows in the list. */
    fun create(): Entry {
        dir.mkdirs()
        var stamp = clock()
        while (File(dir, "d$stamp.ndrw").exists()) stamp++
        val id = "d$stamp"
        writeAtomically(File(dir, "$id.name"), nextName())
        if (store(id).save(Drawing().history()) == DrawingStore.Saved.Failed) {
            File(dir, "$id.name").delete()
            throw IOException("Couldn't make a new drawing")
        }
        return entry(id)
    }

    /** Names are trimmed and kept to [MAX_NAME] characters. A blank name is ignored. */
    fun rename(id: String, name: String) {
        val clean = cleanName(name) ?: return
        writeAtomically(File(dir, "$id.name"), clean)
    }

    /** Removes the drawing, its name and its picture. */
    fun delete(id: String) {
        if (current == id) current = null
        store(id).file.delete()
        File(dir, "$id.name").delete()
        thumbnail(id).delete()
        synchronized(stores) { stores.remove(id) }
    }

    /**
     * Moves the drawing from before there was a list into it, as "Drawing 1", and opens it.
     * Does nothing if there isn't one. Safe to run every time the app starts.
     */
    fun bringInOldDrawing() {
        if (!oldFile.exists()) return
        dir.mkdirs()
        val id = "d${clock()}"
        writeAtomically(File(dir, "$id.name"), nextName())
        if (oldFile.renameTo(store(id).file)) {
            current = id
        } else {
            File(dir, "$id.name").delete()
        }
    }

    private fun name(id: String): String =
        File(dir, "$id.name").takeIf { it.exists() }?.readText()?.let(::cleanName) ?: "Drawing"

    private fun nextName(): String {
        val used = list().mapNotNull { NUMBERED.matchEntire(it.name)?.groupValues?.get(1)?.toIntOrNull() }
        return "Drawing ${(used.maxOrNull() ?: 0) + 1}"
    }

    private fun writeAtomically(file: File, text: String) {
        val temp = File(file.parentFile, file.name + ".new")
        temp.writeText(text)
        if (!temp.renameTo(file)) {
            temp.delete()
            throw IOException("Couldn't write ${file.name}")
        }
    }

    companion object {
        const val MAX_NAME = 60
        private val ID = Regex("d[0-9]+")
        private val ID_FILE = Regex("(d[0-9]+)\\.ndrw")
        private val NUMBERED = Regex("Drawing ([0-9]{1,6})")

        fun cleanName(name: String): String? =
            name.replace(Regex("\\s+"), " ").trim().take(MAX_NAME).trim().takeIf { it.isNotEmpty() }
    }
}
