package com.incidentreporter.field.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.incidentreporter.field.core.database.FieldDatabase
import com.incidentreporter.field.data.photo.PhotoStore
import com.incidentreporter.field.domain.model.Category
import com.incidentreporter.field.domain.model.IncidentInput
import com.incidentreporter.field.domain.model.IncidentLocation
import com.incidentreporter.field.domain.model.LocationSource
import com.incidentreporter.field.domain.model.MAX_PHOTOS
import com.incidentreporter.field.domain.model.PendingOp
import com.incidentreporter.field.domain.model.PhotoInput
import com.incidentreporter.field.domain.model.PhotoSource
import com.incidentreporter.field.domain.model.Severity
import com.incidentreporter.field.domain.model.Status
import com.incidentreporter.field.domain.model.SyncState
import com.incidentreporter.field.testing.FakeIncidentApi
import com.incidentreporter.field.testing.FakeSyncScheduler
import com.incidentreporter.field.testing.entity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** Drafts, photo files and local writes, on a real database and real files. */
@RunWith(AndroidJUnit4::class)
class DraftLifecycleTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val clock = Clock.fixed(Instant.parse("2026-10-22T09:00:00Z"), ZoneOffset.UTC)
    private val scheduler = FakeSyncScheduler()
    private lateinit var db: FieldDatabase
    private lateinit var photoStore: PhotoStore
    private lateinit var repo: IncidentRepositoryImpl

    private val input = IncidentInput(
        title = "Leak at bay 3",
        category = Category.PLUMBING,
        severity = Severity.HIGH,
        location = IncidentLocation(latitude = null, longitude = null, label = "Bay 3", source = LocationSource.MANUAL),
    )

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, FieldDatabase::class.java).build()
        photoStore = PhotoStore(tmp.newFolder("photos"), context.contentResolver, clock)
        repo = IncidentRepositoryImpl(db, FakeIncidentApi(), photoStore, clock, scheduler)
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** A real 4×3 JPEG, so reading its size runs for real. */
    private fun realJpeg(file: File, exifOrientation: Int? = null): File {
        file.outputStream().use { Bitmap.createBitmap(4, 3, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.JPEG, 90, it) }
        if (exifOrientation != null) {
            ExifInterface(file).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, exifOrientation.toString())
                saveAttributes()
            }
        }
        return file
    }

    /** What the camera screen does: ask for a target file, then the camera writes it. */
    private fun capture(exifOrientation: Int? = null) = photoStore.newCaptureTarget().also { realJpeg(it.file, exifOrientation) }

    @Test
    fun submitDraft_clearsIsDraft_queuesCreate_andRequestsSync() = runTest {
        val id = repo.createDraft()
        repo.submitDraft(id, input)

        val incident = repo.observeIncident(id).first()!!
        assertFalse(incident.isDraft)
        assertEquals("Leak at bay 3", incident.title)
        assertEquals(PendingOp.CREATE, incident.pendingOp)
        assertEquals(SyncState.PENDING, incident.syncState)
        assertEquals(1, scheduler.calls)
    }

    @Test
    fun submitDraft_twice_isIdempotent() = runTest {
        // A gloved double tap on Submit.
        val id = repo.createDraft()
        repo.submitDraft(id, input)
        repo.submitDraft(id, input)

        assertEquals(1, scheduler.calls)
    }

    @Test
    fun discardDraft_deletesRowPhotoRowsAndFiles() = runTest {
        val id = repo.createDraft()
        val shot = capture()
        val photo = repo.attachPhoto(id, shot)
        assertEquals(PhotoSource.CAMERA, photo.source)
        assertEquals(4 to 3, photo.widthPx to photo.heightPx)

        repo.discardDraft(id)

        assertNull(repo.observeIncident(id).first())
        assertFalse(shot.file.exists())
    }

    @Test
    fun createDraft_purgesAbandonedDraftsAndOrphanFiles_butKeepsSubmittedPhotos() = runTest {
        val submitted = repo.createDraft()
        val kept = capture()
        repo.attachPhoto(submitted, kept)
        repo.submitDraft(submitted, input)
        val abandoned = repo.createDraft()
        val shot = capture()
        repo.attachPhoto(abandoned, shot)
        val orphan = File(photoStore.dir, "orphan.jpg").apply { writeText("x") } // the app died mid-capture

        repo.createDraft()

        assertNull(repo.observeIncident(abandoned).first())
        assertFalse(shot.file.exists())
        assertFalse(orphan.exists())
        assertTrue(kept.file.exists()) // evidence of a submitted report is never purged
    }

    @Test
    fun attachPhoto_sixth_isRejected_andItsFileDeleted() = runTest {
        val id = repo.createDraft()
        repeat(MAX_PHOTOS) { repo.attachPhoto(id, capture()) }
        val sixth = capture()

        assertTrue(runCatching { repo.attachPhoto(id, sixth) }.exceptionOrNull() is IllegalStateException)
        assertFalse(sixth.file.exists())
    }

    @Test
    fun attachPhoto_picked_copiesTheBytesIntoAppStorage() = runTest {
        val original = realJpeg(File(tmp.newFolder("gallery"), "IMG_0001.jpg"))
        val id = repo.createDraft()

        val photo = repo.attachPhoto(id, PhotoInput.Picked(Uri.fromFile(original).toString()))
        original.delete() // the gallery original can vanish at any time

        assertEquals(PhotoSource.GALLERY, photo.source)
        assertEquals(photoStore.dir, photo.file.parentFile)
        assertTrue(photo.file.exists())
    }

    @Test
    fun attachPhoto_exifRotated_swapsWidthAndHeight() = runTest {
        // Portrait shots are often stored as landscape pixels plus a "rotate 90" tag.
        val id = repo.createDraft()

        val photo = repo.attachPhoto(id, capture(ExifInterface.ORIENTATION_ROTATE_90))

        assertEquals(3 to 4, photo.widthPx to photo.heightPx)
    }

    @Test
    fun delete_hardDeletesNeverSyncedReport_butQueuesDeleteForSyncedOne() = runTest {
        val local = repo.createDraft()
        repo.submitDraft(local, input)
        db.incidentDao().upsert(entity("synced"))
        val before = scheduler.calls

        repo.delete(local)
        assertNull(repo.observeIncident(local).first())
        assertEquals(before, scheduler.calls) // the server never heard of it, so there's nothing to send

        repo.delete("synced")
        assertEquals(PendingOp.DELETE, repo.observeIncident("synced").first()!!.pendingOp)
        assertEquals(before + 1, scheduler.calls)
    }

    @Test
    fun update_queuesUpdate_andReadOnlyIncidentsRefuseChanges() = runTest {
        db.incidentDao().upsert(entity("open"))
        db.incidentDao().upsert(entity("resolved", status = Status.RESOLVED))

        repo.update("open", input)
        val edited = repo.observeIncident("open").first()!!
        assertEquals("Leak at bay 3", edited.title)
        assertEquals(PendingOp.UPDATE, edited.pendingOp)
        assertEquals(1, scheduler.calls)

        assertTrue(runCatching { repo.update("resolved", input) }.exceptionOrNull() is IllegalStateException)
        assertTrue(runCatching { repo.delete("resolved") }.exceptionOrNull() is IllegalStateException)
    }
}
