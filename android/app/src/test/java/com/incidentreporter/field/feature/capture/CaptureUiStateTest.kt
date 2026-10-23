package com.incidentreporter.field.feature.capture

import com.incidentreporter.field.feature.capture.CameraAccess.DENIED
import com.incidentreporter.field.feature.capture.CameraAccess.GRANTED
import com.incidentreporter.field.testing.aPhoto
import org.junit.Assert.assertEquals
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
    fun hint_showsUntilTheFirstPhoto_onlyOverTheViewfinder_andNotFromTheForm() {
        assertTrue(CaptureUiState(access = GRANTED).showHint)
        assertFalse(CaptureUiState(access = GRANTED, photos = listOf(aPhoto("p1"))).showHint)
        assertFalse(CaptureUiState(access = DENIED).showHint)
        assertFalse(CaptureUiState(access = GRANTED, returnToForm = true).showHint) // "details come next" is wrong there
    }

    @Test
    fun proceedAndBack_tables() {
        val one = listOf(aPhoto("p1"))
        assertEquals(ProceedAction.CONFIRM_SKIP, CaptureUiState().proceedAction())
        assertEquals(ProceedAction.OPEN_FORM, CaptureUiState(photos = one).proceedAction())
        assertEquals(ProceedAction.OPEN_FORM, CaptureUiState(returnToForm = true).proceedAction()) // the form already exists, nothing to confirm

        assertEquals(BackAction.CONFIRM_DISCARD, CaptureUiState(photos = one).backAction(hasDraft = true))
        assertEquals(BackAction.LEAVE, CaptureUiState(photos = one, returnToForm = true).backAction(hasDraft = true))
        assertEquals(BackAction.DISCARD_AND_LEAVE, CaptureUiState().backAction(hasDraft = true))
        assertEquals(BackAction.LEAVE, CaptureUiState().backAction(hasDraft = false))
    }
}
