package com.incidentreporter.field.feature.capture

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.incidentreporter.field.R
import com.incidentreporter.field.data.photo.PhotoStore
import com.incidentreporter.field.domain.model.MAX_PHOTOS
import com.incidentreporter.field.domain.model.Photo
import com.incidentreporter.field.domain.model.PhotoInput
import com.incidentreporter.field.domain.repository.IncidentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CaptureUiState(
    val access: CameraAccess = CameraAccess.CHECKING,
    val photos: List<Photo> = emptyList(),
    val isCapturing: Boolean = false,
) {
    private val hasRoom = photos.size < MAX_PHOTOS

    val shutterEnabled: Boolean = access == CameraAccess.GRANTED && !isCapturing && hasRoom

    /** The gallery needs no camera permission, so a denied camera never blocks a photo. */
    val galleryEnabled: Boolean = !isCapturing && hasRoom

    /** "Shoot first" sits over the viewfinder until the first photo. */
    val showHint: Boolean = photos.isEmpty() && access == CameraAccess.GRANTED
}

sealed interface CaptureEvent {
    data object RequestPermission : CaptureEvent
    data class ShowMessage(@StringRes val text: Int) : CaptureEvent
}

@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: IncidentRepository,
    private val photoStore: PhotoStore,
) : ViewModel() {

    val camera = CameraCaptureHolder()

    // Navigation stores CaptureRoute's arguments under their property names. Null until the first photo creates the draft.
    private val draftId: StateFlow<String?> = savedStateHandle.getStateFlow("draftId", null)

    /** What this screen controls. The photos come from Room. */
    private val screen = MutableStateFlow(CaptureUiState())

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<CaptureUiState> = combine(
        screen,
        draftId.flatMapLatest { id -> if (id == null) flowOf(null) else repository.observeIncident(id) },
    ) { screen, draft -> screen.copy(photos = draft?.photos.orEmpty()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CaptureUiState())

    private val _events = Channel<CaptureEvent>(Channel.BUFFERED)
    val events: Flow<CaptureEvent> = _events.receiveAsFlow()

    fun onPermissionEntry(granted: Boolean, shouldShowRationale: Boolean) {
        if (screen.value.access != CameraAccess.CHECKING) return // back after a rotation, the answer is already known
        when (CameraPermission.onEntry(granted, shouldShowRationale)) {
            EntryAction.SHOW_PREVIEW -> screen.update { it.copy(access = CameraAccess.GRANTED) }
            EntryAction.SHOW_RATIONALE -> screen.update { it.copy(access = CameraAccess.RATIONALE) }
            EntryAction.REQUEST -> viewModelScope.launch { _events.send(CaptureEvent.RequestPermission) }
        }
    }

    fun onPermissionResult(granted: Boolean, shouldShowRationale: Boolean) =
        screen.update { it.copy(access = CameraPermission.afterRequest(granted, shouldShowRationale)) }

    fun onShutter() {
        if (!state.value.shutterEnabled) return
        addPhoto(R.string.capture_failed) { draftId ->
            val target = photoStore.newCaptureTarget()
            camera.takePicture(target.file) // a failed shot may leave a partial file, and the next new report sweeps it away
            repository.attachPhoto(draftId, target)
        }
    }

    fun onPicked(uri: String) {
        if (!state.value.galleryEnabled) return
        addPhoto(R.string.form_photo_failed) { draftId -> repository.attachPhoto(draftId, PhotoInput.Picked(uri)) }
    }

    /** One photo at a time: the shutter and the gallery wait until this one is saved, or has failed. */
    private fun addPhoto(@StringRes failure: Int, add: suspend (draftId: String) -> Unit) {
        if (screen.value.isCapturing) return // a second tap before the first photo is saved
        screen.update { it.copy(isCapturing = true) }
        viewModelScope.launch {
            try {
                add(ensureDraft())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(CaptureEvent.ShowMessage(failure))
            } finally {
                screen.update { it.copy(isCapturing = false) }
            }
        }
    }

    /** The first photo creates the draft, not opening the camera, so backing out of an empty camera leaves nothing behind. */
    private suspend fun ensureDraft(): String =
        draftId.value ?: repository.createDraft().also { savedStateHandle["draftId"] = it }
}
