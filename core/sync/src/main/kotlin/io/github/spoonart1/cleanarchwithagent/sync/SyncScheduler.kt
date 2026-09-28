package io.github.spoonart1.cleanarchwithagent.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decides when a sync runs.
 *
 * Three triggers, each for a different reason:
 *  - shortly after a local edit, so a change reaches the server promptly;
 *  - when the app comes to the foreground, to pick up other devices' changes;
 *  - periodically, as a backstop for when neither of those happened.
 *
 * An interface because callers should depend on "ask for a sync", not on
 * WorkManager. It also keeps repository tests free of WorkManager setup.
 */
interface SyncScheduler {

    /** Requests a sync shortly after a local edit, debounced. */
    fun requestSync()

    /** Syncs immediately, e.g. a "sync now" button. */
    fun syncNow()

    /** Starts the periodic backstop. Safe to call on every app start. */
    fun schedulePeriodicSync()
}

/**
 * The real implementation.
 *
 * Every trigger funnels into the same unique work, so syncs never run in
 * parallel and trample each other.
 */
@Singleton
class WorkManagerSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : SyncScheduler {

    private val workManager: WorkManager
        get() = WorkManager.getInstance(context)

    /**
     * Requests a sync after a short delay.
     *
     * The delay debounces a burst of edits: typing five items queues five
     * requests, and [ExistingWorkPolicy.REPLACE] collapses them into one pass
     * rather than five.
     */
    override fun requestSync() {
        workManager.enqueueUniqueWork(
            SyncWorker.WORK_NAME_ONE_OFF,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(networkConstraints)
                .setInitialDelay(EDIT_DEBOUNCE_SECONDS, TimeUnit.SECONDS)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    BACKOFF_SECONDS,
                    TimeUnit.SECONDS,
                )
                .build(),
        )
    }

    /**
     * Syncs now, without the debounce delay.
     *
     * Used by the foreground trigger and by the settings screen's "sync now".
     * [ExistingWorkPolicy.KEEP] so tapping twice does not cancel a sync that is
     * already running.
     */
    override fun syncNow() {
        workManager.enqueueUniqueWork(
            SyncWorker.WORK_NAME_ONE_OFF,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(networkConstraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    BACKOFF_SECONDS,
                    TimeUnit.SECONDS,
                )
                .build(),
        )
    }

    override fun schedulePeriodicSync() {
        workManager.enqueueUniquePeriodicWork(
            SyncWorker.WORK_NAME_PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SyncWorker>(
                PERIODIC_INTERVAL_MINUTES,
                TimeUnit.MINUTES,
            )
                .setConstraints(networkConstraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    BACKOFF_SECONDS,
                    TimeUnit.SECONDS,
                )
                .build(),
        )
    }

    /** No point waking up to sync with no connection. */
    private val networkConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    private companion object {
        const val EDIT_DEBOUNCE_SECONDS = 5L
        const val BACKOFF_SECONDS = 30L

        /** WorkManager's minimum periodic interval is 15 minutes. */
        const val PERIODIC_INTERVAL_MINUTES = 15L
    }
}
