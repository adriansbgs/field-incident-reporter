package com.incidentreporter.field.feature.detail

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.incidentreporter.field.R
import com.incidentreporter.field.domain.model.Incident
import com.incidentreporter.field.domain.repository.IncidentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface DetailUiState {
    data object Loading : DetailUiState
    data object NotFound : DetailUiState
    data class Content(val incident: Incident) : DetailUiState
}

sealed interface DetailEvent {
    data object NavigateBack : DetailEvent
    data class ShowMessage(@StringRes val text: Int) : DetailEvent
}

@HiltViewModel
class IncidentDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: IncidentRepository,
) : ViewModel() {

    // Navigation stores DetailRoute's arguments under their property names.
    private val id: String = checkNotNull(savedStateHandle["id"])

    val state: StateFlow<DetailUiState> = repository.observeIncident(id)
        .map { if (it == null) DetailUiState.NotFound else DetailUiState.Content(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUiState.Loading)

    private val _events = Channel<DetailEvent>(Channel.BUFFERED)
    val events: Flow<DetailEvent> = _events.receiveAsFlow()

    fun onDelete() {
        viewModelScope.launch {
            _events.send(DetailEvent.NavigateBack) // leave first, so the screen never flashes "no longer exists"
            withContext(NonCancellable) { // leaving clears this ViewModel, and the delete must still finish
                try {
                    repository.delete(id)
                } catch (e: IllegalStateException) {
                    // A pull showed an operator picked it up while the dialog was open. It's theirs now, so it stays.
                }
            }
        }
    }

    fun onRetry() {
        viewModelScope.launch { repository.retrySync(id) }
    }

    fun onUseServerVersion() {
        viewModelScope.launch {
            if (repository.useServerVersion(id).isFailure) _events.send(DetailEvent.ShowMessage(R.string.detail_server_version_failed))
        }
    }
}
