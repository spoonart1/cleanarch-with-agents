# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this project is

An open-source Android starter template demonstrating multi-module Clean Architecture with
offline-first sync. It is meant to be **read and learned from**, so prefer clarity over cleverness:
no reflection tricks, no clever generics, no abbreviations a newcomer would have to decode.

Build status: v1 complete — all seven phases. The app runs end to end against an in-memory fake
backend, CI runs build, lint and unit tests on every PR, and `./gradlew build test` passes from a
fresh clone with no secrets. See "Build phases" for what was deliberately left out of v1.

## How to work here

**Work in small, compilable steps.** For anything beyond a trivial edit — a new screen, ViewModel,
repository, or use case — plan first, get agreement, then build it in pieces rather than emitting a
finished file in one response. Each step should leave the code compiling, so a failure points at the
edit that caused it. Prefer `Edit` on an existing file over rewriting it wholesale.

A workable order for a new component:

1. Skeleton — package, imports, declaration, constructor, public API
2. State and data structures
3. Business logic
4. UI and event wiring
5. Previews, sample data, docs

**Check the comment switch before step 1.** `cleanarch.aiComments` in `gradle.properties` decides
whether code you write carries a KDoc header and inline comments. It ships `false`, so the default is
none. Read it at the start of a task rather than at the end — retrofitting comments, or stripping a
file full of them, is a diff nobody asked for. See `.claude/conventions.md` § "Comments when an agent
writes code" for what the switch does and does not cover.

**Verify, don't assume.** Run the build after multi-file changes. Surface dependency and version
conflicts before working around them — a version pin chosen silently becomes someone else's puzzle.

**Ask rather than guess when an API is uncertain.** A wrong guess in a template teaches the wrong
thing to everyone who clones it.

Each phase ends with `./gradlew build test` passing, a conventional commit, and **a stop for
maintainer confirmation before the next phase begins.**

## Commands

```bash
./gradlew build                  # assembles debug+release, runs lint. The main gate.
./gradlew test                   # all JVM unit tests
./gradlew detektAll              # static analysis, every module
./gradlew detektAll -PdetektAutoCorrect=true   # same, fixing formatting in place
./gradlew jacocoTestReportAll            # coverage reports (HTML + XML)
./gradlew jacocoCoverageVerificationAll  # the 90% business-logic gate
./gradlew :core:database:test    # one module
# One class or one method. Note these take testDebugUnitTest, not the `test`
# lifecycle task, which does not accept --tests.
./gradlew :core:sync:testDebugUnitTest --tests "*OutboxTest*"
./gradlew :core:sync:testDebugUnitTest --tests "*OutboxTest.replays*"
./gradlew lint                   # lint only
./gradlew projects               # confirm module wiring
./gradlew :feature:settings:impl:dependencies --configuration debugCompileClasspath | grep feature
```

That last command is the architectural check — run it after touching module dependencies. See rule 1.

Instrumented tests (`connectedAndroidTest`) need a running emulator. **The maintainer runs those, not
the agent** — write them, but do not attempt to execute them.

Compose UI tests and DAO tests run on Robolectric, so they are part of `./gradlew test` and need no
emulator. A module with Compose UI tests needs
`src/test/resources/robolectric.properties` containing `sdk=34`: Espresso's input injection calls
`InputManager.getInstance()`, which no longer exists on SDK 37, so without it every Compose test
fails with `NoSuchMethodException`.

Gradle's daemon holds file locks on Windows. If a directory won't delete, run `./gradlew --stop`
first.

### CI

`.github/workflows/ci.yml` runs build, unit tests and lint on pushes to `main` and on PRs. Three
choices there are deliberate and worth not undoing:

- **`runs-on: ubuntu-24.04`, not `ubuntu-latest`.** That label moves to Ubuntu 26.04 during late
  2026, where the default JDK becomes 25 and JDK 8 is removed. Moving should be a decision, not a
  surprise.
- **No `cache: gradle` on `setup-java`.** `gradle/actions/setup-gradle` owns the Gradle cache, and
  the Gradle docs warn against configuring both.
- **`gradlew` must stay mode `100755` in git.** Committed as `100644` it fails on Linux runners with
  "Permission denied" before anything builds. Check with `git ls-files -s gradlew`.

Only `main` writes to the Gradle cache; PR runs read it. A PR reporting a cache miss is expected
behaviour, not a fault.

## Architecture

