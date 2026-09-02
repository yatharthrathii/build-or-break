# Build or Break - Design System

Read `rules.md` section 3 first. That section lists what is banned. This
document defines what we do instead.

The goal is not "modern". The goal is **specific**. A generated interface is
recognisable because it is the statistical average of every interface. The only
defence is a set of committed, slightly unusual choices that are applied
consistently. Those choices are below. They are not suggestions.

---

## 1. The idea the design is built on

This app is a **ledger of a day**, not a dashboard.

It should feel closer to a well set page in a notebook than to a fitness
product: warm paper, real ink, a printed rail down the margin, numbers set in a
typeface with a voice. Calm at rest, precise in motion.

Everything below serves that.

---

## 2. Colour

Warm neutral base, single warm accent. No purple, no violet, no neon, no
gradient. Light mode is the default.

### Light

| Token | Hex | Use |
|---|---|---|
| `surface` | `#F7F4EF` | App background. Warm paper, never pure white |
| `surfaceDim` | `#EDE8E0` | Recessed areas, past section of the rail |
| `surfaceContainer` | `#FFFCF7` | Raised rows, sheets, cards |
| `surfaceContainerHigh` | `#FFFFFF` | The single active row |
| `onSurface` | `#1C1917` | Primary text. Warm near black, never `#000000` |
| `onSurfaceVariant` | `#6B635A` | Secondary text, detail lines |
| `outline` | `#D6CEC3` | Hairlines, the unfilled rail |
| `primary` | `#A8452B` | Build accent. Rust, terracotta family |
| `onPrimary` | `#FFFFFF` | |
| `primaryContainer` | `#F5DDD2` | Selected chips, the now marker halo |
| `done` | `#4F6146` | Completed state. Muted moss, not a bright green |
| `missed` | `#A19A90` | Missed state. Warm grey. **Never red** |
| `warning` | `#B8862A` | Budget warnings, degraded reliability tier |
| `danger` | `#8C2F22` | Destructive confirmations only. Nothing else |
| `breakAccent` | `#2F4858` | Reserved for Break mode in V2. Cool slate |

### Dark

Warm dark, not navy, not pure black. An OLED true black with a cool accent is
the most common generated look and it is banned.

| Token | Hex |
|---|---|
| `surface` | `#14120E` |
| `surfaceDim` | `#0F0E0A` |
| `surfaceContainer` | `#201D17` |
| `surfaceContainerHigh` | `#2A261E` |
| `onSurface` | `#ECE6DC` |
| `onSurfaceVariant` | `#A79E92` |
| `outline` | `#40392F` |
| `primary` | `#E58B6A` |
| `onPrimary` | `#3A1409` |
| `primaryContainer` | `#5C2415` |
| `done` | `#9DB58D` |
| `missed` | `#726B62` |
| `warning` | `#E0B563` |
| `danger` | `#E08072` |

### Rules

- Dynamic colour (Material You) is **opt in**, off by default. Our identity
  ships first. A user who prefers their wallpaper palette can switch in
  Settings
- Elevation is expressed by moving up the `surfaceContainer` ramp, never by a
  drop shadow. Shadows are permitted on exactly two things: the FAB and a
  modal bottom sheet
- A gradient is permitted in exactly one place: the sixty second wake alarm
  screen, where the background warms from `surfaceDim` to `primaryContainer` as
  the volume ramps. It is functional, it communicates time passing, and it is
  the only one

---

## 3. Typography

Two families. Neither is Inter.

| Role | Family | Notes |
|---|---|---|
| Display and numerals | **Fraunces** | Variable serif with `SOFT` and `WONK` axes. Free on Google Fonts. Editorial, human, immediately not generic |
| UI, body, labels | **DM Sans** | Variable, warm geometric, excellent tabular figures. Free on Google Fonts |
| Debug and audit screens | **JetBrains Mono** | Reliability screen and audit log only |

**Fraunces is used in exactly three places.** Discipline is what makes it work:

1. The current time on the rail's now marker
2. The day header on the Today screen
3. The headline numbers in the weekly report

Everything else is DM Sans. If you find Fraunces in a fourth place, remove it.

### Scale

| Token | Family | Size / line | Weight | Use |
|---|---|---|---|---|
| `displayNumeral` | Fraunces | 44 / 48 | 500, `SOFT` 40 | Report headline figures |
| `nowClock` | Fraunces | 22 / 24 | 500 | Rail now marker |
| `dayHeader` | Fraunces | 26 / 30 | 400 | "Wednesday" |
| `titleLarge` | DM Sans | 20 / 26 | 600 | Screen titles, block titles |
| `titleMedium` | DM Sans | 17 / 22 | 600 | Item title in a row |
| `body` | DM Sans | 15 / 21 | 400 | Detail lines |
| `label` | DM Sans | 13 / 16 | 500 | Chips, buttons, metadata |
| `timeStamp` | DM Sans | 14 / 16 | 500, tabular | Times in the timeline |
| `micro` | DM Sans | 11 / 14 | 500, letterspacing 0.04em, uppercase | Section dividers only |

### Rules

