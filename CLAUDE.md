# CLAUDE.md

Guidance for Claude Code when working in this repository.

## Response style

Answer tersely: short sentences, no preamble, no restating the question. Code, commands, file
paths and error messages stay exact. Security warnings and confirmations stay in full sentences.

## What this project is

An open-source Android starter template: multi-module Clean Architecture with offline-first sync.
It is meant to be **read and learned from**, so prefer clarity over cleverness: no reflection
tricks, no clever generics, no abbreviations a newcomer would have to decode.

v1 is complete. The app runs end to end against an in-memory fake backend, CI runs build, lint and
unit tests on every PR, and `./gradlew build test` passes from a fresh clone with no secrets.
Roadmap only (not v1): photo attachments, conflict-resolution UI, Macrobenchmark and baseline
profiles, Fastlane, a real backend.

## How to work here

- **Small, compilable steps.** For anything beyond a trivial edit (a screen, ViewModel, repository,
  use case): plan, get agreement, then build in pieces so a failure points at the edit that caused
  it. Order: skeleton and public API → state and data structures → business logic → UI and event
  wiring → previews, sample data, docs. Prefer `Edit` over rewriting a file.
- **Check `cleanarch.aiComments` in `gradle.properties` before writing code.** It ships `false`:
  no KDoc headers, no inline comments. Read it at the start, not the end. Details in
  `.claude/conventions.md` § "Comments when an agent writes code".
- **Verify, don't assume.** Run the build after multi-file changes. Surface dependency and version
  conflicts instead of pinning around them.
- **Ask when an API is uncertain.** A wrong guess in a template teaches everyone who clones it.
- **Each unit of work ends with** `./gradlew build test` passing, a conventional commit, and a stop
  for maintainer confirmation.

## Commands

```bash
./gradlew build                  # assembles debug+release, runs lint. The main gate.
./gradlew test                   # all JVM unit tests (Robolectric included)
./gradlew detektAll              # static analysis, every module
./gradlew detektAll -PdetektAutoCorrect=true   # fixes formatting in place; local only, never CI
./gradlew jacocoTestReportAll            # coverage reports, informative
./gradlew jacocoCoverageVerificationAll  # the 90% business-logic gate
./gradlew :core:database:test    # one module
./gradlew :core:sync:testDebugUnitTest --tests "*OutboxTest*"        # one class (`test` has no --tests)
./gradlew :core:sync:testDebugUnitTest --tests "*OutboxTest.replays*" # one method
./gradlew projects               # confirm module wiring
./gradlew :feature:settings:impl:dependencies --configuration debugCompileClasspath | grep feature
```

The last command is the architectural check for rule 1. Run it after touching module dependencies.

- **Instrumented tests (`connectedAndroidTest`) are the maintainer's to run.** Write them; never
  execute them.
- Compose UI tests and DAO tests run on Robolectric inside `./gradlew test`. A module with Compose
  UI tests needs `src/test/resources/robolectric.properties` containing `sdk=34`; on SDK 37
  Espresso's input injection fails with `NoSuchMethodException`.
- Gradle's daemon holds file locks on Windows. If a directory won't delete, run `./gradlew --stop`.

### CI

`.github/workflows/ci.yml` runs build, unit tests and lint on pushes to `main` and on PRs. Three
deliberate choices:

- **`runs-on: ubuntu-24.04`, not `ubuntu-latest`.** The floating label moves to 26.04 (JDK 25
  default, JDK 8 removed) in late 2026. Moving should be a decision.
- **No `cache: gradle` on `setup-java`.** `gradle/actions/setup-gradle` owns the cache; Gradle warns
  against both.
- **`gradlew` stays mode `100755` in git**, or Linux runners fail with "Permission denied". Check
  with `git ls-files -s gradlew`.

Only `main` writes the Gradle cache; a PR reporting a cache miss is normal.

## Architecture

Dependencies point inward, enforced by what each module declares:

```
app  ──▶ feature:*:impl ──▶ core:data ──▶ core:sync ──▶ core:database ──▶ core:model
                                                    └─▶ core:network  ──┘
```

**Room is the single source of truth.** The UI observes Flows from the database and never reads the
network. A write goes to Room *and* appends an outbox row in the same transaction; the sync engine
drains the outbox separately. If a change lets the UI read a network response directly, it is wrong.

Features contribute navigation entries through Hilt multibindings (`@IntoSet`). Adding a feature
must not require editing a shared file.

### Rules that must not be broken

1. **No feature depends on another feature's `impl`.** Navigate via its `api` module (route/key
   only). `app` is the sole module that sees any `impl`.
2. **`core:model` is pure Kotlin**, no Android dependency ever. It alone uses `cleanarch.jvm.library`.
3. **`app` stays thin.** No screens, ViewModels, repositories or business logic. Wiring and
   navigation only.
