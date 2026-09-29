# Code conventions

The single source of truth for this project's conventions. `CLAUDE.md` and the agents in
`.claude/agents/` point here rather than restating the rules, so there is one place to edit and
nothing to keep in sync.

Most of these are **enforced by tooling**, not merely documented — `./gradlew detektAll` and
`./gradlew jacocoCoverageVerificationAll` fail the build on a violation, and CI runs both. Where a
rule is *not* machine-checked, it says so, because those are the ones a human or an agent has to
catch by reading.

The detekt configuration is `config/detekt/detekt.yml`. It layers over detekt's defaults rather than
replacing them, and every deviation in it carries a comment explaining why. **Do not report a
deviation the config documents as intentional** — `ImportOrdering`, `Indentation`, and the
`Exception` omission in `TooGenericExceptionCaught` are all deliberate.

---

## 1. Naming

### Booleans read as a question

`isLoading`, `hasItems`, `canRetry` — never a bare noun like `loading` or `deleted`.

- The allowed prefixes are the `allowedPrefixes` list in
  `tools/detekt-rules/.../detekt/BooleanPropertyNaming.kt` — currently `is`, `has`, `can`, `should`,
  `are`, `was`, `were`, `will`, `does`, `did`. **Read that list rather than this one**, which will
  drift.
- `has`, `can` and `should` are correct and must not be "corrected" to `is`. Forcing `isHasItems`
  would be worse than the problem the rule solves.
- The prefix must be followed by an uppercase letter, so `island` is flagged and `isLoading` is not.
- Wire and database models keep their server field name via
  `@SerialName("deleted") val isDeleted: Boolean`. The Kotlin name is what the rule governs; never
  change the serialised name to satisfy it.
- **Gap worth knowing:** the custom rule only sees *declared* types (`val done: Boolean`). detekt's
  own `naming:BooleanPropertyNaming` covers inferred ones but needs type resolution, which is off in
  this project (see §6). So `val done = true` is **not** caught by tooling — catch it by eye.
- Exception: names you cannot change because they override a framework or library API.

### Test names read as a sentence

`test <functionName> when <condition> should <expected result>`, as a Kotlin backtick name:

```kotlin
fun `test getName when success should return true`()
```

- All three keywords are literal and required: `test`, `when`, **and `should`**.
- The enforcing regex is `NAME_PATTERN` in `tools/detekt-rules/.../detekt/TestFunctionNaming.kt` —
  currently `^test\s+\S.*\swhen\s+\S.*\sshould\s+\S.*$`. Each segment must be non-empty, so
  `test when should` fails.
- `<functionName>` is the *function under test*, not the class. For a Flow property, the property
  name is right: `test uiState when the checklist is missing should expose an error`.
- Fires on `@Test`, `@ParameterizedTest` and `@RepeatedTest`, scoped to `**/test/**` and
  `**/androidTest/**`.
- The point is that a CI failure says what broke without anyone opening the file.
- Instrumented tests under `src/androidTest` cannot use spaces in method names; use the project's
  chosen format there.

---

## 2. Structure

### Test bodies are Given / When / And / Then

Marked with those comments, in that order, **with a space after the slashes** (`// Given`, not
`//Given`):

| Marker | Purpose |
|---|---|
| `// Given` | prepare the properties |
| `// When` | perform the action |
| `// And` | a further setup step or action — only when there is one |
| `// Then` | verify or assert |

- **Not machine-checked.** A detekt rule for this produces false positives on every table-driven or
  Turbine-based test, so it stays a convention. Nothing will catch a mistake for you, which is also
  why a false positive here is costly — there is no tool output to correct you.
- Put the marker where the action genuinely is. With Turbine the collection block often wraps both
  the action and the assertion: mark the call that triggers the change as `// When` and the assert as
  `// Then`, rather than labelling the whole block.

### Functions stay under 20 lines

Enforced by `complexity:LongMethod` (threshold 20).

- **Count the way detekt counts.** It does *not* exclude blank or comment-only lines, and it includes
  the signature line. Do not subtract them, or your count will disagree with CI. Within a line or two
  of the threshold, trust `detektAll` over your own count.
- Exempt: `@Composable` and `@Preview` (`ignoreAnnotated`). A declarative UI tree is one expression,
  and splitting it to satisfy a line count makes it harder to read.
- Exempt: test sources (`excludes: ['**/test/**', '**/androidTest/**']`). Given/When/Then with a
  realistic fixture routinely runs longer, and extracting the setup moves it away from the assertion
  it explains.
