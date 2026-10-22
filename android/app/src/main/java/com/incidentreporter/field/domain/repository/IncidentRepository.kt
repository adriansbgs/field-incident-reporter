package com.incidentreporter.field.domain.repository

import com.incidentreporter.field.domain.model.Incident
import kotlinx.coroutines.flow.Flow

/** The app's only door to incident data. Screens read Room through it and never wait on the network. */
interface IncidentRepository {

    /** Submitted incidents, newest first. Excludes drafts and rows waiting to be deleted. */
    fun observeIncidents(): Flow<List<Incident>>

    /** One incident, drafts included. Emits null when it doesn't exist. */
    fun observeIncident(id: String): Flow<Incident?>

    /** Pulls the server's list into Room. A failure is always an AppError, and the cached rows stay. */
    suspend fun refresh(): Result<Unit>
}
