// Navigation route only. Anything that needs to navigate to checklists depends
// on this module; nothing depends on :feature:checklists:impl except :app.
plugins {
    id("cleanarch.android.library")
}

android {
    namespace = "io.github.spoonart1.cleanarchwithagent.checklists.api"
}
