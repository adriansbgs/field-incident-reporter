package com.incidentreporter.field.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.incidentreporter.field.data.local.newDraftEntity
import com.incidentreporter.field.domain.model.PendingOp
import com.incidentreporter.field.testing.entity
import com.incidentreporter.field.testing.photo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real SQLite on the device: these pin behavior a fake could not (foreign keys, upsert semantics, @Embedded nulls). */
@RunWith(AndroidJUnit4::class)
class IncidentDaoTest {

    private lateinit var db: FieldDatabase
    private lateinit var dao: IncidentDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FieldDatabase::class.java).build()
        dao = db.incidentDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun deletingIncident_cascadesToItsPhotoRows() = runTest {
        dao.upsert(entity("a"))
        dao.upsertPhotos(listOf(photo("p1", "a")))

        dao.delete(listOf("a"))

        assertNull(dao.photo("p1"))
    }

    @Test
    fun upsertingExistingIncident_keepsItsPhotoRows() = runTest {
        // REPLACE would delete-then-insert the incident and cascade its photos away. @Upsert must not.
        dao.upsert(entity("a"))
        dao.upsertPhotos(listOf(photo("p1", "a")))

        dao.upsert(entity("a").copy(title = "edited"))

        assertEquals(1, dao.photoCount("a"))
        assertEquals("edited", dao.get("a")!!.incident.title)
    }

    @Test
    fun observeVisible_hidesDraftsAndPendingDeletes_newestFirst() = runTest {
        dao.upsert(entity("old", createdAt = 1))
        dao.upsert(entity("new", createdAt = 2))
        dao.upsert(entity("draft", isDraft = true))
        dao.upsert(entity("deleted", pendingOp = PendingOp.DELETE))

        assertEquals(listOf("new", "old"), dao.observeVisible().first().map { it.incident.id })
    }

    @Test
    fun draftWithoutLocation_readsBackAsNullLocation() = runTest {
        dao.upsert(newDraftEntity(id = "d1", now = 0))

        assertNull(dao.get("d1")!!.incident.location)
    }
}
