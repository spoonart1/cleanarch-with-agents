import androidx.room.gradle.RoomExtension
import io.github.spoonart1.cleanarchwithagent.buildlogic.library
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * Room for the `core:database` module.
 *
 * Schemas are exported to a checked-in directory so migrations are reviewable
 * in pull requests and can be used by migration tests.
 */
class AndroidRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.google.devtools.ksp")
        pluginManager.apply("androidx.room")

        extensions.configure<RoomExtension> {
            schemaDirectory("$projectDir/schemas")
        }

        dependencies {
            add("implementation", library("androidx-room-runtime"))
            add("implementation", library("androidx-room-ktx"))
            add("ksp", library("androidx-room-compiler"))
            add("testImplementation", library("androidx-room-testing"))
        }
    }
}