4. **Never swallow `CancellationException`.** A bare `catch (e: Exception)` around a suspending call
   breaks structured concurrency; rethrow it or catch a narrower type.
5. **Fakes over mocks.** No mocking library is on any classpath, deliberately. Shared fakes live in
   `core:testing`.
6. **Every multi-table write happens in one transaction.**

### Code conventions

**Canonical source: [`.claude/conventions.md`](.claude/conventions.md).** The agents read it too;
edit there, not here. Summary:

- Booleans read as a question (`isLoading`, `hasItems`, `canRetry`). Detekt-enforced.
- Test names: `test <function> when <clause> should <result>`, backticked. Detekt-enforced.
- Test bodies: `// Given` / `// When` / `// And` / `// Then`. Convention only.
- Functions under 20 lines; `@Composable`, `@Preview` and test sources exempt.
- Business logic gated at 90% line coverage. Exclusions are for code with no logic, never for code
  that is merely hard to test.

## Build system: AGP 9 (read before touching `build-logic`)

**AGP 9.2.1** differs from AGP 8 in ways that make most convention-plugin examples online,
including Now in Android, fail to compile. Verified empirically against this project:

- **`CommonExtension` has no type parameters.** `CommonExtension<*, *, *, *, *, *>` does not compile.
- **`CommonExtension` exposes only getters.** `defaultConfig { }` and `lint { }` exist only on
  `ApplicationExtension` / `LibraryExtension`; shared helpers write
  `extension.defaultConfig.minSdk = ...`.
- **AGP compiles Kotlin itself** (`android.builtInKotlin`). Never apply
  `org.jetbrains.kotlin.android` to an Android module. Only `core:model` applies KGP.
- **`kotlinOptions { }` is gone.** `jvmTarget` derives from `compileOptions.targetCompatibility`;
  setting both fails with "Inconsistent JVM-target compatibility".
- **KSP only.** kapt is incompatible with built-in Kotlin.
- Gradle 9 refuses an `include`d module whose directory does not exist, and fails a `Test` task that
  finds no tests (relaxed in `KotlinCompiler.kt`).

When unsure of a DSL shape, check the jar, not a snippet:

```bash
javap -classpath . com.android.build.api.dsl.CommonExtension   # after unzipping gradle-api-9.2.1.jar
```

### Versions

All versions live in `gradle/libs.versions.toml`. Its inline comments explain every non-obvious
constraint (compileSdk 37, KSP's version scheme, the Hilt floor, the lifecycle group pin, the two
`hilt-compiler` artifacts). Read them before bumping anything, and verify candidate versions against
Maven Central rather than memory.

### Convention plugins

Module build files should be a few lines. Shared logic belongs in
`build-logic/convention/src/main/kotlin/`:

| Plugin id | Use for |
|---|---|
| `cleanarch.android.application` | `app` only |
| `cleanarch.android.library` | core Android libraries |
| `cleanarch.android.feature` | every `feature:*:impl` (library + compose + hilt + feature deps) |
| `cleanarch.android.compose` | modules with Compose UI |
| `cleanarch.android.hilt` | modules using DI |
| `cleanarch.android.room` | `core:database` only |
| `cleanarch.jvm.library` | `core:model` only |
| `cleanarch.detekt` | every module, from the root `subprojects { }` |
| `cleanarch.jacoco` | every module, from the root `subprojects { }` |

Non-obvious about the last two:

- The root script must declare them with `apply false` in its own `plugins { }` block, or they are
  not on its classpath.
- `Project.libs` resolves the catalog from `rootProject`, because a subproject inside
  `subprojects { }` is not yet evaluated.
- Custom detekt rules live in `tools/detekt-rules` in the main build, not `build-logic`. Detekt
  resolves rule sets via `detektPlugins` at its own runtime and cannot name an included-build project.

### Detekt and JaCoCo on AGP 9

Empirical fixes that contradict the guides online:

- **JaCoCo's class directory is `intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes`**,
  not `tmp/kotlin-classes/debug`. The AGP 8 path yields a report with zero classes: a green build
  that measured nothing.
- **Synthetic classes are excluded from the coverage gate.** The rule scores per `CLASS`, and Kotlin
  emits one per suspend function and lambda (`SyncEngine$push$1`) that cannot be covered
  independently.
- **Detekt 1.23.8 runs with type resolution off.** It embeds Kotlin 2.0.21 while the project compiles
  with 2.4.20; type resolution produces spurious unresolved references. Every rule used is
  syntactic. There is no detekt 2.x yet.
- **No detekt baseline file, deliberately.** A shipped baseline teaches that suppression is the
  normal response to a finding. Intentional config deviations are commented in
  `config/detekt/detekt.yml`.

## Secrets

Release signing reads `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS` and
`RELEASE_KEY_PASSWORD` from the environment and falls back to an unsigned build when absent.
Keystores and `google-services.json` are gitignored. Never add a code path that reads signing
material from a file in the repo.