- Prefer extracting a genuinely cohesive step. **A split made only to get under the number is worse
  than the long function.**

`MagicNumber` and `MaxLineLength` are also relaxed for test sources. Readability still matters — keep
the fixture close to the assertion it explains.

---

## 3. Testing

### Fakes over mocks

CLAUDE.md rule 5. **There is no mocking library on any classpath in this project, and that is
deliberate** — this is a hard constraint, not a style preference.

- Use the shared fakes, or write a small fake that records what it was asked to do.
- Assert on the resulting state, not on which methods were called.

### Where the fixtures actually live

Test dependencies are **not** wired uniformly. Getting this wrong produces tests that do not compile:

- **Feature modules** (`cleanarch.android.feature`) get `:core:testing` automatically — so
  `testChecklist()`, `testChecklistItem()`, `FakeChecklistRepository` and `MainDispatcherRule` are
  available.
- **`core:*` modules do not.** They declare their own test dependencies and define local fixtures:
  `core:sync` has `SyncTestFixtures.kt` with `checklistEntity()`, `itemEntity()`, `outboxEntity()`,
  `FakeNetwork` and `FakeSyncTokenStore`. Use the local fixture; never assume the shared one is
  importable.
- Every Android library gets `junit`, `kotlinx-coroutines-test` and `turbine` from the library
  convention plugin.

### How to write them

- **Drive a real in-memory Room database** where the behaviour under test *is* the database's.
  `core:data` and `core:sync` both do this deliberately: the point of those tests is that a row and
  its outbox entry land in one transaction, which a faked DAO could not demonstrate.
- **Turbine for Flow assertions** (`flow.test { }`), `runTest` for suspending code. No
  `Thread.sleep`. Do not assert on a `StateFlow`'s `.value` immediately after an action when the
  emission is asynchronous — that is the classic flaky test.
- **`MainDispatcherRule`** in ViewModel tests, so `viewModelScope` runs on the test dispatcher.
- **Cover the branch, not just the happy path**: the success case, each failure or empty case, and
  any boundary the code names explicitly (an attempt limit, a blank input, a missing parent row). The
  failure paths are where the bugs are.
- **One behaviour per test.** A test whose name needs "and" is usually two tests.
- **Assert the whole mapped model** in a mapper test rather than one field — a mapper bug that drops
  a field is exactly what the test is for, and checking only `text` will not catch it.
- Add an assertion message where the failure would otherwise be cryptic.

### Never weaken a test to make it pass

If a test fails there are three possibilities: the test is wrong, your understanding of the code is
wrong, or you found a real bug. Work out which and say so. Deleting the assertion is never the
answer.

If a test cannot be written without a production change — a final class, an inline dependency, a
hardcoded dispatcher — **stop and report it as a design finding** rather than reaching for
reflection, `Thread.sleep`, or a widened visibility modifier. `@VisibleForTesting` or `private` →
`internal` only with explicit approval.

**Instrumented tests are the maintainer's to run.** `connectedAndroidTest` needs a running emulator.
Write them when asked; never execute them.

---

## 4. Coverage

Business logic is gated at **90% line coverage**.

- The gated set is `businessLogicIncludes` in
  `build-logic/convention/src/main/kotlin/io/github/spoonart1/cleanarchwithagent/buildlogic/Jacoco.kt`
  — currently `*UseCase*`, `*Repository*`, `*RepositoryImpl*`, `*ViewModel*`, `*Mapper*`, `*Manager*`,
  `*Controller*`, `*Engine*`, `*Handler*`, `*Validator*`, `*DataSource*`. **Read that list rather
  than this one.**
- Excluded: generated code (Hilt, Room, serialization), Compose screens and previews, synthetic
  classes, and thin framework adapters such as `RetrofitNetworkDataSource` and
  `WorkManagerSyncScheduler` that contain no decision of our own.

### Two numbers, easily confused

| Task | Measures | Typical |
|---|---|---|
| `jacocoTestReportAll` | the whole module — informative | routinely **below** 90% |
| `jacocoCoverageVerificationAll` | the business-logic subset — **the gate** | must be ≥ 90% |

A module reporting 67% is **not** a finding if the gate passes. Gating on Compose UI would measure
rendering rather than correctness, and a gate people learn to ignore is worse than none.

### Chase coverage honestly

- **Exclusions are for code with no logic, never for code that is merely hard to test.** A class that
  is hard to test *and* has logic is a design signal worth reporting.
- Never add a `coverageExclusions` entry to make a failing gate go away.
- A test written only to move a percentage — calling a function and asserting nothing meaningful —
  passes the gate and protects nothing. Find what is genuinely untested, not what is cheapest to
  cover.

