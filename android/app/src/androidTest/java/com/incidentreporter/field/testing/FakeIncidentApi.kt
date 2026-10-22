package com.incidentreporter.field.testing

import com.incidentreporter.field.data.remote.IncidentApi
import com.incidentreporter.field.data.remote.IncidentDto
import com.incidentreporter.field.data.remote.IncidentPatchDto
import retrofit2.Response

/** In-memory json-server. Only the pull so far; the write endpoints arrive with the sync tests (Task 8). */
class FakeIncidentApi : IncidentApi {

    val rows = linkedMapOf<String, IncidentDto>()

    /** Thrown by the next call, once. */
    var failNext: Throwable? = null

    /** Runs after list() has read the rows but before it returns: a response in flight. */
    var onList: suspend () -> Unit = {}

    override suspend fun list(): List<IncidentDto> {
        failNext?.let { failNext = null; throw it }
        val response = rows.values.sortedByDescending { it.createdAt }
        onList()
        return response
    }

    override suspend fun get(id: String): IncidentDto = TODO("Task 8")

    override suspend fun create(body: IncidentDto): IncidentDto = TODO("Task 8")

    override suspend fun update(id: String, body: IncidentPatchDto): IncidentDto = TODO("Task 8")

    override suspend fun delete(id: String): Response<Unit> = TODO("Task 8")
}
