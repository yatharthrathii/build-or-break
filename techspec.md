# Build or Break - Technical Specification

Read `rules.md` first. This document is the source of truth for stack, module
boundaries, and platform strategy.

---

## 1. Platform decision

**Android native, Kotlin, single platform.**

Rejected alternatives and why:

| Option | Rejected because |
|---|---|
| Flutter or React Native | The entire product value is native alarm reliability. Cross platform means writing the same platform channel code plus an extra abstraction layer, for no gain |
| Kotlin Multiplatform or Compose Multiplatform | Doubles the surface area to serve a platform that cannot deliver the core feature |
| iOS | iOS has no exact alarm API. Local notifications cap at 64 pending, are silenced by Focus and silent mode, and Critical Alerts entitlement is not granted for this category. The core promise cannot be kept |

India, the primary early market, is roughly 95% Android. This is the correct
first and only platform.

## 2. SDK levels

```
compileSdk = 36
targetSdk  = 36     // current Play requirement
minSdk     = 26     // Android 8.0
```

`minSdk 26` gives us `java.time` natively (no core library desugaring needed),
notification channels, and background execution limits that already match our
model, while keeping close to full device reach in India.

## 3. Module structure

Eleven modules. This is a deliberate ceiling, not an aspiration to grow.

```
:app                  Compose shell, Navigation 3 graph, DI wiring,
                      feature packages (today, plan, insights, settings,
                      onboarding, paywall)
:core:model           Pure Kotlin data types. Zero dependencies
:core:domain          Pure JVM. Timeline engine, use cases, repository
                      interfaces. ZERO Android imports
:core:data            Room, DataStore, repository implementations
:core:designsystem    Theme, tokens, typography, shapes, shared composables
:core:common          Dispatchers, TimeProvider, Result types, extensions
:core:testing         Test fixtures, fake TimeProvider, fake repositories
:scheduler            AlarmManager, notifications, foreground service,
                      broadcast receivers, Live Updates
:billing              RevenueCat wrapper, EntitlementRepository impl
:widget               Glance widget
:benchmark            Macrobenchmark, baseline profile generator
```

**Dependency direction, strictly one way:**

```
:app  ->  :core:designsystem  ->  :core:model
  |   ->  :core:domain        ->  :core:model
  |   ->  :billing            ->  :core:domain
  |   ->  :scheduler          ->  :core:domain
  |   ->  :core:data          ->  :core:domain
  |   ->  :widget             ->  :core:domain
```

`:core:domain` depends on `:core:model` and nothing else. It never sees Room,
never sees Android, never sees a third party SDK.

**When to split a feature out of `:app`:** promote a feature package to its own
`:feature:*` module when it exceeds roughly fifteen files or when a clean build
of `:app` passes forty five seconds. Not before. Premature modularisation costs
build time and buys nothing.

## 4. Architecture

**MVVM with unidirectional data flow.**

```
Composable  --event-->  ViewModel  --call-->  UseCase  --call-->  Repository
    ^                       |                                        |
    |                       |                                    Room / DataStore
    +---StateFlow<UiState>--+
```

Rules:
- One immutable `UiState` data class per screen, exposed as `StateFlow`
- Events travel up as method calls on the ViewModel, never as callbacks stored
  in state
- One shot effects (navigation, snackbar) use a `Channel` exposed as a `Flow`,
  never a boolean in state
- Use cases exist only where real logic lives. A use case that forwards one
  call to one repository is noise. Delete it
- Repository interfaces live in `:core:domain`, implementations in `:core:data`

## 5. The timeline engine

This is the heart of the product and it lives entirely in `:core:domain` as
pure functions. See `schema.md` for the data types.

```kotlin
fun interface TimelineResolver {
    fun resolve(
        template: DayTemplate,
        items: List<Item>,
        date: LocalDate,
        dayShift: Duration,
        completions: List<Occurrence>,
        zone: ZoneId,
    ): ResolvedDay
}
```

Resolution order, deterministic and pure:

