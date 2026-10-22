package com.incidentreporter.field.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.incidentreporter.field.core.database.FieldDatabase
import com.incidentreporter.field.data.local.newDraftEntity
import com.incidentreporter.field.data.photo.PhotoStore
import com.incidentreporter.field.data.repository.IncidentRepositoryImpl
import com.incidentreporter.field.domain.model.Category
import com.incidentreporter.field.domain.model.IncidentInput
import com.incidentreporter.field.domain.model.IncidentLocation
import com.incidentreporter.field.domain.model.LocationSource
import com.incidentreporter.field.domain.model.PendingOp
import com.incidentreporter.field.domain.model.Severity
import com.incidentreporter.field.domain.model.SyncState
import com.incidentreporter.field.testing.FakeIncidentApi
import com.incidentreporter.field.testing.FakeSyncScheduler
import com.incidentreporter.field.testing.MutableClock
import com.incidentreporter.field.testing.httpError
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.time.Instant

/** The upload: each change reaches the server once, conflicts are caught, and failures stop where the user can see them. */
@RunWith(AndroidJUnit4::class)
class SyncProcessorTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val api = FakeIncidentApi()
    private val clock = MutableClock()
    private lateinit var db: FieldDatabase
    private lateinit var repository: IncidentRepositoryImpl
    private lateinit var processor: SyncProcessor

    private val input = IncidentInput(
        title = "Leak at bay 3",
        category = Category.PLUMBING,
        severity = Severity.HIGH,
        location = IncidentLocation(latitude = null, longitude = null, label = "Bay 3", source = LocationSource.MANUAL),
    )

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, FieldDatabase::class.java).build()
        val photoStore = PhotoStore(tmp.newFolder("photos"), context.contentResolver, clock)
        repository = IncidentRepositoryImpl(db, api, photoStore, clock, FakeSyncScheduler())
        processor = SyncProcessor(db, api, photoStore, clock)
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** A report the technician submitted, waiting for its first upload. */
    private suspend fun submitted(id: String, title: String = "Leak $id") {
        db.incidentDao().upsert(newDraftEntity(id, clock.millis()))
        repository.submitDraft(id, input.copy(title = title))
    }

    private suspend fun synced(id: String) {
        submitted(id)
        assertEquals(SyncOutcome.DONE, processor.syncPending())
    }

    /** Synced, then edited a second later, so the edit waits as an UPDATE. */
    private suspend fun syncedThenEdited(id: String) {
        synced(id)
        clock.advance(1_000)
        repository.update(id, input.copy(title = "Edited"))
    }

    private suspend fun row(id: String) = db.incidentDao().get(id)!!.incident

    private suspend fun state(id: String) = row(id).syncState

    private suspend fun op(id: String) = row(id).pendingOp

    private suspend fun error(id: String) = row(id).lastSyncError

    private suspend fun localUpdatedAtIso(id: String) = Instant.ofEpochMilli(row(id).updatedAt).toString()

    @Test
    fun create_success_marksSynced() = runTest {
        submitted("a")

        assertEquals(SyncOutcome.DONE, processor.syncPending())

        assertEquals(SyncState.SYNCED, state("a"))
        assertEquals(PendingOp.NONE, op("a"))
        assertTrue("a" in api.rows)
    }

    @Test
    fun lostCreateResponse_retryPatchesInsteadOfDuplicating() = runTest {
        submitted("a")
        api.dropNextCreateResponse = true

        assertEquals(SyncOutcome.RETRY, processor.syncPending())
        assertEquals(SyncState.PENDING, state("a"))
        assertEquals(SyncOutcome.DONE, processor.syncPending())

        assertEquals(1, api.rows.size)
        assertEquals(1, api.createCalls) // a second POST would hit json-server's duplicate-id 500
        assertEquals(SyncState.SYNCED, state("a"))
    }

    @Test
    fun editDuringCreateUpload_endsAsPendingUpdate() = runTest {
        submitted("a", title = "First")
        api.onCreate = {
            clock.advance(1_000)
            repository.update("a", input.copy(title = "Second"))
        }

        processor.syncPending()

        assertEquals(PendingOp.UPDATE, op("a"))
        assertEquals(SyncState.PENDING, state("a"))

        api.onCreate = {}
        processor.syncPending()

        assertEquals("Second", api.rows["a"]!!.title)
        assertEquals(SyncState.SYNCED, state("a"))
    }

    @Test
    fun update_whenServerNewer_marksStale_andDoesNotPatch() = runTest {
        syncedThenEdited("a")
        api.rows["a"] = api.rows["a"]!!.copy(updatedAt = "2030-01-01T00:00:00Z") // an operator changed it meanwhile

        processor.syncPending()

        assertEquals(SyncState.STALE, state("a"))
        assertEquals(0, api.updateCalls)
        assertEquals("Edited", row("a").title) // the technician's edit is kept, not thrown away
    }

    @Test
    fun update_afterOwnWriteLanded_isNotStale() = runTest {
        // The app was killed after its PATCH reached the server but before it recorded the success.
        syncedThenEdited("a")
        api.rows["a"] = api.rows["a"]!!.copy(updatedAt = localUpdatedAtIso("a"))

        processor.syncPending()

        assertEquals(SyncState.SYNCED, state("a"))
    }

    @Test
    fun serverError_fifthTime_marksFailed() = runTest {
        submitted("a")

        repeat(5) {
            api.failNext = httpError(503)
            processor.syncPending()
        }

        assertEquals(SyncState.FAILED, state("a"))
        assertEquals("Server error (HTTP 503)", error("a"))
    }

    @Test
    fun delete_404_isSuccess_andRemovesRow() = runTest {
        synced("a")
        repository.delete("a")
        api.rows.remove("a") // someone else deleted it first

        assertEquals(SyncOutcome.DONE, processor.syncPending())

        assertNull(repository.observeIncident("a").first())
    }

    @Test
    fun staleRow_reEdited_overwritesServer() = runTest {
        // Editing again after seeing "Changed on server" is a deliberate choice, so the technician's version wins.
        syncedThenEdited("a")
        api.rows["a"] = api.rows["a"]!!.copy(updatedAt = "2030-01-01T00:00:00Z")
        processor.syncPending()
        assertEquals(SyncState.STALE, state("a"))

        clock.advance(1_000)
        repository.update("a", input.copy(title = "Mine, on purpose"))
        processor.syncPending()

        assertEquals(SyncState.SYNCED, state("a"))
        assertEquals("Mine, on purpose", api.rows["a"]!!.title)
    }
}