Dependencies point inward, and the direction is enforced by what each module declares:

```
app  ──▶ feature:*:impl ──▶ core:data ──▶ core:sync ──▶ core:database ──▶ core:model
                                                    └─▶ core:network  ──┘
```

**Room is the single source of truth.** The UI observes Flows from the database and never reads the
network directly. A write goes to Room *and* appends an outbox row in the same transaction; the sync
engine drains the outbox separately. This is the core idea of the template — if a change would let
the UI read a network response directly, it is wrong.

Features contribute navigation entries through Hilt multibindings (`@IntoSet`), so `app` has no
central registration list to forget. Adding a feature must not require editing a shared file.

### Rules that must not be broken

These are the point of the template. Violating one silently defeats its purpose:

1. **No feature depends on another feature's `impl`.** To navigate to another feature, depend on its
   `api` module, which holds the route/key only. `app` is the sole module that sees any `impl`.
2. **`core:model` is pure Kotlin** — no Android dependency, ever. It uses the `cleanarch.jvm.library`
   convention plugin; every other module is an Android library. This is what keeps domain models
   testable without an emulator.
3. **The `app` module stays thin.** No screens, ViewModels, repositories or business logic. It wires
   modules together, hosts navigation, and nothing else.
4. **Never swallow `CancellationException`** in coroutine code. A bare `catch (e: Exception)` around
   a suspending call breaks structured concurrency — rethrow it or catch a narrower type.
5. **Prefer fakes over mocks** in tests. Shared fakes live in `core:testing`.
6. **Every multi-table write happens in one transaction.**

### Code conventions

**The canonical source is [`.claude/conventions.md`](.claude/conventions.md).** It covers naming
(booleans, test names), test structure (Given/When/Then), function length, the testing rules, the
90% coverage gate, Compose and state, modern-Android practice, and the tooling facts that change how
those rules behave. The agents in `.claude/agents/` read it too, so there is one place to edit and
nothing to keep in sync.

In brief — read the file for the detail and the reasoning:

- **Booleans read as a question** (`isLoading`, `hasItems`, `canRetry`), enforced by a custom detekt
  rule.
- **Test names read as a sentence**: `test <function> when <clause> should <result>`, backticked and
  enforced.
- **Test bodies are Given / When / And / Then** — a convention, not a detekt rule.
- **Functions stay under 20 lines**, `@Composable`/`@Preview` and test sources exempt.
- **Fakes over mocks.** No mocking library is on any classpath, deliberately.
- **Business logic is gated at 90% line coverage.** Exclusions are for code with no logic, never for
  code that is merely hard to test.
- **Agent-written comments are off by default**, switched by `cleanarch.aiComments` in
  `gradle.properties`. Not read by the build — a flag for agents.

The architectural rules that must not be broken are above, in "Rules that must not be broken"; those
stay here because they define the template rather than its style.

## Build system — read this before touching `build-logic`

The build uses **AGP 9.2.1**, which differs from AGP 8 in ways that make most convention-plugin
examples found online — including Now in Android — fail to compile. These were verified empirically
against this project, not taken from documentation:

- **`CommonExtension` has no type parameters.** The ubiquitous `CommonExtension<*, *, *, *, *, *>`
  receiver does not compile.
- **`CommonExtension` exposes only getters**, not `Action`-taking blocks. `defaultConfig { }` and
  `lint { }` are declared only on the concrete `ApplicationExtension` / `LibraryExtension`. Shared
  helpers must write `extension.defaultConfig.minSdk = ...` instead.
- **AGP compiles Kotlin itself** (`android.builtInKotlin`). Do **not** apply
  `org.jetbrains.kotlin.android` to an Android module — it fails. Only `core:model` applies KGP,
  via the JVM convention plugin.
- **`kotlinOptions { }` is gone.** `jvmTarget` derives from `compileOptions.targetCompatibility`;
  setting both causes "Inconsistent JVM-target compatibility".
- **KSP only — kapt is incompatible** with built-in Kotlin. There is no kapt fallback.
- Gradle 9 refuses to configure an `include`d module whose directory does not exist, and fails a
  `Test` task that discovers no tests (relaxed in `KotlinCompiler.kt` while modules are still empty).

When unsure of an AGP DSL shape, check the jar rather than trusting a snippet:

```bash
javap -classpath . com.android.build.api.dsl.CommonExtension   # after unzipping gradle-api-9.2.1.jar
```

### Versions

