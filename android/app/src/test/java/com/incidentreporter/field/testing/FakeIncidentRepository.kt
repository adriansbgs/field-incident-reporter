package com.incidentreporter.field.testing

import com.incidentreporter.field.domain.model.Incident
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
}
