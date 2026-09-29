package io.github.spoonart1.cleanarchwithagent

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/**
 * Swaps in Hilt's test Application for instrumented tests.
 *
 * Without this, `@HiltAndroidTest` classes would run against
 * [CleanArchApplication] and fail: Hilt needs its own generated Application to
 * host the test component.
 *
 * Wired up via `testInstrumentationRunner` in the app's build file.
 */
class CleanArchTestRunner : AndroidJUnitRunner() {

    override fun newApplication(
        classLoader: ClassLoader?,
        className: String?,
        context: Context?,
    ): Application = super.newApplication(
        classLoader,
        HiltTestApplication::class.java.name,
        context,
    )
}
