plugins {
    id("cleanarch.android.application")
    id("cleanarch.android.compose")
    id("cleanarch.android.hilt")
}

android {
    namespace = "io.github.spoonart1.cleanarchwithagent"

    defaultConfig {
        applicationId = "io.github.spoonart1.cleanarchwithagent"
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            // Read from the environment, never from a file in the repo. On a
            // fresh clone none of these are set, so the block stays unconfigured
            // and `assembleRelease` falls back to no signing config rather than
            // failing — which keeps `./gradlew build` working for everyone.
            val storePath = System.getenv("RELEASE_STORE_FILE")
            if (!storePath.isNullOrBlank()) {
                storeFile = file(storePath)
                storePassword = System.getenv("RELEASE_STORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Only attach the signing config when the environment actually
            // supplied one; an unconfigured config would fail the build.
            signingConfig = if (System.getenv("RELEASE_STORE_FILE").isNullOrBlank()) {
                null
            } else {
                signingConfigs.getByName("release")
            }
        }
    }
}

dependencies {
    // The app module wires modules together and hosts navigation. It contains
    // no screens, ViewModels, repositories or business logic.
    implementation(projects.core.designsystem)
    implementation(projects.core.sync)

    implementation(projects.feature.checklists.impl)
    implementation(projects.feature.settings.impl)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
}
