package com.incidentreporter.field.data.remote

import com.incidentreporter.field.domain.model.REPORTED_BY
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Exactly the API contract (server/README.md). Enums and times are plain strings here; the mappers parse them. */
@Serializable
data class IncidentDto(
    val id: String,
    val title: String,
    val description: String = "",
    val category: String = "OTHER",
    val severity: String = "MEDIUM",
    val status: String = "OPEN",
    val location: LocationDto? = null,
    val photos: List<PhotoDto> = emptyList(),
    val reportedBy: String = REPORTED_BY,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class LocationDto(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val label: String? = null,
    val source: String = "MANUAL",
)

@Serializable
data class PhotoDto(
    val id: String,
    val fileName: String,
    val source: String = "GALLERY",
    val capturedAt: String,
    val widthPx: Int = 0,
    val heightPx: Int = 0,
)

/** PATCH body: only what a technician can edit, plus updatedAt. Status is server-owned. */
@Serializable
data class IncidentPatchDto(
    val title: String,
    val description: String,
    val category: String,
    val severity: String,
    val location: LocationDto?,
    val updatedAt: String,
)

val AppJson = Json {
    ignoreUnknownKeys = true // a hand-edited db.json may carry extra keys
    explicitNulls = false    // omit nulls when sending
}
