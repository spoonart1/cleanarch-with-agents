# Code conventions

The single source of truth for this project's conventions. `CLAUDE.md` and the agents in
`.claude/agents/` point here rather than restating the rules.

Most rules are **enforced by tooling**: `./gradlew detektAll` and
`./gradlew jacocoCoverageVerificationAll` fail the build, and CI runs both. Where a rule is *not*
machine-checked, it says so; those are the ones a reader has to catch.

Detekt config is `config/detekt/detekt.yml`. It layers over detekt's defaults, and every deviation
carries a comment. **Do not report a deviation the config documents as intentional**:
`ImportOrdering`, `Indentation`, and the `Exception` omission in `TooGenericExceptionCaught`.

---

## 1. Naming

### Booleans read as a question

`isLoading`, `hasItems`, `canRetry`. Never a bare noun like `loading` or `deleted`.

- Allowed prefixes: the `allowedPrefixes` list in
  `tools/detekt-rules/.../detekt/BooleanPropertyNaming.kt` (currently `is`, `has`, `can`, `should`,
  `are`, `was`, `were`, `will`, `does`, `did`). **Read that list, not this one.**
- `has`, `can` and `should` are correct. Never "correct" them to `is`.
- The prefix must be followed by an uppercase letter: `island` is flagged, `isLoading` is not.
- Wire and database models keep the server field name via `@SerialName("deleted") val isDeleted`.
  The rule governs the Kotlin name only; never change the serialised name.
- **Gap:** the custom rule sees only *declared* types. `val done = true` is not caught by tooling
  (type resolution is off, §6). Catch it by eye.
- Exception: names that override a framework or library API.

### Test names read as a sentence

`test <functionName> when <condition> should <expected result>`, as a backtick name:

```kotlin
fun `test getName when success should return true`()
```

- All three keywords are literal and required: `test`, `when`, `should`.
- Enforced by `NAME_PATTERN` in `tools/detekt-rules/.../detekt/TestFunctionNaming.kt`, currently
  `^test\s+\S.*\swhen\s+\S.*\sshould\s+\S.*$`. Each segment must be non-empty.
- `<functionName>` is the *function under test*, not the class. For a Flow property use the property
  name: `test uiState when the checklist is missing should expose an error`.
- Fires on `@Test`, `@ParameterizedTest` and `@RepeatedTest` under `**/test/**` and
  `**/androidTest/**`.
- Instrumented tests cannot use spaces in method names; use the project's chosen format there.

---

## 2. Structure

### Test bodies are Given / When / And / Then

Comment markers, in order, **with a space after the slashes** (`// Given`, not `//Given`):

| Marker | Purpose |
|---|---|
| `// Given` | prepare the properties |
| `// When` | perform the action |
| `// And` | a further setup step or action, only when there is one |
| `// Then` | verify or assert |

- **Not machine-checked.** A detekt rule for this false-positives on table-driven and Turbine tests.
  Nothing will catch a mistake for you.
- Put the marker where the action is. With Turbine the collection block often wraps action and
  assertion: mark the triggering call `// When` and the assert `// Then`, not the whole block.

### Functions stay under 20 lines

Enforced by `complexity:LongMethod` (threshold 20).

- **Count the way detekt counts:** blank and comment lines included, signature line included. Near
  the threshold, trust `detektAll` over your own count.
- Exempt: `@Composable` and `@Preview` (`ignoreAnnotated`), and test sources
  (`excludes: ['**/test/**', '**/androidTest/**']`).
- Extract a genuinely cohesive step. **A split made only to get under the number is worse than the
  long function.**

`MagicNumber` and `MaxLineLength` are also relaxed for test sources. Keep the fixture close to the
assertion it explains.

---

## 3. Testing

### Fakes over mocks

CLAUDE.md rule 5. **No mocking library is on any classpath, deliberately.** A hard constraint.

- Use the shared fakes, or write a small fake that records what it was asked to do.
- Assert on resulting state, not on which methods were called.

