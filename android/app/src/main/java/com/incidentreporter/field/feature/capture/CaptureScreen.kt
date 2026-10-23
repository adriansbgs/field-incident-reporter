package com.incidentreporter.field.feature.capture

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.RotationProvider
import androidx.camera.core.SurfaceRequest
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.NoPhotography
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
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
import com.incidentreporter.field.core.designsystem.ConfirmDialog
import com.incidentreporter.field.core.designsystem.FieldPalette
import com.incidentreporter.field.core.designsystem.FieldTheme
import com.incidentreporter.field.core.designsystem.PhotoReviewOverlay
import com.incidentreporter.field.core.designsystem.PhotoThumb
import com.incidentreporter.field.core.designsystem.previewIncident
import com.incidentreporter.field.domain.model.MAX_PHOTOS
import com.incidentreporter.field.domain.model.Photo

@Composable
fun CaptureScreen(
    onOpenForm: (draftId: String) -> Unit,
    onBack: () -> Unit,
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
                is CaptureEvent.OpenForm -> onOpenForm(event.draftId)
                CaptureEvent.Back -> onBack()
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
    BackHandler(onBack = viewModel::onBack)
    LightSystemBarIcons()
    LockPortrait(viewModel.camera)

    CaptureScreen(
        state = state,
        surfaceRequest = surfaceRequest,
        snackbarHostState = snackbarHostState,
        onClose = viewModel::onBack,
        onToggleFlash = viewModel::onToggleFlash,
        onShutter = viewModel::onShutter,
        onGallery = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onProceed = viewModel::onProceed,
        onReview = viewModel::onReview,
        onAllowCamera = { requestPermission.launch(Manifest.permission.CAMERA) },
        onOpenSettings = {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
        },
    )

    state.reviewing?.let { photo ->
        PhotoReviewOverlay(
            photo = photo,
            onClose = { viewModel.onReview(null) },
            onDelete = viewModel::onDeleteReviewed,
            onRetake = viewModel::onDeleteReviewed,
        )
    }
    if (state.confirmSkip) {
        ConfirmDialog(
            title = stringResource(R.string.capture_skip_title),
            text = stringResource(R.string.capture_skip_body),
            confirmLabel = stringResource(R.string.capture_skip_continue),
            dismissLabel = stringResource(R.string.capture_skip_add),
            onConfirm = viewModel::onConfirmSkip,
            onDismiss = viewModel::onDismissDialogs,
            destructive = false,
        )
    }
    if (state.confirmDiscard) {
        ConfirmDialog(
            title = stringResource(R.string.discard_title),
            text = stringResource(R.string.discard_body),
            confirmLabel = stringResource(R.string.discard_confirm),
            dismissLabel = stringResource(R.string.discard_keep),
            onConfirm = viewModel::onConfirmDiscard,
            onDismiss = viewModel::onDismissDialogs,
        )
    }
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

/** The camera screen stays portrait so its controls never move, and each photo is still saved the way the phone is held. */
@Composable
private fun LockPortrait(camera: CameraCaptureHolder) {
    val activity = LocalActivity.current ?: return
    // Saved, because turning to portrait can recreate the Activity, and portrait is not what to restore on the way out.
    val original = rememberSaveable { activity.requestedOrientation }
    DisposableEffect(activity) {
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        val rotation = RotationProvider(activity)
        val listener = RotationProvider.Listener { camera.setRotation(it) }
        rotation.addListener(ContextCompat.getMainExecutor(activity), listener)
        onDispose {
            rotation.removeListener(listener)
            if (!activity.isChangingConfigurations) activity.requestedOrientation = original
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
    onToggleFlash: () -> Unit,
    onShutter: () -> Unit,
    onGallery: () -> Unit,
    onProceed: () -> Unit,
    onReview: (Photo) -> Unit,
    onAllowCamera: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Scaffold(
        topBar = { TopBar(showFlash = state.hasFlash && state.access == CameraAccess.GRANTED, state.flashOn, onClose, onToggleFlash) },
        bottomBar = { BottomBand(state, onShutter, onGallery, onProceed, onReview) },
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
private fun TopBar(showFlash: Boolean, flashOn: Boolean, onClose: () -> Unit, onToggleFlash: () -> Unit) {
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
        if (showFlash) {
            IconButton(onClick = onToggleFlash, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp)) {
                Icon(
                    if (flashOn) Icons.Outlined.FlashOn else Icons.Outlined.FlashOff,
                    contentDescription = stringResource(if (flashOn) R.string.capture_flash_off else R.string.capture_flash_on),
                    tint = Color.White,
                )
            }
        }
    }
}

/** The photo strip, once there is a photo, above the gallery button, the shutter and Skip or Done. */
@Composable
private fun BottomBand(
    state: CaptureUiState,
    onShutter: () -> Unit,
    onGallery: () -> Unit,
    onProceed: () -> Unit,
    onReview: (Photo) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(FieldPalette.cameraBottomScrim)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
            .padding(horizontal = 20.dp),
    ) {
        if (state.photos.isNotEmpty()) PhotoStrip(state.photos, onReview)
        // A Box, not a Row: the shutter stays centred whatever width Skip or Done takes.
        Box(
            Modifier
                .fillMaxWidth()
                .height(if (state.photos.isEmpty()) 168.dp else 124.dp),
        ) {
            GalleryButton(state.galleryEnabled, onGallery, Modifier.align(Alignment.CenterStart))
            ShutterButton(state.shutterEnabled, onShutter, Modifier.align(Alignment.Center))
            ProceedButton(state.photos.size, enabled = !state.isCapturing, onProceed, Modifier.align(Alignment.CenterEnd))
        }
    }
}

@Composable
private fun PhotoStrip(photos: List<Photo>, onReview: (Photo) -> Unit) {
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
                PhotoThumb(
                    photo.file,
                    size = 54.dp,
                    contentDescription = stringResource(R.string.form_photo, index + 1, photos.size),
                    modifier = Modifier.clickable { onReview(photo) },
                )
            }
        }
        Text(
            stringResource(R.string.capture_count, photos.size, MAX_PHOTOS),
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
private fun GalleryButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
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
        modifier = modifier.size(56.dp),
    ) {
        Icon(Icons.Outlined.PhotoLibrary, contentDescription = stringResource(R.string.photo_from_gallery))
    }
}

