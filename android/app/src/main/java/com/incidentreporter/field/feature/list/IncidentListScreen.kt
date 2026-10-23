package com.incidentreporter.field.feature.list

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.incidentreporter.field.R
import com.incidentreporter.field.core.designsystem.FieldTheme
import com.incidentreporter.field.core.designsystem.MessageState
import com.incidentreporter.field.core.designsystem.PendingBanner
import com.incidentreporter.field.core.designsystem.PhotoThumb
import com.incidentreporter.field.core.designsystem.SeverityChip
import com.incidentreporter.field.core.designsystem.SkeletonRow
import com.incidentreporter.field.core.designsystem.StatusChip
import com.incidentreporter.field.core.designsystem.SyncBadge
import com.incidentreporter.field.core.designsystem.label
import com.incidentreporter.field.core.designsystem.previewIncident
import com.incidentreporter.field.core.result.AppError
import com.incidentreporter.field.domain.model.Incident
import com.incidentreporter.field.domain.model.Severity
import com.incidentreporter.field.domain.model.Status
import com.incidentreporter.field.domain.model.SyncState

@Composable
fun IncidentListScreen(
    onOpen: (id: String) -> Unit,
    onReport: () -> Unit,
    viewModel: IncidentListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ListEvent.ShowMessage -> snackbarHostState.showSnackbar(resources.getString(event.text))
            }
        }
    }

    IncidentListScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onOpen = onOpen,
        onReport = onReport,
        onToggleFilters = viewModel::onToggleFilters,
        onStatusFilter = viewModel::onStatusFilter,
        onSeverityFilter = viewModel::onSeverityFilter,
        onRefresh = viewModel::onRefresh,
        onClearFilters = viewModel::onClearFilters,
    )
}

/** Stateless: draws [state] and reports taps. Previews call this one, since they have no ViewModel. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IncidentListScreen(
    state: IncidentListUiState,
    snackbarHostState: SnackbarHostState,
    onOpen: (id: String) -> Unit,
    onReport: () -> Unit,
    onToggleFilters: () -> Unit,
    onStatusFilter: (Status) -> Unit,
    onSeverityFilter: (Severity) -> Unit,
    onRefresh: () -> Unit,
    onClearFilters: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.list_title)) },
                actions = {
                    IconButton(onClick = onToggleFilters) {
                        BadgedBox(badge = { if (!state.filtersVisible && state.hasFilter) Badge() }) {
                            Icon(
                                Icons.Outlined.FilterList,
                                contentDescription = stringResource(if (state.filtersVisible) R.string.list_hide_filters else R.string.list_show_filters),
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onReport,
                // The FAB hides its text from TalkBack, so the icon carries the button's name.
                icon = { Icon(Icons.Outlined.PhotoCamera, contentDescription = stringResource(R.string.list_report)) },
                text = { Text(stringResource(R.string.list_report)) },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            if (state.pendingCount > 0) PendingBanner(state.pendingCount)
            if (state.filtersVisible) FilterRow(state, onStatusFilter, onSeverityFilter)
            PullToRefreshBox(
                // The skeleton already says "loading", a spinner on top would say it twice.
                isRefreshing = state.isRefreshing && state.content != ListContent.Skeleton,
                onRefresh = onRefresh,
            ) {
                when (state.content) {
                    ListContent.Skeleton -> Column { repeat(3) { SkeletonRow() } }
                    ListContent.Error -> MessageState(
                        title = stringResource(R.string.list_error_title),
                        body = stringResource(R.string.list_error_body),
                        actionLabel = stringResource(R.string.list_retry),
                        onAction = onRefresh,
                    )
                    ListContent.Empty -> MessageState(
                        title = stringResource(R.string.list_empty_title),
                        body = stringResource(R.string.list_empty_body),
                    )
                    ListContent.EmptyFiltered -> MessageState(
                        title = stringResource(R.string.list_empty_filtered_title),
                        body = null,
                        actionLabel = stringResource(R.string.list_clear_filters),
                        onAction = onClearFilters,
                    )
                    // Bottom padding, so the last row can scroll out from under the button.
                    ListContent.Populated -> LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                        // No item keys: a keyed list stays anchored on its old first row, hiding a new report above it.
                        items(state.incidents) { incident ->
                            IncidentRow(incident, onClick = { onOpen(incident.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterRow(state: IncidentListUiState, onStatus: (Status) -> Unit, onSeverity: (Severity) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Status.entries.forEach { status ->
            FilterChip(
                selected = state.statusFilter == status,
                onClick = { onStatus(status) },
                label = { Text(stringResource(status.label())) },
            )
        }
        Severity.entries.forEach { severity ->
            FilterChip(
                selected = state.severityFilter == severity,
                onClick = { onSeverity(severity) },
                label = { Text(stringResource(severity.label())) },
            )
        }
    }
}

@Composable
private fun IncidentRow(incident: Incident, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            PhotoThumb(incident.photos.firstOrNull()?.file, size = 56.dp)
            Column(verticalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = incident.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    SyncBadge(incident.syncState)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SeverityChip(incident.severity)
                    StatusChip(incident.status)
                }
                Text(metaLine(incident), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** "5 min. ago · 2 photos" */
@Composable
private fun metaLine(incident: Incident): String {
    val time = DateUtils.getRelativeTimeSpanString(
        incident.createdAt.toEpochMilli(),
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
    )
    val count = incident.photos.size
    val photos = if (count == 0) stringResource(R.string.list_no_photos) else pluralStringResource(R.plurals.list_photo_count, count, count)
    return "$time · $photos"
}

private val previewIncidents = listOf(
    previewIncident("a", syncState = SyncState.PENDING),
    previewIncident(
        "b",
        title = "Exposed wiring at gate 2",
        severity = Severity.CRITICAL,
        status = Status.IN_PROGRESS,
        syncState = SyncState.FAILED,
        photoCount = 1,
    ),
    previewIncident("c", title = "Pothole on the access road", severity = Severity.MEDIUM, status = Status.RESOLVED, photoCount = 0),
)

@Composable
private fun ListPreview(state: IncidentListUiState) {
    FieldTheme {
        IncidentListScreen(
            state = state,
            snackbarHostState = remember { SnackbarHostState() },
            onOpen = {},
            onReport = {},
            onToggleFilters = {},
            onStatusFilter = {},
            onSeverityFilter = {},
            onRefresh = {},
            onClearFilters = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun PopulatedPreview() = ListPreview(IncidentListUiState(all = previewIncidents, isFirstLoad = false))

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun SkeletonPreview() = ListPreview(IncidentListUiState())

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun ErrorPreview() = ListPreview(IncidentListUiState(isFirstLoad = false, loadError = AppError.Network()))

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun EmptyPreview() = ListPreview(IncidentListUiState(isFirstLoad = false))

/** Filters hidden while one is active, so the filter icon carries its dot. */
@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun EmptyFilteredPreview() = ListPreview(
    IncidentListUiState(all = previewIncidents, isFirstLoad = false, statusFilter = Status.OPEN, severityFilter = Severity.LOW, filtersVisible = false),
)
