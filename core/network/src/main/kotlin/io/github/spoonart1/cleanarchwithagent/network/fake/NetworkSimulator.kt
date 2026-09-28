package io.github.spoonart1.cleanarchwithagent.network.fake

import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How the fake backend should behave. Switchable at runtime from the settings screen. */
enum class NetworkMode {
    /** Responds promptly and succeeds. */
    NORMAL,

    /** Succeeds, but slowly enough to see a loading state. */
    SLOW,

    /** Fails a configurable fraction of calls, to exercise retry and backoff. */
    FLAKY,

    /** Every call fails as if there were no connectivity. */
    OFFLINE,
}

/**
 * Fault injection for the fake backend.
 *
 * This is what lets someone see offline-first behaviour without running a
 * server: switch to OFFLINE, make edits, watch them queue, switch back to
 * NORMAL and watch them sync.
 *
 * Failures are thrown as [IOException] because that is what a real transport
 * failure looks like to the layer above — the sync engine must not need to know
 * whether it is talking to the fake or to Retrofit.
 */
@Singleton
class NetworkSimulator @Inject constructor() {

    private val _mode = MutableStateFlow(NetworkMode.NORMAL)
    val mode: StateFlow<NetworkMode> = _mode.asStateFlow()

    private val _failureRate = MutableStateFlow(DEFAULT_FLAKY_FAILURE_RATE)
    val failureRate: StateFlow<Float> = _failureRate.asStateFlow()

    /** Seedable so tests can make FLAKY deterministic. */
    internal var random: Random = Random.Default

    fun setMode(mode: NetworkMode) {
        _mode.value = mode
    }

    /** [rate] is clamped to 0f..1f; 0.3f means roughly three calls in ten fail. */
    fun setFailureRate(rate: Float) {
        _failureRate.value = rate.coerceIn(0f, 1f)
    }

    /**
     * Applies the current mode's latency and failure behaviour.
     *
     * Call this before serving a request. It either returns normally or throws
     * [IOException].
     */
    suspend fun simulate() {
        when (_mode.value) {
            NetworkMode.NORMAL -> delay(NORMAL_LATENCY_MS)

            NetworkMode.SLOW -> delay(SLOW_LATENCY_MS)

            NetworkMode.FLAKY -> {
                delay(NORMAL_LATENCY_MS)
                if (random.nextFloat() < _failureRate.value) {
                    throw IOException("Simulated flaky network failure")
                }
            }

            NetworkMode.OFFLINE -> {
                // A short delay rather than none: failing instantly is unlike a
                // real connection attempt and hides timing bugs in the caller.
                delay(OFFLINE_LATENCY_MS)
                throw IOException("Simulated offline: no network available")
            }
        }
    }

    private companion object {
        const val NORMAL_LATENCY_MS = 150L
        const val SLOW_LATENCY_MS = 3_000L
        const val OFFLINE_LATENCY_MS = 50L
        const val DEFAULT_FLAKY_FAILURE_RATE = 0.5f
    }
}
