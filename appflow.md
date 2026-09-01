# Build or Break - App Flows

Read `rules.md`, `prd.md`, and `design.md` first. This document defines every
screen, every notification, and every path between them.

---

## 1. Navigation graph

Navigation 3, type safe routes as `@Serializable` Kotlin objects.

```
Onboarding (only until complete)
  |
  +-- Today                 [start destination, bottom bar]
  |     +-- BlockRunner            (full screen, no bottom bar)
  |     +-- ItemDetail             (bottom sheet)
  |     +-- SkipReason             (bottom sheet)
  |     +-- DayShift               (bottom sheet)
  |     +-- TemplatePicker         (bottom sheet)
  |     +-- TrackSession           (full screen, V1.5)
  |
  +-- Plan                  [bottom bar]
  |     +-- TemplateEditor
  |     +-- ItemEditor
  |     +-- PlanImport             (paste a plan)
  |
  +-- Insights              [bottom bar]
  |     +-- WeekDetail
  |     +-- MeasurementChart
  |
  +-- Settings              [bottom bar]
        +-- Reliability
        +-- DeliveryAudit
        +-- Appearance
        +-- DataAndPrivacy
        +-- About

Paywall                     (modal, reachable from many places)
WakeAlarm                   (full screen intent, outside the graph)
```

Four bottom bar destinations. Not five, not three.

---

## 2. First run

Target: a usable first item exists in under sixty seconds. No account, no
carousel, no permission wall.

```
1. Cold open
   One screen. The rail is drawn, empty. One line of text:
   "A plan you already have, run properly."
   Two buttons: [ Paste a plan ]  [ Start from scratch ]
   No sign in. No skip button, because there is nothing to skip.

2a. Paste a plan
    A single large text field. The user pastes whatever they have:
    a table from ChatGPT, a list from a trainer, a note.
    [ Read it ]

    The parser (local, deterministic, no network) extracts candidate rows by
    looking for a leading time token. It produces a preview list.

    Preview screen: every detected item shown as an editable row with
    time, title, and a guessed salience. A count at the top:
    "Found 22 items. 2 alarms, 7 with sound, 6 silent, 7 in app only."

    The user can fix any row inline. [ Use this ]

    If the parser finds nothing, it says so plainly and falls through to 2b.
    It never guesses wildly.

2b. Start from scratch
    One item editor, pre focused on the title field.

3. Notification permission
   Asked only now, in context, with one sentence of why:
   "Build or Break needs to send notifications to run your day."
   If denied, the app still works. Tier drops to IN_APP_ONLY and the
   Reliability screen explains it.

4. OEM step (only on affected manufacturers)
   Shown only for Xiaomi, Oppo, Vivo, Realme, OnePlus, Samsung and the rest
   of the list in techspec.md. Two or three device specific steps with a
   deep link intent per step and a [ Done ] and [ Skip ] on each.
   Skipping is allowed and remembered. The Reliability screen can resume it.

5. Land on Today
   onboarding_complete = true
```

**Never on first run:** exact alarm permission, full screen intent permission,
the paywall, a rating prompt, a tips carousel.

---

## 3. Today screen

The primary surface. See `design.md` section 5.1 for the rail.

### Layout, top to bottom

```
Day header        Fraunces. "Wednesday"
Template chip     "Office day". Tap opens TemplatePicker
Rail progress     "5 of 12"
[ Late banner ]   Only when the wake item settled more than 30 min late
Timeline          Past items, now marker, future items
```

### Row anatomy

```
rail | leader | [glyph]  Title                        Time
                         Detail line                  Duration
                         [skip reason, if present]
```

### Interactions

| Gesture | Result |
|---|---|
| Tap a future row | ItemDetail sheet |
| Tap a block row | BlockRunner, full screen |
| Tap the glyph on a due or overdue row | Mark done, with haptic and rail animation |
| Long press any row | Quick actions: Minimum, plus 10, plus 30, Skip, Edit |
| Swipe a row right | Done |
| Swipe a row left | Skip, opens SkipReason sheet |
| Tap a past row (within 24 h) | Backfill sheet: Done, Minimum, or Skip |
| Tap the template chip | TemplatePicker |
| Tap the late banner | DayShift sheet |
| Pull to refresh | Re resolve the day and reschedule. Not a network call |

### States

- **Empty:** the rail with no rows, one line of copy, one button to add an item
- **All done:** the rail fully filled, the day header, and the end of day
  summary card (see section 8)
- **Budget exceeded:** an inline warning above the timeline, not a dialog:
  "This day has 5 alarms. Three or fewer works better." with a button to open
  a bulk salience editor

---

## 4. Block runner

Full screen, no bottom bar, keeps the screen on. A foreground service is
running for the duration.

```
Block title                            3 / 5
Segmented progress, one segment per step

        Current step title
        Countdown, Fraunces, tabular

        Detail text

  [ Done ]            [ Minimum ]
        Skip this step

  Next: <next step title>
```

