---
name: new-feature-module
description: Create a new feature in this Android template as a paired api/impl module set, wired with the convention plugins, a ViewModel with immutable UI state, and a passing test. Use when asked to add a feature, screen, or feature module to this project.
disable-model-invocation: true
argument-hint: "[feature-name]"
arguments: [feature-name]
allowed-tools:
  - Read
  - Write
  - Edit
  - Bash(./gradlew *)
  - Bash(mkdir *)
  - Bash(ls *)
  - Bash(git *)
---

# Add the feature module `$feature-name`

Create a feature as two modules: `:feature:$feature-name:api` (route only) and
`:feature:$feature-name:impl` (everything else). Work through the steps in order; each builds on
the previous one.

Use a single lowercase word for `$feature-name`. It becomes a Gradle path, a Kotlin package segment
and a type-safe project accessor (`projects.feature.reports.api`), and a hyphen or capital breaks at
least one of those. If the name given is not already in that form, normalise it and say so.

Read `CLAUDE.md` first if you have not already. The rules below are not stylistic; breaking them
defeats the point of the template.

## 1. Create the directories

Gradle 9 refuses to configure a module whose directory does not exist, so make these before
touching `settings.gradle.kts`:

```bash
PKG=io/github/spoonart1/cleanarchwithagent
mkdir -p feature/$feature-name/api/src/main/kotlin/$PKG/$feature-name/api
mkdir -p feature/$feature-name/impl/src/main/kotlin/$PKG/$feature-name/impl
mkdir -p feature/$feature-name/impl/src/test/kotlin/$PKG/$feature-name/impl
```

## 2. Register in `settings.gradle.kts`

Add next to the other feature includes:

```kotlin
include(":feature:$feature-name:api")
include(":feature:$feature-name:impl")
```

## 3. Write the two build files

`feature/$feature-name/api/build.gradle.kts` — the route only, so it stays a plain library with no
Compose and no Hilt:

```kotlin
plugins {
    id("cleanarch.android.library")
}

android {
    namespace = "io.github.spoonart1.cleanarchwithagent.$feature-name.api"
}
```

`feature/$feature-name/impl/build.gradle.kts` — one plugin supplies Compose, Hilt, lifecycle,
navigation, `core:data`, `core:designsystem`, `core:model` and `core:testing`:

```kotlin
plugins {
    id("cleanarch.android.feature")
}

android {
    namespace = "io.github.spoonart1.cleanarchwithagent.$feature-name.impl"
}

dependencies {
    implementation(projects.feature.`$feature-name`.api)
}
```

Do not add Compose, Hilt, coroutines or lifecycle dependencies by hand — the convention plugin
already provides them, and restating them causes version drift. If something genuinely missing is
needed by every feature, add it to `AndroidFeatureConventionPlugin` instead of to one module.

**If this feature navigates to another feature, depend on that feature's `api` module only.**
Depending on another feature's `impl` is the one rule that must never be broken; step 8 verifies it.

## 4. The route, in `api`

`api/src/main/kotlin/.../$feature-name/api/${Feature}Route.kt`:

```kotlin
package io.github.spoonart1.cleanarchwithagent.$feature-name.api

/** Navigation route for the $feature-name feature. Depend on this module to navigate here. */
const val ${FEATURE}_ROUTE = "$feature-name"
```

Keep this module free of anything else — no models, no interfaces, no Compose. The moment it
contains implementation detail, the api/impl split stops buying anything.

## 5. The UI state and ViewModel, in `impl`

One immutable state type exposed as a `StateFlow`. Model loading/content/error as a sealed interface
so an impossible combination cannot be represented:

```kotlin
sealed interface ${Feature}UiState {
    data object Loading : ${Feature}UiState
    data class Content(val items: List<${Feature}Item>) : ${Feature}UiState
    data class Error(val message: String) : ${Feature}UiState
}

@HiltViewModel
class ${Feature}ViewModel @Inject constructor(
    // inject repositories from core:data here
) : ViewModel() {

    private val _uiState = MutableStateFlow<${Feature}UiState>(${Feature}UiState.Loading)
    val uiState: StateFlow<${Feature}UiState> = _uiState.asStateFlow()
}
```

