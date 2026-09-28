import com.android.build.api.dsl.ApplicationExtension
import io.github.spoonart1.cleanarchwithagent.buildlogic.configureKotlinAndroid
import io.github.spoonart1.cleanarchwithagent.buildlogic.version
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Applied by the `app` module only.
 *
 * Note that `org.jetbrains.kotlin.android` is deliberately absent: AGP 9
 * compiles Kotlin itself, and applying the Kotlin Android plugin on top of
 * built-in Kotlin is a known failure.
 */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")

        extensions.configure<ApplicationExtension> {
            configureKotlinAndroid(this)
            defaultConfig {
                targetSdk = version("targetSdk")
            }
        }
    }
}