Rules:
- Marking a step done advances immediately, no confirm
- The countdown is advisory. It never blocks advancing early or late
- Skipping a step inside a block opens the SkipReason sheet inline and then
  advances
- Backing out mid block leaves the block in progress. Reopening resumes
- Finishing the last step: the block collapses into the Today rail with the
  completion animation, the foreground service stops

---

## 5. Notification flows

### 5.1 Standard item, salience NOTIFY

```
Title:   Shake 2
Body:    Milk, banana, peanuts
Actions: [ Done ]  [ Minimum ]  [ +10 ]
Expanded also shows: [ +30 ]  [ Skip ]  and the consequence preview
```

| Action | Behaviour |
|---|---|
| `Done` | Occurrence -> DONE. Notification cancelled. Rail advances. No app open |
| `Minimum` | Occurrence -> DONE_MINIMUM |
| `+10` / `+30` | `shiftMinutes` increases, `snoozeCount` increments, day re resolves, downstream alarms rescheduled, a new notification is scheduled |
| `Skip` | Occurrence -> SKIPPED, then a **silent follow up** notification appears with the reason chips as actions plus a `RemoteInput` text field. Answering is optional and it self dismisses after 30 minutes |
| Body tap | Opens Today, scrolled to that item |
| Dismiss | Nothing changes. The item stays PENDING until its miss deadline |

### 5.2 Snooze consequence preview

Rendered in `BigTextStyle` inside the expanded notification, computed by
running `resolve()` twice and diffing.

```
+10 min:  Walk moves 7:00 to 7:10. Everything still fits.
+30 min:  Walk moves 7:00 to 7:30, close to Dinner at 8:30.
```

Only items that actually move are named. If nothing moves, the line reads
"Nothing else moves."

### 5.3 Measurement item

```
Title:   Weight check
Body:    After the toilet, before water
         Yesterday 61.2 kg. Last 7 days +0.4
Input:   RemoteInput, numeric hint, unit suffix shown
Actions: [ Save ]  [ Not today ]
```

Saving writes a `Measurement` row and settles the occurrence. The app never
opens.

### 5.4 Wake alarm, salience ALARM

Full screen intent when `canUseFullScreenIntent()` is true, otherwise a
high importance heads up with an insistent sound.

```
Current time, Fraunces, very large
Background warms from surfaceDim to primaryContainer over 60 s
Volume ramps over the same 60 s

[ Up. Next: Weight check ]        <- primary, 72 dp tall
  Snooze 9 min                    <- secondary, text only
```

The primary dismiss action **is** the first task. It marks the wake item done
and opens the block runner at step one. There is no bare "Dismiss".

Snooze is available but honest: it shows how many times it has been used today
and, from the third snooze, offers "Your wake time may be wrong. Move it to
8:20?"

### 5.5 Interval item

Fires at each interval inside its window, silent tier by default. Actions are
`Done` and `Not now`. `Not now` skips only this occurrence, never the series.
The series stops at the window end with no further noise.

### 5.6 Window item nag ladder

```
window start        soft notification
+ ladder step 1     reminder
+ ladder step 2     last call, "window closes at 6:00"
window end          auto settle to MISSED, no notification
```

---

## 6. Adaptation flows

### 6.1 Late wake

Trigger: the wake item settles more than 30 minutes after its planned time, or
the app opens after the wake item's planned time with the item still PENDING.

```
Banner on Today:  "You are 90 minutes behind."   [ Shift the day ]

DayShift sheet:
  Shift everything by  [ -  90 min  + ]
  Pinned items stay put:
    Gym 9:00 (pinned)      [ shift it too ]
  Preview list showing every new time
  [ Apply ]  [ Just today, reduced ]  [ Cancel ]
```

`Just today, reduced` sets `DayMode.REDUCED`, which swaps every item that has a
minimum version for its minimum, and drops TIMELINE tier items entirely.

### 6.2 Template switch

Opened from the template chip. A list of templates for the active plan, with
the auto matched one marked. Choosing one rewrites `day_log` for today and
re resolves. Occurrences already settled are preserved.

### 6.3 Backfill

Any past row within 24 hours is tappable. The sheet offers Done, Minimum, or
Skip and writes `settledAt` as the real current time, so the audit stays
honest.

---

## 7. Skip reason flow

Never blocking. Never mandatory. It appears **after** the skip is recorded, so
the user is never held hostage at the moment they are already having a bad day.

```
SkipReason sheet
  "Why did this not happen?"   (optional)

  Chips, single select:
  [ Work came up ] [ Forgot ] [ Not in the mood ]
  [ Unwell ] [ Travelling ] [ No time ] [ Did it later ]

  One line text field, optional

  [ Save ]        Dismissing without saving is fine and is not re prompted
```

If the same chip is chosen three times on the same weekday for the same item,
the app surfaces it once in the weekly report. Not sooner, never as a popup.

---

## 8. End of day

Fires at the last item's settle time, or at a configured wind down time.

