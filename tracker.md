# Build or Break - Progress Tracker

Living document. Update it at the end of every working session, not at the end
of a milestone. If this file is stale, nothing else in the repo can be trusted.

**Status:** M1 scaffolded, not yet verified by a build
**Last updated:** 2026-09-01
**Current milestone:** M1 - Foundation (M0 still open, do it in parallel)

**Blocking next action:** the Gradle wrapper is missing. Open the project in
Android Studio and let it generate the wrapper, or run
`gradle wrapper --gradle-version 8.14.3` once, then `./gradlew build`. Nothing
in M1 is confirmed until that build is green.

---

## How to use this file

- Tick a box only when it is actually done, not when it is mostly done
- Add a dated line to the session log at the end of every session
- Move anything that is blocked into the Blocked table with a reason
- Anything learned the hard way goes into Decisions and Learnings, so it is not
  re learned in three weeks

---

## Milestone overview

| Milestone | Scope | Estimate | Status |
|---|---|---|---|
| M0 | Set the clock running | 2 days | Not started |
| M1 | Foundation | 1 week | Scaffolded, build not verified |
| M2 | Timeline engine | 2 weeks | Not started |
| M3 | Persistence | 1 week | Not started |
| M4 | Scheduler | 3 weeks | Not started |
| M5 | The app | 2 weeks | Not started |
| M6 | Closed testing | 2 weeks (overlaps) | Not started |
| M7 | Insights, widget, polish | 2 weeks | Not started |
| M8 | Monetization | 1 week | Not started |
| M9 | Production | 1 week | Not started |

---

## M0 - Set the clock running

- [ ] Play Console account created, USD 25 paid
- [ ] Closed testing requirement confirmed for this account
- [ ] 15 tester names written down (need 12 to follow through)
- [ ] At least 3 testers on Xiaomi, Realme, Oppo or Vivo confirmed
- [ ] GitHub repository created, public
- [ ] Package name reserved: `com.buildorbreak.app`
- [ ] `Build or Break` checked for availability on Play

---

## M1 - Foundation

- [x] `libs.versions.toml` with every version pinned
- [x] Convention plugins in `build-logic` (application, library, compose, hilt, room, jvm)
- [x] All 11 modules created and wired
- [x] SDK levels set: compile 36, target 36, min 26, JDK 17
- [x] Hilt, Room + KSP, DataStore, Navigation 3, kotlinx.serialization declared
- [x] Spotless + ktlint, Detekt configured to fail the build
- [x] Detekt `ForbiddenMethodCall` rules banning direct clock access and GlobalScope
- [x] GitHub Actions workflow written
- [x] `:core:common` with `TimeProvider`, `AppDispatchers`, `Outcome`
- [x] `:core:testing` with `FakeTimeProvider`, `TestAppDispatchers`, and its own tests
- [x] App shell: Application, MainActivity, `CoreModule`, launcher icon, backup rules
- [x] R8 release config, proguard rules, backup exclusions for secrets
- [x] `:benchmark` with baseline profile generator and startup benchmark
- [ ] **Gradle wrapper generated** (blocking, see top of file)
- [ ] `./gradlew build` green locally
- [ ] CI green on first push
- [ ] Verify `:core:domain` cannot import `android.util.Log`
- [ ] Version numbers in `libs.versions.toml` confirmed against what actually resolves
- [ ] ADR 001 written
- [ ] **Exit:** build green, CI green, `:core:domain` cannot import Android

---

## M2 - Timeline engine

**Implementation**
- [ ] `:core:model` complete
- [ ] `Anchor` sealed interface, all four types
- [ ] `TimelineResolver`, all nine resolution steps
- [ ] Cycle detection
- [ ] `INTERVAL` expansion
- [ ] `WINDOW` nag ladder
- [ ] Day shift with pinned handling
- [ ] Salience budget
- [ ] Cascade diff
- [ ] Weekly statistics
- [ ] Export generator

**Tests (these are the exit criteria)**
- [ ] Each anchor type in isolation
- [ ] Three deep `RELATIVE` chain
- [ ] `RELATIVE` cycle detected and broken
- [ ] Skipped parent resolves child from planned time
- [ ] Day shift respects pinned
- [ ] Day shift past midnight clamps
- [ ] Weekday mask filters correctly
- [ ] `INTERVAL` shorter than window fires once
- [ ] DST spring forward
- [ ] DST fall back
- [ ] Timezone change keeps wall clock time
- [ ] Budget flags 4 alarms
- [ ] Cascade diff names only moved items
- [ ] Coverage 90% or higher
- [ ] Suite runs in under 1 second

---

## M3 - Persistence

- [ ] All Room entities, version 1, including Track tables
- [ ] `exportSchema = true`, schema JSON committed
- [ ] DAOs including the single Today query
- [ ] Entity to domain mappers
- [ ] Repository implementations
- [ ] DataStore preferences
- [ ] `EncryptedSharedPreferences` set up
- [ ] Migration test harness
- [ ] Debug seed: the real 22 item weight gain routine
- [ ] **Exit:** seeded day loads in one query and resolves correctly

---

## M4 - Scheduler (the hard one)

**Implementation**
- [ ] `DeliveryTier` runtime detection
- [ ] `AlarmScheduler` interface and implementation
- [ ] `setAlarmClock` for ALARM tier
- [ ] `setExactAndAllowWhileIdle` for other tiers
- [ ] Rolling reschedule window
- [ ] Notification channels per tier
- [ ] Actions handled in a receiver, no activity launch
- [ ] Snooze consequence preview
- [ ] `RemoteInput` measurement entry
- [ ] Full screen wake alarm with 60 s volume ramp
- [ ] Foreground service for active blocks
- [ ] Receivers: boot, locked boot, package replaced, timezone, time, date
- [ ] Daily maintenance job at 00:05
- [ ] `DeliveryAudit` write and update
- [ ] OEM detection and vendor deep links
- [ ] Android 16 `ProgressStyle` Live Update
- [ ] Permission flows with fallbacks
- [ ] ADR 002 and ADR 005 written

