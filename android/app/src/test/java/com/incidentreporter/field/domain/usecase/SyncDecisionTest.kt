package com.incidentreporter.field.domain.usecase

import com.incidentreporter.field.core.result.AppError
import com.incidentreporter.field.domain.model.PendingOp
import com.incidentreporter.field.domain.model.PendingOp.CREATE
import com.incidentreporter.field.domain.model.PendingOp.DELETE
import com.incidentreporter.field.domain.model.PendingOp.NONE
import com.incidentreporter.field.domain.model.PendingOp.UPDATE
import com.incidentreporter.field.domain.model.SyncState
import com.incidentreporter.field.domain.model.SyncState.FAILED
import com.incidentreporter.field.domain.model.SyncState.PENDING
import com.incidentreporter.field.domain.model.SyncState.STALE
import com.incidentreporter.field.domain.model.SyncState.SYNCED
import com.incidentreporter.field.domain.model.SyncState.SYNCING
import com.incidentreporter.field.domain.usecase.Collapse.HardDeleteLocally
import com.incidentreporter.field.domain.usecase.Collapse.Queue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncDecisionTest {

    /** One row of the collapse table. `expected == null` means the pair is impossible and must throw. */
    private data class Case(val current: PendingOp, val state: SyncState, val mutation: Mutation, val expected: Collapse?)

    @Test
    fun collapse_coversEveryPendingOpAndMutation() {
        listOf(
            Case(NONE, SYNCED, Mutation.CREATE, Queue(CREATE)),
            Case(NONE, SYNCED, Mutation.UPDATE, Queue(UPDATE)),
            Case(NONE, SYNCED, Mutation.DELETE, Queue(DELETE)),
            Case(CREATE, PENDING, Mutation.CREATE, null),
            Case(CREATE, PENDING, Mutation.UPDATE, Queue(CREATE)),     // never reached the server: still a create
            Case(CREATE, PENDING, Mutation.DELETE, HardDeleteLocally), // never reached the server: just forget it
            Case(CREATE, FAILED, Mutation.DELETE, HardDeleteLocally),
            Case(CREATE, SYNCING, Mutation.DELETE, Queue(DELETE)),     // POST in flight: the server may already have it
            Case(UPDATE, PENDING, Mutation.CREATE, null),
            Case(UPDATE, PENDING, Mutation.UPDATE, Queue(UPDATE)),     // a second edit overwrites the first
            Case(UPDATE, STALE, Mutation.UPDATE, Queue(UPDATE)),
            Case(UPDATE, PENDING, Mutation.DELETE, Queue(DELETE)),     // delete supersedes the edit
            Case(DELETE, PENDING, Mutation.CREATE, null),
            Case(DELETE, PENDING, Mutation.UPDATE, null),              // deleted rows are hidden, so can't be edited
            Case(DELETE, PENDING, Mutation.DELETE, Queue(DELETE)),
        ).forEach { case ->
            val result = runCatching { SyncDecision.collapse(case.current, case.state, case.mutation) }
            if (case.expected == null) {
                assertTrue("$case should throw", result.exceptionOrNull() is IllegalStateException)
            } else {
                assertEquals("$case", case.expected, result.getOrThrow())
            }
        }
    }

    @Test
    fun opAfterSuccess_table() {
        assertEquals(NONE, SyncDecision.opAfterSuccess(CREATE, 10, CREATE, 10))   // unchanged: settled
        assertEquals(UPDATE, SyncDecision.opAfterSuccess(CREATE, 10, CREATE, 20)) // edited during the POST
        assertEquals(DELETE, SyncDecision.opAfterSuccess(CREATE, 10, DELETE, 20)) // deleted during the POST
        assertEquals(UPDATE, SyncDecision.opAfterSuccess(UPDATE, 10, UPDATE, 20)) // edited during the PATCH
    }

    @Test
    fun stateAfterError_table() {
        assertEquals(PENDING, SyncDecision.stateAfterError(AppError.Network(), 0))
        assertEquals(FAILED, SyncDecision.stateAfterError(AppError.Rejected(422), 0))
        assertEquals(FAILED, SyncDecision.stateAfterError(AppError.Serialization(), 0))
        assertEquals(PENDING, SyncDecision.stateAfterError(AppError.Server(503), 4))
        assertEquals(FAILED, SyncDecision.stateAfterError(AppError.Server(503), 5))
    }

    @Test
    fun isStale_table() {
        assertTrue(SyncDecision.isStale(remoteUpdatedAt = 200, knownServerUpdatedAt = 100, localUpdatedAt = 150))
        assertFalse(SyncDecision.isStale(remoteUpdatedAt = 100, knownServerUpdatedAt = 100, localUpdatedAt = 150))  // server unchanged
        assertFalse(SyncDecision.isStale(remoteUpdatedAt = 200, knownServerUpdatedAt = 100, localUpdatedAt = 200))  // our own write landed
        assertFalse(SyncDecision.isStale(remoteUpdatedAt = 200, knownServerUpdatedAt = null, localUpdatedAt = 150)) // never saw the server copy
    }

    @Test
    fun reconcile_insertsNewAndDeletesServerRemoved() {
        val plan = SyncDecision.planReconcile(listOf(LocalMeta("gone", isDraft = false, pendingOp = NONE)), remoteIds = setOf("new"))
        assertEquals(ReconcilePlan(upsert = setOf("new"), delete = setOf("gone")), plan)
    }

    @Test
    fun reconcile_leavesPendingAndDraftRowsAlone() {
        val local = listOf(
            LocalMeta("a", isDraft = false, pendingOp = UPDATE), // my unsent edit
            LocalMeta("c", isDraft = false, pendingOp = CREATE), // my unsent report
            LocalMeta("d", isDraft = true, pendingOp = NONE),    // a report still being written
        )
        assertEquals(ReconcilePlan(upsert = emptySet(), delete = emptySet()), SyncDecision.planReconcile(local, remoteIds = setOf("a")))
    }
}
