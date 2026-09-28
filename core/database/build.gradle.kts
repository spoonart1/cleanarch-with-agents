plugins {
    id("cleanarch.android.library")
    id("cleanarch.android.hilt")
    id("cleanarch.android.room")
}

android {
    namespace = "io.github.spoonart1.cleanarchwithagent.database"
}

dependencies {
    // api: entities expose model enums (SyncStatus, OutboxEntityType) in their
    // public signatures, so consumers need these types on their compile path.
    api(projects.core.model)
    implementation(projects.core.common)

    // Room needs a real SQLite instance, so the DAO tests run on Robolectric
    // rather than on a device. That keeps them in `./gradlew test` and in CI
    // with no emulator.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
}
