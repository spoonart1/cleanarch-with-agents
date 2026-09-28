import com.android.build.api.dsl.LibraryExtension
import io.github.spoonart1.cleanarchwithagent.buildlogic.configureKotlinAndroid
import io.github.spoonart1.cleanarchwithagent.buildlogic.library
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** The baseline for every Android library module in the project. */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")

        extensions.configure<LibraryExtension> {
            configureKotlinAndroid(this)
            // Library modules do not declare targetSdk; that is an
            // application-level concern.
            testOptions.unitTests.isIncludeAndroidResources = true
        }

        dependencies {
            add("implementation", library("kotlinx-coroutines-core"))
            add("testImplementation", library("junit"))
            add("testImplementation", library("kotlinx-coroutines-test"))
            add("testImplementation", library("turbine"))
        }
    }
}
