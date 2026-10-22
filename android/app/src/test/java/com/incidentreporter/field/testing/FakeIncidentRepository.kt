package com.incidentreporter.field.testing

import com.incidentreporter.field.domain.model.Incident
import com.incidentreporter.field.domain.model.IncidentInput
import com.incidentreporter.field.domain.model.Photo
import com.incidentreporter.field.domain.model.PhotoInput
import com.incidentreporter.field.domain.repository.IncidentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory repository for ViewModel tests. Grows as the interface grows. */
class FakeIncidentRepository : IncidentRepository {

    val incidents = MutableStateFlow<List<Incident>>(emptyList())

    var refreshResult: suspend () -> Result<Unit> = { Result.success(Unit) }

    override fun observeIncidents(): Flow<List<Incident>> = incidents

    override fun observeIncident(id: String): Flow<Incident?> = incidents.map { all -> all.find { it.id == id } }

    override suspend fun refresh(): Result<Unit> = refreshResult()

    // No ViewModel writes yet. These get real fakes when the form arrives.
    override suspend fun createDraft(): String = TODO()

    override suspend fun attachPhoto(incidentId: String, input: PhotoInput): Photo = TODO()

    override suspend fun removePhoto(photoId: String): Unit = TODO()

    override suspend fun discardDraft(id: String): Unit = TODO()

    override suspend fun submitDraft(id: String, input: IncidentInput): Unit = TODO()

    override suspend fun update(id: String, input: IncidentInput): Unit = TODO()

    override suspend fun delete(id: String): Unit = TODO()
}
