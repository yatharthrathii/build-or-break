# Build or Break

Android app. Kotlin, Jetpack Compose, local first, no accounts, no analytics.
A routine alarm and reminder app that reshapes the day around what actually
happened.

## Read these before doing anything

`rules.md` is the constitution of this project. Read it at the start of every
session. It wins over anything else, including this file.

| File | Read it before |
|---|---|
| `rules.md` | Anything. Every session, first |
| `prd.md` | Adding or changing a feature |
| `techspec.md` | Writing code |
| `architecture.md` | Writing code. Contracts, flows, naming, the recipe |
| `schema.md` | Touching data or persistence |
| `design.md` | Touching UI |
| `appflow.md` | Building a screen or a notification |
| `implementationPlan.md` | Starting a milestone |
| `tracker.md` | Every session, and update it at the end |

## The five rules that get broken most often

1. **No em dash, no en dash.** Anywhere. Not in code, UI copy, docs, or commits.
2. **`:core:domain` has zero Android imports.** It is a pure JVM module.
3. **Never call `now()` directly.** Inject `TimeProvider`. Always.
4. **Nothing about this project may look AI generated.** That covers the UI
   (`rules.md` section 3 and `design.md`), the commit history (`rules.md`
   section 10) and the README (`rules.md` section 11). Most commits carry no
   body. No conventional commit prefixes. No emoji anywhere.
5. **Never gate reliability behind the paywall.** Alarms and export are free
   forever.

## Current state

M1 foundation is scaffolded but **the build has not been verified**. The Gradle
wrapper is missing and no Gradle CLI is installed on this machine. Nothing in M1
counts as done until `./gradlew build` is green. See the top of `tracker.md`.

**Phasing:** Phase 1 is build habits only and ships to the developer's own phone
first, not to Play. Break mode is Phase 2. Play Store work is deferred to stage
1c, after Phase 1 is feature complete. Nothing costs money before then.

**No machine learning anywhere.** Every adaptive behaviour is a median, an
average, a group by or a division, running in `:core:domain` with no network.
See `techspec.md` section 5b before reaching for an API.

## Project layout

```
app/            UI shell, feature packages, DI wiring
core/model      pure JVM, data types only
core/domain     pure JVM, the timeline engine
core/common     pure JVM, TimeProvider, AppDispatchers, Outcome
core/testing    pure JVM, shared test fixtures
core/data       Room, DataStore, repository implementations
core/designsystem  tokens, theme, shared composables
scheduler/      alarms, notifications, foreground service, receivers
billing/        entitlements, isolated billing SDK
widget/         Glance widget
benchmark/      macrobenchmark and baseline profile
build-logic/    convention plugins
```

## Commands

```
./gradlew build                  everything
./gradlew qualityCheck           spotless, detekt, all unit tests
./gradlew :core:domain:test      the engine suite, must stay under 1 second
./gradlew spotlessApply          fix formatting
./gradlew assembleDebug          debug apk
./gradlew :app:generateBaselineProfile    needs a connected device
```
