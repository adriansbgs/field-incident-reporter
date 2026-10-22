package com.incidentreporter.field.feature.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.incidentreporter.field.domain.model.Incident
import com.incidentreporter.field.domain.repository.IncidentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed interface DetailUiState {
    data object Loading : DetailUiState
    data object NotFound : DetailUiState
    data class Content(val incident: Incident) : DetailUiState
}

@HiltViewModel
class IncidentDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: IncidentRepository,
) : ViewModel() {

    // Navigation stores DetailRoute's arguments under their property names.
    private val id: String = checkNotNull(savedStateHandle["id"])

    val state: StateFlow<DetailUiState> = repository.observeIncident(id)
        .map { if (it == null) DetailUiState.NotFound else DetailUiState.Content(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUiState.Loading)
}
