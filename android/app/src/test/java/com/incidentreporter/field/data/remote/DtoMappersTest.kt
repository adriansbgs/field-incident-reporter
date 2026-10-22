package com.incidentreporter.field.data.remote

import com.incidentreporter.field.domain.model.Category
import com.incidentreporter.field.domain.model.LocationSource
import com.incidentreporter.field.domain.model.PendingOp
import com.incidentreporter.field.domain.model.PhotoSource
import com.incidentreporter.field.domain.model.Severity
import com.incidentreporter.field.domain.model.Status
import com.incidentreporter.field.domain.model.SyncState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class DtoMappersTest {

    private val sample = IncidentDto(
        id = "24f2d67b-518a-405b-afd8-8e75becaa72a",
        title = "Hydraulic press leaking fluid",
        description = "Puddle forming under the press at bay 3.",
        category = "EQUIPMENT_FAILURE",
        severity = "HIGH",
        status = "OPEN",
        location = LocationDto(latitude = -0.9471, longitude = 100.4172, label = "Bay 3, South Workshop", source = "GPS"),
        photos = listOf(PhotoDto(id = "p1", fileName = "p1.jpg", source = "CAMERA", capturedAt = "2026-10-20T08:14:02Z", widthPx = 1080, heightPx = 1920)),
        reportedBy = "tech-001",
        createdAt = "2026-10-20T08:15:30Z",
        updatedAt = "2026-10-20T09:00:00Z",
    )

    private fun decode(json: String) = AppJson.decodeFromString(IncidentDto.serializer(), json)

    @Test
    fun decode_toleratesMissingOptionalFieldsAndUnknownValues() {
        // What a hand-edited db.json can contain: unknown enum strings, an extra key, missing optional fields.
        val edited = decode(
            """
            {"id":"x","title":"T","createdAt":"2026-10-20T08:15:30Z","updatedAt":"2026-10-20T08:15:30Z",
             "category":"VOLCANO","severity":"URGENT","status":"CLOSED","priority":3,
             "location":{"source":"SATELLITE","label":"Gate"},
             "photos":[{"id":"p","fileName":"p.jpg","source":"SCANNER","capturedAt":"2026-10-20T08:14:02Z"}]}
            """,
        ).toLocal()

        with(edited.incident) {
            assertEquals("", description)
            assertEquals(Category.OTHER, category)
            assertEquals(Severity.MEDIUM, severity)
            assertEquals(Status.IN_PROGRESS, status) // unknown status is read-only, never editable
            assertEquals(LocationSource.MANUAL, location!!.source)
        }
        assertEquals(PhotoSource.GALLERY, edited.photos.single().source) // unknown provenance is the least trusted

        val bare = decode("""{"id":"y","title":"T","createdAt":"2026-10-20T08:15:30Z","updatedAt":"2026-10-20T08:15:30Z"}""").toLocal()
        assertNull(bare.incident.location)
        assertTrue(bare.photos.isEmpty())
    }

    @Test
    fun toLocal_isSyncedAndRemembersServerTime() {
        val local = sample.toLocal().incident

        assertEquals(SyncState.SYNCED, local.syncState)
        assertEquals(PendingOp.NONE, local.pendingOp)
        assertFalse(local.isDraft)
        assertEquals(Instant.parse("2026-10-20T09:00:00Z").toEpochMilli(), local.serverUpdatedAt) // baseline for conflict detection
    }

    @Test
    fun roundTrip_dtoToLocalToDto_isLossless() {
        assertEquals(sample, sample.toLocal().toDto())
    }

    @Test
    fun wireFormat_hasNoLocalOnlyFields_andPatchNeverSendsStatus() {
        val local = sample.toLocal()
        val created = AppJson.encodeToString(IncidentDto.serializer(), local.toDto())
        listOf("isDraft", "syncState", "pendingOp", "serverUpdatedAt", "lastSync").forEach {
            assertFalse("POST body leaks '$it'", created.contains(it))
        }

        val patch = AppJson.encodeToString(IncidentPatchDto.serializer(), local.incident.toPatch())
        listOf("status", "photos", "createdAt", "\"id\"").forEach { // status is server-owned
            assertFalse("PATCH body sends $it", patch.contains(it))
        }
        assertTrue(patch.contains("2026-10-20T09:00:00Z")) // updatedAt travels with every edit
    }
}