1. Filter items by the day's weekday mask and the template
2. Resolve `FIXED` anchors to concrete `LocalDateTime`
3. Topologically resolve `RELATIVE` anchors from their parents, detecting cycles
4. Expand `INTERVAL` anchors into concrete occurrences inside their window
5. Resolve `WINDOW` items to a start plus a nag ladder
6. Apply `dayShift` to everything except items marked `pinned`
7. Merge in existing `Occurrence` rows so completed and skipped state survives
8. Apply the salience budget check and surface a warning if exceeded
9. Sort and emit `ResolvedDay`

The whole function is synchronous, side effect free, and testable with no
Android runtime. This is the single most important architectural property in
the project.

**Cascade:** when an item is snoozed by `n` minutes, `resolve` is re run with
that item's occurrence carrying a `shiftMin`. Downstream `RELATIVE` items move
with it, `FIXED` items do not. The preview shown in the notification is simply
the diff between two calls to `resolve`.

## 5b. The insight engine: why there is no model here

Every adaptive behaviour in this product is deterministic arithmetic running in
`:core:domain`. There is no machine learning, no trained model, no inference
call, and no network dependency. This section exists because the question comes
up, and because the answer needs to be written down once.

| Feature the user sees | What it actually is | Roughly |
|---|---|---|
| "You take this at 5:45, not 4:00" | Median of the last 30 completion times | 8 lines |
| "Wednesday, missed 4 times, work came up" | One `GROUP BY weekday, chip HAVING n >= 3` | 1 query |
| "At this rate, plus 1.1 kg" | `(progress / daysElapsed) * totalDays` | 4 lines |
| "Sunday is your strongest day" | Group by weekday, average completion | 3 lines |
| Cascade, day shift, snooze preview | Arithmetic over the anchor graph | Already in the resolver |
| Weekly and monthly review | Counting | Aggregate queries |
| Never miss twice | A counter | 1 field |

The one place naive arithmetic gives a wrong answer is body weight, which swings
by up to a kilogram a day from water and food. Raw readings must never drive a
suggestion. All weight derived output uses a **seven day moving average**, with
raw readings drawn faintly behind the smoothed line.

```kotlin
fun smoothed(readings: List<Double>): List<Double> =
    readings.windowed(size = 7, step = 1, partialWindows = true).map { it.average() }
```

**Why this is the right call, not a compromise:**

- Works with no network, which `rules.md` section 1 requires anyway
- Costs nothing per user, forever
- Answers in under a millisecond instead of seconds
- Cannot invent a number, and cannot be wrong in a way we did not write
- Is unit testable with fixed inputs, which a model is not
- Can always explain itself, because the reason is the calculation

**Where a language model could legitimately help, later and optionally:**

1. **Plan import fallback.** A deterministic parser handles the common shape,
   which is a leading time token followed by a title. A model is only a fallback
   for text the parser cannot read, and even then the user confirms every row
   before anything is saved.
2. **Weekly narrative.** Turning numbers the app already has into readable
   prose. This is cosmetic. It produces no information the numbers did not
   already contain.

Both are V2, both are opt in, and both use the user's own API key, so they cost
this project nothing. Neither may ever become a dependency of a core feature.

**Where real machine learning would apply, and why it does not here:** research
on just-in-time adaptive interventions uses reinforcement learning to pick
notification timing. That needs many users and months of data per user. With a
single user, a median is a better estimator than a trained policy, and it is
also honest about its own uncertainty.

---

## 6. Time handling

Time correctness is the highest bug risk in this product. Rules:

1. Never store an absolute epoch for a plan item. Store `LocalTime` plus the
   template's `ZoneId`
2. Compute the concrete fire time at scheduling time, not at authoring time
3. Never call `now()` directly. Inject `TimeProvider`:
   ```kotlin
   interface TimeProvider {
       fun now(): Instant
       fun zone(): ZoneId
       fun today(): LocalDate
   }
   ```
