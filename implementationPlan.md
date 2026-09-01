# Build or Break - Implementation Plan

Read `rules.md` first. Progress is tracked in `tracker.md`.

Estimates assume evening work alongside a full time job, roughly ten to twelve
hours a week. A full time week is about three of these weeks.

**Total to Play Store production: about 12 to 14 weeks.**

Rule for every milestone: **do not start the next one until the exit criteria
are all true.** The exit criteria exist because skipping them is how a project
like this quietly becomes unshippable.

---

## M0 - Set the clock running (2 days)

Everything here unblocks something that has a waiting period. Do it first,
before any code.

- [ ] Create the Google Play Console developer account, USD 25
- [ ] Confirm whether the 12 tester, 14 day closed testing rule applies
      (it does for personal accounts created after 13 November 2023)
- [ ] Write down 15 named people who will be testers, aiming for 12 who follow
      through. Mixed devices, at least three Xiaomi, Realme, Oppo or Vivo
- [ ] Create the GitHub repository, public
- [ ] Reserve the package name: `com.buildorbreak.app`
- [ ] Check that `Build or Break` is not taken on Play

**Exit:** account exists, tester list written down, repo created.

---

## M1 - Foundation (1 week)

No features. Just the skeleton, done properly once.

- [ ] Gradle version catalog `libs.versions.toml`, every version pinned
- [ ] Convention plugins in `build-logic` for the android library, compose, and
      hilt setups
- [ ] All eleven modules created and wired, per `techspec.md` section 3
- [ ] `compileSdk 36`, `targetSdk 36`, `minSdk 26`, JDK 17
- [ ] Hilt, Room with KSP, DataStore, Navigation 3, kotlinx.serialization
- [ ] Spotless with ktlint, Detekt, both failing the build on violation
- [ ] GitHub Actions: assemble, unit test, lint on every push
- [ ] `:core:common` with `TimeProvider`, dispatcher qualifiers, `Result` type
- [ ] `:core:testing` with `FakeTimeProvider`
- [ ] ADR 001 written: why Android native

**Exit:** `./gradlew build` is green, CI is green, and `:core:domain` has zero
Android dependencies (verify by trying to import `android.util.Log` and
confirming it does not compile).

---

## M2 - The timeline engine (2 weeks)

The most important milestone in the project. Pure Kotlin, no Android, no UI.

- [ ] `:core:model` complete, every type in `schema.md` section 2
- [ ] `Anchor` sealed interface with all four types
- [ ] `TimelineResolver` implemented, following the nine step order in
      `techspec.md` section 5
- [ ] Cycle detection for `RELATIVE` chains
- [ ] `INTERVAL` expansion
- [ ] `WINDOW` nag ladder resolution
- [ ] Day shift with pinned item handling
- [ ] Salience budget calculation
- [ ] Cascade diff function, used by the snooze preview
- [ ] Weekly statistics calculator, pure functions
- [ ] Export generator for CSV and Markdown

**Tests, and this list is the exit criteria:**

- [ ] Each anchor type resolves correctly in isolation
- [ ] A `RELATIVE` chain three deep resolves in order
- [ ] A `RELATIVE` cycle is detected and broken safely
- [ ] A skipped parent resolves its child from the planned time
- [ ] Day shift moves unpinned items and leaves pinned items alone
- [ ] Day shift past midnight clamps correctly
- [ ] Weekday mask filters gym to Mon, Wed, Fri
- [ ] `INTERVAL` with a window shorter than the interval fires once
- [ ] DST spring forward on a non existent local time resolves forward
- [ ] DST fall back on a repeated local time resolves to the first
- [ ] Timezone change re resolves to the same wall clock time
- [ ] The salience budget flags a day with four alarms
- [ ] Snooze cascade diff names only the items that actually moved
- [ ] Coverage of `:core:domain` is 90% or higher
- [ ] The whole `:core:domain` test suite runs in under one second

**Exit:** every test above passes and the engine has never once imported
anything from Android.

---

## M3 - Persistence (1 week)

- [ ] Every Room entity from `schema.md` section 3, version 1, including the
      Track tables that V1 does not use yet
- [ ] `exportSchema = true`, schema JSON committed
- [ ] DAOs, including the single Today query in `schema.md` section 4
- [ ] Mappers between Room entities and domain models, in `:core:data` only
- [ ] Repository implementations behind the `:core:domain` interfaces
- [ ] DataStore for preferences
- [ ] `EncryptedSharedPreferences` for the future API key
- [ ] Room migration test harness set up, even with only one version
- [ ] Seed data: the developer's real weight gain routine, as a debug fixture

**Exit:** the Today query returns the seeded 22 item day in a single query, and
the engine resolves it correctly in an instrumented test.

---

## M4 - The scheduler (3 weeks, the hardest milestone)

Budget more time than feels reasonable. This is where the product lives or dies.

- [ ] `DeliveryTier` detection at runtime
- [ ] `AlarmScheduler` interface in `:core:domain`, implementation in
      `:scheduler`
- [ ] `setAlarmClock` for ALARM, `setExactAndAllowWhileIdle` for the rest
- [ ] Rolling reschedule window, not the whole year at once
- [ ] Notification channels, one per salience tier
- [ ] Notification actions: Done, Minimum, +10, +30, Skip, all handled in a
      `BroadcastReceiver` without opening the app
- [ ] Snooze consequence preview in `BigTextStyle`
- [ ] `RemoteInput` for measurement items
- [ ] Full screen intent wake alarm with the 60 second volume ramp, guarded by
      `canUseFullScreenIntent()`
