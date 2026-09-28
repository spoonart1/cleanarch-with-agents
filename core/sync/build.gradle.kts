plugins {
    id("cleanarch.android.library")
    id("cleanarch.android.hilt")
}

android {
    namespace = "io.github.spoonart1.cleanarchwithagent.sync"
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.common)
    implementation(projects.core.database)
    implementation(projects.core.network)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(libs.androidx.work.testing)
}
