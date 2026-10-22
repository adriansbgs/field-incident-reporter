package com.incidentreporter.field.feature.list

import app.cash.turbine.test
import com.incidentreporter.field.R
import com.incidentreporter.field.core.result.AppError
import com.incidentreporter.field.domain.model.Status
import com.incidentreporter.field.domain.model.SyncState
import com.incidentreporter.field.testing.FakeIncidentRepository
import com.incidentreporter.field.testing.MainDispatcherRule
import com.incidentreporter.field.testing.anIncident
import com.incidentreporter.field.testing.keepCollecting
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IncidentListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repo = FakeIncidentRepository()

    private fun newViewModel() = IncidentListViewModel(repo)

    @Test
    fun firstLoad_showsSkeleton_thenPopulated() = runTest {
        val gate = CompletableDeferred<Result<Unit>>()
        repo.refreshResult = { gate.await() }
        val vm = newViewModel()
        keepCollecting(vm.state)

        assertEquals(ListContent.Skeleton, vm.state.value.content)

        repo.incidents.value = listOf(anIncident())
        gate.complete(Result.success(Unit))
        runCurrent()

        assertEquals(ListContent.Populated, vm.state.value.content)
    }

    @Test
    fun refreshFails_withEmptyCache_showsError_andRetryRecovers() = runTest {
        repo.refreshResult = { Result.failure(AppError.Network()) }
        val vm = newViewModel()
        keepCollecting(vm.state)
        runCurrent()

        assertEquals(ListContent.Error, vm.state.value.content)

        repo.refreshResult = {
            repo.incidents.value = listOf(anIncident())
            Result.success(Unit)
        }
        vm.onRefresh()
        runCurrent()

        assertEquals(ListContent.Populated, vm.state.value.content)
    }

    @Test
    fun refreshFails_withCache_staysPopulated_andShowsMessage() = runTest {
        repo.incidents.value = listOf(anIncident())
        repo.refreshResult = { Result.failure(AppError.Network()) }
        val vm = newViewModel()

        vm.events.test {
            keepCollecting(vm.state)
            runCurrent()

            assertEquals(ListContent.Populated, vm.state.value.content)
            assertEquals(ListEvent.ShowMessage(R.string.list_refresh_failed), awaitItem())
        }
    }

    @Test
    fun statusFilter_narrows_showsEmptyFiltered_andReselectingClears() = runTest {
        repo.incidents.value = listOf(anIncident("o", status = Status.OPEN), anIncident("r", status = Status.RESOLVED))
        val vm = newViewModel()
        keepCollecting(vm.state)
        runCurrent()

        vm.onStatusFilter(Status.RESOLVED)
        runCurrent()
        assertEquals(listOf("r"), vm.state.value.incidents.map { it.id })

        vm.onStatusFilter(Status.IN_PROGRESS)
        runCurrent()
        assertEquals(ListContent.EmptyFiltered, vm.state.value.content)

        vm.onStatusFilter(Status.IN_PROGRESS) // tapping the selected chip again clears it
        runCurrent()
        assertEquals(listOf("o", "r"), vm.state.value.incidents.map { it.id })
    }

    @Test
    fun pendingCount_countsOnlyRowsWaitingToUpload() = runTest {
        repo.incidents.value = SyncState.entries.map { anIncident(id = it.name, syncState = it) }
        val vm = newViewModel()
        keepCollecting(vm.state)
        runCurrent()

        // PENDING and SYNCING share the "Pending" badge. FAILED and STALE need the user, so they aren't "waiting".
        assertEquals(2, vm.state.value.pendingCount)
    }
}
