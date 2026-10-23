package com.incidentreporter.field.feature.form

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.incidentreporter.field.data.remote.AppJson
import com.incidentreporter.field.domain.model.Category
import com.incidentreporter.field.domain.model.LocationSource
import com.incidentreporter.field.domain.repository.Coordinates
import com.incidentreporter.field.domain.repository.CurrentLocation
import com.incidentreporter.field.domain.usecase.FormField
import com.incidentreporter.field.domain.usecase.validateIncident
import com.incidentreporter.field.testing.FakeIncidentRepository
import com.incidentreporter.field.testing.MainDispatcherRule
import com.incidentreporter.field.testing.aPhoto
import com.incidentreporter.field.testing.anIncident
import com.incidentreporter.field.testing.keepCollecting
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
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

    private fun newViewModel(saved: Map<String, Any?>, location: CurrentLocation = CurrentLocation { null }) =
        IncidentFormViewModel(SavedStateHandle(saved), repo, location)

    /** A new report's form, open on the draft d1. */
    private fun TestScope.draftForm(location: CurrentLocation): IncidentFormViewModel {
        repo.incidents.value = listOf(anIncident("d1", title = "", isDraft = true))
        return newViewModel(mapOf("incidentId" to "d1"), location).also { keepCollecting(it.state) }
    }

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

    @Test
    fun permissionGranted_withFix_fillsGps() = runTest {
        val vm = draftForm(location = CurrentLocation { Coordinates(-0.9471, 100.4172) })

        vm.onLocationPermissionResult(true)
        runCurrent()

        assertEquals(LocationStatus.ACQUIRED, vm.state.value.locationStatus)
        assertEquals(LocationSource.GPS, vm.state.value.fields.toInput(0).location!!.source)
    }

    @Test
    fun deniedOrNoFix_fallsBackToTheLabel() = runTest {
        var calls = 0
        val denied = draftForm(
            location = CurrentLocation {
                calls++
                null
            },
        )
        denied.onLocationPermissionResult(false)
        runCurrent()
        assertEquals(LocationStatus.UNAVAILABLE, denied.state.value.locationStatus)
        assertEquals(0, calls) // a denial never asks for a fix

        val noFix = draftForm(location = CurrentLocation { null })
        noFix.onLocationPermissionResult(true)
        runCurrent()
        assertEquals(LocationStatus.UNAVAILABLE, noFix.state.value.locationStatus)

        noFix.onLocationLabelChange("Bay 3")
        runCurrent()
        assertNull(validateIncident(noFix.state.value.fields.toInput(0))[FormField.LOCATION]) // a typed place is enough to submit
    }

    @Test
    fun doubleTap_acquiresOnce() = runTest {
        val gate = CompletableDeferred<Coordinates?>()
        var calls = 0
        val vm = draftForm(
            location = CurrentLocation {
                calls++
                gate.await()
            },
        )

        vm.onLocationPermissionResult(true)
        vm.onLocationPermissionResult(true) // a gloved double tap on "Use my location"
        gate.complete(Coordinates(-0.9471, 100.4172))
        runCurrent()

        assertEquals(1, calls)
        assertEquals(LocationStatus.ACQUIRED, vm.state.value.locationStatus)
    }

    @Test
    fun editMode_neverReplacesTheReportedLocation() = runTest {
        var calls = 0
        repo.incidents.value = listOf(anIncident("e1"))
        val vm = newViewModel(
            mapOf("incidentId" to "e1"),
            location = CurrentLocation {
                calls++
                Coordinates(1.0, 2.0)
            },
        )
        keepCollecting(vm.state)
        runCurrent()

        vm.onLocationPermissionResult(true)
        runCurrent()

        assertEquals(0, calls) // where the technician stood when reporting, not where they are when editing
        assertEquals("Bay 3", vm.state.value.fields.locationLabel)
    }
}
