package io.github.secnewt.draw.render

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Saves a picture as a PNG in Pictures/Newt Draw, where the gallery finds it. Android lets
 * an app add its own pictures there without any permission.
 */
object PictureSaver {
    const val FOLDER = "Newt Draw"

    fun fileName(now: LocalDateTime = LocalDateTime.now()): String =
        "Newt Draw ${now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH-mm-ss"))}.png"

    /** Returns where it was saved, or null if anything went wrong. Never throws. */
    fun save(context: Context, picture: Bitmap, name: String = fileName()): Uri? =
        runCatching { trySave(context, picture, name) }.getOrNull()

    private fun trySave(context: Context, picture: Bitmap, name: String): Uri? {
        val resolver = context.contentResolver
        val details = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$FOLDER")
            // Hidden from the gallery until the file is completely written.
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, details) ?: return null
        val written = runCatching {
            resolver.openOutputStream(uri)?.use { picture.compress(Bitmap.CompressFormat.PNG, 100, it) } == true
        }.getOrDefault(false)
        if (!written) {
            runCatching { resolver.delete(uri, null, null) }
            return null
        }
        val shown = resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        if (shown < 1) {
            // Still hidden from the gallery, so don't claim it was saved.
            runCatching { resolver.delete(uri, null, null) }
            return null
        }
        return uri
    }
}
