package com.incidentreporter.field.domain.model

import java.io.File
import java.time.Instant

enum class Severity { LOW, MEDIUM, HIGH, CRITICAL }
enum class Status { OPEN, IN_PROGRESS, RESOLVED }
enum class Category { EQUIPMENT_FAILURE, ROAD_HAZARD, ELECTRICAL, PLUMBING, STRUCTURAL, OTHER }
enum class LocationSource { GPS, MANUAL }
enum class PhotoSource { CAMERA, GALLERY }

/** Local only: where this record stands against the server. Never sent over the API. */
enum class SyncState { SYNCED, PENDING, SYNCING, FAILED, STALE }

/** Local only: the one change this record still owes the server. */
enum class PendingOp { NONE, CREATE, UPDATE, DELETE }

const val MAX_TITLE_LENGTH = 80
const val MAX_DESCRIPTION_LENGTH = 1000
const val MAX_PHOTOS = 5
const val REPORTED_BY = "tech-001"

data class IncidentLocation(
    val latitude: Double?,
    val longitude: Double?,
    val label: String?,
    val source: LocationSource,
)

data class Photo(
    val id: String,
    val fileName: String,
    val source: PhotoSource,
    val capturedAt: Instant,
    val widthPx: Int,
    val heightPx: Int,
    val file: File,
)

data class Incident(
    val id: String,
    val title: String,
    val description: String,
    val category: Category,
    val severity: Severity,
    val status: Status,
    val location: IncidentLocation?,
    val photos: List<Photo>,
    val reportedBy: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val isDraft: Boolean,
    val syncState: SyncState,
    val pendingOp: PendingOp,
    val lastSyncError: String?,
) {
    /** Only OPEN incidents may be edited or deleted. */
    val isEditable: Boolean get() = status == Status.OPEN
}

/** What the form submits: the fields a technician can set. */
data class IncidentInput(
    val title: String = "",
    val description: String = "",
    val category: Category? = null,
    val severity: Severity = Severity.MEDIUM,
    val location: IncidentLocation? = null,
    val photoCount: Int = 0,
)

/** A photo on its way into a draft: a camera shot already written to disk, or a gallery pick. */
sealed interface PhotoInput {
    data class Captured(val photoId: String, val file: File) : PhotoInput
    data class Picked(val uri: String) : PhotoInput
}
