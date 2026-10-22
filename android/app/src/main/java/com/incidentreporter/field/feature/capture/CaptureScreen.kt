package com.incidentreporter.field.feature.capture

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.SurfaceRequest
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.NoPhotography
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.incidentreporter.field.R
import com.incidentreporter.field.core.designsystem.FieldPalette
import com.incidentreporter.field.core.designsystem.FieldTheme
import com.incidentreporter.field.core.designsystem.PhotoThumb
import com.incidentreporter.field.core.designsystem.previewIncident
import com.incidentreporter.field.domain.model.MAX_PHOTOS
import com.incidentreporter.field.domain.model.Photo

@Composable
fun CaptureScreen(
    onClose: () -> Unit,
    viewModel: CaptureViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val surfaceRequest by viewModel.camera.surfaceRequest.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current

    fun hasPermission() = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    fun shouldShowRationale() = activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)

    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onPermissionResult(granted, shouldShowRationale())
    }
    // The system photo picker needs no permission, so it works with the camera denied.
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.onPicked(uri.toString())
    }

    LaunchedEffect(viewModel) {
        viewModel.onPermissionEntry(hasPermission(), shouldShowRationale())
        viewModel.events.collect { event ->
            when (event) {
                CaptureEvent.RequestPermission -> requestPermission.launch(Manifest.permission.CAMERA)
                is CaptureEvent.ShowMessage -> snackbarHostState.showSnackbar(resources.getString(event.text))
            }
        }
    }
    // Back from Settings with camera access switched on: show the viewfinder without asking again.
    LifecycleResumeEffect(Unit) {
        if (hasPermission()) viewModel.onPermissionResult(granted = true, shouldShowRationale = false)
        onPauseOrDispose {}
    }
    // Bound to this screen's lifecycle: the camera runs while the screen is visible, and stops when it isn't.
    LaunchedEffect(lifecycleOwner, state.access) {
        if (state.access == CameraAccess.GRANTED) viewModel.camera.bind(context, lifecycleOwner)
    }
    LightSystemBarIcons()

    CaptureScreen(
        state = state,
        surfaceRequest = surfaceRequest,
        snackbarHostState = snackbarHostState,
        onClose = onClose,
        onShutter = viewModel::onShutter,
        onGallery = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onAllowCamera = { requestPermission.launch(Manifest.permission.CAMERA) },
        onOpenSettings = {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
        },
    )
}

/** Light status and navigation bar icons over the dark camera, dark again on the way out. */
@Composable
private fun LightSystemBarIcons() {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    DisposableEffect(window, view) {
        val bars = WindowCompat.getInsetsController(window, view)
        bars.isAppearanceLightStatusBars = false
        bars.isAppearanceLightNavigationBars = false
        onDispose {
            bars.isAppearanceLightStatusBars = true
            bars.isAppearanceLightNavigationBars = true
        }
    }
}

