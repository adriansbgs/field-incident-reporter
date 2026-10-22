package com.incidentreporter.field.testing

import com.incidentreporter.field.core.database.IncidentEntity
import com.incidentreporter.field.core.database.LocationColumns
import com.incidentreporter.field.core.database.PhotoEntity
import com.incidentreporter.field.data.remote.IncidentDto
import com.incidentreporter.field.data.remote.PhotoDto
import com.incidentreporter.field.domain.model.Category
import com.incidentreporter.field.domain.model.LocationSource
import com.incidentreporter.field.domain.model.PendingOp
import com.incidentreporter.field.domain.model.PhotoSource
import com.incidentreporter.field.domain.model.REPORTED_BY
import com.incidentreporter.field.domain.model.Severity
import com.incidentreporter.field.domain.model.Status
import com.incidentreporter.field.domain.model.SyncState

fun entity(
    id: String,
    isDraft: Boolean = false,
    pendingOp: PendingOp = PendingOp.NONE,
    syncState: SyncState = SyncState.SYNCED,
    createdAt: Long = 0,
    updatedAt: Long = 0,
): IncidentEntity = IncidentEntity(
    id = id,
    title = "Leak $id",
    description = "",
    category = Category.OTHER,
    severity = Severity.MEDIUM,
    status = Status.OPEN,
    location = LocationColumns(latitude = null, longitude = null, label = "Bay 3", source = LocationSource.MANUAL),
    reportedBy = REPORTED_BY,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDraft = isDraft,
    syncState = syncState,
    pendingOp = pendingOp,
    lastSyncAttemptAt = null,
    syncAttemptCount = 0,
    lastSyncError = null,
    serverUpdatedAt = null,
)

fun photo(id: String, incidentId: String): PhotoEntity =
    PhotoEntity(id = id, incidentId = incidentId, fileName = "$id.jpg", source = PhotoSource.CAMERA, capturedAt = 0, widthPx = 4, heightPx = 3)

fun serverIncident(id: String, title: String = "Leak $id", photoIds: List<String> = emptyList()): IncidentDto = IncidentDto(
    id = id,
    title = title,
    photos = photoIds.map { PhotoDto(id = it, fileName = "$it.jpg", capturedAt = "2026-10-20T08:14:02Z") },
    createdAt = "2026-10-20T08:15:30Z",
    updatedAt = "2026-10-20T08:15:30Z",
)
