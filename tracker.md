# Build or Break - Progress Tracker

Living document. Update it at the end of every working session, not at the end
of a milestone. If this file is stale, nothing else in the repo can be trusted.

**Status:** M1 scaffolded, build not yet verified
**Last updated:** 2026-09-01
**Repository:** github.com/yatharthrathii/build-or-break
**Target:** working APK on own phone by about week 10, Phase 1 complete by
about week 17, Play Store after that

**Blocking next action:** the Gradle wrapper is missing, so `gradlew` does not
exist and CI fails on every push. Open the project in Android Studio and let it
generate the wrapper, or run `gradle wrapper --gradle-version 8.14.3`. Then
`./gradlew build`, fix whatever does not resolve, and commit `gradlew`,
`gradlew.bat` and `gradle/wrapper/gradle-wrapper.jar`.

---

## How to use this file

- Tick a box only when it is actually done, not when it is mostly done
- Add a dated line to the session log every session
- Anything blocked goes in the Blocked table with a reason
- Anything learned the hard way goes in Decisions and Learnings

---

## Milestone overview

| Milestone | Scope | Estimate | Status |
|---|---|---|---|
| M1 | Foundation | 1 week | Scaffolded, build not verified |
| M2 | Timeline and goal engine | 3 weeks | Not started |
| M3 | Persistence | 1 week | Not started |
| M4 | Scheduler | 3 to 4 weeks | Not started |
| M5 | App, core screens | 3 weeks | Not started |
| **M5b** | **Signed APK on own phone** | 2 days | **The point of stage 1a** |
| M6 | Goals, motivation, weekly review | 2 weeks | Not started |
| M7 | Learning tracks | 1.5 weeks | Not started |
| M8 | Insights, widget, polish | 2 weeks | Not started |
| M9 | Play preparation | 1 week plus 14 day wait | Deferred |
| M10 | Monetization | 1 week | Deferred |
| M11 | Production | 1 week | Deferred |

Nothing costs money before M9.

---

## M1 - Foundation

- [x] `libs.versions.toml` with every version pinned
- [x] Convention plugins in `build-logic`
- [x] All 11 modules created and wired
- [x] SDK levels: compile 36, target 36, min 26, JDK 17
- [x] Spotless, ktlint and Detekt configured to fail the build
- [x] Detekt `ForbiddenMethodCall` bans direct clock access and `GlobalScope`
- [x] GitHub Actions workflow written
- [x] `:core:common` with `TimeProvider`, `AppDispatchers`, `Outcome`
- [x] `:core:testing` with `FakeTimeProvider` and its own tests
- [x] App shell, `CoreModule`, launcher icon, backup rules, R8 config
- [x] `:benchmark` with baseline profile generator and startup benchmark
- [x] Repository created, personal identity set locally, first push done
- [ ] **Gradle wrapper generated and committed** (blocking)
- [ ] `./gradlew build` green locally
- [ ] Version numbers confirmed against what actually resolves
- [ ] CI green
- [ ] `:core:domain` verified unable to import `android.util.Log`
- [ ] ADR 001 written

---

## M2 - Timeline and goal engine

**Timeline**
- [ ] `:core:model` complete
- [ ] `Anchor` sealed interface, all four types
- [ ] `TimelineResolver`, all nine steps
- [ ] Cycle detection
- [ ] `INTERVAL` expansion
- [ ] `WINDOW` nag ladder
- [ ] Day shift with pinned handling
- [ ] Salience budget
- [ ] Cascade diff

**Goals**
- [ ] Four goal kinds
- [ ] Seven day moving average
- [ ] Pace target and projection
- [ ] `DayQuality` at eighty and fifty percent
- [ ] Milestone evaluation, four suppression rules
- [ ] Weekly review, both outcome branches
- [ ] Catch up with the twenty percent cap
- [ ] Median time shift detection
- [ ] Skip reason grouping
- [ ] Best and worst weekday
- [ ] Export generator

**Tests (these are the exit criteria)**
- [ ] Each anchor type in isolation
- [ ] Three deep `RELATIVE` chain
- [ ] `RELATIVE` cycle detected and broken
- [ ] Skipped parent resolves child from planned time
- [ ] Day shift respects pinned, clamps past midnight
- [ ] Weekday mask filters correctly
- [ ] `INTERVAL` shorter than window fires once
- [ ] DST spring forward and fall back
- [ ] Timezone change keeps wall clock time
- [ ] Budget flags four alarms
- [ ] Cascade diff names only moved items
- [ ] Moving average smooths a 1 kg daily swing
- [ ] Projection correct for all four goal kinds
- [ ] A `POOR` day yields no countdown and no milestone
- [ ] A milestone with an award row never fires again
- [ ] No two milestones on consecutive days
- [ ] Catch up never exceeds twenty percent of a week
- [ ] Coverage 90 percent or higher
- [ ] Suite under 1 second

---

## M3 - Persistence

- [ ] All tables from `schema.md`, version 1
- [ ] goal, goal_progress, day_close, milestone_award included
- [ ] Track tables included, active in Phase 1
- [ ] `exportSchema = true`, schema JSON committed
- [ ] DAOs including the single Today query
- [ ] Mappers, repository implementations
- [ ] DataStore, `EncryptedSharedPreferences`
- [ ] Migration test harness
- [ ] Debug seed: real 22 item routine plus a plus 2 kg goal

---

## M4 - Scheduler (the hard one)

