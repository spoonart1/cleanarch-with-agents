import io.github.spoonart1.cleanarchwithagent.buildlogic.library
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project

/**
 * The single plugin every `feature:*:impl` module applies.
 *
 * It wires up the dependencies a feature always needs, so a feature build file
 * stays about three lines long and no feature can forget a piece of the stack.
 *
 * Note what is deliberately NOT here: any other feature's `impl` module. A
 * feature reaches another feature only through its `api` module, and declares
 * that edge explicitly in its own build file.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("cleanarch.android.library")
        pluginManager.apply("cleanarch.android.compose")
        pluginManager.apply("cleanarch.android.hilt")

        dependencies {
            add("implementation", project(":core:model"))
            add("implementation", project(":core:common"))
            add("implementation", project(":core:designsystem"))
            add("implementation", project(":core:data"))

            add("implementation", library("androidx-core-ktx"))
            add("implementation", library("androidx-lifecycle-runtime-ktx"))
            add("implementation", library("androidx-lifecycle-viewmodel-compose"))
            add("implementation", library("androidx-navigation-compose"))
            add("implementation", library("androidx-hilt-navigation-compose"))

            add("testImplementation", project(":core:testing"))
            add("androidTestImplementation", project(":core:testing"))
        }
    }
}
