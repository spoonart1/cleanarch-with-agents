package io.github.spoonart1.cleanarchwithagent.sync

import io.github.spoonart1.cleanarchwithagent.database.dao.OutboxDao
import io.github.spoonart1.cleanarchwithagent.model.SyncState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

/** Whether a sync is currently running, independent of how many items are queued. */
internal enum class SyncActivity {
    IDLE,
    RUNNING,
}

/**
 * Exposes sync progress to the UI as a single [SyncState] flow.
 *
 * Activity and pending count come from two different places — an in-memory flag
 * and a database query — so they are combined here rather than in the UI. A
 * screen should subscribe to one flow, not reconcile two.
 */
@Singleton
class SyncStatusMonitor @Inject constructor(
    private val outboxDao: OutboxDao,
) {

    private val activity = MutableStateFlow(SyncActivity.IDLE)
    private val lastError = MutableStateFlow<String?>(null)

    val syncState: Flow<SyncState> =
        combine(activity, lastError, outboxDao.pendingCount()) { activity, error, pending ->
            when {
                activity == SyncActivity.RUNNING -> SyncState.Syncing(pendingCount = pending)
                error != null -> SyncState.Error(message = error, pendingCount = pending)
                else -> SyncState.Idle(pendingCount = pending)
            }
        }

    internal fun onSyncStarted() {
        activity.value = SyncActivity.RUNNING
        // Clear the previous failure so a retry does not show a stale error
        // while it is running.
        lastError.value = null
    }

    internal fun onSyncFinished() {
        activity.value = SyncActivity.IDLE
        lastError.value = null
    }

    internal fun onSyncFailed(message: String) {
        activity.value = SyncActivity.IDLE
        lastError.value = message
    }
}