4. Reschedule the entire day on every one of these:
   `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `ACTION_TIMEZONE_CHANGED`,
   `ACTION_TIME_CHANGED`, `ACTION_DATE_CHANGED`, `ACTION_LOCKED_BOOT_COMPLETED`
5. DST is tested even though India does not observe it, because the app ships
   globally. A `LocalTime` that does not exist on a spring forward day resolves
   forward to the next valid instant. A repeated `LocalTime` resolves to the
   first occurrence

## 7. Alarm and notification strategy

The core engineering problem. Full flow detail in `appflow.md`.

### Delivery tiers

```kotlin
enum class DeliveryTier {
    FULL_SCREEN_ALARM,     // exact alarm + full screen intent granted
    EXACT_HEADS_UP,        // exact alarm granted, no full screen intent
    INEXACT_NOTIFICATION,  // neither, accuracy degrades to about 15 min
    IN_APP_ONLY,           // notifications denied entirely
}
```

`AlarmScheduler.currentTier()` is evaluated at runtime, never assumed. The
Reliability screen shows the current tier and the exact steps to reach a higher
one.

### Scheduling

| Salience | Mechanism |
|---|---|
| `ALARM` | `AlarmManager.setAlarmClock()`. Exempt from Doze, shows the system alarm icon, highest priority. Paired with a full screen intent when granted |
| `NOTIFY` | `setExactAndAllowWhileIdle()` |
| `SILENT` | `setExactAndAllowWhileIdle()`, low importance channel, no sound |
| `TIMELINE` | Not scheduled at all. Rendered in app and in the widget only |

- A **foreground service** runs for the duration of an active block. This is the
  only mechanism that reliably survives aggressive OEM battery managers
- Only the next few occurrences are scheduled at any time, with a rolling
  reschedule on app open, on completion, on boot, and via a daily
  `WorkManager` job at 00:05
- Every scheduled alarm writes a `DeliveryAudit` row at schedule time, updated
  at fire time. This produces the reliability number we publish

### Permissions

| Permission | Handling |
|---|---|
| `USE_EXACT_ALARM` | Declared. Requires a Play Console declaration form. Core function qualifies as an alarm and reminder app |
| `SCHEDULE_EXACT_ALARM` | Declared as the fallback path, requested via `ACTION_REQUEST_SCHEDULE_EXACT_ALARM` if the above is unavailable |
| `POST_NOTIFICATIONS` | Runtime request, asked in context after the user creates their first item, never on first launch |
| `USE_FULL_SCREEN_INTENT` | Declared. Always checked with `NotificationManager.canUseFullScreenIntent()` before use |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Declared with a written justification for the block runner |
| `RECEIVE_BOOT_COMPLETED` | Declared |

### OEM handling

A dedicated onboarding step, shown only on affected manufacturers, walks the
user through autostart and battery optimisation. Detection is by
`Build.MANUFACTURER`, with per vendor deep link intents and a graceful fallback
to the general battery settings screen when a vendor intent is missing.

Vendors covered: Xiaomi, Redmi, Poco, Oppo, Realme, Vivo, iQOO, OnePlus,
Samsung, Honor, Huawei, Tecno, Infinix.

## 8. Stack

Pinned in `gradle/libs.versions.toml`. Never hardcode a version in a
`build.gradle.kts`.

| Concern | Choice | Note |
|---|---|---|
| Language | Kotlin 2.x, JDK 17 | K2 compiler |
| Build | Gradle Kotlin DSL, version catalog, convention plugins in `build-logic` | Convention plugins keep eleven modules from repeating themselves |
| UI | Jetpack Compose, Material 3 with Material 3 Expressive | Physics based motion, expanded type scale, shape library |
| Navigation | Navigation 3 | Stable since November 2025. Type safe, Compose native, no `NavController` string routes |
| DI | Hilt with KSP | Standard, integrates with `hiltViewModel()` |
| Persistence | Room with KSP, `exportSchema = true` | Schemas committed to the repo |
| Preferences | DataStore Preferences | No SharedPreferences anywhere |
| Async | Coroutines and Flow, injected `CoroutineDispatcher` | |
| Serialization | kotlinx.serialization | Also used for Navigation 3 route types |
| Background | AlarmManager for exact, WorkManager for maintenance | Never WorkManager for a user facing time |
| Widget | Glance | |
| Billing | RevenueCat, isolated in `:billing` | Free to USD 2,500 MTR |
| Crash reporting | Sentry, **opt in, disabled by default** | Preserves the "no data collected" declaration |
| Analytics | None | Deliberate, permanent |
| Lint | ktlint via Spotless, Detekt | Failing build on violation |
| Testing | JUnit 5 in `:core:domain`, JUnit 4 plus Robolectric on Android modules, Turbine, MockK, Truth | |
| Screenshot tests | Roborazzi | Free, catches visual regressions in light and dark |
| Benchmark | Macrobenchmark plus Baseline Profile Gradle plugin | |
| CI | GitHub Actions, unlimited minutes on a public repo | |

## 9. Build configuration

```kotlin
buildTypes {
    release {
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        signingConfig = signingConfigs.getByName("release")
    }
    debug {
        applicationIdSuffix = ".debug"
        isDebuggable = true
    }
}
```

- R8 full mode enabled in `gradle.properties`
- Baseline profile generated by `:benchmark` and committed before each release
- Startup profile generated alongside it, to improve DEX layout
- Signing keys and the RevenueCat API key live in `local.properties`, which is
  gitignored. CI reads them from repository secrets
- `android.nonTransitiveRClass=true`, `android.nonFinalResIds=true`

## 10. Performance practices

- Compose strong skipping is on by default in the current compiler. Keep every
  parameter passed to a composable stable, use `ImmutableList` from
  kotlinx.collections.immutable for list parameters
- Never pass a whole `UiState` into a leaf composable. Pass the fields it needs
- `LazyColumn` items always carry a stable `key`
- `derivedStateOf` for anything computed from scrolling
- Room queries return `Flow`, are `@Transaction` where they span relations, and
  the Today screen loads through exactly one query
- No image loading on the critical path. The app ships no photographic assets
- App startup uses `androidx.startup` with no work on the main thread beyond
  inflating the first frame

## 11. Testing strategy

| Layer | What is tested | Tool |
|---|---|---|
| `:core:domain` | Every anchor type, cascade, day shift, DST, cycles, salience budget, weekday masks. Target 90% coverage | JUnit 5, pure JVM, milliseconds |
| `:core:data` | Every Room migration, DAO correctness | Robolectric, `MigrationTestHelper` |
| `:scheduler` | Tier selection, reschedule triggers, notification action routing | Robolectric, fake `AlarmManager` |
| `:billing` | Entitlement resolution, offline cache, downgrade behaviour | JUnit, fake RevenueCat |
| UI | Today screen, block runner, paywall in light and dark | Roborazzi screenshot tests |
| Startup and scroll | Cold start, warm start, timeline scroll | Macrobenchmark |
| Real device | Alarm delivery on at least one Xiaomi or Realme device | Manual, logged in the audit table |

CI runs everything except the real device pass on every pull request.

## 12. Security

- No secrets in the repository. `local.properties` gitignored, CI uses secrets
- The AI feature stores the user's API key in `EncryptedSharedPreferences`
- Exported components: only the launcher activity and the alarm receiver, and
  the receiver validates its intent action and extras before acting
- No `WebView`. No dynamic code loading. No reflection based deserialization
- Backup rules explicitly exclude the API key and the entitlement cache

## 13. Decisions to record as ADRs

Write these as short files in `docs/adr/` as they are implemented. They are the
highest value artefact in the repository for a reader evaluating the work.

1. Why Android native and not cross platform
2. Why `setAlarmClock` and not WorkManager
3. Why local time is stored instead of an epoch
4. Why the domain module is pure JVM
5. Why tiered delivery with fallback instead of assuming permissions
6. Why local only with no accounts
7. Why RevenueCat behind our own interface
8. Why analytics are permanently excluded
