package io.github.spoonart1.cleanarchwithagent.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Runs one sync pass in the background.
 *
 * Kept deliberately thin: all the logic lives in [SyncEngine], which is a plain
 * class and therefore testable without WorkManager. The worker only translates
 * a [SyncResult] into WorkManager's vocabulary.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val syncEngine: SyncEngine,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = when (syncEngine.sync()) {
        is SyncResult.Success -> Result.success()
        // retry() re-runs this work with the exponential backoff configured on
        // the request, rather than failing permanently.
        is SyncResult.Retry -> Result.retry()
    }

    companion object {
        const val WORK_NAME_ONE_OFF = "sync_once"
        const val WORK_NAME_PERIODIC = "sync_periodic"
    }
}
