package com.incidentreporter.field.data.sync

import androidx.room.withTransaction
import com.incidentreporter.field.core.database.FieldDatabase
import com.incidentreporter.field.core.database.IncidentEntity
import com.incidentreporter.field.core.database.IncidentWithPhotos
import com.incidentreporter.field.core.result.AppError
import com.incidentreporter.field.core.result.toAppError
import com.incidentreporter.field.data.photo.PhotoStore
import com.incidentreporter.field.data.remote.IncidentApi
import com.incidentreporter.field.data.remote.IncidentDto
import com.incidentreporter.field.data.remote.toDto
import com.incidentreporter.field.data.remote.toPatch
import com.incidentreporter.field.domain.model.PendingOp
import com.incidentreporter.field.domain.model.SyncState
import com.incidentreporter.field.domain.usecase.SyncDecision
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

enum class SyncOutcome { DONE, RETRY }

/** Sends every change the phone owes the server, one record at a time, and records how each one went. */
class SyncProcessor @Inject constructor(
    private val db: FieldDatabase,
    private val api: IncidentApi,
    private val photoStore: PhotoStore,
    private val clock: Clock,
) {

    private val dao = db.incidentDao()

    /** RETRY when a record is still waiting, on the network or on a struggling server. */
    suspend fun syncPending(): SyncOutcome {
        val states = dao.syncQueue().map { sync(it) }
        return if (SyncState.PENDING in states) SyncOutcome.RETRY else SyncOutcome.DONE
    }

    /** Sends one record's pending change. Returns the state the record is left in. */
    private suspend fun sync(row: IncidentWithPhotos): SyncState {
        val sent = row.incident // a snapshot, the user may keep editing the live row while this uploads
        dao.markSyncing(sent.id, clock.millis())
        return try {
            when (sent.pendingOp) {
                PendingOp.CREATE -> {
                    // An earlier try may have reached the server and lost only its response. A second POST would be a 500.
                    if (sent.lastSyncAttemptAt != null && getOrNull(sent.id) != null) api.update(sent.id, sent.toPatch())
                    else api.create(row.toDto())
                    landed(sent)
                }
                PendingOp.UPDATE -> {
                    val remoteUpdatedAt = Instant.parse(api.get(sent.id).updatedAt).toEpochMilli()
                    if (SyncDecision.isStale(remoteUpdatedAt, sent.serverUpdatedAt, sent.updatedAt)) {
                        dao.markStale(sent.id, remoteUpdatedAt)
                        SyncState.STALE
                    } else {
                        api.update(sent.id, sent.toPatch())
                        landed(sent)
                    }
                }
                PendingOp.DELETE -> {
                    val response = api.delete(sent.id)
                    if (!response.isSuccessful && response.code() != 404) throw HttpException(response) // 404: already gone
                    dao.delete(listOf(sent.id))
                    photoStore.delete(row.photos.map { it.fileName })
                    SyncState.SYNCED
                }
                PendingOp.NONE -> error("The sync queue never holds a record with nothing to send")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed(sent, e.toAppError())
        }
    }

    /** The server has [sent]. Whatever the user changed since is still owed, so it stays queued. */
    private suspend fun landed(sent: IncidentEntity): SyncState = db.withTransaction {
        val now = dao.get(sent.id)?.incident ?: return@withTransaction SyncState.SYNCED
        val op = SyncDecision.opAfterSuccess(sent.pendingOp, sent.updatedAt, now.pendingOp, now.updatedAt)
        val state = if (op == PendingOp.NONE) SyncState.SYNCED else SyncState.PENDING
        dao.upsert(now.copy(pendingOp = op, syncState = state, serverUpdatedAt = sent.updatedAt, syncAttemptCount = 0, lastSyncError = null))
        state
    }

    private suspend fun failed(sent: IncidentEntity, error: AppError): SyncState {
        val attempts = sent.syncAttemptCount + if (error is AppError.Server) 1 else 0
        val state = SyncDecision.stateAfterError(error, attempts)
        dao.markSyncResult(sent.id, state, attempts, error.message.takeIf { state == SyncState.FAILED })
        return state
    }

    /** The server's copy, or null when it has none. */
    private suspend fun getOrNull(id: String): IncidentDto? = try {
        api.get(id)
    } catch (e: HttpException) {
        if (e.code() == 404) null else throw e
    }
}
