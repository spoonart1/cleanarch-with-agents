package io.github.spoonart1.cleanarchwithagent.buildlogic

/**
 * Classes excluded from coverage measurement everywhere.
 *
 * The principle: exclude only code that a human did not write or that has no
 * branches worth asserting on. Anything containing a decision stays in.
 */
internal val coverageExclusions = listOf(
    // --- Generated: not ours to test -----------------------------------------
    "**/R.class",
    "**/R$*.class",
    "**/BuildConfig.*",
    "**/Manifest*.*",
    // Hilt/Dagger. The generated component graph is the framework's concern;
    // a DI wiring mistake surfaces as a compile error, not a coverage gap.
    "**/*_Factory*.*",
    "**/*_MembersInjector*.*",
    "**/*_HiltModules*.*",
    "**/*_Impl*.*",
    "**/Hilt_*.*",
    "**/*Hilt*.*",
    "**/Dagger*Component*.*",
    "**/*Module_*Factory.*",
    "**/*_GeneratedInjector.*",
    // Room generates DAO and database implementations from annotated
    // interfaces; the queries themselves are covered by the DAO tests that
    // exercise the interface.
    "**/*Database_Impl.*",
    "**/*Dao_Impl.*",
    // Kotlin serialization writes $$serializer classes per @Serializable type.
    "**/*\$\$serializer.*",
    "**/*$*Serializer.*",

    // --- Compose UI ----------------------------------------------------------
    // Composables are verified by Robolectric UI tests, which assert on
    // rendered semantics rather than executing lines. Counting them here would
    // measure the wrong thing and make the number unreadable — which is exactly
    // how a coverage gate stops being believed.
    "**/*ComposableSingletons*.*",
    "**/*Screen*.*",
    "**/*ScreenKt*.*",
    "**/ui/theme/**",
    "**/*Preview*.*",

    // --- Declarations with no logic ------------------------------------------
    // Kotlin synthesises component1/copy/equals/hashCode for these; there is no
    // author-written branch to cover.
    "**/*\$WhenMappings.*",
    "**/*Kt\$*.*",

    // Synthetic classes the Kotlin compiler generates for suspend functions and
    // lambdas: SyncEngine$push$1, ChecklistDetailViewModel$uiState$1 and so on.
    //
    // These must be excluded or the CLASS-element rule scores each one
    // separately. A suspend function's continuation class carries the state
    // machine, not author-written branches, and it cannot be "covered" on its
    // own — the enclosing class already accounts for that code. Left in, they
    // produce a page of 0.00 violations for functions whose tests pass.
    "**/*\$*\$*.*",
    "**/*\$\$Lambda*.*",
    "**/*\$WhenMappings*.*",
    "**/*\$Companion.*",

    // --- Framework adapters --------------------------------------------------
    // Classes whose whole body is a call into an Android framework object, with
    // no decision of our own: RetrofitNetworkDataSource forwards two methods to
    // a Retrofit interface, WorkManagerSyncScheduler builds WorkRequests, and
    // SyncWorker is a WorkManager entry point. They need an emulator to
    // exercise and there is nothing to assert about them that the framework
    // does not already guarantee.
    //
    // Note the distinction being drawn: these are excluded for having no logic,
    // NOT for being hard to test. The logic they delegate to — SyncEngine — is
    // gated at 90%.
    "**/RetrofitNetworkDataSource.*",
    "**/WorkManagerSyncScheduler.*",
    "**/*Worker.*",
    "**/*Application.*",
    "**/*Activity.*",
)

/**
 * The layers the 90% gate actually applies to: everything where a bug would be
 * a behaviour bug rather than a rendering one. This is the "usecase, repository,
 * controller or any other business logic layer" of the project's convention,
 * expressed as class-name patterns.
 */
internal val businessLogicIncludes = listOf(
    "**/*UseCase*.*",
    "**/*Repository*.*",
    "**/*RepositoryImpl*.*",
    "**/*ViewModel*.*",
    "**/*Mapper*.*",
    "**/*Manager*.*",
    "**/*Controller*.*",
    "**/*Engine*.*",
    "**/*Handler*.*",
    "**/*Validator*.*",
    "**/*DataSource*.*",
)
