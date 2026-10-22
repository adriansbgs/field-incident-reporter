package com.incidentreporter.field.core.database

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.incidentreporter.field.domain.model.Category
import com.incidentreporter.field.domain.model.LocationSource
import com.incidentreporter.field.domain.model.PendingOp
import com.incidentreporter.field.domain.model.PhotoSource
import com.incidentreporter.field.domain.model.Severity
import com.incidentreporter.field.domain.model.Status
import com.incidentreporter.field.domain.model.SyncState

@Entity(tableName = "incidents")
data class IncidentEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val category: Category,
    val severity: Severity,
    val status: Status,
    @Embedded(prefix = "location_") val location: LocationColumns?,
    val reportedBy: String,
    val createdAt: Long,
    val updatedAt: Long,
    // Local-only sync bookkeeping. Never sent to the server.
    val isDraft: Boolean,
    val syncState: SyncState,
    val pendingOp: PendingOp,
    val lastSyncAttemptAt: Long?,
    val syncAttemptCount: Int,
    val lastSyncError: String?,
    val serverUpdatedAt: Long?,
)

data class LocationColumns(
    val latitude: Double?,
    val longitude: Double?,
    val label: String?,
    val source: LocationSource?,
)

@Entity(
    tableName = "photos",
    foreignKeys = [
        ForeignKey(
            entity = IncidentEntity::class,
            parentColumns = ["id"],
            childColumns = ["incidentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("incidentId")],
)
data class PhotoEntity(
    @PrimaryKey val id: String,
    val incidentId: String,
    val fileName: String,
    val source: PhotoSource,
    val capturedAt: Long,
    val widthPx: Int,
    val heightPx: Int,
)

/** One incident with its photos, loaded together. */
data class IncidentWithPhotos(
    @Embedded val incident: IncidentEntity,
    @Relation(parentColumn = "id", entityColumn = "incidentId") val photos: List<PhotoEntity>,
)
