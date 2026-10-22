package com.incidentreporter.field.feature.capture

import com.incidentreporter.field.feature.capture.CameraAccess.DENIED
import com.incidentreporter.field.feature.capture.CameraAccess.GRANTED
import com.incidentreporter.field.testing.aPhoto
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureUiStateTest {

    @Test
    fun shutter_disabledAtFive_whileCapturing_andWhenDenied_butGalleryStaysLive() {
        assertFalse(CaptureUiState(access = GRANTED, photos = List(5) { aPhoto("p$it") }).shutterEnabled)
        assertFalse(CaptureUiState(access = GRANTED, isCapturing = true).shutterEnabled)
        assertFalse(CaptureUiState(access = DENIED).shutterEnabled)
        assertTrue(CaptureUiState(access = GRANTED).shutterEnabled)
        assertTrue(CaptureUiState(access = DENIED).galleryEnabled) // no camera never means no photo
    }

    @Test
    fun hint_showsUntilTheFirstPhoto_andOnlyOverTheViewfinder() {
        assertTrue(CaptureUiState(access = GRANTED).showHint)
        assertFalse(CaptureUiState(access = GRANTED, photos = listOf(aPhoto("p1"))).showHint)
        assertFalse(CaptureUiState(access = DENIED).showHint)
    }
}
