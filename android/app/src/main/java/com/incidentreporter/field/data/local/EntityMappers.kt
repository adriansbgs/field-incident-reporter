package com.incidentreporter.field.data.local

import com.incidentreporter.field.core.database.IncidentEntity
import com.incidentreporter.field.core.database.IncidentWithPhotos
import com.incidentreporter.field.core.database.LocationColumns
import com.incidentreporter.field.core.database.PhotoEntity
import com.incidentreporter.field.domain.model.Category
import com.incidentreporter.field.domain.model.Incident
import com.incidentreporter.field.domain.model.IncidentLocation
import com.incidentreporter.field.domain.model.PendingOp
import com.incidentreporter.field.domain.model.Photo
import com.incidentreporter.field.domain.model.REPORTED_BY
import com.incidentreporter.field.domain.model.Severity
import com.incidentreporter.field.domain.model.Status
import com.incidentreporter.field.domain.model.SyncState
import java.io.File
import java.time.Instant

fun IncidentWithPhotos.toDomain(photoDir: File): Incident = Incident(
    id = incident.id,
    title = incident.title,
    description = incident.description,
    category = incident.category,
    severity = incident.severity,
    status = incident.status,
    location = incident.location?.toDomain(),
    photos = photos.map { it.toDomain(photoDir) },
    reportedBy = incident.reportedBy,
    createdAt = Instant.ofEpochMilli(incident.createdAt),
    updatedAt = Instant.ofEpochMilli(incident.updatedAt),
    isDraft = incident.isDraft,
    syncState = incident.syncState,
    pendingOp = incident.pendingOp,
    lastSyncError = incident.lastSyncError,
)

fun PhotoEntity.toDomain(photoDir: File): Photo = Photo(
    id = id,
    fileName = fileName,
    source = source,
    capturedAt = Instant.ofEpochMilli(capturedAt),
    widthPx = widthPx,
    heightPx = heightPx,
    file = File(photoDir, fileName),
)

private fun LocationColumns.toDomain(): IncidentLocation? =
    source?.let { IncidentLocation(latitude, longitude, label, it) }

/** Local only, never listed, never sent. Placeholders are overwritten on submit. */
fun newDraftEntity(id: String, now: Long): IncidentEntity = IncidentEntity(
    id = id,
    title = "",
    description = "",
    category = Category.OTHER,
    severity = Severity.MEDIUM,
    status = Status.OPEN,
    location = null,
    reportedBy = REPORTED_BY,
    createdAt = now,
    updatedAt = now,
    isDraft = true,
    syncState = SyncState.SYNCED,
    pendingOp = PendingOp.NONE,
    lastSyncAttemptAt = null,
    syncAttemptCount = 0,
    lastSyncError = null,
    serverUpdatedAt = null,
)
