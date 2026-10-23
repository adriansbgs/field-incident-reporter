package com.incidentreporter.field.core.di

import android.content.Context
import androidx.room.Room
import com.incidentreporter.field.BuildConfig
import com.incidentreporter.field.core.database.FieldDatabase
import com.incidentreporter.field.data.location.FusedCurrentLocation
import com.incidentreporter.field.data.photo.PhotoStore
import com.incidentreporter.field.data.remote.AppJson
import com.incidentreporter.field.data.remote.IncidentApi
import com.incidentreporter.field.data.repository.IncidentRepositoryImpl
import com.incidentreporter.field.data.sync.SyncScheduler
import com.incidentreporter.field.data.sync.WorkManagerSyncScheduler
import com.incidentreporter.field.domain.repository.CurrentLocation
import com.incidentreporter.field.domain.repository.IncidentRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun incidentApi(): IncidentApi = Retrofit.Builder()
        .baseUrl(BuildConfig.BASE_URL)
        .addConverterFactory(AppJson.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(IncidentApi::class.java)

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): FieldDatabase =
        Room.databaseBuilder(context, FieldDatabase::class.java, "field.db").build()

    @Provides
    @Singleton
    fun photoStore(@ApplicationContext context: Context, clock: Clock): PhotoStore =
        PhotoStore(File(context.filesDir, "photos").apply { mkdirs() }, context.contentResolver, clock)

    @Provides
    fun incidentRepository(impl: IncidentRepositoryImpl): IncidentRepository = impl

    @Provides
    fun clock(): Clock = Clock.systemUTC()

    @Provides
    fun syncScheduler(impl: WorkManagerSyncScheduler): SyncScheduler = impl

    @Provides
    fun currentLocation(impl: FusedCurrentLocation): CurrentLocation = impl
}
