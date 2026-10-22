package com.incidentreporter.field.data.local

import com.incidentreporter.field.core.database.IncidentEntity
import com.incidentreporter.field.core.database.IncidentWithPhotos
import com.incidentreporter.field.core.database.LocationColumns
import com.incidentreporter.field.core.database.PhotoEntity
import com.incidentreporter.field.domain.model.Category
import com.incidentreporter.field.domain.model.IncidentLocation
import com.incidentreporter.field.domain.model.LocationSource
import com.incidentreporter.field.domain.model.PendingOp
import com.incidentreporter.field.domain.model.PhotoSource
import com.incidentreporter.field.domain.model.Severity
import com.incidentreporter.field.domain.model.Status
import com.incidentreporter.field.domain.model.SyncState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant

class EntityMappersTest {

    private val photoDir = File("/photos")

    private val entity = IncidentEntity(
        id = "a",
        title = "Leak",
        description = "Under the press",
        category = Category.EQUIPMENT_FAILURE,
        severity = Severity.HIGH,
        status = Status.OPEN,
        location = LocationColumns(latitude = -0.9471, longitude = 100.4172, label = "Bay 3", source = LocationSource.GPS),
        reportedBy = "tech-001",
        createdAt = 2_000,
        updatedAt = 3_000,
        isDraft = false,
        syncState = SyncState.PENDING,
        pendingOp = PendingOp.UPDATE,
        lastSyncAttemptAt = null,
        syncAttemptCount = 0,
        lastSyncError = null,
        serverUpdatedAt = 2_000,
    )

    @Test
    fun toDomain_mapsFieldsAndResolvesPhotoFiles() {
        val photo = PhotoEntity(id = "p1", incidentId = "a", fileName = "p1.jpg", source = PhotoSource.CAMERA, capturedAt = 1_000, widthPx = 4, heightPx = 3)

        val incident = IncidentWithPhotos(entity, listOf(photo)).toDomain(photoDir)

        assertEquals(IncidentLocation(-0.9471, 100.4172, "Bay 3", LocationSource.GPS), incident.location)
        assertEquals(Instant.ofEpochMilli(2_000), incident.createdAt)
        assertEquals(Instant.ofEpochMilli(3_000), incident.updatedAt)
        assertEquals(PendingOp.UPDATE, incident.pendingOp)
        assertEquals(File(photoDir, "p1.jpg"), incident.photos.single().file)
        assertEquals(Instant.ofEpochMilli(1_000), incident.photos.single().capturedAt)
    }

    @Test
    fun newDraft_isLocalOnly_andHasNoLocation() {
        val incident = IncidentWithPhotos(newDraftEntity(id = "d1", now = 5_000), emptyList()).toDomain(photoDir)

        assertTrue(incident.isDraft)
        assertNull(incident.location)
        assertEquals(PendingOp.NONE, incident.pendingOp)
        assertEquals(Instant.ofEpochMilli(5_000), incident.createdAt)
    }
}
