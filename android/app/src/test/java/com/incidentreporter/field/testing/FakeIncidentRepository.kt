package com.incidentreporter.field.testing

import com.incidentreporter.field.domain.model.Incident
import com.incidentreporter.field.domain.model.IncidentInput
import com.incidentreporter.field.domain.model.Photo
import com.incidentreporter.field.domain.model.PhotoInput
import com.incidentreporter.field.domain.repository.IncidentRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory repository for ViewModel tests. Writes are recorded, not applied. */
class FakeIncidentRepository : IncidentRepository {

    val incidents = MutableStateFlow<List<Incident>>(emptyList())

    var refreshResult: suspend () -> Result<Unit> = { Result.success(Unit) }

    /** Every write, in order, as "method:id". */
    val calls = mutableListOf<String>()

    /** When set, submitDraft waits for it: a submit still in flight. */
    var submitGate: CompletableDeferred<Unit>? = null

    override fun observeIncidents(): Flow<List<Incident>> = incidents

    override fun observeIncident(id: String): Flow<Incident?> = incidents.map { all -> all.find { it.id == id } }

    override suspend fun refresh(): Result<Unit> = refreshResult()

    override suspend fun createDraft(): String {
        calls += "createDraft"
        return "draft"
    }

    override suspend fun attachPhoto(incidentId: String, input: PhotoInput): Photo {
        calls += "attachPhoto:$incidentId"
        return aPhoto("picked")
    }

    override suspend fun removePhoto(photoId: String) {
        calls += "removePhoto:$photoId"
    }

    override suspend fun discardDraft(id: String) {
        calls += "discardDraft:$id"
    }

    override suspend fun submitDraft(id: String, input: IncidentInput) {
        calls += "submitDraft:$id"
        submitGate?.await()
    }

    override suspend fun update(id: String, input: IncidentInput) {
        calls += "update:$id"
    }

    override suspend fun delete(id: String) {
        calls += "delete:$id"
    }
}
