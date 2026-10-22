package com.incidentreporter.field.data.photo

import android.content.ContentResolver
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.incidentreporter.field.domain.model.Photo
import com.incidentreporter.field.domain.model.PhotoInput
import com.incidentreporter.field.domain.model.PhotoSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Clock
import java.util.UUID

/** Owns the photo files. The database stores only file names, the files live under [dir]. */
class PhotoStore(
    val dir: File,
    private val resolver: ContentResolver,
    private val clock: Clock,
) {

    /** Where the camera should write its next shot. The file doesn't exist until the camera writes it. */
    fun newCaptureTarget(): PhotoInput.Captured {
        val id = UUID.randomUUID().toString()
        return PhotoInput.Captured(id, File(dir, "$id.jpg"))
    }

    /** Takes ownership of the bytes. A camera shot is already here, a gallery pick is copied in. */
    suspend fun store(input: PhotoInput): Photo = withContext(Dispatchers.IO) {
        val target = when (input) {
            is PhotoInput.Captured -> input
            is PhotoInput.Picked -> newCaptureTarget().also { copyIn(Uri.parse(input.uri), it.file) }
        }
        val (width, height) = uprightSize(target.file)
        Photo(
            id = target.photoId,
            fileName = target.file.name,
            source = if (input is PhotoInput.Picked) PhotoSource.GALLERY else PhotoSource.CAMERA,
            capturedAt = clock.instant(),
            widthPx = width,
            heightPx = height,
            file = target.file,
        )
    }

    suspend fun delete(fileNames: Collection<String>) {
        withContext(Dispatchers.IO) { fileNames.forEach { File(dir, it).delete() } }
    }

    /** Deletes every file no photo row points at: leftovers of abandoned drafts and of captures cut short. */
    suspend fun deleteUnreferenced(referencedFileNames: Collection<String>) {
        val keep = referencedFileNames.toSet()
        withContext(Dispatchers.IO) { dir.listFiles()?.filter { it.name !in keep }?.forEach { it.delete() } }
    }

    /** The gallery's copy can vanish at any time, so the app keeps its own. A failed copy leaves nothing behind. */
    private fun copyIn(uri: Uri, target: File) {
        try {
            checkNotNull(resolver.openInputStream(uri)) { "Can't open $uri" }.use { input ->
                target.outputStream().use { input.copyTo(it) }
            }
        } catch (e: Exception) {
            target.delete()
            throw e
        }
    }

    /** Width and height as the photo is meant to be seen, after its EXIF rotation tag. */
    private fun uprightSize(file: File): Pair<Int, Int> {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        val rotation = ExifInterface(file).rotationDegrees
        return if (rotation == 90 || rotation == 270) bounds.outHeight to bounds.outWidth else bounds.outWidth to bounds.outHeight
    }
}
