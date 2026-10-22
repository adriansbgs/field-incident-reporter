package com.incidentreporter.field.testing

import com.incidentreporter.field.data.remote.IncidentApi
import com.incidentreporter.field.data.remote.IncidentDto
import com.incidentreporter.field.data.remote.IncidentPatchDto
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/** In-memory json-server, quirks included: a missing id is a 404, a duplicate POST is a 500. */
class FakeIncidentApi : IncidentApi {

    val rows = linkedMapOf<String, IncidentDto>()

    /** Thrown by the next call, once. */
    var failNext: Throwable? = null

    /** Runs after list() has read the rows but before it returns: a response in flight. */
    var onList: suspend () -> Unit = {}

    /** Runs after create() has stored the row but before it returns. */
    var onCreate: suspend () -> Unit = {}

    /** The next create() stores the row, then the connection drops before the response arrives. */
    var dropNextCreateResponse = false

    var createCalls = 0
        private set

    var updateCalls = 0
        private set

    override suspend fun list(): List<IncidentDto> {
        failIfAsked()
        val response = rows.values.sortedByDescending { it.createdAt }
        onList()
        return response
    }

    override suspend fun get(id: String): IncidentDto {
        failIfAsked()
        return rows[id] ?: throw httpError(404)
    }

    override suspend fun create(body: IncidentDto): IncidentDto {
        failIfAsked()
        createCalls++
        if (body.id in rows) throw httpError(500) // json-server refuses a duplicate id with a 500
        rows[body.id] = body
        onCreate()
        if (dropNextCreateResponse) {
            dropNextCreateResponse = false
            throw IOException("Connection reset")
        }
        return body
    }

    override suspend fun update(id: String, body: IncidentPatchDto): IncidentDto {
        failIfAsked()
        updateCalls++
        val row = rows[id] ?: throw httpError(404)
        val updated = row.copy(
            title = body.title,
            description = body.description,
            category = body.category,
            severity = body.severity,
            location = body.location,
            updatedAt = body.updatedAt,
        )
        rows[id] = updated
        return updated
    }

    override suspend fun delete(id: String): Response<Unit> {
        failIfAsked()
        return if (rows.remove(id) != null) Response.success(Unit) else Response.error(404, "{}".toResponseBody())
    }

    private fun failIfAsked() {
        failNext?.let {
            failNext = null
            throw it
        }
    }
}

/** What Retrofit throws for a non-2xx response. */
fun httpError(code: Int): HttpException = HttpException(Response.error<Any>(code, "{}".toResponseBody()))
