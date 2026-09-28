package io.github.spoonart1.cleanarchwithagent.common

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wall-clock time, behind an interface.
 *
 * `updatedAt` decides which side wins a conflict, so tests need to control it
 * exactly. Calling [System.currentTimeMillis] directly would make those tests
 * depend on how fast the machine runs.
 */
interface Clock {
    fun nowMillis(): Long
}

@Singleton
class SystemClock @Inject constructor() : Clock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}

/**
 * Generates the local IDs and outbox operation IDs.
 *
 * Injected for the same reason as [Clock]: a test asserting that a replayed
 * operation is idempotent needs to know the ID it is replaying.
 */
interface IdGenerator {
    fun newId(): String
}

@Singleton
class UuidGenerator @Inject constructor() : IdGenerator {
    override fun newId(): String = UUID.randomUUID().toString()
}
