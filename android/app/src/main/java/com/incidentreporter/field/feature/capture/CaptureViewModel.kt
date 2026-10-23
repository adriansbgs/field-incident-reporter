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

/** Skip with no photo asks first. A photo, or a form waiting behind the camera, goes straight on. */
enum class ProceedAction { CONFIRM_SKIP, OPEN_FORM }

/** Leaving with photos asks first, and an empty draft is dropped quietly. Opened from the form, the camera just closes. */
enum class BackAction { CONFIRM_DISCARD, DISCARD_AND_LEAVE, LEAVE }

data class CaptureUiState(
    val access: CameraAccess = CameraAccess.CHECKING,
    val photos: List<Photo> = emptyList(),
    val isCapturing: Boolean = false,
    val returnToForm: Boolean = false,
    val reviewing: Photo? = null,
    val confirmSkip: Boolean = false,
    val confirmDiscard: Boolean = false,
    val hasFlash: Boolean = false,
    val flashOn: Boolean = false,
) {
    private val hasRoom = photos.size < MAX_PHOTOS

    val shutterEnabled: Boolean = access == CameraAccess.GRANTED && !isCapturing && hasRoom

    /** The gallery needs no camera permission, so a denied camera never blocks a photo. */
    val galleryEnabled: Boolean = !isCapturing && hasRoom

    /** "Shoot first" sits over the viewfinder until the first photo, and only while the details are still to come. */
    val showHint: Boolean = photos.isEmpty() && access == CameraAccess.GRANTED && !returnToForm

    fun proceedAction(): ProceedAction =
        if (photos.isEmpty() && !returnToForm) ProceedAction.CONFIRM_SKIP else ProceedAction.OPEN_FORM

    fun backAction(hasDraft: Boolean): BackAction = when {
        returnToForm -> BackAction.LEAVE // the form holds the draft, and shows any new photos
        photos.isNotEmpty() -> BackAction.CONFIRM_DISCARD
        hasDraft -> BackAction.DISCARD_AND_LEAVE // a failed first shot, or every photo deleted
        else -> BackAction.LEAVE
    }
}

sealed interface CaptureEvent {
    data object RequestPermission : CaptureEvent
    data class OpenForm(val draftId: String) : CaptureEvent
    data object Back : CaptureEvent
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

    // Decided once and saved: the first photo sets draftId, so working it out again after process death would get it wrong.
    private val returnToForm: Boolean = savedStateHandle.get<Boolean>("returnToForm")
        ?: (draftId.value != null).also { savedStateHandle["returnToForm"] = it }

    /** What this screen controls. The photos come from Room, the flash unit from CameraX. */
    private val screen = MutableStateFlow(CaptureUiState(returnToForm = returnToForm))

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<CaptureUiState> = combine(
        screen,
        draftId.flatMapLatest { id -> if (id == null) flowOf(null) else repository.observeIncident(id) },
        camera.hasFlash,
    ) { screen, draft, hasFlash -> screen.copy(photos = draft?.photos.orEmpty(), hasFlash = hasFlash) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), screen.value)

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

    fun onProceed() {
        if (screen.value.isCapturing) return // the photo still being saved would be lost along with this screen
        when (state.value.proceedAction()) {
            ProceedAction.CONFIRM_SKIP -> screen.update { it.copy(confirmSkip = true) }
            ProceedAction.OPEN_FORM -> proceed()
        }
    }

    fun onConfirmSkip() {
        screen.update { it.copy(confirmSkip = false) }
        proceed()
    }

    fun onBack() {
        when (state.value.backAction(hasDraft = draftId.value != null)) {
            BackAction.CONFIRM_DISCARD -> screen.update { it.copy(confirmDiscard = true) }
            BackAction.DISCARD_AND_LEAVE -> discardAndLeave()
            BackAction.LEAVE -> viewModelScope.launch { _events.send(CaptureEvent.Back) }
        }
    }

    fun onConfirmDiscard() {
        screen.update { it.copy(confirmDiscard = false) }
        discardAndLeave()
    }

    fun onDismissDialogs() = screen.update { it.copy(confirmSkip = false, confirmDiscard = false) }

    /** Opens the review overlay on [photo], or closes it with null. */
    fun onReview(photo: Photo?) = screen.update { it.copy(reviewing = photo) }

    /** Delete and Retake are the same here: the photo goes, and the viewfinder is right behind the overlay. */
    fun onDeleteReviewed() {
        val photo = screen.value.reviewing ?: return
        screen.update { it.copy(reviewing = null) }
        viewModelScope.launch { repository.removePhoto(photo.id) }
    }

    fun onToggleFlash() {
        val on = !screen.value.flashOn
        camera.setFlash(on)
        screen.update { it.copy(flashOn = on) }
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

    /** On to the form: a new one for this draft, or back to the form that opened the camera. A skipped photo still makes a draft. */
    private fun proceed() {
        viewModelScope.launch { _events.send(if (returnToForm) CaptureEvent.Back else CaptureEvent.OpenForm(ensureDraft())) }
    }

    private fun discardAndLeave() {
        viewModelScope.launch {
            draftId.value?.let { repository.discardDraft(it) }
            _events.send(CaptureEvent.Back)
        }
    }

    /** The first photo creates the draft, not opening the camera, so backing out of an empty camera leaves nothing behind. */
    private suspend fun ensureDraft(): String =
        draftId.value ?: repository.createDraft().also { savedStateHandle["draftId"] = it }
}
