package com.incidentreporter.field.feature.capture

import org.junit.Assert.assertEquals
import org.junit.Test

class CameraPermissionTest {

    @Test
    fun permission_tables() {
        assertEquals(EntryAction.SHOW_PREVIEW, CameraPermission.onEntry(granted = true, shouldShowRationale = false))
        assertEquals(EntryAction.SHOW_RATIONALE, CameraPermission.onEntry(granted = false, shouldShowRationale = true))
        // A first ask, or a permanent denial, which the system answers at once without showing anything.
        assertEquals(EntryAction.REQUEST, CameraPermission.onEntry(granted = false, shouldShowRationale = false))

        assertEquals(CameraAccess.GRANTED, CameraPermission.afterRequest(granted = true, shouldShowRationale = false))
        assertEquals(CameraAccess.RATIONALE, CameraPermission.afterRequest(granted = false, shouldShowRationale = true))
        assertEquals(CameraAccess.DENIED, CameraPermission.afterRequest(granted = false, shouldShowRationale = false))
    }
}
