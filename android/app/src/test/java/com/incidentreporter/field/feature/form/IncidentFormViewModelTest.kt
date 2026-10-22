package com.incidentreporter.field.feature.form

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.incidentreporter.field.data.remote.AppJson
import com.incidentreporter.field.domain.model.Category
import com.incidentreporter.field.domain.usecase.FormField
import com.incidentreporter.field.testing.FakeIncidentRepository
import com.incidentreporter.field.testing.MainDispatcherRule
import com.incidentreporter.field.testing.aPhoto
import com.incidentreporter.field.testing.anIncident
import com.incidentreporter.field.testing.keepCollecting
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IncidentFormViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repo = FakeIncidentRepository()

    private fun newViewModel(saved: Map<String, Any?>) = IncidentFormViewModel(SavedStateHandle(saved), repo)

    /** The three required fields, filled the way a technician would. */
    private fun fill(vm: IncidentFormViewModel) {
        vm.onTitleChange("Leak")
        vm.onCategoryChange(Category.OTHER)
        vm.onLocationLabelChange("Bay 3")
    }

    @Test
    fun submit_withErrors_showsAllErrors_andDoesNotCommit() = runTest {
        repo.incidents.value = listOf(anIncident("d1", title = "", isDraft = true))
        val vm = newViewModel(mapOf("incidentId" to "d1"))
        keepCollecting(vm.state)

        vm.onSubmit()
        runCurrent()

        assertEquals(setOf(FormField.TITLE, FormField.CATEGORY, FormField.LOCATION), vm.state.value.errors.keys)
        assertTrue(repo.calls.none { it.startsWith("submitDraft") })
    }

    @Test
    fun doubleSubmit_commitsOnce() = runTest {
        repo.incidents.value = listOf(anIncident("d1", title = "", isDraft = true))
        repo.submitGate = CompletableDeferred()
        val vm = newViewModel(mapOf("incidentId" to "d1"))
        keepCollecting(vm.state)
        fill(vm)

        vm.events.test {
            vm.onSubmit()
            vm.onSubmit() // a gloved double tap, while the first submit is still saving
            repo.submitGate!!.complete(Unit)

            assertEquals(FormEvent.NavigateToList, awaitItem())
            expectNoEvents()
        }
        assertEquals(1, repo.calls.count { it.startsWith("submitDraft") })
    }

    @Test
    fun review_thenDelete_removesPhoto_andClosesOverlay() = runTest {
        val photo = aPhoto("p1")
        repo.incidents.value = listOf(anIncident("d1", title = "", isDraft = true, photos = listOf(photo)))
        val vm = newViewModel(mapOf("incidentId" to "d1"))
        keepCollecting(vm.state)
        runCurrent()

        vm.onReview(photo)
        runCurrent()
        assertEquals(photo, vm.state.value.reviewing)

        vm.onRemovePhoto("p1")
        runCurrent()

        assertNull(vm.state.value.reviewing)
        assertTrue("removePhoto:p1" in repo.calls)
    }

    @Test
    fun editMode_prefillsFromIncident() = runTest {
        repo.incidents.value = listOf(anIncident("e1", title = "Leak"))
        val vm = newViewModel(mapOf("incidentId" to "e1"))
        keepCollecting(vm.state)
        runCurrent()

        assertEquals(FormMode.EDIT, vm.state.value.mode)
        assertEquals("Leak", vm.state.value.fields.title)
    }

    @Test
    fun processDeath_restoresTypedFields_withoutReloading() = runTest {
        repo.incidents.value = listOf(anIncident("e1", title = "Server title"))
        val saved = AppJson.encodeToString(FormFields(title = "Typed before death", loaded = true))
        val vm = newViewModel(mapOf("incidentId" to "e1", "form" to saved))
        keepCollecting(vm.state)
        runCurrent()

        assertEquals("Typed before death", vm.state.value.fields.title)
    }
}
