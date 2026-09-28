package io.github.spoonart1.cleanarchwithagent.buildlogic

import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/**
 * Compiler flags applied to every module.
 *
 * Under AGP 9's built-in Kotlin the `kotlin { }` extension is registered by AGP
 * rather than by the Kotlin Android plugin, so it is looked up by its base type
 * instead of the `KotlinAndroidProjectExtension` that AGP 8 setups reach for.
 *
 * `jvmTarget` is intentionally NOT set here: with built-in Kotlin it is derived
 * from `android.compileOptions.targetCompatibility`, and setting both is the
 * usual source of "Inconsistent JVM-target compatibility" failures.
 */
internal fun Project.configureKotlinCompilerOptions() {
    // Configure the tasks directly. This works identically for the built-in
    // Kotlin compilation (Android modules) and for the kotlin-jvm plugin
    // (core:model), so one helper covers both.
    tasks.withType(KotlinCompile::class.java).configureEach {
        compilerOptions {
            // Opt in to APIs the template legitimately uses rather than
            // sprinkling @OptIn across the codebase.
            freeCompilerArgs.addAll(
                "-opt-in=kotlin.RequiresOptIn",
                "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            )
        }
    }
}

/**
 * Gradle 9 fails a `Test` task that has a test source set but discovers no
 * tests. Modules are created empty in Phase 1 and gain their tests in later
 * phases, so that check is relaxed here.
 *
 * This is scoped deliberately: it only tolerates *zero* tests. A module whose
 * tests all get filtered out, or whose test class fails to load, still fails the
 * build — so this cannot silently hide a broken suite.
 */
internal fun Project.allowModulesWithoutTests() {
    tasks.withType(Test::class.java).configureEach {
        failOnNoDiscoveredTests.set(false)
    }
}
