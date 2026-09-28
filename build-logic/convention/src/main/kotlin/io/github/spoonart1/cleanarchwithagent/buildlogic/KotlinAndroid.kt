package io.github.spoonart1.cleanarchwithagent.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType

/** The shared version catalog, so convention plugins never hardcode a version. */
val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

/** Look up a library by its catalog alias, e.g. `library("hilt-android")`. */
internal fun Project.library(alias: String): Provider<MinimalExternalModuleDependency> =
    libs.findLibrary(alias).orElseThrow {
        IllegalArgumentException("No library '$alias' in gradle/libs.versions.toml")
    }

/** Look up an integer version by its catalog alias, e.g. `version("minSdk")`. */
internal fun Project.version(alias: String): Int =
    libs.findVersion(alias).orElseThrow {
        IllegalArgumentException("No version '$alias' in gradle/libs.versions.toml")
    }.requiredVersion.toInt()

/**
 * Configuration shared by every Android module, application or library.
 *
 * Two AGP 9 details shape this function, and both differ from the AGP 8-era
 * convention plugins most examples show:
 *
 *  1. `CommonExtension` has no type parameters. The familiar
 *     `CommonExtension<*, *, *, *, *, *>` receiver does not compile here.
 *  2. `CommonExtension` exposes plain getters, not `Action`-taking block
 *     functions — `defaultConfig { }` and `lint { }` are declared only on the
 *     concrete `ApplicationExtension` / `LibraryExtension`. So this function
 *     configures the objects returned by the getters directly, which keeps one
 *     helper working for both module types.
 *
 * Kotlin itself is compiled by AGP (`android.builtInKotlin`), so the
 * `org.jetbrains.kotlin.android` plugin must NOT be applied to these modules
 * and the old `kotlinOptions { }` block no longer exists.
 */
internal fun Project.configureKotlinAndroid(extension: CommonExtension) {
    extension.compileSdk = version("compileSdk")
    extension.defaultConfig.minSdk = version("minSdk")

    extension.compileOptions.sourceCompatibility = JavaVersion.VERSION_17
    extension.compileOptions.targetCompatibility = JavaVersion.VERSION_17

    extension.lint.apply {
        // A template should not ship a red build, but neither should it hide
        // problems: errors fail the build, warnings stay visible.
        abortOnError = true
        warningsAsErrors = false
        checkDependencies = true
    }

    // jvmTarget is deliberately not set here. Under built-in Kotlin it is
    // derived from compileOptions.targetCompatibility, and setting both is the
    // usual cause of "Inconsistent JVM-target compatibility" failures.
    configureKotlinCompilerOptions()
    allowModulesWithoutTests()
}
