package com.incidentreporter.field.testing

import com.incidentreporter.field.data.sync.SyncScheduler

/** Counts sync requests instead of scheduling work. */
class FakeSyncScheduler : SyncScheduler {

    var calls = 0
        private set

    override fun requestSync() {
        calls++
    }
}
