package com.incidentreporter.field.data.remote

import com.incidentreporter.field.core.database.IncidentEntity
import com.incidentreporter.field.core.database.IncidentWithPhotos
import com.incidentreporter.field.core.database.LocationColumns
import com.incidentreporter.field.core.database.PhotoEntity
import com.incidentreporter.field.domain.model.Category
import com.incidentreporter.field.domain.model.LocationSource
import com.incidentreporter.field.domain.model.PendingOp
import com.incidentreporter.field.domain.model.PhotoSource
import com.incidentreporter.field.domain.model.Severity
import com.incidentreporter.field.domain.model.Status
import com.incidentreporter.field.domain.model.SyncState
import java.time.Instant

/** A server row as stored locally: synced, nothing owed, and its updatedAt kept as the conflict baseline. */
fun IncidentDto.toLocal(): IncidentWithPhotos {
    val serverTime = Instant.parse(updatedAt).toEpochMilli()
    return IncidentWithPhotos(
        incident = IncidentEntity(
            id = id,
            title = title,
            description = description,
            category = category.toEnumOr(Category.OTHER),
            severity = severity.toEnumOr(Severity.MEDIUM),
            status = status.toEnumOr(Status.IN_PROGRESS), // unknown status: read-only, never editable
            location = location?.let { LocationColumns(it.latitude, it.longitude, it.label, it.source.toEnumOr(LocationSource.MANUAL)) },
            reportedBy = reportedBy,
            createdAt = Instant.parse(createdAt).toEpochMilli(),
            updatedAt = serverTime,
            isDraft = false,
            syncState = SyncState.SYNCED,
            pendingOp = PendingOp.NONE,
            lastSyncAttemptAt = null,
            syncAttemptCount = 0,
            lastSyncError = null,
            serverUpdatedAt = serverTime,
        ),
        photos = photos.map {
            PhotoEntity(
                id = it.id,
                incidentId = id,
                fileName = it.fileName,
                source = it.source.toEnumOr(PhotoSource.GALLERY), // unknown provenance: least trusted
                capturedAt = Instant.parse(it.capturedAt).toEpochMilli(),
                widthPx = it.widthPx,
                heightPx = it.heightPx,
            )
        },
    )
}

fun IncidentWithPhotos.toDto(): IncidentDto = IncidentDto(
    id = incident.id,
    title = incident.title,
    description = incident.description,
    category = incident.category.name,
    severity = incident.severity.name,
    status = incident.status.name,
    location = incident.location?.toDto(),
    photos = photos.map { PhotoDto(it.id, it.fileName, it.source.name, it.capturedAt.toIso(), it.widthPx, it.heightPx) },
    reportedBy = incident.reportedBy,
    createdAt = incident.createdAt.toIso(),
    updatedAt = incident.updatedAt.toIso(),
)

fun IncidentEntity.toPatch(): IncidentPatchDto = IncidentPatchDto(
    title = title,
    description = description,
    category = category.name,
    severity = severity.name,
    location = location?.toDto(),
    updatedAt = updatedAt.toIso(),
)

private fun LocationColumns.toDto() = LocationDto(latitude, longitude, label, (source ?: LocationSource.MANUAL).name)

private fun Long.toIso(): String = Instant.ofEpochMilli(this).toString()

private inline fun <reified E : Enum<E>> String.toEnumOr(default: E): E =
    enumValues<E>().firstOrNull { it.name == this } ?: default
