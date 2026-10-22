package com.incidentreporter.field.core.designsystem

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
import java.time.Duration
import java.time.Instant

/** Sample data for @Preview functions only. */
internal fun previewIncident(
    id: String = "a",
    title: String = "Hydraulic press leaking fluid",
    severity: Severity = Severity.HIGH,
    status: Status = Status.OPEN,
    syncState: SyncState = SyncState.SYNCED,
    photoCount: Int = 2,
): Incident = Incident(
    id = id,
    title = title,
    description = "Puddle forming under the press at bay 3. Area taped off.",
    category = Category.EQUIPMENT_FAILURE,
    severity = severity,
    status = status,
    location = IncidentLocation(latitude = -0.9471, longitude = 100.4172, label = "Bay 3, South Workshop", source = LocationSource.GPS),
    photos = List(photoCount) { i -> Photo("$id$i", "$id$i.jpg", PhotoSource.CAMERA, Instant.EPOCH, 1080, 1920, File("$id$i.jpg")) },
    reportedBy = REPORTED_BY,
    createdAt = Instant.now().minus(Duration.ofMinutes(25)),
    updatedAt = Instant.now().minus(Duration.ofMinutes(5)),
    isDraft = false,
    syncState = syncState,
    pendingOp = PendingOp.NONE,
    lastSyncError = null,
)
