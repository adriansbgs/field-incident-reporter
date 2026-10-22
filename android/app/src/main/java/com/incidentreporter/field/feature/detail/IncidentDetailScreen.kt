package com.incidentreporter.field.feature.detail

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.incidentreporter.field.R
import com.incidentreporter.field.core.designsystem.FieldTheme
import com.incidentreporter.field.core.designsystem.MessageState
import com.incidentreporter.field.core.designsystem.PhotoThumb
import com.incidentreporter.field.core.designsystem.SeverityChip
import com.incidentreporter.field.core.designsystem.StatusChip
import com.incidentreporter.field.core.designsystem.SyncBadge
import com.incidentreporter.field.core.designsystem.label
import com.incidentreporter.field.core.designsystem.previewIncident
import com.incidentreporter.field.domain.model.Incident
import com.incidentreporter.field.domain.model.IncidentLocation
import com.incidentreporter.field.domain.model.Status
import com.incidentreporter.field.domain.model.SyncState
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun IncidentDetailScreen(
    onBack: () -> Unit,
    viewModel: IncidentDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    IncidentDetailScreen(state, onBack)
}

/** Stateless: draws [state] and reports taps. Previews call this one, since they have no ViewModel. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IncidentDetailScreen(state: DetailUiState, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        when (state) {
            DetailUiState.Loading -> Unit
            DetailUiState.NotFound -> MessageState(stringResource(R.string.detail_not_found), body = null, modifier = Modifier.padding(padding))
            is DetailUiState.Content -> DetailContent(state.incident, Modifier.padding(padding))
        }
    }
}

@Composable
private fun DetailContent(incident: Incident, modifier: Modifier) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
    ) {
        if (incident.photos.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
                itemsIndexed(incident.photos, key = { _, photo -> photo.id }) { index, photo ->
                    PhotoThumb(
                        file = photo.file,
                        size = 96.dp,
                        contentDescription = stringResource(R.string.detail_photo, index + 1, incident.photos.size),
                    )
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(incident.title, style = MaterialTheme.typography.headlineSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                SeverityChip(incident.severity)
                StatusChip(incident.status)
                SyncBadge(incident.syncState)
            }
            if (incident.description.isNotBlank()) {
                Text(incident.description, style = MaterialTheme.typography.bodyLarge)
            }
            Field(R.string.detail_category, stringResource(incident.category.label()))
            incident.location?.let { Field(R.string.detail_location, locationText(it)) }
            Field(R.string.detail_reported_by, incident.reportedBy)
            Field(R.string.detail_created, timeFormat.format(incident.createdAt))
            Field(R.string.detail_updated, timeFormat.format(incident.updatedAt))
            Actions(editable = incident.isEditable)
        }
    }
}

@Composable
private fun Field(@StringRes label: Int, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

/** The place label, the coordinates, or both on two lines. */
@Composable
private fun locationText(location: IncidentLocation): String {
    val coordinates = if (location.latitude != null && location.longitude != null) {
        stringResource(R.string.detail_coordinates, location.latitude, location.longitude)
    } else {
        null
    }
    return listOfNotNull(location.label, coordinates).joinToString("\n")
}

/** Only OPEN incidents can change. The buttons stay visible and say why they're disabled. */
@Composable
private fun Actions(editable: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = {}, enabled = editable, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Text(stringResource(R.string.detail_edit))
            }
            OutlinedButton(onClick = {}, enabled = editable, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) {
                Text(stringResource(R.string.detail_delete))
            }
        }
        if (!editable) {
            Text(stringResource(R.string.detail_read_only), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private val timeFormat: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withZone(ZoneId.systemDefault())

@Preview(showBackground = true, widthDp = 360, heightDp = 760)
@Composable
private fun OpenIncidentPreview() {
    FieldTheme { IncidentDetailScreen(DetailUiState.Content(previewIncident()), onBack = {}) }
}

/** Edit and Delete disabled, with the reason underneath. */
@Preview(showBackground = true, widthDp = 360, heightDp = 760)
@Composable
private fun ReadOnlyIncidentPreview() {
    FieldTheme {
        IncidentDetailScreen(
            DetailUiState.Content(previewIncident(status = Status.RESOLVED, syncState = SyncState.STALE, photoCount = 0)),
            onBack = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun NotFoundPreview() {
    FieldTheme { IncidentDetailScreen(DetailUiState.NotFound, onBack = {}) }
}
