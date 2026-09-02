# Build or Break - Implementation Plan

Read `rules.md` first. Progress is tracked in `tracker.md`.

Estimates assume evening work alongside a full time job, roughly ten to twelve
hours a week. A full time week is about three of these weeks.

---

## The shape of this plan

Phase 1 ships to the developer's own phone first. Play comes later. That single
decision moves the twelve tester wait, the console fee, the restricted
permission declarations, the data safety form and the store listing off the
critical path entirely.

| Stage | Milestones | Roughly |
|---|---|---|
| **1a. Usable personal build** | M1 to M5 | week 10 or 11 |
| **1b. Feature complete** | M6 to M8, while dogfooding | week 17 |
| **1c. Play release** | M9 to M11 | after 1b |
| **Phase 2** | Break mode | after 1c |

**Two numbers, both honest:**

- **About 2.5 months to a working APK on your phone.** Core only, roughly
  fifteen features, and that is correct.
- **About 4 months to everything in Phase 1.**

Those two are not in conflict, they are sequential. The six weeks between them
are spent using the app every day while the remaining features land. Bugs found
by using it are free. Bugs found after shipping everything are not.

**Rule for every milestone: do not start the next one until the exit criteria
are all true.** The exit criteria are the plan.

---

# STAGE 1a: get it on the phone

## M1 - Foundation (1 week)

**Status: scaffolded, build not yet verified.**

- [x] Version catalog, convention plugins, eleven modules
- [x] Detekt and ktlint failing the build, including the forbidden clock rule
- [x] GitHub Actions workflow
- [x] `TimeProvider`, `AppDispatchers`, `Outcome`, `FakeTimeProvider`
- [x] App shell, launcher icon, R8 config, backup exclusions
- [ ] Gradle wrapper generated and committed
- [ ] `./gradlew build` green locally, versions confirmed
- [ ] CI green
- [ ] ADR 001 written

**Exit:** build green, CI green, `:core:domain` cannot import Android.

## M2 - Timeline and goal engine (3 weeks)

The most important milestone in the project. Pure Kotlin, no Android, no UI, no
network. Everything here is arithmetic, see `techspec.md` section 5b.

**Timeline**
- [ ] `:core:model` complete
- [ ] `Anchor` sealed interface, all four types
- [ ] `TimelineResolver`, all nine resolution steps
- [ ] Cycle detection, `INTERVAL` expansion, `WINDOW` nag ladder
- [ ] Day shift with pinned handling
- [ ] Salience budget
- [ ] Cascade diff, used by the snooze preview

**Goals**
- [ ] Four goal kinds: `NUMBER`, `COUNT`, `DURATION`, `CONSISTENCY`
- [ ] Seven day moving average
- [ ] Pace target and projection
- [ ] `DayQuality` classification at eighty and fifty percent
- [ ] Milestone evaluation with all four suppression rules
- [ ] Weekly review calculator, including the two outcome branches
- [ ] Catch up suggestion with the twenty percent cap
- [ ] Median based time shift detection
- [ ] Skip reason grouping
- [ ] Best and worst weekday
- [ ] Export generator, CSV and Markdown

**Tests, and this list is the exit criteria**
- [ ] Each anchor type in isolation
- [ ] Three deep `RELATIVE` chain resolves in order
- [ ] `RELATIVE` cycle detected and broken safely
- [ ] Skipped parent resolves its child from planned time
- [ ] Day shift respects pinned items, clamps past midnight
- [ ] Weekday mask filters gym to Mon, Wed, Fri
- [ ] `INTERVAL` shorter than its window fires once
- [ ] DST spring forward and fall back
- [ ] Timezone change keeps wall clock time
- [ ] Budget flags a four alarm day
- [ ] Cascade diff names only items that moved
- [ ] Moving average smooths a one kilogram daily swing
- [ ] Projection is correct for all four goal kinds
- [ ] **A `POOR` day yields no countdown and no milestone**
- [ ] A milestone with an existing award row never fires again
- [ ] Two milestones never fire on consecutive days
- [ ] Catch up never exceeds twenty percent of a normal week
- [ ] Coverage of `:core:domain` is 90 percent or higher
- [ ] The whole suite runs in under one second

**Exit:** every test above passes, and the engine has never imported Android.

## M3 - Persistence (1 week)

- [ ] Every table in `schema.md`, version 1, including goal, goal_progress,
      day_close, milestone_award and the track tables
- [ ] `exportSchema = true`, schema JSON committed
- [ ] DAOs, including the single Today query
- [ ] Mappers, repository implementations, DataStore
- [ ] Migration test harness
- [ ] Debug seed: the real 22 item weight gain routine plus a plus 2 kg goal

**Exit:** the seeded day loads in one query and resolves correctly.

## M4 - Scheduler (3 weeks, budget 4)

The hardest milestone. The product lives or dies here.

- [ ] `DeliveryTier` runtime detection and fallback
- [ ] `setAlarmClock` for ALARM, `setExactAndAllowWhileIdle` for the rest
- [ ] Rolling reschedule window
- [ ] Channels per salience tier
- [ ] Notification actions handled in a receiver, no activity launch
- [ ] Snooze consequence preview
- [ ] `RemoteInput` for measurement entry
- [ ] Full screen wake alarm, sixty second volume ramp, dismiss chains to task one
- [ ] Foreground service for active blocks
- [ ] Receivers: boot, locked boot, package replaced, timezone, time, date
- [ ] Daily maintenance job at 00:05, which also writes `day_close` and
      `goal_progress`
