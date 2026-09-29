import io.github.spoonart1.cleanarchwithagent.buildlogic.businessLogicIncludes
import io.github.spoonart1.cleanarchwithagent.buildlogic.coverageExclusions
import io.github.spoonart1.cleanarchwithagent.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport
import java.math.BigDecimal

/**
 * Per-module JaCoCo coverage, plus the 90% gate on business logic.
 *
 * Applied to every module that runs unit tests. Two tasks are added per module:
 *
 *  * `jacocoTestReport` — HTML and XML coverage for everything not in
 *    [coverageExclusions]. This is the number a human reads.
 *  * `jacocoCoverageVerification` — the gate. It measures only classes matching
 *    [businessLogicIncludes] and fails under 90% line coverage.
 *
 * Reporting on a wide set while gating on a narrow one is deliberate: the
 * report stays informative about the whole module, while the gate holds a hard
 * line exactly where a regression would be a behaviour bug. A gate that
 * includes Compose UI measures rendering, not correctness, and teams learn to
 * ignore it.
 */
class JacocoConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("jacoco")

        extensions.configure<JacocoPluginExtension> {
            toolVersion = libs.findVersion("jacoco").orElseThrow {
                IllegalArgumentException("No version 'jacoco' in gradle/libs.versions.toml")
            }.requiredVersion
        }

        tasks.withType<Test>().configureEach {
            extensions.configure(JacocoTaskExtension::class.java) {
                // Required for Robolectric: its instrumenting class loader and
                // JaCoCo's on-the-fly instrumentation both rewrite bytecode,
                // and without this they disagree about class IDs, producing
                // either zero coverage or a crash.
                isIncludeNoLocationClasses = true
                excludes = listOf("jdk.internal.*")
            }
        }

        // Android modules build their test task lazily per variant, so the
        // report tasks are wired after evaluation once the variants exist.
        afterEvaluate {
            registerCoverageTasks()
        }
    }

    private fun Project.registerCoverageTasks() {
        val testTaskName = resolveTestTaskName() ?: return

        // On a JVM module the `jacoco` base plugin has already created both
        // tasks; on an Android module it has not. `withTypeOrRegister` covers
        // both without a duplicate-task error.
        withTypeOrRegister<JacocoReport>("jacocoTestReport") {
            group = "verification"
            description = "Generates a coverage report for $testTaskName."
            dependsOn(testTaskName)

            // includes = null: report on the whole module, so the HTML view
            // shows where coverage actually stands rather than only the gated
            // subset.
            classDirectories.setFrom(classesMatching(coverageExclusions, includes = null))
            sourceDirectories.setFrom(files("src/main/kotlin", "src/main/java"))
            executionData.setFrom(fileTree(layout.buildDirectory).include(EXEC_PATTERNS))

            reports {
                html.required.set(true)
                xml.required.set(true)
                csv.required.set(false)
            }
        }

        withTypeOrRegister<JacocoCoverageVerification>("jacocoCoverageVerification") {
            group = "verification"
            description = "Fails if business-logic line coverage is below " +
                    "${MINIMUM_COVERAGE.multiply(BigDecimal(PERCENT))}%."
            dependsOn(testTaskName)

            // The gate looks ONLY at business-logic classes. See the class doc.
            classDirectories.setFrom(classesMatching(coverageExclusions, businessLogicIncludes))
            sourceDirectories.setFrom(files("src/main/kotlin", "src/main/java"))
            executionData.setFrom(fileTree(layout.buildDirectory).include(EXEC_PATTERNS))

            violationRules {
                rule {
                    element = "CLASS"
                    limit {
                        counter = "LINE"
                        value = "COVEREDRATIO"
                        minimum = MINIMUM_COVERAGE
                    }
                    // A class with no business logic in it contributes nothing
                    // either way; excludes are applied via classDirectories
                    // above rather than repeated here.
                }
            }
        }
    }

    /**
     * Configures the task called [name] if it already exists, and registers it
     * otherwise.
     *
     * The `jacoco` base plugin creates `jacocoTestReport` and
     * `jacocoCoverageVerification` for JVM projects but not for Android ones,
     * so neither a plain `register` nor a plain `named` works across both.
     */
    private inline fun <reified T : Task> Project.withTypeOrRegister(
        name: String,
        crossinline configure: T.() -> Unit,
    ) {
        if (tasks.findByName(name) != null) {
            tasks.named(name, T::class.java) { configure() }
        } else {
            tasks.register(name, T::class.java) { configure() }
        }
    }

    /**
     * Builds the class file tree for a coverage task.
     *
     * @param excludes always applied — generated and UI code.
     * @param includes when non-null, restricts the tree to business logic. Null
     *   means "report on everything that survived [excludes]".
     */
    private fun Project.classesMatching(
        excludes: List<String>,
        includes: List<String>?
    ): FileTree {
        // Where compiled classes actually land. The first path is the AGP 9
        // one and is NOT the `tmp/kotlin-classes/debug` that every Jacoco +
        // Android guide online gives: under built-in Kotlin
        // (android.builtInKotlin) the compiler writes to
        //   intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes
        // Using the AGP 8 path silently produces a report with zero classes in
        // it — a green build that measured nothing, which is worse than a red
        // one. Verified by inspecting the build directory, not from docs.
        val classRoots = files(
            layout.buildDirectory.dir("intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes"),
            layout.buildDirectory.dir("javac/debug/compileDebugJavaWithJavac/classes"),
            // Plain JVM modules (core:model, tools:detekt-rules).
            layout.buildDirectory.dir("classes/kotlin/main"),
            layout.buildDirectory.dir("classes/java/main"),
        )
        return classRoots.asFileTree.matching {
            if (includes != null) include(includes)
            exclude(excludes)
        }
    }

    /**
     * The unit-test task to measure, or null for a project that has none.
     *
     * Android library and application modules produce `testDebugUnitTest`; a
     * plain JVM module (core:model, tools:detekt-rules) produces `test`. A
     * container project such as `:core` or `:feature` — and `:app`, which
     * applies neither the java nor the kotlin-jvm plugin in a testable form —
     * gets no coverage tasks at all.
     *
     * Note this is decided from the APPLIED PLUGIN, not from whether the task
     * object exists yet: AGP registers `testDebugUnitTest` during variant
     * creation, which happens after the `afterEvaluate` block this runs in, so
     * a `findByName` check here would always miss and silently register
     * nothing.
     */
    private fun Project.resolveTestTaskName(): String? = when {
        pluginManager.hasPlugin("com.android.library") ||
                pluginManager.hasPlugin("com.android.application") -> "testDebugUnitTest"

        pluginManager.hasPlugin("org.jetbrains.kotlin.jvm") ||
                pluginManager.hasPlugin("java") -> "test"

        else -> null
    }

    private companion object {
        const val PERCENT = 100
        val MINIMUM_COVERAGE: BigDecimal = BigDecimal("0.90")

        /** Where both AGP and the JVM plugin write their .exec/.ec data. */
        val EXEC_PATTERNS = listOf(
            "jacoco/*.exec",
            "outputs/unit_test_code_coverage/**/*.exec",
            "outputs/code_coverage/**/*.ec",
        )
    }
}