---

## 5. Architecture and code style

The architectural rules that must not be broken (feature isolation, `core:model` purity, thin `app`,
`CancellationException`, single-transaction writes) live in **CLAUDE.md § "Rules that must not be
broken"** and are not repeated here.

**Composables and state**

- Split composables into a stateful wrapper that reads the ViewModel and a stateless one that takes
  state plus lambdas. Only the stateless one is previewable and cheap to test; give it a `@Preview`.
- ViewModels expose one immutable UI state as a `StateFlow`; screens render it and emit events up.
- Use UI-specific models in the UI layer, not domain or database types directly.
- Use stable keys in lazy lists.
- Map between database, network and domain models in `core:data` — those types must not leak past it.

**Modern Android practice**

Apply where it genuinely improves the code. Say what the alternative buys; **if the answer is only
"it is newer", it is not a finding.**

- Superseded APIs: `AsyncTask`, `Handler(Looper)` for scheduling, `startActivityForResult`,
  `onBackPressed`, `LocalBroadcastManager`, `findViewById` in a module that otherwise uses view
  binding or Compose.
- Callback or RxJava chains where the surrounding code already uses coroutines and Flow — not a
  reason to migrate an RxJava codebase mid-change.
- `LiveData` in new code; this project standardises on `StateFlow`.
- Compose: state hoisted out of a composable that only renders, `derivedStateOf` for per-frame
  recomputation, correctly keyed `LaunchedEffect`, stable parameter types.
- Blocking calls inside `suspend` functions without a dispatcher, and dispatchers hardcoded rather
  than injected — the latter makes a function untestable and is flagged by detekt's
  `InjectDispatcher`.

**Never recommend or use an API without confirming it exists in the version this project pins.**
Check `gradle/libs.versions.toml` and the module's `build.gradle.kts` first. This project pins AGP 9,
Kotlin 2.4 and `compileSdk` 37, where a good deal of widely-repeated advice is simply wrong. A
confident wrong recommendation in a starter template teaches the wrong thing to everyone who clones
it.

---

## 6. Tooling facts that change the rules

Non-obvious constraints that make a convention behave differently than expected. The full
explanations are in CLAUDE.md § "Detekt and JaCoCo on AGP 9".

- **Detekt runs with type resolution off.** It embeds Kotlin 2.0.21 while this project compiles with
  2.4.20, and feeding 2.4 sources to the 2.0 solver produces spurious unresolved-reference findings.
  Consequence: any rule needing type info does not fire — see the boolean gap in §1.
- **There is no detekt baseline file, deliberately.** A template that ships a baseline teaches
  contributors that suppression is the normal response to a finding.
- **`-PdetektAutoCorrect=true` rewrites source files.** It is opt-in for local use and off in CI, so
  a build never edits its own checkout to go green. Never pass it from an agent or a CI job.
- **Only aggregate tasks exist at the root**: `detektAll`, `jacocoTestReportAll`,
  `jacocoCoverageVerificationAll`. There is no root `detekt` or `jacocoTestReport` — those exist per
  module (`:core:sync:detekt`). Invoking the bare name from the root fails and silently skips the
  check.

---

## 7. Security and personal data

- Never commit a credential, token, or key — including in test fixtures, and including
  expired-looking ones. Use an obvious placeholder.
- Never use real personal data as test data. Invent names, numbers and addresses.
- Keep personal data (names, phone numbers, emails, precise location, IDs, payment, health) out of
  `Log`, crash reports, analytics events and exception messages.
- Collect no more personal data than the feature needs.
- Name the data and the destination when it goes to a new third-party SDK or endpoint, and flag it
  for the team to assess. A cross-border transfer often needs an assessment before it ships. Report
  the technical fact; leave the legal conclusion to the team and their counsel.
- **If a real credential is found in the repo or its history:** treat it as compromised, revoke and
  rotate it immediately — rewriting history does not un-leak a value already pushed or cloned — then
  follow the organisation's security incident process.

---

## Commands

```bash
./gradlew build                          # assembles debug+release, runs lint
./gradlew test                           # all JVM unit tests
./gradlew detektAll                      # static analysis, every module
./gradlew jacocoTestReportAll            # coverage reports (informative)
./gradlew jacocoCoverageVerificationAll  # the 90% gate (authoritative)

./gradlew :module:testDebugUnitTest --tests "*ClassName*"   # one class, while iterating
./gradlew detektAll -PdetektAutoCorrect=true                # local formatting fix only — never in CI
```
