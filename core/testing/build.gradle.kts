// Shared test fakes, rules and data builders. Depended on by test source sets
// across the project, so its dependencies are `api` to pass them through.
plugins {
    id("cleanarch.android.library")
    id("cleanarch.android.hilt")
}

android {
    namespace = "io.github.spoonart1.cleanarchwithagent.testing"
}

dependencies {
    api(projects.core.model)
    api(projects.core.data)
    // TestClock and TestIdGenerator implement interfaces from core:common.
    api(projects.core.common)

    api(libs.junit)
    api(libs.kotlinx.coroutines.test)
    api(libs.turbine)
}
