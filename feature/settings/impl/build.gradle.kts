plugins {
    id("cleanarch.android.feature")
}

android {
    namespace = "io.github.spoonart1.cleanarchwithagent.settings.impl"
}

dependencies {
    implementation(projects.feature.settings.api)
    // Settings navigates to checklists through its api module only — never
    // through :feature:checklists:impl.
    implementation(projects.feature.checklists.api)
    implementation(projects.core.sync)
    implementation(projects.core.network)
}
