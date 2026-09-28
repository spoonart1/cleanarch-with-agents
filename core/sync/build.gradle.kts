plugins {
    id("cleanarch.android.library")
    id("cleanarch.android.hilt")
}

android {
    namespace = "io.github.spoonart1.cleanarchwithagent.sync"
}

dependencies {
    // api: SyncStatusMonitor exposes Flow<SyncState> and SyncEngine returns
    // types built from core:model, so consumers need it on their compile path.
    api(projects.core.model)
    implementation(projects.core.common)
    implementation(projects.core.database)
    implementation(projects.core.network)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(libs.androidx.work.testing)

    // The sync tests drive a real in-memory Room database rather than a fake
    // DAO: the conflict rule depends on actual transaction and query
    // behaviour, so faking the database would test the fake instead.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    // Room only for the tests: production code here talks to DAOs, not to Room
    // itself, so this stays out of the implementation classpath.
    testImplementation(libs.androidx.room.runtime)
    testImplementation(libs.androidx.room.ktx)
}