- [ ] `DeliveryAudit` write and update
- [ ] OEM detection and per vendor settings deep links
- [ ] Android 16 `ProgressStyle` Live Update
- [ ] ADR 002 and ADR 005

**Exit criteria, physical device only, not an emulator**
- [ ] 20 consecutive overnight alarms within 60 s on a Xiaomi or Realme
- [ ] Survives reboot
- [ ] Survives force stop followed by any app open
- [ ] Every notification action works without opening the app
- [ ] All permissions denied still leaves a usable app
- [ ] The audit log produces a real on time percentage

## M5 - The app, core screens (3 weeks)

- [ ] Design system: full token set, Fraunces and DM Sans bundled, paper grain
- [ ] Four custom state glyphs, the rail component
- [ ] Today screen, all interactions
- [ ] Block runner
- [ ] Item editor, template editor, day templates
- [ ] Plan import parser and preview
- [ ] Skip reason, day shift, template picker, backfill sheets
- [ ] Goal setup including the honesty check
- [ ] Daily close card, all three versions
- [ ] Settings, Reliability screen, delivery log
- [ ] Onboarding
- [ ] Roborazzi screenshot tests, light and dark

**Exit:** the real 22 item routine can be created, run for a full day and
reviewed entirely inside the app. `design.md` section 14 checklist passes.

## M5b - Ship it to the phone (2 days)

- [ ] Release keystore generated with `keytool`, backed up in two places
- [ ] Signing config reading from `local.properties`
- [ ] Signed release APK built and installed on the daily phone
- [ ] Real routine entered, real goal set
- [ ] **Start using it every day**

**This is the point of the whole plan. Everything after this happens while the
app is in daily use.**

---

# STAGE 1b: feature complete, while dogfooding

## M6 - Goals, motivation, weekly review (2 weeks)

- [ ] Milestone cards with the motion in `design.md`
- [ ] Countdown treatment, suppressed on poor days
- [ ] Weekly review screen, both outcome branches
- [ ] Catch up suggestions
- [ ] Median time shift suggestions surfaced
- [ ] Never miss twice intervention
- [ ] Skip reason patterns in the review
- [ ] Do not count this week

**Exit:** three weeks of the developer's own real data produce a review that is
correct and worth reading.

## M7 - Learning tracks (1.5 weeks)

- [ ] Track, unit and session model wired to the timeline
- [ ] Syllabus paste and parse
- [ ] Session screen with the "where you stopped" note
- [ ] Completion ETA projection
- [ ] Duration goals reading from track sessions

**Exit:** a real learning track runs for a week alongside the weight routine.

## M8 - Insights, widget, polish (2 weeks)

- [ ] Insights screen, consistency ring, charts with raw and smoothed series
- [ ] Monthly review
- [ ] Export to CSV and Markdown, share sheet
- [ ] Glance widget
- [ ] Every empty and error state
- [ ] Copy reviewed against `rules.md` section 2
- [ ] TalkBack pass, 200 percent font scale pass
- [ ] Baseline profile committed, macrobenchmark recorded
- [ ] Everything six weeks of daily use turned up

**Exit:** performance budgets in `rules.md` section 5 met and measured. Phase 1
is feature complete.

---

# STAGE 1c: Play Store

Do not start until stage 1b is done and the app has been in daily use for at
least six weeks.

## M9 - Play preparation (1 week, plus a 14 day wait)

- [ ] Play Console account, 25 USD
- [ ] Twelve testers recruited, at least three on Xiaomi, Realme, Oppo or Vivo
- [ ] Privacy policy hosted on GitHub Pages
- [ ] Data safety form: no data collected
- [ ] `USE_EXACT_ALARM` and `USE_FULL_SCREEN_INTENT` declarations
- [ ] Store listing positioned as a routine alarm and reminder app
- [ ] Icon, feature graphic, six screenshots, all self made
- [ ] Closed testing live, fourteen day clock started

## M10 - Monetization (1 week)

- [ ] `EntitlementRepository` implementation backed by RevenueCat, in `:billing`
- [ ] Offline entitlement cache
- [ ] Gates at every point in `appflow.md` section 11
- [ ] Paywall screen
- [ ] Products with regional pricing, lifetime listed first for India
- [ ] Restore purchase, downgrade preserves everything
- [ ] Real purchase tested end to end

## M11 - Production (1 week)

- [ ] Fourteen days of closed testing complete
- [ ] Production access granted
- [ ] README updated with a real screen recording and the measured reliability
      figure
- [ ] All eight ADRs written
- [ ] Live

---

# Phase 2

Only after the app has been live for a month and is still in daily use.

Break mode: risk windows, urge surfing timer, urge log, saved counter,
temptation bundling, optional geofence nudges, optional AI weekly narrative with
the user's own key, Hindi and Hinglish locales, Wear OS.

---

## Rules for the build

1. **Never skip the exit criteria.**
2. **M4 will take longer than estimated.** Four weeks instead of three is not a
   failure, it is the correct amount of time.
3. **Test on a real cheap Android phone.** An emulator will tell you the alarms
   work. A Redmi will tell you the truth.
4. **From M5b onward, use it every day.** That is the whole point of the
   sequencing.
5. **Before adding anything, check `prd.md` section 5 non goals.** It is usually
   in there.
6. **Nothing costs money until M9.** If a step seems to need a paid tool, the
   step is wrong.
