plugins {
    id("cleanarch.android.feature")
}

android {
    namespace = "io.github.spoonart1.cleanarchwithagent.checklists.impl"
}

dependencies {
    implementation(projects.feature.checklists.api)
    implementation(projects.core.sync)
}
