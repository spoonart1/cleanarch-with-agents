plugins {
    id("cleanarch.android.library")
    id("cleanarch.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.spoonart1.cleanarchwithagent.network"
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.common)

    implementation(libs.retrofit.core)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)
}
