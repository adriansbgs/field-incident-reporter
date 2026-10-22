package com.incidentreporter.field.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SyncProblem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.incidentreporter.field.R
import com.incidentreporter.field.domain.model.Severity
import com.incidentreporter.field.domain.model.Status
import com.incidentreporter.field.domain.model.SyncState
import java.io.File

@Composable
fun SeverityChip(severity: Severity) {
    val colors = when (severity) {
        Severity.LOW -> FieldPalette.severityLow
        Severity.MEDIUM -> FieldPalette.severityMedium
        Severity.HIGH -> FieldPalette.severityHigh
        Severity.CRITICAL -> FieldPalette.severityCritical
    }
    Chip(stringResource(severity.label()), colors.label, colors.container)
}

@Composable
fun StatusChip(status: Status) {
    Chip(stringResource(status.label()), MaterialTheme.colorScheme.onSurfaceVariant, MaterialTheme.colorScheme.surfaceContainerHigh)
}

@Composable
private fun Chip(text: String, textColor: Color, container: Color) {
    Text(
        text = text,
        color = textColor,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier
            .background(container, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** Outlined, so it never reads as a severity chip. Nothing at all when synced. */
@Composable
fun SyncBadge(state: SyncState) {
    val (icon, text, color) = when (state) {
        SyncState.SYNCED -> return
        SyncState.PENDING, SyncState.SYNCING -> Triple(Icons.Outlined.Schedule, R.string.sync_pending, FieldPalette.syncWaiting)
        SyncState.FAILED -> Triple(Icons.Outlined.ErrorOutline, R.string.sync_failed, FieldPalette.syncNeedsYou)
        SyncState.STALE -> Triple(Icons.Outlined.SyncProblem, R.string.sync_stale, FieldPalette.syncNeedsYou)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .border(1.dp, color, CircleShape)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Text(stringResource(text), color = color, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun PendingBanner(count: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(FieldPalette.bannerContainer)
            .padding(horizontal = 16.dp, vertical = 11.dp),
    ) {
        Icon(Icons.Outlined.CloudUpload, contentDescription = null, tint = FieldPalette.bannerIcon, modifier = Modifier.size(18.dp))
        Text(
            text = pluralStringResource(R.plurals.list_pending_banner, count, count),
            color = FieldPalette.bannerText,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** Empty, error and not-found screens. Scrollable, so pull-to-refresh still works around it. */
@Composable
fun MessageState(
    title: String,
    body: String?,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (body != null) {
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        if (actionLabel != null) {
            OutlinedButton(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) { Text(actionLabel) }
        }
    }
}

@Composable
fun SkeletonRow() {
    val fill = MaterialTheme.colorScheme.surfaceVariant
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Box(Modifier.size(56.dp).background(fill, RoundedCornerShape(8.dp)))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            Box(Modifier.fillMaxWidth(0.7f).height(14.dp).background(fill, RoundedCornerShape(4.dp)))
            Box(Modifier.fillMaxWidth(0.4f).height(12.dp).background(fill, RoundedCornerShape(4.dp)))
        }
    }
}

/** The placeholder icon sits underneath, a missing or unreadable file simply leaves it showing. */
@Composable
fun PhotoThumb(file: File?, size: Dp, modifier: Modifier = Modifier, contentDescription: String? = null) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(if (size < 64.dp) 8.dp else 10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Icon(Icons.Outlined.Image, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
        if (file != null) {
            AsyncImage(
                model = file,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Asks before something that can't be undone. The confirm button is red, because it's the destructive one. */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text(dismissLabel) }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun SeverityChipPreview() {
    FieldTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(16.dp)) {
            Severity.entries.forEach { SeverityChip(it) }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun StatusChipPreview() {
    FieldTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(16.dp)) {
            Status.entries.forEach { StatusChip(it) }
        }
    }
}

/** SYNCED draws nothing, so four badges show for five states. */
@Preview(showBackground = true)
@Composable
private fun SyncBadgePreview() {
    FieldTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(16.dp)) {
            SyncState.entries.forEach { SyncBadge(it) }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PendingBannerPreview() {
    FieldTheme {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PendingBanner(count = 1)
            PendingBanner(count = 3)
        }
    }
}

@Preview(showBackground = true, heightDp = 320)
@Composable
private fun MessageStatePreview() {
    FieldTheme {
        MessageState(
            title = stringResource(R.string.list_error_title),
            body = stringResource(R.string.list_error_body),
            actionLabel = stringResource(R.string.list_retry),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SkeletonRowPreview() {
    FieldTheme { SkeletonRow() }
}

/** In a preview there's no file to load, so this shows the placeholder, same as a seed photo on the phone. */
@Preview(showBackground = true)
@Composable
private fun PhotoThumbPreview() {
    FieldTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(16.dp)) {
            PhotoThumb(file = null, size = 56.dp)
            PhotoThumb(file = null, size = 96.dp)
        }
    }
}

@Preview
@Composable
private fun ConfirmDialogPreview() {
    FieldTheme {
        ConfirmDialog(
            title = "Discard this report?",
            text = "Photos and details you entered will be deleted.",
            confirmLabel = "Discard",
            dismissLabel = "Keep editing",
            onConfirm = {},
            onDismiss = {},
        )
    }
}
