plugins {
    id("cleanarch.android.feature")
}

android {
    namespace = "io.github.spoonart1.cleanarchwithagent.checklists.impl"
}

dependencies {
    implementation(projects.feature.checklists.api)
    // Needed to navigate to settings. The api module holds the route only —
    // this module has no access to :feature:settings:impl.
    implementation(projects.feature.settings.api)
    implementation(projects.core.sync)
}
