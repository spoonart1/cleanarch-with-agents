import io.github.spoonart1.cleanarchwithagent.buildlogic.library
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * Hilt for Android modules.
 *
 * KSP only — `kotlin-kapt` is incompatible with AGP 9's built-in Kotlin, so
 * there is no kapt fallback in this project.
 */
class AndroidHiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.google.devtools.ksp")
        pluginManager.apply("com.google.dagger.hilt.android")

        dependencies {
            add("implementation", library("hilt-android"))
            add("ksp", library("hilt-compiler"))
        }
    }
}
