// Custom detekt rules for this project's conventions.
//
// A plain JVM library, deliberately NOT using the cleanarch.jvm.library
// convention plugin: that plugin targets the Android-free domain layer and
// pulls in test dependencies this module does not want. This module's only
// job is to produce a jar that detekt can load as a rule set.
//
// It lives in the main build (not build-logic) because detekt resolves rule
// sets through the `detektPlugins` configuration at its own runtime, and a
// project inside an included build cannot be referenced there.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

group = "io.github.spoonart1.cleanarchwithagent.detekt"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // compileOnly: detekt supplies its own API at runtime. Bundling it would
    // put two copies of the API on detekt's classpath.
    compileOnly(libs.detekt.api)

    testImplementation(libs.detekt.test)
    testImplementation(libs.junit)
}
