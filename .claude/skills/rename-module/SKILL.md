---
name: rename-module
description: Rename this template's package, Android namespace, applicationId, convention plugin ids and root project name to a new base name chosen by the user. Use when asked to rebrand the template, change the package or application id, or make a fresh clone your own.
disable-model-invocation: true
argument-hint: "[new-package-name]"
arguments: [new-package-name]
allowed-tools:
  - Read
  - Write
  - Edit
  - Grep
  - Glob
  - Bash(./gradlew *)
  - Bash(git *)
  - Bash(mkdir *)
  - Bash(ls *)
  - Bash(find *)
  - Bash(grep *)
---

# Rename this project to `$new-package-name`

A fresh clone of this template carries the maintainer's identity in five separate places. This
skill moves all five together, because moving four of them leaves a build that compiles and a
project that still says someone else's name.

Work through the phases in order. **Phase 3 does not begin until the user has approved the plan
produced in phase 2.** A rename touches every file in the repo; an unreviewed one is not
recoverable by reading the diff afterwards.

## 1. Get the new name, and validate it

If `$new-package-name` was not supplied, ask for it. Ask in these words, because the two things
are usually the same and the user should be told so rather than made to guess:

> What is the new module structure name — the Kotlin package and Android applicationId? They are
> normally identical. Example: `com.android.cleanarch`

Then validate. The name must be a valid Java/Kotlin package and a valid Android application id:

- **lowercase only** — no capitals
- **no spaces**
- **no dashes (`-`)**
- segments separated by `.`, at least two of them
- each segment starts with a letter, then letters or digits or `_`
- no segment is a Java reserved word (`in`, `is`, `object`, `fun`, `val`, `class`, `package`, …)

```bash
NEW_PKG="com.android.cleanarch"
printf '%s' "$NEW_PKG" | grep -Eq '^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$' \
  && echo "valid" || echo "INVALID"
```

If it fails, **do not silently normalise it.** Say exactly which rule it broke, propose a corrected
form, and ask the user to confirm that form before continuing. Silently turning `Com.My-App` into
`com.myapp` guesses at intent, and the name ends up in a published application id where a wrong
guess is expensive to undo later.

Derive the other values from the confirmed name and state them back:

| Value | Derived as | Example |
|---|---|---|
| `NEW_PKG` | what the user gave | `com.android.cleanarch` |
| `NEW_PKG_PATH` | dots → `/` | `com/android/cleanarch` |
| `NEW_PLUGIN_PREFIX` | last segment of `NEW_PKG` | `cleanarch` |
| `NEW_ROOT_NAME` | ask, or PascalCase of the last segment | `CleanArch` |

The old values in a stock clone are:

| Value | Current |
|---|---|
| `OLD_PKG` | `io.github.spoonart1.cleanarchwithagent` |
| `OLD_PKG_PATH` | `io/github/spoonart1/cleanarchwithagent` |
| `OLD_PLUGIN_PREFIX` | `cleanarch` |
| `OLD_ROOT_NAME` | `CleanArchWithAgent` |

Confirm `OLD_PKG` against the working tree rather than trusting this table — someone may have
renamed already:

```bash
grep -n 'applicationId' app/build.gradle.kts
```

**If `NEW_PLUGIN_PREFIX` equals `OLD_PLUGIN_PREFIX`** (both `cleanarch`, as in the example above),
skip the plugin-id half of phase 4 entirely and say so. Rewriting an identifier to itself is a
no-op that only risks corrupting the file.

## 2. Produce the plan, then stop

Before editing anything, scan the tree and present the user a concrete list of what will change.
Scan, do not recall — the counts must come from this working tree:

```bash
git ls-files | grep -v '^\.git' > /tmp/tracked.txt

# every file containing the old package, with a per-file hit count
grep -c 'io\.github\.spoonart1\.cleanarchwithagent' $(cat /tmp/tracked.txt) 2>/dev/null \
  | grep -v ':0$'

# directory trees that must move
find . -type d -path "*/io/github/spoonart1" -not -path "*/build/*"

# convention plugin id registrations and their use sites
grep -rn '"cleanarch\.' build-logic/convention/build.gradle.kts
grep -rln 'id("cleanarch\.' --include="*.kts" . | grep -v '/build/'
```

