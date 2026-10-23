package com.incidentreporter.field.data.location

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.incidentreporter.field.domain.repository.Coordinates
import com.incidentreporter.field.domain.repository.CurrentLocation
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

/** Play Services picks GPS, Wi-Fi or the mobile network for us, and a current fix is one call. */
class FusedCurrentLocation @Inject constructor(@ApplicationContext context: Context) : CurrentLocation {

    private val client = LocationServices.getFusedLocationProviderClient(context)

    // Only called once a location permission is granted. If it's revoked meanwhile, the SecurityException lands in the catch.
    @OptIn(ExperimentalCoroutinesApi::class)
    @SuppressLint("MissingPermission")
    override suspend fun get(): Coordinates? = try {
        withTimeoutOrNull(10_000.milliseconds) {
            val cancel = CancellationTokenSource()
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancel.token).await(cancel)
        }?.let { Coordinates(it.latitude, it.longitude) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null // no permission, or no Play Services: the place label still works
    }
}