/** Stateless: draws [state] and reports taps. Previews call this one, since they have no ViewModel. */
@Composable
private fun CaptureScreen(
    state: CaptureUiState,
    surfaceRequest: SurfaceRequest?,
    snackbarHostState: SnackbarHostState,
    onClose: () -> Unit,
    onShutter: () -> Unit,
    onGallery: () -> Unit,
    onAllowCamera: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Scaffold(
        topBar = { TopBar(onClose) },
        bottomBar = { BottomBand(state, onShutter, onGallery) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Black,
        contentWindowInsets = WindowInsets(0), // the viewfinder runs edge to edge, under both bars
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            when (state.access) {
                CameraAccess.GRANTED -> surfaceRequest?.let { CameraXViewfinder(surfaceRequest = it, modifier = Modifier.fillMaxSize()) }
                CameraAccess.RATIONALE -> PermissionMessage(
                    title = R.string.capture_rationale_title,
                    body = R.string.capture_rationale_body,
                    action = R.string.capture_allow,
                    onAction = onAllowCamera,
                    modifier = Modifier.padding(padding),
                )
                CameraAccess.DENIED -> PermissionMessage(
                    title = R.string.capture_denied_title,
                    body = R.string.capture_denied_body,
                    action = R.string.capture_open_settings,
                    onAction = onOpenSettings,
                    modifier = Modifier.padding(padding),
                )
                CameraAccess.CHECKING -> Unit
            }
            if (state.showHint) {
                Text(
                    stringResource(R.string.capture_hint),
                    color = FieldPalette.cameraHintText,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(padding)
                        .padding(top = 16.dp)
                        .background(FieldPalette.cameraPill, CircleShape)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun TopBar(onClose: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(FieldPalette.cameraTopScrim)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .height(64.dp),
    ) {
        IconButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterStart).padding(start = 4.dp)) {
            Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.capture_close), tint = Color.White)
        }
        Text(
            stringResource(R.string.form_title_new),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

/** The photo strip, once there is a photo, above the gallery button and the shutter. */
@Composable
private fun BottomBand(state: CaptureUiState, onShutter: () -> Unit, onGallery: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(FieldPalette.cameraBottomScrim)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
            .padding(horizontal = 20.dp),
    ) {
        if (state.photos.isNotEmpty()) PhotoStrip(state.photos)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(if (state.photos.isEmpty()) 168.dp else 124.dp),
        ) {
            GalleryButton(state.galleryEnabled, onGallery)
            Box(contentAlignment = Alignment.Center, modifier = Modifier.weight(1f)) { ShutterButton(state.shutterEnabled, onShutter) }
            Spacer(Modifier.size(56.dp)) // balances the gallery button, so the shutter sits in the middle
        }
    }
}

@Composable
private fun PhotoStrip(photos: List<Photo>) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 10.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
        ) {
            photos.forEachIndexed { index, photo ->
                PhotoThumb(photo.file, size = 54.dp, contentDescription = stringResource(R.string.detail_photo, index + 1, photos.size))
            }
        }
        Text(
            stringResource(R.string.form_photo_count, photos.size, MAX_PHOTOS),
            color = Color.White.copy(alpha = 0.68f),
            style = MaterialTheme.typography.labelMedium,
            // TalkBack reads the new count after each shot, without moving focus.
            modifier = Modifier
                .padding(start = 10.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

@Composable
private fun GalleryButton(enabled: Boolean, onClick: () -> Unit) {
    OutlinedIconButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.5.dp, FieldPalette.cameraControlStroke),
        colors = IconButtonDefaults.outlinedIconButtonColors(
            containerColor = FieldPalette.cameraControlFill,
            contentColor = Color.White,
            disabledContentColor = Color.White.copy(alpha = 0.38f),
        ),
        modifier = Modifier.size(56.dp),
    ) {
        Icon(Icons.Outlined.PhotoLibrary, contentDescription = stringResource(R.string.photo_from_gallery))
    }
}

@Composable
private fun ShutterButton(enabled: Boolean, onClick: () -> Unit) {
    val label = stringResource(R.string.photo_take)
    OutlinedIconButton(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        border = BorderStroke(3.dp, FieldPalette.cameraShutterRing),
        modifier = Modifier
            .size(80.dp)
            .semantics { contentDescription = label },
    ) {
        Box(
            Modifier
                .size(62.dp)
                .background(Color.White.copy(alpha = if (enabled) 1f else 0.38f), CircleShape),
        )
    }
}

/** Takes the viewfinder's place when the camera can't be used, and keeps the gallery as a way forward. */
@Composable
private fun PermissionMessage(
    @StringRes title: Int,
    @StringRes body: Int,
    @StringRes action: Int,
    onAction: () -> Unit,
    modifier: Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Icon(Icons.Outlined.NoPhotography, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
        Text(stringResource(title), color = Color.White, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            stringResource(body),
            color = Color.White.copy(alpha = 0.8f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = onAction,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = FieldPalette.cameraOnWhite),
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text(stringResource(action))
        }
    }
}

@Composable
private fun CapturePreview(state: CaptureUiState) {
    FieldTheme {
        CaptureScreen(
            state = state,
            surfaceRequest = null,
            snackbarHostState = remember { SnackbarHostState() },
            onClose = {},
            onShutter = {},
            onGallery = {},
            onAllowCamera = {},
            onOpenSettings = {},
        )
    }
}

/** No photo yet: the hint, and the taller control band. */
@Preview(widthDp = 360, heightDp = 760)
@Composable
private fun FirstShotPreview() = CapturePreview(CaptureUiState(access = CameraAccess.GRANTED))

/** Five photos: the strip is full and the shutter is off. */
@Preview(widthDp = 360, heightDp = 760)
@Composable
private fun FullStripPreview() = CapturePreview(
    CaptureUiState(access = CameraAccess.GRANTED, photos = previewIncident(photoCount = MAX_PHOTOS).photos),
)

@Preview(widthDp = 360, heightDp = 760)
@Composable
private fun RationalePreview() = CapturePreview(CaptureUiState(access = CameraAccess.RATIONALE))

@Preview(widthDp = 360, heightDp = 760)
@Composable
private fun DeniedPreview() = CapturePreview(CaptureUiState(access = CameraAccess.DENIED))