Present the plan grouped by kind, with counts. In a stock clone as of 2026-09-29 this is 101
tracked files containing the old package and 23 directory trees to move; use your own scan's
numbers, not these, and treat a large divergence as a sign the tree has already been renamed.
The groups:

1. **Kotlin/Java sources** — `package` and `import` lines in `app`, all seven `core:*` modules,
   both features' `api` and `impl`, `tools/detekt-rules`, and the `buildlogic` helpers under
   `build-logic/convention/src/main/kotlin/$OLD_PKG_PATH/`.
2. **Directory trees** — 23 `…/src/{main,test,androidTest}/kotlin/$OLD_PKG_PATH` paths move to
   `$NEW_PKG_PATH`. These are `git mv`, not text edits.
3. **Android namespaces** — one `namespace =` per module build file (13 of them), plus
   `applicationId` in `app/build.gradle.kts`. The `applicationIdSuffix = ".debug"` line is not a
   name and does not change.
4. **Convention plugin ids** — the nine `cleanarch.*` ids registered in
   `build-logic/convention/build.gradle.kts`, every `id("cleanarch.…")` use site in module build
   files, and the two applied from `subprojects { }` in the root `build.gradle.kts`.
5. **Root project name** — `rootProject.name` in `settings.gradle.kts`.
6. **Build-logic internals** — the `buildlogic` package of `Jacoco.kt`, `KotlinAndroid.kt` and
   `KotlinCompiler.kt`, and the `group =` line in `tools/detekt-rules/build.gradle.kts`.
7. **Docs and config** — `CLAUDE.md`, `README.md`, `.claude/conventions.md`,
   `.claude/agents/*.md`, `.claude/skills/new-feature-module/SKILL.md` (its `PKG=` line and every
   namespace example), `config/detekt/detekt.yml`, and any `proguard-rules.pro` naming a class.

Also name what is deliberately **not** changing, so the user is not surprised by what survives:

- the git remote, and the GitHub repo name
- the on-disk checkout directory (`D:\Spoonart-Repo\CleanArchWithAgent`)
- `applicationIdSuffix = ".debug"`
- anything under a `build/` directory — regenerated, and never edited by hand

Then **stop and ask for approval.** Show the four derived values and the grouped counts, and wait.

## 3. Branch and rename

Never rename on a dirty tree — the verification in phase 5 depends on `git status` being readable
as "only the rename".

```bash
git status --porcelain     # must be empty; if not, stop and ask
git checkout -b rename/$NEW_PLUGIN_PREFIX
```

**Move the directories first, then rewrite the text.** Doing it the other way round leaves Kotlin
files whose `package` declaration no longer matches their path, and the compiler error points at
the package line rather than at the missing move.

```bash
OLD_PKG_PATH=io/github/spoonart1/cleanarchwithagent
NEW_PKG_PATH=com/android/cleanarch

for dir in $(find . -type d -path "*/$OLD_PKG_PATH" -not -path "*/build/*"); do
  parent="${dir%/$OLD_PKG_PATH}"
  mkdir -p "$parent/$(dirname $NEW_PKG_PATH)"
  git mv "$dir" "$parent/$NEW_PKG_PATH"
done
```

If the old and new paths share a leading segment (`com/android/…` → `com/android/…`), that loop
can collide. Check the shape before running it, and if they overlap, move through a temporary path
rather than forcing it.

The move leaves empty ancestor directories behind — verified: moving `…/kotlin/io/github/spoonart1`
leaves `…/kotlin/io/github` in place. Gradle 9 will not fail on them, but a stray `io/` in a
template is exactly the kind of leftover this skill exists to prevent. Git does not track empty
directories, so remove them from the filesystem:

```bash
find . -type d -empty -not -path "./.git/*" -not -path "*/build/*" -delete
```

Run it twice — deleting `io/github` is what makes `io/` empty.

Now the text. Package string first, then plugin ids, then root name:

```bash
git ls-files -z | xargs -0 grep -lZ 'io\.github\.spoonart1\.cleanarchwithagent' \
  | xargs -0 sed -i 's/io\.github\.spoonart1\.cleanarchwithagent/com.android.cleanarch/g'
```

