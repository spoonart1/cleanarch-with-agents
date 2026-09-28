package io.github.spoonart1.cleanarchwithagent.common

import javax.inject.Qualifier

/**
 * Qualifiers for injected dispatchers.
 *
 * Injecting a dispatcher instead of calling [kotlinx.coroutines.Dispatchers] IO
 * directly is what lets tests swap in a test dispatcher and run deterministically.
 * Production code should never reference `Dispatchers.IO` by hand.
 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class DefaultDispatcher

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class MainDispatcher

/** Marks the process-lifetime coroutine scope, used for work that must outlive a screen. */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ApplicationScope
