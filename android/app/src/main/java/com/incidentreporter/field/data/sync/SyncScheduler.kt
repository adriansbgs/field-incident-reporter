package com.incidentreporter.field.data.sync

/** Asks for pending changes to be uploaded soon. The repository calls it after every queued change. */
fun interface SyncScheduler {
    fun requestSync()
}
