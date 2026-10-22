package com.incidentreporter.field.feature.form

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.incidentreporter.field.R
import com.incidentreporter.field.data.remote.AppJson
import com.incidentreporter.field.domain.model.Category
import com.incidentreporter.field.domain.model.Incident
import com.incidentreporter.field.domain.model.IncidentInput
import com.incidentreporter.field.domain.model.IncidentLocation
import com.incidentreporter.field.domain.model.LocationSource
import com.incidentreporter.field.domain.model.Photo
import com.incidentreporter.field.domain.model.PhotoInput
import com.incidentreporter.field.domain.model.Severity
import com.incidentreporter.field.domain.repository.IncidentRepository
import com.incidentreporter.field.domain.usecase.FormField
import com.incidentreporter.field.domain.usecase.ValidationError
import com.incidentreporter.field.domain.usecase.validateIncident
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import javax.inject.Inject

/** What the technician has typed. Saved as JSON on every change, so it survives the process being killed. */
@Serializable
data class FormFields(
    val title: String = "",
    val description: String = "",
    val category: Category? = null,
    val severity: Severity = Severity.MEDIUM,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationLabel: String = "",
    val touched: Set<FormField> = emptySet(),
    val loaded: Boolean = false,
)

/** Coordinates make a GPS location, with the label riding along. Otherwise a typed place makes a manual one. */
fun FormFields.toInput(photoCount: Int): IncidentInput = IncidentInput(
    title = title,
    description = description,
    category = category,
    severity = severity,
    location = when {
        latitude != null && longitude != null -> IncidentLocation(latitude, longitude, locationLabel.trim().ifEmpty { null }, LocationSource.GPS)
        locationLabel.isNotBlank() -> IncidentLocation(null, null, locationLabel.trim(), LocationSource.MANUAL)
        else -> null
    },
    photoCount = photoCount,
)

/** CREATE fills in a draft, EDIT changes a submitted report. */
enum class FormMode { CREATE, EDIT }

data class FormUiState(
    val mode: FormMode = FormMode.CREATE,
    val fields: FormFields = FormFields(),
    val photos: List<Photo> = emptyList(),
    val errors: Map<FormField, ValidationError> = emptyMap(),
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val reviewing: Photo? = null,
) {
    /** Anything that leaving would throw away. */
    val hasContent: Boolean = photos.isNotEmpty() || fields.title.isNotBlank() || fields.description.isNotBlank()
}

sealed interface FormEvent {
    data object NavigateToList : FormEvent
    data class ShowMessage(@StringRes val text: Int) : FormEvent
}

@HiltViewModel
class IncidentFormViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: IncidentRepository,
) : ViewModel() {

    // Navigation stores FormRoute's arguments under their property names.
    private val id: String = checkNotNull(savedStateHandle["incidentId"])

    /** What this screen controls. Photos come from Room, and errors are worked out from the fields. */
    private val form = MutableStateFlow(
        FormUiState(fields = savedStateHandle.get<String>(FORM_KEY)?.let { AppJson.decodeFromString<FormFields>(it) } ?: FormFields()),
    )

    val state: StateFlow<FormUiState> = combine(form, repository.observeIncident(id)) { form, incident ->
        val photos = incident?.photos.orEmpty()
        val errors = validateIncident(form.fields.toInput(photos.size)).filterKeys { it in form.fields.touched }
        form.copy(photos = photos, errors = errors)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), form.value)

    private val _events = Channel<FormEvent>(Channel.BUFFERED)
    val events: Flow<FormEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            val incident = repository.observeIncident(id).first()
            if (incident == null) {
                _events.send(FormEvent.NavigateToList) // the draft was purged, or the report deleted, while we were away
                return@launch
            }
            val mode = if (incident.isDraft) FormMode.CREATE else FormMode.EDIT
            form.update { it.copy(mode = mode, isLoading = false) }
            // Fields restored after process death win: they are what the technician typed.
            if (!form.value.fields.loaded) updateFields { if (mode == FormMode.EDIT) incident.toFields() else FormFields(loaded = true) }
        }
    }

    fun onTitleChange(value: String) = updateFields { it.copy(title = value) }

    fun onDescriptionChange(value: String) = updateFields { it.copy(description = value) }

    fun onCategoryChange(category: Category) = updateFields { it.copy(category = category) }

    fun onSeverityChange(severity: Severity) = updateFields { it.copy(severity = severity) }

    fun onLocationLabelChange(value: String) = updateFields { it.copy(locationLabel = value) }

    /** A field's error shows once the technician has left it, not while they're still typing it. */
    fun onFieldBlur(field: FormField) = updateFields { it.copy(touched = it.touched + field) }

    fun onPhotoPicked(uri: String) {
        viewModelScope.launch {
            try {
                repository.attachPhoto(id, PhotoInput.Picked(uri))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.send(FormEvent.ShowMessage(R.string.form_photo_failed)) // an unreadable file, or already at the limit
            }
        }
    }

    /** Opens the review overlay on [photo], or closes it with null. */
    fun onReview(photo: Photo?) = form.update { it.copy(reviewing = photo) }

    fun onRemovePhoto(photoId: String) {
        form.update { it.copy(reviewing = null) }
        viewModelScope.launch { repository.removePhoto(photoId) }
    }

    fun onSubmit() {
        if (form.value.isSubmitting) return // a second tap while the first is still saving
        updateFields { it.copy(touched = FormField.entries.toSet()) }
        val input = form.value.fields.toInput(state.value.photos.size)
        if (validateIncident(input).isNotEmpty()) return
        form.update { it.copy(isSubmitting = true) }
        viewModelScope.launch {
            try {
                if (form.value.mode == FormMode.CREATE) repository.submitDraft(id, input) else repository.update(id, input)
                _events.send(FormEvent.NavigateToList)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalStateException) {
                // The report stopped being open while the form was up, e.g. an operator picked it up.
                form.update { it.copy(isSubmitting = false) }
                _events.send(FormEvent.ShowMessage(R.string.form_not_editable))
            }
        }
    }

    fun onDiscard() {
        viewModelScope.launch {
            repository.discardDraft(id)
            _events.send(FormEvent.NavigateToList)
        }
    }

    private fun updateFields(transform: (FormFields) -> FormFields) {
        form.update { it.copy(fields = transform(it.fields)) }
        savedStateHandle[FORM_KEY] = AppJson.encodeToString(form.value.fields)
    }
}

private const val FORM_KEY = "form"

private fun Incident.toFields() = FormFields(
    title = title,
    description = description,
    category = category,
    severity = severity,
    latitude = location?.latitude,
    longitude = location?.longitude,
    locationLabel = location?.label.orEmpty(),
    loaded = true,
)
