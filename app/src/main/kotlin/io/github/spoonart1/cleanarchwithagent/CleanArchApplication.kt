package io.github.spoonart1.cleanarchwithagent

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import io.github.spoonart1.cleanarchwithagent.sync.SyncScheduler
import javax.inject.Inject

/**
 * Application entry point.
 *
 * Implements [Configuration.Provider] so WorkManager builds workers through
 * Hilt's factory — without it, `SyncWorker`'s injected `SyncEngine` could not be
 * supplied and every sync would crash on construction.
 */
@HiltAndroidApp
class CleanArchApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var syncScheduler: SyncScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        // The periodic backstop, in case neither an edit nor a foreground event
        // triggers a sync for a while.
        syncScheduler.schedulePeriodicSync()

        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    // Coming to the foreground: pick up anything other devices
                    // changed while the app was away.
                    syncScheduler.syncNow()
                }
            },
        )
    }
}