- **Every number that changes uses tabular figures.** Times, counts, weights,
  timers. A digit that shifts the layout when it changes is a bug
- Maximum two weights on one screen
- No letterspacing anywhere except `micro`
- No text is centred except inside a button and on the wake alarm screen
- Line length in the report never exceeds 68 characters

---

## 4. Shape

A uniform radius on everything is a generated-UI signature. Different roles get
different radii, on purpose.

| Role | Radius |
|---|---|
| Timeline row | `4.dp` (near square, reads as a list, not a stack of cards) |
| Card, sheet, dialog surface | `14.dp` |
| Bottom sheet top corners | `18.dp` |
| Button | `10.dp` |
| Chip, pill, tag | fully rounded |
| Now marker on the rail | `2.dp` (a printed notch, deliberately hard) |
| Widget | `16.dp` (matches launcher convention) |

---

## 5. Signature elements

These four things are the app's identity. They are what someone remembers.

### 5.1 The rail

A `2.dp` vertical line running down the left margin of the Today screen at
`24.dp` from the edge.

- The section above the now marker is `primary` at 40% opacity, filled
- The section below is `outline`, hairline
- Hour boundaries are marked with a `6.dp` horizontal tick in `outline`
- The **now marker** is a solid `primary` notch, `4.dp` wide by `18.dp` tall,
  with the current time set beside it in Fraunces
- Every timeline row connects to the rail with a `12.dp` horizontal leader line

The rail grows as the day completes. That growth is the reward loop. There is
no separate progress bar anywhere in the app because the rail is the progress
bar.

### 5.2 Paper grain

A `128 x 128` tiling monochrome noise texture, applied to `surface` at **3%**
opacity in light mode and **2%** in dark. It costs one small asset and it is
the single fastest way to signal that a human made this. Never applied to
`surfaceContainer`, only to the base.

### 5.3 State glyphs

Four custom vector paths, drawn once, `20.dp`. Not Material's defaults.

| State | Glyph |
|---|---|
| Done | A single check stroke with slightly uneven weight, like a pen mark |
| Done, minimum | The same stroke, shorter, at 70% length |
| Missed | A hollow ring, `1.5.dp` stroke, `missed` colour. Never an X, never red |
| Skipped | A hollow ring with a single horizontal line through it |

### 5.4 App icon

The rail motif, abstracted: a vertical stroke with one filled notch, rust on
warm paper. No checkmark, no calendar, no clock face, no letter. It reads at
48dp and it looks like nothing else in the category.

---

## 6. Motion

Material 3 Expressive physics. Springs, never duration and easing.

| Motion | Spec |
|---|---|
| Row enter and exit | Spatial spring, damping `0.8`, stiffness `380` |
| Colour and alpha change | Effects spring, damping `1.0`, stiffness `1600` |
| Sheet and dialog | Spatial spring, damping `0.9`, stiffness `300` |
| Rail growth | Spatial spring, damping `1.0`, stiffness `200`, deliberately slower |

### The completion animation

This matters more than any other detail in the app, because it is the moment
the product delivers its reward.

**Do not fade the row out. Do not show confetti.**

The row collapses upward toward the rail over roughly 320 ms while the filled
portion of the rail extends downward past it. The next item's leader line
draws in. The whole thing reads as the day advancing, not as a task
disappearing.

**The one exception:** the very first completed item, ever. A single ink drop
ripple expands once from the rail notch. It fires once in the lifetime of the
install, flagged by `first_completion_seen`. That restraint is what makes it
land.

### Milestones

Nine moments, listed in `appflow.md` section 8.3. Each fires once in the lifetime
of the install. The design job here is **restraint**, because praise that arrives
often stops being praise.

- A milestone is a **card that slides up from the bottom of Today**, not a modal,
  not a full screen takeover, not a dialog that must be dismissed
- It uses `primaryContainer` as its ground, which is the only time that token
  appears at card size anywhere in the app. That is what makes it register
- **No confetti. No badge. No trophy. No emoji. No sound.** One haptic
  `LONG_PRESS`, and the rail draws its filled section one step further
- It carries a number, then at most six words. `Halfway. +1.0 of +2.0.
  16 days left.`
- It auto dismisses after eight seconds, or on any tap or scroll. It never
  requires acknowledgement
- **Goal reached** is the one exception and gets a full screen: the whole run
  drawn as one continuous rail, the start and end numbers, and one question about
  what comes next

### The countdown

- Lives in the daily close card only. It is never in the top bar, never a
  persistent widget element, never in a notification
- The progress bar is the rail vocabulary, not a Material `LinearProgressIndicator`:
  a `2.dp` horizontal line, filled portion in `primary`, remainder in `outline`
- The projected value is set in `bodyMedium`, never emphasised, never coloured
  red or amber. It is a fact, not a warning
- **On a `POOR` day the countdown and the bar are not rendered at all.** Not
  greyed, not collapsed. Absent. See `rules.md` section 2 rule 8

### Reduced motion

If `Settings.Global.ANIMATOR_DURATION_SCALE` is 0, every spring becomes an
instant state change. Nothing breaks, nothing is lost.

