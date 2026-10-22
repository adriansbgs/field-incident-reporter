package com.incidentreporter.field.feature.detail

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.incidentreporter.field.testing.FakeIncidentRepository
import com.incidentreporter.field.testing.MainDispatcherRule
import com.incidentreporter.field.testing.anIncident
import com.incidentreporter.field.testing.keepCollecting
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IncidentDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repo = FakeIncidentRepository()

    @Test
    fun state_followsRepository_andIsNotFoundWhenMissing() = runTest {
        repo.incidents.value = listOf(anIncident("a", title = "Leak"))
        val vm = IncidentDetailViewModel(SavedStateHandle(mapOf("id" to "a")), repo)
        keepCollecting(vm.state)
        runCurrent()

        assertEquals("Leak", (vm.state.value as DetailUiState.Content).incident.title)

        repo.incidents.value = emptyList() // deleted while open, e.g. by a pull
        runCurrent()

        assertEquals(DetailUiState.NotFound, vm.state.value)
    }

    @Test
    fun delete_navigatesBack_thenDeletes() = runTest {
        repo.incidents.value = listOf(anIncident("a"))
        val vm = IncidentDetailViewModel(SavedStateHandle(mapOf("id" to "a")), repo)
        keepCollecting(vm.state)

        vm.events.test {
            vm.onDelete()
            assertEquals(DetailEvent.NavigateBack, awaitItem())
        }
        assertTrue("delete:a" in repo.calls)
    }
}
