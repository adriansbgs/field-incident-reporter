package com.incidentreporter.field.domain.repository

import com.incidentreporter.field.domain.model.Incident
import com.incidentreporter.field.domain.model.IncidentInput
import com.incidentreporter.field.domain.model.Photo
import com.incidentreporter.field.domain.model.PhotoInput
import kotlinx.coroutines.flow.Flow

/** The app's only door to incident data. Screens read Room through it and never wait on the network. */
interface IncidentRepository {

    /** Submitted incidents, newest first. Excludes drafts and rows waiting to be deleted. */
    fun observeIncidents(): Flow<List<Incident>>

    /** One incident, drafts included. Emits null when it doesn't exist. */
    fun observeIncident(id: String): Flow<Incident?>

    /** Pulls the server's list into Room. A failure is always an AppError, and the cached rows stay. */
    suspend fun refresh(): Result<Unit>

    /** Starts a new report as a hidden draft. Other drafts, and photo files nothing points at, are purged first. */
    suspend fun createDraft(): String

    /** Takes the photo's bytes and attaches them to a draft. Throws IllegalStateException past MAX_PHOTOS. */
    suspend fun attachPhoto(incidentId: String, input: PhotoInput): Photo

    /** Removes one of a draft's photos, row and file. */
    suspend fun removePhoto(photoId: String)

    /** Throws a draft away with its photos. Does nothing if it's already gone. */
    suspend fun discardDraft(id: String)

    /** Turns a draft into a report waiting to upload. A second call does nothing. */
    suspend fun submitDraft(id: String, input: IncidentInput)

    /** Edits an open report. Throws IllegalStateException for any other status. */
    suspend fun update(id: String, input: IncidentInput)

    /** Deletes an open report: at once if the server never saw it, otherwise once the server agrees. */
    suspend fun delete(id: String)
}
