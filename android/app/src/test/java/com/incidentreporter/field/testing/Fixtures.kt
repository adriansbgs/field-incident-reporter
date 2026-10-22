package com.incidentreporter.field.testing

import com.incidentreporter.field.domain.model.Category
import com.incidentreporter.field.domain.model.Incident
import com.incidentreporter.field.domain.model.IncidentLocation
import com.incidentreporter.field.domain.model.LocationSource
import com.incidentreporter.field.domain.model.PendingOp
import com.incidentreporter.field.domain.model.Photo
import com.incidentreporter.field.domain.model.PhotoSource
import com.incidentreporter.field.domain.model.REPORTED_BY
import com.incidentreporter.field.domain.model.Severity
import com.incidentreporter.field.domain.model.Status
import com.incidentreporter.field.domain.model.SyncState
import java.io.File
import java.time.Instant

private val T0: Instant = Instant.parse("2026-10-20T08:00:00Z")

fun anIncident(
    id: String = "i1",
    title: String = "Leak",
    status: Status = Status.OPEN,
    isDraft: Boolean = false,
    syncState: SyncState = SyncState.SYNCED,
    photos: List<Photo> = emptyList(),
): Incident = Incident(
    id = id,
    title = title,
    description = "",
    category = Category.OTHER,
    severity = Severity.MEDIUM,
    status = status,
    location = IncidentLocation(latitude = null, longitude = null, label = "Bay 3", source = LocationSource.MANUAL),
    photos = photos,
    reportedBy = REPORTED_BY,
    createdAt = T0,
    updatedAt = T0,
    isDraft = isDraft,
    syncState = syncState,
    pendingOp = PendingOp.NONE,
    lastSyncError = null,
)

fun aPhoto(id: String): Photo = Photo(
    id = id,
    fileName = "$id.jpg",
    source = PhotoSource.CAMERA,
    capturedAt = T0,
    widthPx = 4,
    heightPx = 3,
    file = File("/photos/$id.jpg"),
)