Restrict the plugin-id rewrite to the word boundary — `cleanarch.android.library` must change
while an unrelated occurrence of the bare word must not:

```bash
git ls-files -z | xargs -0 sed -i \
  's/\bcleanarch\.\(android\|jvm\|detekt\|jacoco\)\b/myprefix.\1/g'
```

`rootProject.name` is a single line; edit it directly rather than with a global substitution.

**On Windows:** Gradle's daemon holds file locks, and a `git mv` of a directory it has open will
fail. Run `./gradlew --stop` before phase 3.

## 4. Re-scan for anything left behind

This is the step the user asked for by name, and it is not optional. A rename that misses one
file produces a build that compiles today and confuses someone in six months.

Every one of these must return **no output**:

```bash
# the old package, in any form
git ls-files -z | xargs -0 grep -n 'io\.github\.spoonart1' ; echo "---"
git ls-files -z | xargs -0 grep -n 'cleanarchwithagent' ; echo "---"

# the old plugin prefix (skip if the prefix was unchanged by design)
git ls-files -z | xargs -0 grep -n 'id("cleanarch\.' ; echo "---"
git ls-files -z | xargs -0 grep -n '"cleanarch\.' ; echo "---"

# the old root name
grep -rn 'CleanArchWithAgent' settings.gradle.kts ; echo "---"

# stale directory trees
find . -type d -path "*io/github/spoonart1*" -not -path "*/build/*"
find . -type d -empty -not -path "./.git/*" -not -path "*/build/*"
```

Search case-insensitively too, then judge each hit rather than rewriting it blind:

```bash
git ls-files -z | xargs -0 grep -in 'spoonart\|cleanarchwithagent'
```

A surviving hit is not automatically a bug. Legitimate ones include a `LICENSE` copyright holder,
a git remote URL in `README.md`, and the changelog or commit history. Class names built from the
old name — `CleanArchDatabase`, `CleanArchApplication`, `CleanArchNavHost`, `CleanArchTestRunner`
— are a judgement call: they are type names, not the package, and renaming them is a larger and
separate change. **List them for the user and ask**; do not decide unilaterally either way.

## 5. Build

A grep-clean tree is not a working tree. Run the real gate:

```bash
./gradlew --stop        # release Windows file locks from the pre-rename daemon
./gradlew clean
./gradlew build test
```

`build` assembles debug and release and runs lint; `test` runs every JVM unit test including the
Robolectric Compose and DAO tests. Both must pass. Then confirm the structure survived:

```bash
./gradlew projects                      # every module still registered
./gradlew detektAll                     # custom rules still resolve after the group rename
./gradlew :feature:settings:impl:dependencies --configuration debugCompileClasspath | grep feature
```

That last one is the architectural check from `CLAUDE.md` — no feature may see another feature's
`impl`. A rename should not change it, and if it does, something moved that should not have.

Do not run `connectedAndroidTest`. It needs an emulator, and the maintainer runs those.

### If the build fails

- **`Plugin [id: 'cleanarch.android.library'] was not found`** — the id was renamed in the module
  build files but not in `build-logic/convention/build.gradle.kts`, or the reverse. Both sides must
  move together.
- **`Unresolved reference` inside `build-logic`** — the `buildlogic` helper package moved but an
  importing convention plugin did not. Included builds compile separately, so the root build's
  success says nothing about this.
- **Package/directory mismatch** — a `git mv` was missed. Re-run the `find` from phase 4.
- **Anything mentioning AGP DSL shapes** — read the AGP 9 section of `CLAUDE.md` before changing
  a convention plugin. AGP 8 examples found online do not compile against this build.

## 6. Commit

One commit, conventional, with the rename in the subject:

```bash
git add -A
git commit -m "chore: rename package to com.android.cleanarch"
```

Do not push, and do not merge to `main` — that is the maintainer's call.

## Report back

State plainly:

- the four derived values, and the old ones they replaced
- how many files and directories changed, by group
- the output of every phase-4 scan — say "clean" only if it genuinely returned nothing
- the result of `./gradlew build test`, quoting the failure if it failed
- every leftover hit you left in place, and why
- every class name containing the old name that you did **not** rename

If the build did not pass, say so first. A rename reported as finished while the project does not
compile is worse than one reported as half done.