- [ ] `DeliveryTier` runtime detection
- [ ] `setAlarmClock` and `setExactAndAllowWhileIdle`
- [ ] Rolling reschedule window
- [ ] Channels per tier
- [ ] Actions in a receiver, no activity launch
- [ ] Snooze consequence preview
- [ ] `RemoteInput` measurement entry
- [ ] Full screen wake alarm, 60 s ramp, dismiss chains to task one
- [ ] Foreground service for blocks
- [ ] All six receivers
- [ ] Daily 00:05 job, writes `day_close` and `goal_progress`
- [ ] `DeliveryAudit`
- [ ] OEM detection and deep links
- [ ] Android 16 Live Update
- [ ] ADR 002 and ADR 005

**Exit, physical device only**
- [ ] 20 overnight alarms within 60 s on a Xiaomi or Realme
- [ ] Survives reboot
- [ ] Survives force stop plus app open
- [ ] Every action works without opening the app
- [ ] All permissions denied still usable
- [ ] Audit log gives a real percentage

---

## M5 - App, core screens

- [ ] Design tokens, Fraunces, DM Sans, paper grain
- [ ] Four state glyphs, the rail
- [ ] Today screen
- [ ] Block runner
- [ ] Item and template editors, day templates
- [ ] Plan import and preview
- [ ] Skip reason, day shift, template picker, backfill
- [ ] Goal setup with the honesty check
- [ ] Daily close card, all three versions
- [ ] Settings, Reliability, delivery log
- [ ] Onboarding
- [ ] Roborazzi tests
- [ ] `design.md` section 14 checklist passes

---

## M5b - Get it on the phone

- [ ] Release keystore generated with `keytool`
- [ ] **Keystore backed up in two places** (losing it is unrecoverable)
- [ ] Signing config reading from `local.properties`
- [ ] Signed release APK installed on the daily phone
- [ ] Real routine and goal entered
- [ ] **Daily use started on:** _______________

---

## M6 - Goals, motivation, weekly review

- [ ] Milestone cards
- [ ] Countdown, suppressed on poor days
- [ ] Weekly review, both branches
- [ ] Catch up suggestions
- [ ] Median time shift surfaced
- [ ] Never miss twice
- [ ] Skip reason patterns
- [ ] Do not count this week

---

## M7 - Learning tracks

- [ ] Track, unit, session wired to the timeline
- [ ] Syllabus paste and parse
- [ ] Session screen with the "where you stopped" note
- [ ] Completion ETA
- [ ] Duration goals reading from sessions

---

## M8 - Insights, widget, polish

- [ ] Insights, consistency ring, raw plus smoothed charts
- [ ] Monthly review
- [ ] Export and share
- [ ] Glance widget
- [ ] Empty and error states
- [ ] Copy review against `rules.md` section 2
- [ ] TalkBack and 200 percent font pass
- [ ] Baseline profile committed
- [ ] Macrobenchmark recorded
- [ ] Everything six weeks of use turned up

---

## Deferred to stage 1c (M9 to M11)

Play Console, twelve testers, permission declarations, data safety, store
listing, RevenueCat, paywall, production release. Nothing here costs money or
time until Phase 1 is feature complete.

---

## Measured numbers

Fill in as they become real. These go in the README and the CV.

| Metric | Target | Measured | Date |
|---|---|---|---|
| Cold start | under 500 ms | | |
| Today screen rendered | under 700 ms | | |
| Release APK size | under 12 MB | | |
| Alarm on time within 60 s | 95 percent or better | | |
| Total alarms measured | | | |
| Devices tested on | at least 4 | | |
| `:core:domain` coverage | 90 percent or higher | | |
| Domain suite runtime | under 1 s | | |
| Consecutive days of personal use | 30 or more | | |

---

## Blocked

| Item | Blocked by | Since | Next action |
|---|---|---|---|
| M1 exit criteria | Gradle wrapper missing | 2026-09-01 | Generate in Android Studio |

---

## Decisions and learnings

| Date | Learning |
|---|---|
| 2026-09-01 | Play Console personal accounts created after 13 Nov 2023 need 12 testers opted in for 14 continuous days before production, and since 2026 Google checks they actually used the app. Deferring Play to stage 1c takes this off the critical path entirely |
| 2026-09-01 | `targetSdk 36` is mandatory for new submissions as of 31 Aug 2026. Any answer written for API 33 or 34 is unsafe for alarm and foreground service code |
| 2026-09-01 | `:core:testing` depends on `:core:common`, so `:core:common` cannot have a test that uses the shared fixtures. That is a project dependency cycle. Same applies to `:core:model` |
| 2026-09-01 | Detekt `ForbiddenMethodCall` is what actually enforces the injected clock. A rule in a document is a suggestion, a failing build is a rule |
| 2026-09-01 | No machine learning anywhere in this product. Every adaptive behaviour is a median, an average, a group by or a division. See `techspec.md` section 5b. It works offline, costs nothing, and can always explain itself |
| 2026-09-01 | Progress photos rejected. A photo cannot produce a weight number, so the number gets typed anyway, and the photo adds storage, backup, export and privacy work for no signal |
| 2026-09-01 | The countdown is hidden entirely on a day under 50 percent completion. Telling someone how far behind they are on a day they already know went badly is the fastest way to lose them |

---

## Session log

| Date | Done | Next |
|---|---|---|
| 2026-09-01 | Project docs written: rules, prd, techspec, schema, design, appflow, implementation plan, tracker | Start M1 |
| 2026-09-01 | M1 scaffolded: version catalog, 6 convention plugins, 11 modules, detekt and spotless, CI workflow, TimeProvider and FakeTimeProvider with tests, app shell and launcher icon. Build not verified | Generate wrapper, run build |
| 2026-09-01 | Repo created and pushed. Personal identity local only, no AI trailers, MIT licence, README written | Verify build |
| 2026-09-01 | Scope updated: all Build features moved into Phase 1, Break moved to Phase 2, Play deferred to stage 1c, goal and motivation system designed, photos rejected, no ML confirmed and documented | Generate wrapper, then M2 |