- [ ] Foreground service for the active block, `specialUse` type declared
- [ ] Receivers: boot, locked boot, package replaced, timezone, time, date
- [ ] Daily `WorkManager` maintenance job at 00:05
- [ ] `DeliveryAudit` written at schedule time and updated at fire time
- [ ] OEM detection and the per vendor settings deep links
- [ ] Android 16 `ProgressStyle` Live Update for the active block
- [ ] Permission request flows, all in context, all with working fallbacks
- [ ] ADR 002 and ADR 005 written

**Exit criteria, all verified on a physical device, not an emulator:**

- [ ] 20 consecutive alarms fire within 60 seconds on a Xiaomi or Realme device
      with the app in the background and the screen off overnight
- [ ] Alarms survive a reboot
- [ ] Alarms survive a force stop followed by any app open
- [ ] Every notification action works without launching the activity
- [ ] Denying every permission leaves the app usable at `IN_APP_ONLY`
- [ ] The audit log shows a measured on time percentage

---

## M5 - The app (2 weeks)

- [ ] `:core:designsystem`: full token set from `design.md`, Fraunces and
      DM Sans bundled, light and dark, the paper grain asset
- [ ] The four custom state glyphs as vector drawables
- [ ] The rail component, with fill, ticks, now marker, and leader lines
- [ ] Today screen, all interactions from `appflow.md` section 3
- [ ] Block runner
- [ ] Item editor and template editor
- [ ] Plan import parser and preview
- [ ] Skip reason sheet
- [ ] Day shift sheet, template picker, backfill sheet
- [ ] Settings, Appearance, Data and privacy
- [ ] Reliability screen and delivery log
- [ ] Onboarding
- [ ] Roborazzi screenshot tests for Today, block runner, and the wake alarm,
      in light and dark

**Exit:** the developer's real 22 item routine can be created, run for a full
day, and reviewed, entirely inside the app. Design checklist in `design.md`
section 14 passes.

---

## M6 - Closed testing starts (2 weeks, overlaps M7)

Start this the moment M5 is usable. The 14 day clock is the long pole.

- [ ] Release signing config, keystore backed up somewhere safe
- [ ] R8 full mode, minify, resource shrinking, verified release build
- [ ] Privacy policy written and hosted on GitHub Pages
- [ ] Play Data Safety form: no data collected
- [ ] Permissions declaration forms for `USE_EXACT_ALARM` and
      `USE_FULL_SCREEN_INTENT`
- [ ] Store listing written, positioned as a routine alarm and reminder app
- [ ] Icon, feature graphic, 6 screenshots, all self made, no stock
- [ ] Closed testing track live, 12 testers opted in
- [ ] A simple feedback channel for testers, a WhatsApp group is fine

**Exit:** 12 testers opted in and actually using it, day 1 of 14 counted.

---

## M7 - Insights, widget, polish (2 weeks)

- [ ] Insights screen with the consistency ring and weekly stats
- [ ] Pattern surfacing, local only
- [ ] Measurement charts
- [ ] Export to CSV and Markdown, share sheet
- [ ] Glance widget, 4 x 2, one tap done
- [ ] Empty states, error states, all copy reviewed against `rules.md` section 2
- [ ] Full TalkBack pass
- [ ] 200% font scale pass
- [ ] Baseline profile and startup profile generated and committed
- [ ] Macrobenchmark: cold start, warm start, timeline scroll
- [ ] Fix everything the testers reported

**Exit:** performance budgets in `rules.md` section 5 are met and measured.

---

## M8 - Monetization (1 week)

Wired last, designed from day one, which is why it takes a week and not a month.

- [ ] `EntitlementRepository` and `Feature` enum in `:core:domain`
- [ ] `:billing` module with the RevenueCat integration, isolated
- [ ] Offline entitlement cache, a network failure never downgrades a payer
- [ ] Feature gates at every point in `appflow.md` section 11
- [ ] Paywall screen
- [ ] Products created in Play Console: monthly, yearly, lifetime, with regional
      pricing configured
- [ ] Restore purchase
- [ ] Downgrade behaviour: nothing is deleted, nothing is hidden, editing locks
- [ ] Purchase tested end to end on the closed testing track
- [ ] ADR 007 written

**Exit:** a real purchase completes on a real device, entitlement persists
across reinstall, and turning off the network does not lock a paying user out.

---

## M9 - Production (1 week)

- [ ] 14 days of closed testing complete with genuine usage
- [ ] Apply for production access
- [ ] README with an architecture diagram
- [ ] All eight ADRs written
- [ ] Test coverage badge and CI badge
- [ ] Commit history cleaned up, meaningful messages
- [ ] Reliability figure published in the README, from real audit data
- [ ] Production release

**Exit:** the app is live and the developer has been using it daily for at
least eight weeks.

---

## After V1

Do not start any of this until the app has been live for a month and the
developer is still using it daily.

**V1.5:** learning tracks, the "where you stopped" note, ETA projection, median
based time shift suggestions, never miss twice intervention, skip reason pattern
detection, Hindi and Hinglish locales.

**V2:** Break mode, temptation bundling, AI insights with the user's own key,
geofence nudges.

---

## Rules for the build

1. **Never skip the exit criteria.** They are the plan.
2. **M4 will take longer than estimated.** Everyone underestimates it. If it
   takes four weeks instead of three, that is the correct amount of time.
3. **Test on a real cheap Android phone, not an emulator.** An emulator will
   tell you the alarms work. A Redmi will tell you the truth.
4. **Dogfood from M5 onward.** Every day. Bugs you find yourself are free.
5. **When tempted to add a feature, open `prd.md` section 5** and check whether
   it is a non goal. It usually is.
