# Build or Break

An Android routine app that runs your day and reshapes it when the day moves.

## Why I am building this

I am trying to gain weight, and I kept missing things. Not the hard parts. The
small ones. Weighing myself before I drank water. Taking a shake when work ran
over and the afternoon disappeared.

So I went looking for an app that would run the routine rather than record it
afterwards. Routinery gets closest, but it binds every step to a fixed clock
time, and that is also the loudest complaint in its own reviews: if your day
starts at six on Monday and eight on Saturday, you end up maintaining two
routines or living permanently out of sync. Loop and the other open source
trackers are checkbox grids with reminders. The AI ones write you a plan and
then leave you alone at the part that is actually difficult.

None of them bend. So I am building the one I wanted.

## What it does

- Runs a plan you already have. It does not generate one. Paste in whatever you
  wrote, or whatever an AI tool wrote for you, and this schedules and executes it
- Four anchor types, so a step can sit at a fixed clock time, hang off the step
  before it, float inside a window, or repeat on an interval
- Day templates. Office day, work from home, rest day, sick day. One tap in the
  morning reshapes the whole timeline
- A minimum version on every step, defined in advance. On a bad day the
  notification offers the smaller one instead of nothing
- Snooze that shows you what it costs. Moving one step tells you which later
  steps move with it, before you commit
- Whole day shift. Woke up ninety minutes late, move the day, keep the gym slot
  where it is
- Everything stays on the phone. No account, no server, no analytics

## Status

Early. Milestone 1 of 9, which is the build skeleton and the injected clock.

What exists: the module structure, convention plugins, the version catalog,
`TimeProvider` and its test fixtures, static analysis wired to fail the build,
and an app shell that renders one line of text.

What does not exist yet: the timeline engine, the scheduler, and every screen.
There is nothing to install and no screenshots, because there is nothing worth
photographing. I will put a recording here when the Today screen runs a real
day.

## The hard part

Not the AI, and not the UI. It is getting an alarm to fire at 08:00 on a Redmi.

Android makes this genuinely difficult, and it has got harder rather than
easier. Exact alarms are denied by default from Android 14. Full screen intents
are auto granted only to calling and alarm apps since January 2025. Both are
restricted permissions that need a Play Console declaration. On top of the
platform, most of the phones people actually own in India ship a battery manager
that will quietly kill a background app unless the user has found an autostart
toggle three menus deep.

The approach here is to never assume a capability. The scheduler detects what it
is allowed to do at runtime and degrades in tiers, from a full screen alarm down
to an inexact notification, down to in app only. The app tells you which tier it
is on and exactly which two settings would move it up.

Every scheduled alarm also writes an audit row with what time it was supposed to
fire and what time it did. That produces a real number rather than a claim, and
that number goes here once there is enough of it.

## Architecture

```
app/                UI shell, navigation, feature packages
core/model          pure JVM, data types
core/domain         pure JVM, the timeline engine
core/common         pure JVM, TimeProvider, dispatchers
core/testing        pure JVM, shared fixtures
core/data           Room, DataStore, repositories
core/designsystem   tokens, theme, components
scheduler/          alarms, notifications, foreground service, receivers
billing/            entitlements, billing SDK isolated here
widget/             Glance widget
benchmark/          macrobenchmark, baseline profile
```

Four of those are Kotlin JVM modules rather than Android libraries. That is
deliberate. The timeline engine cannot import Android even by accident, which
means the whole day resolution logic is a set of pure functions that test in
milliseconds without an emulator. Detekt also fails the build on any direct call
to `Instant.now`, so the clock is always injected and time is always
controllable in a test.

Kotlin, Compose with Material 3, Room, Hilt, Navigation 3, Glance. Android only,
minSdk 26, targetSdk 36.

## Why Android only

iOS has no exact alarm API. Local notifications cap at 64 pending, get silenced
by Focus and by the ringer switch, and the Critical Alerts entitlement is not
granted for this category. The core promise of the app cannot be kept there, so
I would rather not ship a worse version of it than pretend.

## Docs

The thinking lives in the repository rather than in my head.

- [`rules.md`](rules.md) is the constitution and wins over everything else
- [`prd.md`](prd.md) is scope, the tier split, and the risks
- [`techspec.md`](techspec.md) is the stack and the alarm strategy
- [`schema.md`](schema.md) is the data model
- [`design.md`](design.md) is the design system
- [`appflow.md`](appflow.md) is every screen and every notification
- [`implementationPlan.md`](implementationPlan.md) is the build order
- [`tracker.md`](tracker.md) is where I am actually up to

## Licence

MIT. See [`LICENSE`](LICENSE).

Take the scheduling engine, take the tiered alarm approach, take whatever is
useful. If you are fighting the same battle with Doze and OEM battery managers,
`scheduler/` is the part worth reading.