All versions live in `gradle/libs.versions.toml`, which carries inline comments explaining the
non-obvious constraints. Read those before bumping anything. In particular:

- **`compileSdk` is 37, not 36** — Compose BOM 2026.09.00 pulls compose runtime 1.12.x, which refuses
  consumers compiled against 36. `targetSdk` stays 36.
- **KSP no longer pairs with the Kotlin version.** There is no `2.4.20-2.0.x`; it is plain semver
  (`2.3.12`) and the `2.3` prefix does not mean Kotlin 2.3.
- **Hilt cannot go below 2.59** on AGP 9 (2.59 dropped AGP 8 support).
- **Pin the whole `androidx.lifecycle` group together.** Mixing a 2.11 runtime with a 2.12-alpha
  viewmodel artifact gives `NoSuchMethodError` at runtime.
- `androidx.hilt:hilt-compiler` and `com.google.dagger:hilt-compiler` are different artifacts;
  `hilt-work` needs both KSP processors.

Verify versions against Maven Central before changing them — do not rely on recalled versions.

### Convention plugins

Module build files should be a few lines. If one grows, the logic belongs in a convention plugin in
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
| `cleanarch.detekt` | applied to every module from the root build file |
| `cleanarch.jacoco` | applied to every module from the root build file |

The last two are applied in `subprojects { }` in the root `build.gradle.kts` rather than per module,
so a new module is covered the day it is created. Three things there are non-obvious:

- **The root script must declare them in its own `plugins { }` block** (with `apply false`) before
  `subprojects { }` can apply them by id. A convention plugin from an included build is otherwise not
  on the root script's classpath.
- **`Project.libs` resolves the catalog from `rootProject`.** Applied inside `subprojects { }`, a
  subproject has not been evaluated yet and does not carry the `VersionCatalogsExtension`.
- **Custom detekt rules live in `tools/detekt-rules`, in the main build — not in `build-logic`.**
  Detekt resolves rule sets through the `detektPlugins` configuration at its own runtime, and a
  project inside an included build cannot be named there.

### Detekt and JaCoCo on AGP 9

Both needed empirical fixes that contradict the guides online:

- **JaCoCo's class directory is not `tmp/kotlin-classes/debug`.** Under built-in Kotlin, AGP 9 writes
  to `intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes`. Using the AGP 8 path produces
  a report containing zero classes — a green build that measured nothing, which is worse than a red
  one. Verified by inspecting the build directory.
- **Synthetic classes must be excluded from the coverage gate.** The rule scores per `CLASS`, and
  Kotlin emits one class per suspend function and lambda (`SyncEngine$push$1`). Those carry a state
  machine, not author-written branches, and cannot be covered independently — left in, they produce a
  page of 0.00 violations for functions whose tests pass.
- **Detekt 1.23.8 runs with type resolution off.** It embeds Kotlin 2.0.21's compiler for analysis
  while this project compiles with 2.4.20, and feeding 2.4 sources to the 2.0 type solver produces
  spurious unresolved-reference findings. Every rule this project relies on is syntactic. There is no
  detekt 2.x; revisit when one ships against a matching compiler.
- **`formatting:ImportOrdering` is disabled** because the rule and its own `--auto-correct` disagree:
  the fix moves `javax.*` below `kotlinx.*`, and the check then rejects that result. Running
  auto-correct twice does not converge. Not worth an `.editorconfig` to referee a cosmetic rule.
- **There is no detekt baseline file, deliberately.** The codebase is clean. A template that ships a
  baseline teaches contributors that suppression is the normal response to a finding.

## Secrets

Release signing reads from environment variables (`RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`,
`RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`) and falls back to an unsigned build when they are
absent, so a fresh clone builds with no secrets. Keystores, `google-services.json` and similar are
gitignored. Never introduce a code path that reads signing material from a file in the repo.

## Build phases

1. ✅ Skeleton — version catalog, `build-logic`, empty modules, placeholder app
2. ✅ `core:model`, `core:common`, `core:database`, `core:network` (fake backend + network simulator)
3. ✅ `core:sync`, `core:data`
4. ✅ `core:designsystem`, both features, navigation assembly
5. ✅ CI workflow, app smoke tests
6. ✅ This file, and the `new-feature-module` skill
7. ✅ README

Out of scope for v1 (roadmap only): photo attachments, conflict-resolution UI, Macrobenchmark and
baseline profiles, Fastlane, a real backend.
