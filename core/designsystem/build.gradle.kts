plugins {
    id("cleanarch.android.library")
    id("cleanarch.android.compose")
}

android {
    namespace = "io.github.spoonart1.cleanarchwithagent.designsystem"
}

dependencies {
    // api: SyncStatusIcon and SyncStateIndicator take core:model types as
    // parameters, so callers need them on their compile path.
    api(projects.core.model)

    // api: FeatureNavigation exposes NavGraphBuilder and NavHostController in
    // its signature, and every feature implements it.
    api(libs.androidx.navigation.compose)

    implementation(libs.androidx.compose.material.icons.extended)
}
