package com.incidentreporter.field.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

interface IncidentApi {
    @GET("incidents?_sort=createdAt&_order=desc")
    suspend fun list(): List<IncidentDto>

    @GET("incidents/{id}")
    suspend fun get(@Path("id") id: String): IncidentDto

    @POST("incidents")
    suspend fun create(@Body body: IncidentDto): IncidentDto

    @PATCH("incidents/{id}")
    suspend fun update(@Path("id") id: String, @Body body: IncidentPatchDto): IncidentDto

    /** Returns the raw response so a 404 ("already gone") can count as success without an exception. */
    @DELETE("incidents/{id}")
    suspend fun delete(@Path("id") id: String): Response<Unit>
}