```
Small card at the bottom of Today, not a modal:

  9 of 12 done. 2 minimum. 1 missed.
  Weight 61.4 kg, up 0.2 since Monday.
  Tomorrow starts at 8:00.

  [ See tomorrow ]
```

No score. No grade. No streak. No emoji.

---

## 9. Insights

```
Insights
  Consistency ring        trailing 30 days, one cell per day
  This week               days active, items done, minimum used
  Per item                completion count, median actual time vs planned
  Measurements            a line for each ValueKind in use
  Patterns                surfaced only when a real pattern exists:
                          "Wednesday, shake missed 4 of 5 times.
                           Reason given: work came up."
                          [ Move Wednesday shake to 6:00 PM ]
  Reliability             "Alarms fired on time 98.6% of 412"
                          [ Details ]
```

Everything here is computed locally from Room. No network call exists on this
screen in V1.

**Free tier:** the current week plus the 30 day ring. **Pro:** full history and
the per item median analysis. Locked sections show a real preview with the
data blurred at the row level, never an empty locked box.

---

## 10. Reliability and audit

```
Reliability
  Current tier:  Exact heads up
  What this means: alarms fire on time, but they will not take over
                   the screen when the phone is locked.

  To improve:
   [ ] Allow full screen notifications      [ Open settings ]
   [x] Allow exact alarms
   [ ] Turn off battery optimisation        [ Open settings ]
   [ ] Allow autostart (Xiaomi)             [ Open settings ]

  Measured: 412 alarms, 98.6% within 60 s, worst 4 m 12 s
  [ Delivery log ]
```

The delivery log is a monospaced list from `delivery_audit`, exportable. This
screen is also where the OEM onboarding can be resumed.

---

## 11. Paywall triggers

Contextual only. Never at launch, never on a timer, never after a completion.
Each trigger names the exact capability being reached for.

| Trigger | Copy heading |
|---|---|
| Creating a second plan | `Pro adds unlimited plans` |
| Creating a third day template | `Pro adds unlimited day templates` |
| Adding a sixteenth item | `Pro removes the item limit` |
| Opening history older than 30 days | `Pro keeps your full history` |
| Opening per item median analysis | `Pro adds per item analysis` |
| Creating a second track (V1.5) | `Pro adds unlimited tracks` |
| Enabling AI insights (V1.5) | `Pro adds AI insights` |
| Settings, a single quiet row | `Build or Break Pro` |

Paywall screen:

```
Pro
  Everything in the free app, plus:
   Unlimited plans and day templates
   No item limit
   Full history and per item analysis
   Unlimited learning tracks
   AI insights with your own key

  [ Lifetime      INR 1,499 ]   <- primary, listed first in India
  [ Yearly        INR 999  ]
  [ Monthly       INR 149  ]

  Alarms, reliability, and export are free forever.
  Restore purchase
```

That last line stays on the paywall permanently. It is the honest position and
it is also the best conversion argument we have.

---

## 12. Settings

```
Settings
  Plan
    Active plan, switch, manage
  Appearance
    Theme: System / Light / Dark
    Use wallpaper colours (off by default)
  Reliability
    ->
  Data and privacy
    Export data
    Crash reporting        (off by default, one paragraph of plain English)
    AI insights            (off by default, own API key, V1.5)
    Delete everything
  Build or Break Pro
    ->
  About
    Version, open source licences, source code link
```

---

## 13. System event flows

| Event | Response |
|---|---|
| `BOOT_COMPLETED` | Re resolve today, reschedule all future occurrences |
| `MY_PACKAGE_REPLACED` | Same |
| `TIMEZONE_CHANGED` | Re resolve with the new zone, reschedule, show a one time note in the Today header |
| `TIME_CHANGED` | Re resolve and reschedule |
| `DATE_CHANGED` | Settle yesterday's PENDING occurrences to MISSED, build today |
| Daily `WorkManager` job, 00:05 | Materialise today's occurrences, reschedule, prune audit rows older than 180 days |
| App opened | Re resolve, reconcile any occurrence whose alarm did not fire, update the audit |
| Exact alarm permission revoked while running | Detect on next schedule, drop tier, post one silent notification pointing at Reliability |

---

## 14. Edge cases that must be handled

- Two items with `RELATIVE` anchors pointing at each other: cycle detection in
  `resolve()`, the cycle is broken and both fall back to `FIXED` at their last
  known time, with a warning on the item editor
- An item whose parent was deleted: falls back to `FIXED`, flagged in the editor
- A `RELATIVE` child whose parent was skipped: resolves from the parent's
  planned time, not its actual time
- Day shift pushing an item past midnight: it clamps to 23:59 and is flagged
- An `INTERVAL` window shorter than its interval: fires once at the window start
- Device rebooted during an active block: the block is resumed on next open,
  with elapsed time reconstructed from `firedAt`
- Two devices, same account: not supported in V1. There is no account
- Storage full on export: fails with a plain message, nothing is corrupted
- Downgrade from Pro with 5 templates: all 5 remain visible and usable, editing
  beyond template 2 is locked, nothing is deleted