@Composable
private fun ShutterButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val label = stringResource(R.string.photo_take)
    OutlinedIconButton(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        border = BorderStroke(3.dp, FieldPalette.cameraShutterRing),
        modifier = modifier
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

/** "Skip" before the first photo, "Done (n)" after it. Both lead to the form, and the label says what happens to the photo. */
@Composable
private fun ProceedButton(count: Int, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val sized = modifier
        .heightIn(min = 48.dp)
        .widthIn(min = 86.dp)
    if (count == 0) {
        val description = stringResource(R.string.capture_skip_description)
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            border = BorderStroke(1.5.dp, FieldPalette.cameraControlStroke),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = FieldPalette.cameraControlFill, contentColor = Color.White),
            modifier = sized.semantics { contentDescription = description },
        ) {
            Text(stringResource(R.string.capture_skip))
        }
    } else {
        val description = pluralStringResource(R.plurals.capture_done_description, count, count)
        Button(
            onClick = onClick,
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = FieldPalette.cameraOnWhite),
            modifier = sized.semantics { contentDescription = description },
        ) {
            Text(stringResource(R.string.capture_done, count))
        }
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
            onToggleFlash = {},
            onShutter = {},
            onGallery = {},
            onProceed = {},
            onReview = {},
            onAllowCamera = {},
            onOpenSettings = {},
        )
    }
}

/** No photo yet: the hint, the taller control band, and Skip. */
@Preview(widthDp = 360, heightDp = 760)
@Composable
private fun FirstShotPreview() = CapturePreview(CaptureUiState(access = CameraAccess.GRANTED, hasFlash = true))

/** Five photos: the strip is full, the shutter is off, and Done carries the count. */
@Preview(widthDp = 360, heightDp = 760)
@Composable
private fun FullStripPreview() = CapturePreview(
    CaptureUiState(access = CameraAccess.GRANTED, photos = previewIncident(photoCount = MAX_PHOTOS).photos, hasFlash = true, flashOn = true),
)

@Preview(widthDp = 360, heightDp = 760)
@Composable
private fun RationalePreview() = CapturePreview(CaptureUiState(access = CameraAccess.RATIONALE))

@Preview(widthDp = 360, heightDp = 760)
@Composable
private fun DeniedPreview() = CapturePreview(CaptureUiState(access = CameraAccess.DENIED))
