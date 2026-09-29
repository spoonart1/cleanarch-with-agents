package io.github.spoonart1.cleanarchwithagent.network.fake

import java.io.IOException
import kotlin.random.Random
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkSimulatorTest {

    @Test
    fun `test simulate when the mode is normal should succeed`() = runTest {
        val simulator = NetworkSimulator()

        val result = runCatching { simulator.simulate() }

        assertTrue(result.isSuccess)
    }

    @Test
    fun `test simulate when the mode is offline should always fail`() = runTest {
        val simulator = NetworkSimulator().apply { setMode(NetworkMode.OFFLINE) }

        repeat(10) {
            val result = runCatching { simulator.simulate() }
            assertTrue("offline call $it should have failed", result.isFailure)
            assertTrue(result.exceptionOrNull() is IOException)
        }
    }

    @Test
    fun `test simulate when the mode is slow should succeed but take longer`() = runTest {
        // runTest virtualises delay: testScheduler.currentTime advances by the
        // simulated amount without the test actually sleeping.
        val normalStart = testScheduler.currentTime
        NetworkSimulator().simulate()
        val normalElapsed = testScheduler.currentTime - normalStart

        val slowStart = testScheduler.currentTime
        NetworkSimulator().apply { setMode(NetworkMode.SLOW) }.simulate()
        val slowElapsed = testScheduler.currentTime - slowStart

        assertTrue(
            "expected slow ($slowElapsed ms) to exceed normal ($normalElapsed ms)",
            slowElapsed > normalElapsed,
        )
    }

    @Test
    fun `test simulate when the flaky rate is one should always fail`() = runTest {
        val simulator = NetworkSimulator().apply {
            setMode(NetworkMode.FLAKY)
            setFailureRate(1f)
            random = Random(seed = 1)
        }

        val result = runCatching { simulator.simulate() }

        assertTrue(result.exceptionOrNull() is IOException)
    }

    @Test
    fun `test simulate when the flaky rate is zero should never fail`() = runTest {
        val simulator = NetworkSimulator().apply {
            setMode(NetworkMode.FLAKY)
            setFailureRate(0f)
            random = Random(seed = 1)
        }

        repeat(20) {
            assertTrue(runCatching { simulator.simulate() }.isSuccess)
        }
    }

    @Test
    fun `test simulate when the flaky rate is one half should fail about half the time`() = runTest {
        val simulator = NetworkSimulator().apply {
            setMode(NetworkMode.FLAKY)
            setFailureRate(0.5f)
            random = Random(seed = 42)
        }

        val failures = (1..100).count { runCatching { simulator.simulate() }.isFailure }

        // A seeded generator makes this deterministic; the window is wide enough
        // that it asserts "roughly half" rather than a brittle exact count.
        assertTrue("expected roughly half of 100 calls to fail, got $failures", failures in 35..65)
    }

    @Test
    fun `test failureRate when set outside zero to one should clamp to a valid probability`() {
        val simulator = NetworkSimulator()

        simulator.setFailureRate(5f)
        assertEquals(1f, simulator.failureRate.value)

        simulator.setFailureRate(-1f)
        assertEquals(0f, simulator.failureRate.value)
    }

    @Test
    fun `test mode when it changes should be observable`() {
        val simulator = NetworkSimulator()
        assertEquals(NetworkMode.NORMAL, simulator.mode.value)

        simulator.setMode(NetworkMode.OFFLINE)

        assertEquals(NetworkMode.OFFLINE, simulator.mode.value)
    }
}
