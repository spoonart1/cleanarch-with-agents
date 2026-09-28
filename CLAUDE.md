# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this project is

An open-source Android starter template demonstrating multi-module Clean Architecture with
offline-first sync. It is meant to be **read and learned from**, so prefer clarity over cleverness:
no reflection tricks, no clever generics, no abbreviations a newcomer would have to decode.

Build status: Phases 1–4 and 6 complete. The app runs end to end against an in-memory fake backend.
Remaining: CI and app smoke tests (5), README (7). See "Build phases".

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
./gradlew :core:database:test    # one module
./gradlew :core:sync:test --tests "*OutboxTest*"          # one class
./gradlew :core:sync:test --tests "*OutboxTest.replays*"  # one method
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

- Split composables into a stateful wrapper that reads the ViewModel and a stateless one that takes
  state plus lambdas. Only the stateless one is previewable and cheap to test; give it a `@Preview`.
- ViewModels expose one immutable UI state as a `StateFlow`; screens render it and emit events up.
- Use UI-specific models in the UI layer, not domain or database types directly.
- Use stable keys in lazy lists.
- Map between database, network and domain models in `core:data` — those types must not leak past it.

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
5. CI workflow, app smoke tests
6. ✅ This file, and the `new-feature-module` skill
7. README

Out of scope for v1 (roadmap only): photo attachments, conflict-resolution UI, Macrobenchmark and
baseline profiles, Fastlane, a real backend.
