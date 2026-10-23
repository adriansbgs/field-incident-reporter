package com.incidentreporter.field.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.incidentreporter.field.R
import com.incidentreporter.field.domain.model.Photo

/** One photo, full screen on black. Retake and Delete appear only where photos can still change, null hides them. */
@Composable
fun PhotoReviewOverlay(photo: Photo, onClose: () -> Unit, onDelete: (() -> Unit)?, onRetake: (() -> Unit)?) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            AsyncImage(
                model = photo.file,
                contentDescription = stringResource(R.string.review_photo),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            IconButton(
                onClick = onClose,
                colors = IconButtonDefaults.iconButtonColors(containerColor = FieldPalette.cameraPill, contentColor = Color.White),
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
            ) {
                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.review_close))
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(24.dp),
            ) {
                if (onRetake != null) ReviewAction(Icons.Outlined.Replay, R.string.review_retake, onRetake)
                if (onDelete != null) ReviewAction(Icons.Outlined.Delete, R.string.review_delete, onDelete)
            }
        }
    }
}

@Composable
private fun ReviewAction(icon: ImageVector, label: Int, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = FieldPalette.cameraPill, contentColor = Color.White),
        modifier = Modifier.heightIn(min = 48.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(label))
        }
    }
}

@Preview
@Composable
private fun PhotoReviewOverlayPreview() {
    FieldTheme { PhotoReviewOverlay(previewIncident().photos.first(), onClose = {}, onDelete = {}, onRetake = {}) }
}
