package com.incidentreporter.field.domain.usecase

import com.incidentreporter.field.core.result.AppError
import com.incidentreporter.field.domain.model.PendingOp
import com.incidentreporter.field.domain.model.SyncState

/** A change the user just made to a record. */
enum class Mutation { CREATE, UPDATE, DELETE }

/** What to do with a record after folding in a new [Mutation]. */
sealed interface Collapse {
    data class Queue(val op: PendingOp) : Collapse
    data object HardDeleteLocally : Collapse
}

/** The few columns a server pull needs to decide what it may touch. */
data class LocalMeta(val id: String, val isDraft: Boolean, val pendingOp: PendingOp)

data class ReconcilePlan(val upsert: Set<String>, val delete: Set<String>)

const val MAX_SERVER_ATTEMPTS = 5

/** The sync rules as pure functions: no database, no network, no clock. */
object SyncDecision {

    /** Folds a new change into the one already waiting for the server */
    fun collapse(current: PendingOp, syncState: SyncState, mutation: Mutation): Collapse = when (mutation) {
        Mutation.CREATE -> when (current) {
            PendingOp.NONE -> Collapse.Queue(PendingOp.CREATE)
            else -> error("Cannot create a record that already has $current pending")
        }
        Mutation.UPDATE -> when (current) {
            PendingOp.NONE, PendingOp.UPDATE -> Collapse.Queue(PendingOp.UPDATE)
            PendingOp.CREATE -> Collapse.Queue(PendingOp.CREATE)
            PendingOp.DELETE -> error("Cannot edit a record that is pending deletion")
        }
        Mutation.DELETE ->
            if (current == PendingOp.CREATE && syncState != SyncState.SYNCING) Collapse.HardDeleteLocally
            else Collapse.Queue(PendingOp.DELETE)
    }

    /** After a successful send: what the record still owes, given how it looks now. */
    fun opAfterSuccess(sentOp: PendingOp, sentUpdatedAt: Long, currentOp: PendingOp, currentUpdatedAt: Long): PendingOp = when {
        currentOp == sentOp && currentUpdatedAt == sentUpdatedAt -> PendingOp.NONE
        currentOp == PendingOp.CREATE -> PendingOp.UPDATE // the create landed, what's left is an edit
        else -> currentOp
    }

    /** Offline retries quietly, a rejection needs the user, a failing server gets [MAX_SERVER_ATTEMPTS] tries. */
    fun stateAfterError(error: AppError, serverErrorCount: Int): SyncState = when (error) {
        is AppError.Network -> SyncState.PENDING
        is AppError.Rejected, is AppError.Serialization -> SyncState.FAILED
        is AppError.Server -> if (serverErrorCount >= MAX_SERVER_ATTEMPTS) SyncState.FAILED else SyncState.PENDING
    }

    /** The server changed since we last saw it, unless that change is our own write landing. */
    fun isStale(remoteUpdatedAt: Long, knownServerUpdatedAt: Long?, localUpdatedAt: Long): Boolean =
        knownServerUpdatedAt != null && remoteUpdatedAt > knownServerUpdatedAt && remoteUpdatedAt != localUpdatedAt

    /** What a pull may touch: never drafts, never records with a change still owed to the server. */
    fun planReconcile(local: List<LocalMeta>, remoteIds: Set<String>): ReconcilePlan {
        val protectedIds = local.filter { it.isDraft || it.pendingOp != PendingOp.NONE }.map { it.id }.toSet()
        return ReconcilePlan(
            upsert = remoteIds - protectedIds,
            delete = local.map { it.id }.filter { it !in remoteIds && it !in protectedIds }.toSet(),
        )
    }
}
