import com.android.build.api.dsl.CommonExtension
import io.github.spoonart1.cleanarchwithagent.buildlogic.library
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

/**
 * Adds Compose to a module that already has an Android plugin applied.
 *
 * The Compose compiler plugin is still applied separately under AGP 9's
 * built-in Kotlin — it is not folded into the Android plugin.
 */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

        // CommonExtension is un-parameterized in AGP 9's new DSL, which lets a
        // single lookup cover both application and library modules.
        extensions.getByType<CommonExtension>().apply {
            buildFeatures.compose = true
        }

        dependencies {
            // The BOM must be added as a platform so every androidx.compose
            // artifact below can stay version-less.
            val bom = library("androidx-compose-bom").get()
            add("implementation", platform(bom))
            add("androidTestImplementation", platform(bom))

            add("implementation", library("androidx-compose-foundation"))
            add("implementation", library("androidx-compose-material3"))
            add("implementation", library("androidx-compose-ui"))
            add("implementation", library("androidx-compose-ui-graphics"))
            add("implementation", library("androidx-compose-ui-tooling-preview"))
            // Icons are used by essentially every screen; keeping them here
            // stops each feature from re-declaring the same dependency.
            add("implementation", library("androidx-compose-material-icons-extended"))
            add("implementation", library("androidx-lifecycle-runtime-compose"))

            // ui-tooling carries the @Preview renderer and is debug-only so it
            // never ships in a release build.
            add("debugImplementation", library("androidx-compose-ui-tooling"))
            add("debugImplementation", library("androidx-compose-ui-test-manifest"))

            add("androidTestImplementation", library("androidx-compose-ui-test-junit4"))

            // Compose UI tests also run on the JVM via Robolectric, so they
            // execute in `./gradlew test` and in CI with no emulator.
            add("testImplementation", library("androidx-compose-ui-test-junit4"))
            add("testImplementation", library("androidx-compose-ui-test-manifest"))
            add("testImplementation", library("robolectric"))
        }
    }
}
