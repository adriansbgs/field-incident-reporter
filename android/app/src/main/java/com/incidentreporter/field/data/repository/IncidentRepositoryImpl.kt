package com.incidentreporter.field.data.repository

import androidx.room.withTransaction
import com.incidentreporter.field.core.database.FieldDatabase
import com.incidentreporter.field.core.database.IncidentEntity
import com.incidentreporter.field.core.database.IncidentWithPhotos
import com.incidentreporter.field.core.result.AppError
import com.incidentreporter.field.core.result.apiCall
import com.incidentreporter.field.data.local.newDraftEntity
import com.incidentreporter.field.data.local.toColumns
import com.incidentreporter.field.data.local.toDomain
import com.incidentreporter.field.data.local.toEntity
import com.incidentreporter.field.data.photo.PhotoStore
import com.incidentreporter.field.data.remote.IncidentApi
import com.incidentreporter.field.data.remote.toLocal
import com.incidentreporter.field.data.sync.SyncScheduler
import com.incidentreporter.field.domain.model.Incident
import com.incidentreporter.field.domain.model.IncidentInput
import com.incidentreporter.field.domain.model.MAX_PHOTOS
import com.incidentreporter.field.domain.model.PendingOp
import com.incidentreporter.field.domain.model.Photo
import com.incidentreporter.field.domain.model.PhotoInput
import com.incidentreporter.field.domain.model.REPORTED_BY
import com.incidentreporter.field.domain.model.Status
import com.incidentreporter.field.domain.model.SyncState
import com.incidentreporter.field.domain.repository.IncidentRepository
import com.incidentreporter.field.domain.usecase.Collapse
import com.incidentreporter.field.domain.usecase.Mutation
import com.incidentreporter.field.domain.usecase.SyncDecision
import com.incidentreporter.field.domain.usecase.validateIncident
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.util.UUID
import javax.inject.Inject

class IncidentRepositoryImpl @Inject constructor(
    private val db: FieldDatabase,
    private val api: IncidentApi,
    private val photoStore: PhotoStore,
    private val clock: Clock,
    private val syncScheduler: SyncScheduler,
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

    override suspend fun createDraft(): String {
        val id = UUID.randomUUID().toString()
        val referenced = db.withTransaction {
            dao.deleteDrafts() // one report in progress at a time, starting a new one abandons the old
            dao.upsert(newDraftEntity(id, clock.millis()))
            dao.allPhotoFileNames()
        }
        photoStore.deleteUnreferenced(referenced)
        return id
    }

    override suspend fun attachPhoto(incidentId: String, input: PhotoInput): Photo {
        val photo = photoStore.store(input)
        try {
            db.withTransaction {
                val draft = draft(incidentId)
                check(draft.photos.size < MAX_PHOTOS) { "A report holds at most $MAX_PHOTOS photos" }
                dao.upsertPhotos(listOf(photo.toEntity(incidentId)))
            }
        } catch (e: Exception) {
            photoStore.delete(listOf(photo.fileName)) // a refused photo leaves no file behind
            throw e
        }
        return photo
    }

    override suspend fun removePhoto(photoId: String) {
        val photo = db.withTransaction {
            val photo = dao.photo(photoId) ?: return@withTransaction null
            draft(photo.incidentId)
            dao.deletePhoto(photoId)
            photo
        } ?: return
        photoStore.delete(listOf(photo.fileName))
    }

    override suspend fun discardDraft(id: String) {
        val photos = db.withTransaction {
            val row = dao.get(id) ?: return@withTransaction emptyList()
            check(row.incident.isDraft) { "Only drafts can be discarded" }
            dao.delete(listOf(id))
            row.photos
        }
        photoStore.delete(photos.map { it.fileName })
    }

    override suspend fun submitDraft(id: String, input: IncidentInput) {
        val errors = validateIncident(input)
        require(errors.isEmpty()) { "Invalid report: $errors" }
        val submitted = db.withTransaction {
            val row = checkNotNull(dao.get(id)) { "No incident $id" }.incident
            if (!row.isDraft) return@withTransaction false // already submitted, e.g. a double tap
            dao.upsert(
                row.withInput(input, clock.millis()).copy(
                    isDraft = false,
                    status = Status.OPEN,
                    reportedBy = REPORTED_BY,
                    syncState = SyncState.PENDING,
                    pendingOp = PendingOp.CREATE,
                ),
            )
            true
        }
        if (submitted) syncScheduler.requestSync()
    }

    override suspend fun update(id: String, input: IncidentInput) {
        val errors = validateIncident(input)
        require(errors.isEmpty()) { "Invalid report: $errors" }
        db.withTransaction {
            val row = editable(id).incident
            // An edit always queues: an UPDATE, or folded into a CREATE that hasn't uploaded yet.
            val op = (SyncDecision.collapse(row.pendingOp, row.syncState, Mutation.UPDATE) as Collapse.Queue).op
            dao.upsert(row.withInput(input, clock.millis()).copy(pendingOp = op, syncState = SyncState.PENDING, lastSyncError = null))
        }
        syncScheduler.requestSync()
    }

    override suspend fun delete(id: String) {
        val (collapse, photos) = db.withTransaction {
            val row = editable(id)
            val collapse = SyncDecision.collapse(row.incident.pendingOp, row.incident.syncState, Mutation.DELETE)
            when (collapse) {
                Collapse.HardDeleteLocally -> dao.delete(listOf(id))
                is Collapse.Queue -> dao.upsert(
                    row.incident.copy(pendingOp = collapse.op, syncState = SyncState.PENDING, updatedAt = clock.millis(), lastSyncError = null),
                )
            }
            collapse to row.photos
        }
        when (collapse) {
            Collapse.HardDeleteLocally -> photoStore.delete(photos.map { it.fileName })
            is Collapse.Queue -> syncScheduler.requestSync()
        }
    }

    /** A draft, the only kind of row that gains or loses photos. */
    private suspend fun draft(id: String): IncidentWithPhotos {
        val row = checkNotNull(dao.get(id)) { "No incident $id" }
        check(row.incident.isDraft) { "Photos are fixed once a report is submitted" }
        return row
    }

    /** A submitted OPEN report, the only kind a technician may change. */
    private suspend fun editable(id: String): IncidentWithPhotos {
        val row = checkNotNull(dao.get(id)) { "No incident $id" }
        check(!row.incident.isDraft && row.incident.status == Status.OPEN) { "Only open reports can be changed" }
        return row
    }
}

/** The form's fields written onto a row. createdAt stays the moment the report was started. */
private fun IncidentEntity.withInput(input: IncidentInput, now: Long): IncidentEntity = copy(
    title = input.title.trim(),
    description = input.description.trim(),
    category = checkNotNull(input.category),
    severity = input.severity,
    location = input.location?.toColumns(),
    updatedAt = now,
)
