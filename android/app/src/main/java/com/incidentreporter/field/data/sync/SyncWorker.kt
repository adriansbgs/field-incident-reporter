package com.incidentreporter.field.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/** WorkManager's handle on [SyncProcessor]: it runs once the network is up, and again after the app is killed. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    /** WorkManager builds this worker itself, so it fetches its dependencies from Hilt instead of having them injected. */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun syncProcessor(): SyncProcessor
    }

    override suspend fun doWork(): Result {
        val processor = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java).syncProcessor()
        return when (processor.syncPending()) {
            SyncOutcome.DONE -> Result.success()
            SyncOutcome.RETRY -> Result.retry()
        }
    }
}