Rules that apply here:

- Use a UI-specific model (`${Feature}Item`), not a domain or database type, so Compose sees a
  stable type and the layers stay decoupled.
- Never catch `CancellationException`. A bare `catch (e: Exception)` around a suspending call
  swallows it and breaks structured concurrency — catch a narrower type, or rethrow it explicitly.
- Collect flows with `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Loading)`
  rather than launching a collector that outlives the subscriber.

## 6. The screen, in `impl`

Split the screen in two: a stateful composable that reads the ViewModel, and a stateless one that
takes the state and lambdas. Only the stateless one can be previewed and tested cheaply.

```kotlin
@Composable
fun ${Feature}Screen(viewModel: ${Feature}ViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ${Feature}Screen(uiState = uiState)
}

@Composable
internal fun ${Feature}Screen(uiState: ${Feature}UiState) { /* render each state */ }
```

Use stable keys in any `LazyColumn` (`items(list, key = { it.id })`), and components from
`core:designsystem` rather than new one-off ones.

## 7. A ViewModel test

Put it in `impl/src/test/...`. Use fakes from `core:testing` — **not** mocks. `core:testing` is
already on the test classpath via the convention plugin, and it brings JUnit, Turbine and
`kotlinx-coroutines-test` with it.

Assert the state transition, not just the final value:

```kotlin
@Test
fun `emits content once loaded`() = runTest {
    val viewModel = ${Feature}ViewModel(Fake${Feature}Repository())

    viewModel.uiState.test {
        assertEquals(${Feature}UiState.Loading, awaitItem())
        assertTrue(awaitItem() is ${Feature}UiState.Content)
    }
}
```

A test that only checks the happy path is not enough — cover at least the error case too.

## 8. Verify

All four must pass. Do not report success until they do:

```bash
./gradlew :feature:$feature-name:impl:test          # the new test actually runs
./gradlew build test                                 # nothing else broke
./gradlew projects | grep $feature-name              # both modules registered
./gradlew :feature:$feature-name:impl:dependencies --configuration debugCompileClasspath | grep feature
```

The last command is the architectural check: its output must show this feature's own `api`, plus
other features' `api` modules if navigated to — and **no other feature's `impl`**. If an `impl`
appears, fix the dependency before continuing.

If `build` fails inside `build-logic`, re-read the AGP 9 section of `CLAUDE.md` before changing
anything: AGP 8 examples found online do not compile against this build.

## 9. Navigation registration

Features register their navigation through Hilt multibindings (`@IntoSet`), so that adding a feature
never requires editing a shared list in `app`.

**Check whether `app` already has the navigation assembly before doing this step.** If
`app/src/main/kotlin/.../navigation/` does not exist yet, that infrastructure has not been built
(it lands in Phase 4 — see `CLAUDE.md`). In that case: stop, leave the feature without navigation
wiring, and tell the maintainer it needs hooking up once the assembly exists. Do not invent a
parallel registration mechanism, and do not add a manual entry to a central list — either would
defeat the design.

Once the assembly exists, follow the pattern used by `feature:checklists:impl`; mirror it exactly
rather than designing a new one.

## Report back

State plainly: which files were created, the output of the four verification commands, whether
navigation was wired or deferred, and any rule you could not satisfy. If something was left
incomplete, say so — do not describe a partially wired feature as finished.

---

*Verified 2026-09-28 by generating a throwaway `reports` feature: both tests executed
(`tests="2" skipped="0" failures="0"`), `./gradlew build test` passed, and the boundary check showed
only `:feature:reports:api`. Steps 1–8 are known to work; step 9 is untested because the navigation
assembly does not exist yet.*
