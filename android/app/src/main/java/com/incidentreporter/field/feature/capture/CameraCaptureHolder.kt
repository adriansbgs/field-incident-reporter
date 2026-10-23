package com.incidentreporter.field.feature.capture

import android.content.Context
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.core.takePicture
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** CameraX's two use cases, the viewfinder and still capture. The ViewModel owns this, so leaving and coming back doesn't rebuild them. */
class CameraCaptureHolder {

    private val _surfaceRequest = MutableStateFlow<SurfaceRequest?>(null)

    /** What the viewfinder draws into. CameraX hands out a new one each time it binds. */
    val surfaceRequest: StateFlow<SurfaceRequest?> = _surfaceRequest.asStateFlow()

    private val _hasFlash = MutableStateFlow(false)

    /** Known once the camera is bound. Without a flash unit, the flash button stays hidden. */
    val hasFlash: StateFlow<Boolean> = _hasFlash.asStateFlow()

    private val preview = Preview.Builder().build().apply {
        setSurfaceProvider { request -> _surfaceRequest.value = request }
    }

    // Full HD shows the damage clearly, and saves quickly on a mid-range phone.
    private val imageCapture = ImageCapture.Builder()
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
        .setResolutionSelector(
            ResolutionSelector.Builder()
                .setResolutionStrategy(ResolutionStrategy(Size(1920, 1080), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
                .build(),
        )
        .build()

    /** CameraX opens the camera while [owner] is started, and closes it when [owner] stops. */
    suspend fun bind(context: Context, owner: LifecycleOwner) {
        val provider = ProcessCameraProvider.awaitInstance(context)
        provider.unbindAll()
        val camera = provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
        _hasFlash.value = camera.cameraInfo.hasFlashUnit()
    }

    /** Which way up the next photo is saved: the way the phone is held, not the way the locked screen is drawn. */
    fun setRotation(rotation: Int) {
        imageCapture.targetRotation = rotation
    }

    fun setFlash(on: Boolean) {
        imageCapture.flashMode = if (on) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
    }

    /** Writes the shot straight into [file]. Throws ImageCaptureException when the camera fails. */
    suspend fun takePicture(file: File) {
        imageCapture.takePicture(ImageCapture.OutputFileOptions.Builder(file).build())
    }
}