### Where the fixtures live

Test dependencies are **not** wired uniformly:

- **Feature modules** (`cleanarch.android.feature`) get `:core:testing` automatically:
  `testChecklist()`, `testChecklistItem()`, `FakeChecklistRepository`, `MainDispatcherRule`.
- **`core:*` modules do not.** They define local fixtures; `core:sync` has `SyncTestFixtures.kt`
  with `checklistEntity()`, `itemEntity()`, `outboxEntity()`, `FakeNetwork`, `FakeSyncTokenStore`.
  Never assume the shared one is importable.
- Every Android library gets `junit`, `kotlinx-coroutines-test` and `turbine` from the library
  convention plugin.

### How to write them

- **Drive a real in-memory Room database** where the behaviour under test *is* the database's.
  `core:data` and `core:sync` do this: the point is that a row and its outbox entry land in one
  transaction, which a faked DAO cannot demonstrate.
- **Turbine for Flow assertions** (`flow.test { }`), `runTest` for suspending code. No
  `Thread.sleep`. Do not read a `StateFlow`'s `.value` right after an action when the emission is
  asynchronous.
- **`MainDispatcherRule`** in ViewModel tests.
- **Cover the branch, not just the happy path:** success, each failure or empty case, and any
  boundary the code names (an attempt limit, a blank input, a missing parent row).
- **One behaviour per test.** A name that needs "and" is usually two tests.
- **Assert the whole mapped model** in a mapper test, not one field.
- Add an assertion message where the failure would otherwise be cryptic.

### Never weaken a test to make it pass

A failing test means the test is wrong, your understanding is wrong, or there is a real bug. Work
out which and say so. Deleting the assertion is never the answer.

If a test needs a production change (a final class, an inline dependency, a hardcoded dispatcher),
**stop and report it as a design finding.** No reflection, no `Thread.sleep`, no widened
visibility. `@VisibleForTesting` or `private` → `internal` only with explicit approval.

**Instrumented tests are the maintainer's to run.** Write them when asked; never execute them.

---

## 4. Coverage

Business logic is gated at **90% line coverage**.

- The gated set is `businessLogicIncludes` in
  `build-logic/convention/src/main/kotlin/io/github/spoonart1/cleanarchwithagent/buildlogic/Jacoco.kt`
  (currently `*UseCase*`, `*Repository*`, `*RepositoryImpl*`, `*ViewModel*`, `*Mapper*`, `*Manager*`,
  `*Controller*`, `*Engine*`, `*Handler*`, `*Validator*`, `*DataSource*`). **Read that list, not this
  one.**
- Excluded: generated code (Hilt, Room, serialization), Compose screens and previews, synthetic
  classes, and thin framework adapters with no decision of our own (`RetrofitNetworkDataSource`,
  `WorkManagerSyncScheduler`).

### Two numbers, easily confused

| Task | Measures | Typical |
|---|---|---|
| `jacocoTestReportAll` | the whole module, informative | routinely **below** 90% |
| `jacocoCoverageVerificationAll` | the business-logic subset, **the gate** | must be ≥ 90% |

A module reporting 67% is **not** a finding if the gate passes.

### Chase coverage honestly

- **Exclusions are for code with no logic, never for code that is merely hard to test.** Hard to
  test *and* has logic is a design signal worth reporting.
- Never add a `coverageExclusions` entry to make a failing gate pass.
- A test that calls a function and asserts nothing meaningful passes the gate and protects nothing.
  Find what is genuinely untested, not what is cheapest to cover.

---

## 5. Architecture and code style

The rules that must not be broken live in **CLAUDE.md § "Rules that must not be broken"**.

**Composables and state**

- Split into a stateful wrapper that reads the ViewModel and a stateless one taking state plus
  lambdas. Only the stateless one gets a `@Preview`.
