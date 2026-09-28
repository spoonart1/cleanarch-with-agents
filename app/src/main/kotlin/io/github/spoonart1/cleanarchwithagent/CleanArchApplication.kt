package io.github.spoonart1.cleanarchwithagent

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point.
 *
 * In Phase 3 this gains the WorkManager configuration backed by Hilt's worker
 * factory, plus the initial sync scheduled on startup. It stays free of any
 * business logic.
 */
@HiltAndroidApp
class CleanArchApplication : Application()
