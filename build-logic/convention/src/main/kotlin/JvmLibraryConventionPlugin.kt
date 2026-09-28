import io.github.spoonart1.cleanarchwithagent.buildlogic.allowModulesWithoutTests
import io.github.spoonart1.cleanarchwithagent.buildlogic.configureKotlinCompilerOptions
import io.github.spoonart1.cleanarchwithagent.buildlogic.library
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * Pure-Kotlin JVM module — used only by `core:model`.
 *
 * This is the one place the Kotlin Gradle plugin is applied directly. Android
 * modules get Kotlin from AGP's built-in support instead.
 *
 * Keeping `core:model` free of any Android dependency is what lets the domain
 * models be tested on the JVM with no emulator and no Robolectric.
 */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        // Without this, Android modules that depend on core:model treat it as an
        // opaque external dependency and lint stops analysing its sources.
        pluginManager.apply("com.android.lint")

        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }

        configureKotlinCompilerOptions()
        allowModulesWithoutTests()

        dependencies {
            add("testImplementation", library("junit"))
        }
    }
}
