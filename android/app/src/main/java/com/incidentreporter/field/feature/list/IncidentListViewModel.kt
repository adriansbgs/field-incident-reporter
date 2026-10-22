package com.incidentreporter.field.feature.list

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.incidentreporter.field.R
import com.incidentreporter.field.core.result.AppError
import com.incidentreporter.field.domain.model.Incident
import com.incidentreporter.field.domain.model.Severity
import com.incidentreporter.field.domain.model.Status
import com.incidentreporter.field.domain.model.SyncState
import com.incidentreporter.field.domain.repository.IncidentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
import javax.inject.Inject

enum class ListContent { Skeleton, Error, Empty, EmptyFiltered, Populated }

data class IncidentListUiState(
    val all: List<Incident> = emptyList(),
    val statusFilter: Status? = null,
    val severityFilter: Severity? = null,
    val filtersVisible: Boolean = true,
    val isFirstLoad: Boolean = true,
    val isRefreshing: Boolean = false,
    val loadError: AppError? = null,
) {
    val incidents: List<Incident> = all.filter {
        (statusFilter == null || it.status == statusFilter) && (severityFilter == null || it.severity == severityFilter)
    }

    /** The rows wearing a "Pending" badge, so the banner and the badges always agree. */
    val pendingCount: Int = all.count { it.syncState == SyncState.PENDING || it.syncState == SyncState.SYNCING }

    val hasFilter: Boolean = statusFilter != null || severityFilter != null

    val content: ListContent = when {
        all.isNotEmpty() -> if (incidents.isEmpty()) ListContent.EmptyFiltered else ListContent.Populated
        loadError != null -> ListContent.Error
        isFirstLoad -> ListContent.Skeleton
        else -> ListContent.Empty
    }
}

sealed interface ListEvent {
    data class ShowMessage(@StringRes val text: Int) : ListEvent
    data class OpenForm(val incidentId: String) : ListEvent
}

@HiltViewModel
class IncidentListViewModel @Inject constructor(
    private val repository: IncidentRepository,
) : ViewModel() {

    /** What this screen controls. The incidents themselves always come from Room. */
    private val local = MutableStateFlow(IncidentListUiState())

    val state: StateFlow<IncidentListUiState> = combine(repository.observeIncidents(), local) { all, screen -> screen.copy(all = all) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IncidentListUiState())

    private val _events = Channel<ListEvent>(Channel.BUFFERED)
    val events: Flow<ListEvent> = _events.receiveAsFlow()

    init {
        onRefresh()
    }

    fun onStatusFilter(status: Status) = local.update {
        it.copy(statusFilter = if (it.statusFilter == status) null else status)
    }

    fun onSeverityFilter(severity: Severity) = local.update {
        it.copy(severityFilter = if (it.severityFilter == severity) null else severity)
    }

    fun onClearFilters() = local.update { it.copy(statusFilter = null, severityFilter = null) }

    fun onToggleFilters() = local.update { it.copy(filtersVisible = !it.filtersVisible) }

    /** Until the camera screen exists, a new report opens the form straight away. */
    fun onReportClicked() {
        viewModelScope.launch { _events.send(ListEvent.OpenForm(repository.createDraft())) }
    }

    fun onRefresh() {
        if (local.value.isRefreshing) return
        local.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            val error = repository.refresh().exceptionOrNull() as AppError?
            local.update { it.copy(isFirstLoad = false, isRefreshing = false, loadError = error) }
            if (error != null && repository.observeIncidents().first().isNotEmpty()) {
                _events.send(ListEvent.ShowMessage(R.string.list_refresh_failed))
            }
        }
    }
}
