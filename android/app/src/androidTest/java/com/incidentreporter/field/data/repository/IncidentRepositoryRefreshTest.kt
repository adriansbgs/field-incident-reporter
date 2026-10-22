package com.incidentreporter.field.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.incidentreporter.field.core.database.FieldDatabase
import com.incidentreporter.field.core.database.IncidentDao
import com.incidentreporter.field.core.result.AppError
import com.incidentreporter.field.data.photo.PhotoStore
import com.incidentreporter.field.domain.model.PendingOp
import com.incidentreporter.field.domain.model.SyncState
import com.incidentreporter.field.testing.FakeIncidentApi
import com.incidentreporter.field.testing.FakeSyncScheduler
import com.incidentreporter.field.testing.entity
import com.incidentreporter.field.testing.photo
import com.incidentreporter.field.testing.serverIncident
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.time.Clock

/** The pull: server rows land in Room, and nothing the phone still owes the server is lost. */
@RunWith(AndroidJUnit4::class)
class IncidentRepositoryRefreshTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val api = FakeIncidentApi()
    private lateinit var db: FieldDatabase
    private lateinit var dao: IncidentDao
    private lateinit var photoDir: File
    private lateinit var repository: IncidentRepositoryImpl

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val clock = Clock.systemUTC()
        db = Room.inMemoryDatabaseBuilder(context, FieldDatabase::class.java).build()
        dao = db.incidentDao()
        photoDir = tmp.newFolder("photos")
        repository = IncidentRepositoryImpl(db, api, PhotoStore(photoDir, context.contentResolver, clock), clock, FakeSyncScheduler())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun photoFile(name: String) = File(photoDir, name).apply { writeText("jpeg") }

    @Test
    fun refresh_insertsServerRows_andKeepsLocalPendingEdit() = runTest {
        dao.upsert(entity("a", pendingOp = PendingOp.UPDATE, syncState = SyncState.PENDING).copy(title = "Mine"))
        api.rows["a"] = serverIncident("a", title = "Theirs")
        api.rows["b"] = serverIncident("b", photoIds = listOf("p1"))

        assertTrue(repository.refresh().isSuccess)

        assertEquals("Mine", repository.observeIncident("a").first()!!.title)
        assertEquals(listOf("p1"), repository.observeIncident("b").first()!!.photos.map { it.id })
    }

    @Test
    fun refresh_failure_returnsNetworkError_andKeepsCache() = runTest {
        dao.upsert(entity("a"))
        api.failNext = IOException("offline")

        val error = repository.refresh().exceptionOrNull()

        assertTrue(error is AppError.Network)
        assertEquals(listOf("a"), repository.observeIncidents().first().map { it.id })
    }

    @Test
    fun refresh_dropsWhatTheServerDeleted_withItsPhotoFiles_butNeverDrafts() = runTest {
        dao.upsert(entity("gone"))
        dao.upsertPhotos(listOf(photo("p1", "gone")))
        val file = photoFile("p1.jpg")
        dao.upsert(entity("draft", isDraft = true))

        assertTrue(repository.refresh().isSuccess)

        assertNull(dao.get("gone"))
        assertFalse(file.exists())
        assertNotNull(dao.get("draft"))
    }

    @Test
    fun refresh_racingAnUpload_neverDeletesTheReportThatJustSynced() = runTest {
        // The pull's response is already in flight when the sync worker's POST lands and marks the row synced.
        dao.upsert(entity("x", pendingOp = PendingOp.CREATE, syncState = SyncState.SYNCING))
        dao.upsertPhotos(listOf(photo("p1", "x")))
        val file = photoFile("p1.jpg")
        api.onList = {
            api.rows["x"] = serverIncident("x")
            dao.upsert(entity("x")) // SYNCED, nothing pending
        }

        assertTrue(repository.refresh().isSuccess)

        assertNotNull(dao.get("x"))
        assertTrue(file.exists()) // the only copy of the evidence
    }
}