**Exit criteria, physical device only**
- [ ] 20 consecutive overnight alarms within 60 s on a Xiaomi or Realme
- [ ] Survives reboot
- [ ] Survives force stop plus app open
- [ ] Every notification action works without opening the app
- [ ] All permissions denied still leaves a usable app
- [ ] Audit log produces a real on time percentage

---

## M5 - The app

- [ ] Design system: tokens, Fraunces, DM Sans, paper grain
- [ ] Four custom state glyphs
- [ ] The rail component
- [ ] Today screen, all interactions
- [ ] Block runner
- [ ] Item editor, template editor
- [ ] Plan import parser and preview
- [ ] Skip reason sheet
- [ ] Day shift sheet, template picker, backfill
- [ ] Settings screens
- [ ] Reliability screen and delivery log
- [ ] Onboarding
- [ ] Roborazzi screenshot tests, light and dark
- [ ] **Exit:** real 22 item routine runs end to end for a full day
- [ ] **Exit:** `design.md` section 14 checklist passes

---

## M6 - Closed testing

- [ ] Release signing config, keystore backed up
- [ ] Verified R8 release build
- [ ] Privacy policy hosted
- [ ] Data Safety form: no data collected
- [ ] `USE_EXACT_ALARM` declaration submitted
- [ ] `USE_FULL_SCREEN_INTENT` declaration submitted
- [ ] Store listing written
- [ ] Icon, feature graphic, 6 screenshots, all self made
- [ ] Closed testing track live
- [ ] 12 testers opted in
- [ ] Tester feedback channel created
- [ ] **14 day clock started on:** _______________

---

## M7 - Insights, widget, polish

- [ ] Insights screen, consistency ring
- [ ] Pattern surfacing
- [ ] Measurement charts
- [ ] Export CSV and Markdown
- [ ] Glance widget
- [ ] All empty and error states
- [ ] Copy reviewed against `rules.md` section 2
- [ ] TalkBack pass
- [ ] 200% font scale pass
- [ ] Baseline profile committed
- [ ] Macrobenchmark results recorded
- [ ] Tester bugs fixed
- [ ] **Exit:** all performance budgets met and measured

---

## M8 - Monetization

- [ ] `EntitlementRepository` and `Feature` enum
- [ ] `:billing` module, RevenueCat isolated
- [ ] Offline entitlement cache
- [ ] All gates from `appflow.md` section 11
- [ ] Paywall screen
- [ ] Play products created with regional pricing
- [ ] Restore purchase
- [ ] Downgrade preserves everything
- [ ] Real purchase tested end to end
- [ ] ADR 007 written

---

## M9 - Production

- [ ] 14 days closed testing complete
- [ ] Production access applied for
- [ ] Production access granted
- [ ] README with architecture diagram
- [ ] All 8 ADRs written
- [ ] CI and coverage badges
- [ ] Commit history reviewed
- [ ] Reliability figure published in README
- [ ] **Live on Play Store**

---

## Measured numbers

Fill these in as they become real. These are what go in the README and the CV.

| Metric | Target | Measured | Date |
|---|---|---|---|
| Cold start | under 500 ms | | |
| Today screen rendered | under 700 ms | | |
| Release APK size | under 12 MB | | |
| Alarm on time within 60 s | 95% or better | | |
| Total alarms measured | | | |
| Devices tested on | at least 4 | | |
| `:core:domain` coverage | 90% or higher | | |
| Domain test suite runtime | under 1 s | | |
| Crash free sessions | 99.5% or better | | |
| Consecutive days of personal use | 30 or more | | |

---

## Blocked

| Item | Blocked by | Since | Next action |
|---|---|---|---|
| | | | |

---

## Decisions and learnings

Anything found the hard way goes here, with a date. This is the file that stops
the same mistake being made twice.

| Date | Learning |
|---|---|
| 2026-09-01 | Play Console personal accounts created after 13 Nov 2023 need 12 testers opted in for 14 continuous days before production access, and since 2026 Google also checks that testers actually used the app. This is the longest lead time item in the project, so M0 comes before any code |
| 2026-09-01 | `targetSdk 36` is mandatory for new submissions as of 31 Aug 2026. Any tutorial or Stack Overflow answer written for API 33 or 34 is unsafe for the alarm and foreground service code paths |
| 2026-09-01 | `:core:testing` depends on `:core:common`, so `:core:common` can never have a test that uses the shared fixtures. That is a project dependency cycle and Gradle rejects it. `FakeTimeProvider` is therefore tested from inside `:core:testing`. Same rule applies to `:core:model` |
| 2026-09-01 | Detekt `ForbiddenMethodCall` is the mechanism that actually enforces the injected clock rule. Writing the rule in a document is a suggestion, failing the build is a rule |

---

## Session log

Append one line per session. Date, what moved, what is next.

| Date | Done | Next |
|---|---|---|
| 2026-09-01 | Project docs written: rules, prd, techspec, schema, design, appflow, implementation plan, tracker | Start M0 |
| 2026-09-01 | M1 scaffolded: version catalog, 6 convention plugins, 11 modules, detekt and spotless config, CI workflow, TimeProvider and FakeTimeProvider with tests, app shell and launcher icon. Build NOT verified, no Gradle wrapper on this machine | Generate the wrapper, run `./gradlew build`, fix whatever versions do not resolve |
