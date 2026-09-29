// Root build file. Plugins are declared here (without applying them) so that
// each module can apply them without re-stating a version; the versions
// themselves live in gradle/libs.versions.toml.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.detekt) apply false
    // Declared (not applied) so the root script can apply them to subprojects
    // below: a convention plugin from the included build is only on the root
    // script's classpath once it appears in this block.
    id("cleanarch.detekt") apply false
    id("cleanarch.jacoco") apply false
}

// Static analysis and coverage are applied to every module from here rather
// than module by module. A new module is then covered on the day it is created,
// which is the only way a convention like this stays true — anything requiring
// an opt-in eventually gets forgotten.
//
// :tools:detekt-rules is excluded from detekt itself: it supplies the rule set,
// so having it analyse itself with its own unreleased jar is a circular
// dependency Gradle cannot resolve.
subprojects {
    if (path != ":tools:detekt-rules") {
        apply(plugin = "cleanarch.detekt")
    }
    apply(plugin = "cleanarch.jacoco")
}

// Convenience aggregate: `./gradlew detektAll` runs detekt across every module
// in one invocation, which is what CI calls.
tasks.register("detektAll") {
    group = "verification"
    description = "Runs detekt on every module."
    // Referenced by path rather than resolved with findByName: the latter
    // depends on whether the subproject has been evaluated yet, which makes it
    // quietly skip modules depending on configuration order.
    dependsOn(
        subprojects
            .filter { it.path != ":tools:detekt-rules" }
            .map { "${it.path}:detekt" },
    )
}

// Same for the coverage gate. Unlike detektAll these cannot list task paths
// blindly: the Jacoco convention plugin only registers its tasks in a module
// that actually has a test task, so container projects (:core, :feature) and
// :app have none. `matching` resolves lazily against whatever exists.
tasks.register("jacocoCoverageVerificationAll") {
    group = "verification"
    description = "Verifies business-logic coverage across every module."
    dependsOn(
        subprojects.map { subproject ->
            subproject.tasks.matching { it.name == "jacocoCoverageVerification" }
        },
    )
}

tasks.register("jacocoTestReportAll") {
    group = "verification"
    description = "Generates coverage reports for every module."
    dependsOn(
        subprojects.map { subproject ->
            subproject.tasks.matching { it.name == "jacocoTestReport" }
        },
    )
}
