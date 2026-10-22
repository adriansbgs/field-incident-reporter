package com.incidentreporter.field.feature.capture

/** Where the camera permission stands, as far as this screen knows. */
enum class CameraAccess { CHECKING, GRANTED, RATIONALE, DENIED }

/** What to do when the camera screen opens. */
enum class EntryAction { SHOW_PREVIEW, SHOW_RATIONALE, REQUEST }

/**
 * Asking in three stages: ask once, explain after a denial, then point to Settings.
 * Android's only clue to the stage is shouldShowRequestPermissionRationale, so the rules live here, where a test can reach them.
 */
object CameraPermission {

    fun onEntry(granted: Boolean, shouldShowRationale: Boolean): EntryAction = when {
        granted -> EntryAction.SHOW_PREVIEW
        shouldShowRationale -> EntryAction.SHOW_RATIONALE
        else -> EntryAction.REQUEST // a first ask, or a permanent denial that the system answers at once
    }

    fun afterRequest(granted: Boolean, shouldShowRationale: Boolean): CameraAccess = when {
        granted -> CameraAccess.GRANTED
        shouldShowRationale -> CameraAccess.RATIONALE
        else -> CameraAccess.DENIED
    }
}
