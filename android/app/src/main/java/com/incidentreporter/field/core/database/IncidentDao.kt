package com.incidentreporter.field.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
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
}
