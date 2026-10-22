package com.incidentreporter.field.data.repository

import androidx.room.withTransaction
import com.incidentreporter.field.core.database.FieldDatabase
import com.incidentreporter.field.core.result.AppError
import com.incidentreporter.field.core.result.apiCall
import com.incidentreporter.field.data.local.toDomain
import com.incidentreporter.field.data.photo.PhotoStore
import com.incidentreporter.field.data.remote.IncidentApi
import com.incidentreporter.field.data.remote.toLocal
import com.incidentreporter.field.domain.model.Incident
import com.incidentreporter.field.domain.repository.IncidentRepository
import com.incidentreporter.field.domain.usecase.SyncDecision
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class IncidentRepositoryImpl @Inject constructor(
    private val db: FieldDatabase,
    private val api: IncidentApi,
    private val photoStore: PhotoStore,
) : IncidentRepository {

    private val dao = db.incidentDao()

    override fun observeIncidents(): Flow<List<Incident>> =
        dao.observeVisible().map { rows -> rows.map { it.toDomain(photoStore.dir) } }

    override fun observeIncident(id: String): Flow<Incident?> =
        dao.observe(id).map { it?.toDomain(photoStore.dir) }

    override suspend fun refresh(): Result<Unit> = try {
        // Read before the fetch: a row still owed to the server when we asked stays protected,
        // even if its upload lands while our response is in flight.
        val before = dao.localMeta()
        val remote = apiCall { api.list().map { it.toLocal() } }
        val deletedFiles = db.withTransaction {
            val plan = SyncDecision.planReconcile(before + dao.localMeta(), remote.map { it.incident.id }.toSet())
            remote.filter { it.incident.id in plan.upsert }.forEach { row ->
                dao.upsert(row.incident)
                dao.upsertPhotos(row.photos)
                dao.deletePhotosExcept(row.incident.id, row.photos.map { it.id })
            }
            val gone = plan.delete.toList()
            val names = dao.photoFileNames(gone) // read first: deleting the rows cascades to their photo rows
            dao.delete(gone)
            names
        }
        photoStore.delete(deletedFiles)
        Result.success(Unit)
    } catch (e: AppError) {
        Result.failure(e)
    }
}
