plugins {
    id("cleanarch.android.library")
    id("cleanarch.android.hilt")
    id("cleanarch.android.room")
}

android {
    namespace = "io.github.spoonart1.cleanarchwithagent.database"
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.common)
}
