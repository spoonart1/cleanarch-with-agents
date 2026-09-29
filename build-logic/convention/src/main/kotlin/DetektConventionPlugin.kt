import io.github.spoonart1.cleanarchwithagent.buildlogic.library
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType

/**
 * Static analysis for every module. Applied from the root build file to all
 * projects, so a new module is covered the day it is created rather than the
 * day someone remembers to opt it in.
 *
 * Two things here are worth not undoing:
 *
 *  1. **Type resolution is off.** Detekt 1.23.8 embeds Kotlin 2.0.21's compiler
 *     for analysis, while this project compiles with 2.4.20 via AGP's built-in
 *     Kotlin. Feeding 2.4 sources to the 2.0 type solver produces spurious
 *     unresolved-reference findings. The rules this project relies on are all
 *     syntactic, so they run correctly without it. Revisit when detekt ships a
 *     build against a matching compiler.
 *  2. **`basePath` is set to the repo root** so findings in the SARIF report
 *     carry paths relative to the repository, which is what GitHub's code
 *     scanning UI expects.
 */
class DetektConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("io.gitlab.arturbosch.detekt")

        extensions.configure<DetektExtension> {
            // Layer config/detekt/detekt.yml over detekt's defaults rather than
            // replacing them: a detekt upgrade then brings its new rules along.
            buildUponDefaultConfig = true
            config.setFrom(rootProject.layout.projectDirectory.file("config/detekt/detekt.yml"))
            basePath = rootProject.projectDir.absolutePath
            // No baseline file. The codebase is clean, and a template that
            // ships a baseline teaches new contributors that suppression is
            // the normal response to a finding.
            ignoreFailures = false
            parallel = true
            // Opt-in: `./gradlew detektAll -PdetektAutoCorrect=true` rewrites
            // the formatting violations in place. Off by default so that a CI
            // run reports problems rather than quietly editing the checkout and
            // passing — a build that mutates its own sources hides the diff.
            autoCorrect = providers.gradleProperty("detektAutoCorrect")
                .map { it.toBoolean() }
                .getOrElse(false)
        }

        tasks.withType<Detekt>().configureEach {
            // See the class comment: the 2.0 type solver cannot be trusted
            // against 2.4 sources.
            jvmTarget = "17"

            reports {
                html.required.set(true)
                xml.required.set(true)
                // SARIF is what GitHub code scanning ingests.
                sarif.required.set(true)
                md.required.set(false)
                txt.required.set(false)
            }

            // Generated code is not ours to style-check, and Room/Hilt/Compose
            // between them generate a great deal of it.
            exclude("**/build/**")
            exclude("**/generated/**")
        }

        dependencies {
            // This project's own rules. `detektPlugins` is detekt's runtime
            // classpath — a rule set on the normal compile classpath would
            // never be discovered.
            add("detektPlugins", project(":tools:detekt-rules"))
            // ktlint-backed formatting rules.
            add("detektPlugins", library("detekt-formatting"))
        }
    }
}
