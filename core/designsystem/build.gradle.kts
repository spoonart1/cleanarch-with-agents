plugins {
    id("cleanarch.android.library")
    id("cleanarch.android.compose")
}

android {
    namespace = "io.github.spoonart1.cleanarchwithagent.designsystem"
}

dependencies {
    implementation(libs.androidx.compose.material.icons.extended)
}
