pluginManagement {
    // Convention plugins live in an included build so they are compiled before
    // any module is configured.
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "CleanArchWithAgent"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")

// Core modules, in dependency order (model has no dependencies at all).
include(":core:model")
include(":core:common")
include(":core:designsystem")
include(":core:database")
include(":core:network")
include(":core:sync")
include(":core:data")
include(":core:testing")

// Features. Each is split api/impl so that one feature can navigate to another
// without ever seeing its implementation.
include(":feature:checklists:api")
include(":feature:checklists:impl")
include(":feature:settings:api")
include(":feature:settings:impl")