---

## 7. Haptics

Distinct patterns so the app is legible without looking at it, which matters at
06:00 and in a gym.

| Action | Constant |
|---|---|
| Done | `CONFIRM` |
| Done, minimum | `CLOCK_TICK` |
| Skip | **none**, deliberately. Skipping must feel neutral, not punished |
| Block complete | `LONG_PRESS` |
| Wake alarm dismissed | `SEGMENT_FREQUENT_TICK` |
| Snooze | `CLOCK_TICK` |

---

## 8. Iconography

- Material Symbols **Rounded**, weight 400, optical size 24, grade 0
- Filled variant only for an active or selected state
- The four state glyphs in section 5.3 are custom and override Material
- **No emoji anywhere in the product UI.** Not in tabs, not in item defaults,
  not in notifications, not in the widget. A user may type an emoji into their
  own item title and that is fine. We never ship one
- No stock illustration. Empty states are typography plus one line of the rail

---

## 9. Layout

- Spacing scale: `4, 8, 12, 16, 20, 24, 32, 48`. Nothing off scale
- Screen horizontal padding: `20.dp`
- Timeline content starts at `56.dp` from the left edge (rail at 24, leader 12,
  gap 20)
- **Touch targets: `56.dp` minimum for any primary action.** The standard 48 is
  not enough for a half awake user or sweaty hands. Secondary actions may use 48
- Nothing is centred on the Today screen. The layout is left anchored, which is
  what makes it read as a ledger rather than a marketing page
- One accent colour per screen. If two things are both rust, one of them is
  wrong

---

## 10. Tone of voice

Full rules in `rules.md` section 2. Applied examples:

| Situation | Write this | Never this |
|---|---|---|
| Item due | `Shake 2` / `6:00 PM` | `Time for your Shake 2!` |
| Progress | `5 of 12 done` | `You have completed 41% of your habits` |
| Missed | `Missed at 11:00` | `You failed to complete this` |
| Two misses | `Missed twice this week. Want the minimum version tomorrow?` | `Your streak is in danger!` |
| Weekly report | `4 of 7 days. 3 h 20 m.` | `Great work this week, keep it up!` |
| Empty state | `Nothing scheduled yet.` then a single button | `Start your journey today` |
| Paywall | `Pro adds unlimited plans and day templates.` | `Unlock your full potential` |
| Late wake | `You are 90 minutes behind. Shift the day?` | `Oh no, you overslept!` |
| Reliability degraded | `Alarms may be up to 15 minutes late. Two settings will fix it.` | `Warning! Your alarms are broken!` |

---

## 11. Notification design

Notifications are the primary surface of this app. Eighty percent of
interactions should end without opening it.

- One channel per salience tier: `alarm`, `notify`, `silent`, `service`
- Actions, maximum three visible: `Done`, `Minimum`, `+10`. `Skip` and `+30`
  live in the expanded view
- The item title is the notification title. The detail line is the body. No app
  name prefix, no emoji, no exclamation
- Snooze consequence preview renders in `BigTextStyle` in the expanded view
- Measurement items use `RemoteInput` so a weight is logged without opening the
  app
- The active block uses `ProgressStyle` and is promoted as an Android 16 Live
  Update, so the day appears as a status bar chip
- The wake alarm uses a full screen intent when granted, with the volume ramp
  described in section 2

---

## 12. Widget

Glance. One size that works, not five that are mediocre.

- 4 x 2: the next item, its time, a one tap `Done` target sized at `56.dp`, and
  a compact rail showing the rest of the day
- Same palette, same grain is omitted (widgets should stay flat and cheap)
- Updates on completion, on schedule change, and at most every 15 minutes

---

## 13. Accessibility

- Text contrast: 4.5:1 minimum, 7:1 for body text on `surface`
- Never encode state in colour alone. Every state has a glyph
- Every interactive element has a `contentDescription` that says what it does,
  not what it looks like
- The app is fully usable at the largest system font scale. Test at 200%
- Full TalkBack pass on the Today screen, block runner, and wake alarm before
  each release
- Timer screens announce remaining time politely, not on every tick

---

## 14. Pre merge design checklist

Run this before any UI work is called done. Every item is a hard fail.

- [ ] No purple, violet, neon, or cyan anywhere
- [ ] No decorative gradient (the wake alarm ramp is the only exception)
- [ ] No glassmorphism, blur panel, or glowing border
- [ ] Inter is not in the project
- [ ] Light mode is the default and looks finished, not like an afterthought
- [ ] No element has both a hairline border and a diffuse shadow
- [ ] At least two different corner radii are in use, matching section 4
- [ ] No emoji in any shipped string, layout, or notification
- [ ] Fraunces appears in at most three places on the screen
- [ ] Every changing number uses tabular figures
- [ ] Nothing on the Today screen is horizontally centred
- [ ] Primary touch targets are 56 dp
- [ ] Renders correctly in dark mode and at 200% font scale
- [ ] Copy contains no em dash, no en dash, no exclamation mark, and none of the
      banned words in `rules.md` section 2
