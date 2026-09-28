plugins {
    id("cleanarch.android.library")
    id("cleanarch.android.hilt")
}

android {
    namespace = "io.github.spoonart1.cleanarchwithagent.data"
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.common)
    implementation(projects.core.database)
    implementation(projects.core.network)
    implementation(projects.core.sync)
}