- ViewModels expose one immutable UI state as a `StateFlow`; screens render it and emit events up.
- UI-specific models in the UI layer, never domain or database types.
- Stable keys in lazy lists.
- Map between database, network and domain models in `core:data`; those types must not leak past it.

**Modern Android practice**

Apply where it genuinely improves the code. **If the only argument is "it is newer", it is not a
finding.**

- Superseded APIs: `AsyncTask`, `Handler(Looper)` for scheduling, `startActivityForResult`,
  `onBackPressed`, `LocalBroadcastManager`, `findViewById` beside view binding or Compose.
- Callback or RxJava chains where surrounding code already uses coroutines and Flow. Not a reason
  to migrate an RxJava codebase mid-change.
- `LiveData` in new code; this project standardises on `StateFlow`.
- Compose: hoist state out of render-only composables, `derivedStateOf` for per-frame
  recomputation, correctly keyed `LaunchedEffect`, stable parameter types.
- Blocking calls inside `suspend` functions without a dispatcher; hardcoded dispatchers (detekt
  `InjectDispatcher`).

### Comments when an agent writes code

**Off by default.** Switch: `cleanarch.aiComments` in `gradle.properties`, shipped `false`. Turn on
for one run with `-Pcleanarch.aiComments=true` or by saying so in the prompt. **The build does not
read it**; it is a flag for agents, kept in the repo so it survives sessions and is greppable.

| | Off (default) | On |
|---|---|---|
| KDoc header on a new or substantially rewritten class/file | no | one block: what the type is responsible for and, if not obvious, why it exists |
| Inline comments on non-obvious logic | no | on the branch, workaround or ordering constraint that is genuinely surprising |

A comment restating the code goes stale. Code that needs a comment usually wants a rename or split
instead; prefer that in either mode.

**Always required, whatever the switch says:**

- `// Given` / `// When` / `// And` / `// Then` markers (§2).
- The comment on every deviation in `config/detekt/detekt.yml` and every version constraint in
  `gradle/libs.versions.toml`.
- A reason on any `@Suppress`, `TODO`, or deliberate-looking oddity.
- A public API's existing KDoc. Edit the declaration, update the KDoc; stale docs are worse than none.

When off and something would genuinely trip a reader, **say it in your response, not in the file.**

**Never recommend or use an API without confirming it exists in the version this project pins.**
Check `gradle/libs.versions.toml` and the module's `build.gradle.kts`. AGP 9, Kotlin 2.4 and
`compileSdk` 37 invalidate much widely-repeated advice.

---

## 6. Tooling facts that change the rules

Full explanations in CLAUDE.md § "Detekt and JaCoCo on AGP 9".

- **Detekt runs with type resolution off** (embedded Kotlin 2.0.21 vs project 2.4.20). Any rule
  needing type info does not fire; see the boolean gap in §1.
- **No detekt baseline file, deliberately.**
- **`-PdetektAutoCorrect=true` rewrites source files.** Local use only. Never pass it from an agent
  or CI.
- **Only aggregate tasks exist at the root:** `detektAll`, `jacocoTestReportAll`,
  `jacocoCoverageVerificationAll`. Bare `detekt` or `jacocoTestReport` exist per module only
  (`:core:sync:detekt`); from the root they fail and silently skip the check.

---

## 7. Security and personal data

- Never commit a credential, token, or key, including in test fixtures and including
  expired-looking ones. Use an obvious placeholder.
- Never use real personal data as test data.
- Keep personal data (names, phones, emails, precise location, IDs, payment, health) out of `Log`,
  crash reports, analytics events and exception messages.
- Collect no more personal data than the feature needs.
- When data goes to a new third-party SDK or endpoint, name the data and the destination and flag
  it for the team. Cross-border transfer often needs an assessment first. Report the technical fact;
  leave the legal conclusion to the team.
- **A real credential in the repo or its history is compromised.** Revoke and rotate immediately
  (rewriting history does not un-leak it), then follow the organisation's incident process.

Commands are in CLAUDE.md § "Commands".
