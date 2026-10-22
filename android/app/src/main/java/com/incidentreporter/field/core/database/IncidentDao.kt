package com.incidentreporter.field.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.incidentreporter.field.domain.model.SyncState
import com.incidentreporter.field.domain.usecase.LocalMeta
import kotlinx.coroutines.flow.Flow

@Dao
interface IncidentDao {

    @Transaction
    @Query("SELECT * FROM incidents WHERE isDraft = 0 AND pendingOp != 'DELETE' ORDER BY createdAt DESC")
    fun observeVisible(): Flow<List<IncidentWithPhotos>>

    @Transaction
    @Query("SELECT * FROM incidents WHERE id = :id")
    fun observe(id: String): Flow<IncidentWithPhotos?>

    @Transaction
    @Query("SELECT * FROM incidents WHERE id = :id")
    suspend fun get(id: String): IncidentWithPhotos?

    @Query("SELECT * FROM photos WHERE id = :id")
    suspend fun photo(id: String): PhotoEntity?

    @Query("SELECT COUNT(*) FROM photos WHERE incidentId = :incidentId")
    suspend fun photoCount(incidentId: String): Int

    @Upsert
    suspend fun upsert(incident: IncidentEntity)

    @Upsert
    suspend fun upsertPhotos(photos: List<PhotoEntity>)

    @Query("DELETE FROM incidents WHERE id IN (:ids)")
    suspend fun delete(ids: List<String>)

    @Query("SELECT id, isDraft, pendingOp FROM incidents")
    suspend fun localMeta(): List<LocalMeta>

    @Query("SELECT fileName FROM photos WHERE incidentId IN (:incidentIds)")
    suspend fun photoFileNames(incidentIds: List<String>): List<String>

    @Query("DELETE FROM photos WHERE incidentId = :incidentId AND id NOT IN (:keepIds)")
    suspend fun deletePhotosExcept(incidentId: String, keepIds: List<String>)

    @Query("DELETE FROM incidents WHERE isDraft = 1")
    suspend fun deleteDrafts()

    @Query("SELECT fileName FROM photos")
    suspend fun allPhotoFileNames(): List<String>

    @Query("DELETE FROM photos WHERE id = :id")
    suspend fun deletePhoto(id: String)

    /** What the phone owes the server, oldest change first. FAILED and STALE rows wait for the user. */
    @Transaction
    @Query("SELECT * FROM incidents WHERE isDraft = 0 AND pendingOp != 'NONE' AND syncState NOT IN ('FAILED', 'STALE') ORDER BY updatedAt")
    suspend fun syncQueue(): List<IncidentWithPhotos>

    // The worker writes only sync columns, so an edit made during an upload is never overwritten.
    @Query("UPDATE incidents SET syncState = 'SYNCING', lastSyncAttemptAt = :at WHERE id = :id")
    suspend fun markSyncing(id: String, at: Long)

    @Query("UPDATE incidents SET syncState = :state, syncAttemptCount = :attempts, lastSyncError = :error WHERE id = :id")
    suspend fun markSyncResult(id: String, state: SyncState, attempts: Int, error: String?)
}
