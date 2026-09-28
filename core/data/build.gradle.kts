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

    // The repository tests drive a real in-memory Room database: the point of
    // these tests is that the row and its outbox entry land together, which a
    // faked DAO could not demonstrate.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.room.runtime)
    testImplementation(libs.androidx.room.ktx)
}
