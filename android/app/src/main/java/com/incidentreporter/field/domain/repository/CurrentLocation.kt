package com.incidentreporter.field.domain.repository

data class Coordinates(val latitude: Double, val longitude: Double)

/** Where the phone is right now, or null when there is no permission or no fix in time. */
fun interface CurrentLocation {
    suspend fun get(): Coordinates?
}
