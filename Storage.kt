package app.videogir

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.IOException

object Storage {
    private const val FOLDER = "VOLTEX"

    fun safeName(s: String): String =
        s.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().take(80).ifBlank { "video" }

    /** فایل را در Movies/VOLTEX یا Music/VOLTEX ذخیره می‌کند و مسیر نمایشی را برمی‌گرداند. */
    fun saveToPublic(ctx: Context, src: File, name: String, mime: String, audio: Boolean): String {
        val base = if (audio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES
        val shown = "$base/$FOLDER"

        if (Build.VERSION.SDK_INT >= 29) {
            val resolver = ctx.contentResolver
            val collection = if (audio)
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            else
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, shown)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(collection, values) ?: throw IOException("cannot create file")
            try {
                resolver.openOutputStream(uri)!!.use { o -> src.inputStream().use { it.copyTo(o) } }
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(base), FOLDER)
            dir.mkdirs()
            val dest = File(dir, name)
            src.copyTo(dest, overwrite = true)
            MediaScannerConnection.scanFile(ctx, arrayOf(dest.path), arrayOf(mime), null)
        }
        return shown
    }
}
