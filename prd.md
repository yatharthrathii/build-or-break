# Build or Break - Product Requirements

Version 1.0. Owner: solo developer. Read `rules.md` first.

---

## 1. The problem

People who want to change something about their life do not lack a plan. Free
AI tools produce a good plan in thirty seconds. What they lack is a system that
survives contact with a real day.

Existing tools split into two useless halves:

- **Habit trackers** (Loop, Habitify, HabitKit) are checkbox grids. They record
  what happened. They do not run your day.
- **Routine apps** (Routinery) run timed sequences but bolt them to fixed clock
  times. The top complaint in their reviews is exactly this: a day that starts
  at 06:00 on Monday and 08:00 on Saturday forces you to build two routines or
  live permanently out of sync.
- **AI habit apps** generate a plan and then abandon you at execution.

Nobody has built the layer in between: a scheduler that holds a real plan,
fires reliably, and reshapes itself around the day you actually had.

## 2. The product in one sentence

Build or Break takes a plan you already have and runs it, adjusting to your
actual day instead of demanding a perfect one.

## 3. Who it is for

**Primary user, V1.** Working professionals aged 22 to 35 with a desk job and a
concrete personal goal that has a daily schedule attached: weight gain or loss,
medication adherence, a gym programme, fixing a sleep cycle, learning a skill
in a fixed evening slot. They are comfortable with an AI tool producing the
plan. They are not comfortable with an app that guilts them.

**Explicit non user, V1.** Someone who wants the app to tell them what to do.
We do not produce plans.

## 4. What makes this different

Six things. Everything else is table stakes.

| # | Capability | Why nobody has it |
|---|---|---|
| 1 | **Day templates.** Office day, work from home, rest day, travel, sick day. One tap in the morning reshapes the whole timeline | Competitors model a routine as a fixed list. This needs the schedule to be a resolved projection, not stored rows |
| 2 | **Minimum version.** Every item carries a smaller fallback the user defined in advance. On a bad day the notification offers it | Requires trusting the user to scale down. Most apps chase streak pressure instead |
| 3 | **Snooze with visible consequence.** The notification shows what shifting this item does to the rest of the day, before you shift it | Only possible if you have a real cascade engine |
| 4 | **Whole day shift.** Woke up ninety minutes late? One tap moves the day, keeping anchored items in place | Needs anchor types, which nobody models |
| 5 | **Tiered delivery with an audit log.** The app knows which reliability tier it is running at, tells the user how to improve it, and measures its own accuracy | Unglamorous platform work. AI first competitors skip it |
| 6 | **Learning tracks.** A timeline slot that advances through an ordered syllabus, with a "where you stopped" note carried into the next session | Habit apps model repetition. Learning is progression. Different data shape |

## 5. Goals and non goals

**Goals**
- The developer uses it daily for a real weight gain routine and it holds up
- Alarm delivery is measurably reliable on Indian mid range Android hardware
- Ships to Play Store production as a portfolio grade, open source Android app
- Monetization is wired in from the first commit, so nothing needs rewriting

**Non goals for V1**
- iOS. The platform has no exact alarm API. The core promise cannot be kept
- Cloud sync, accounts, social features, leaderboards
- Generating plans, diet advice, or exercise content
- Bad habit breaking (designed for, but not shipped)
- Project or task management

## 6. Feature scope

### V1: ships to production

**Plan structure**
- One active plan (free tier limit)
- Day templates with a weekday mask and manual override
- Items with four anchor types: FIXED, RELATIVE, WINDOW, INTERVAL
- Blocks: a container that groups consecutive micro steps into one notification
  and a guided run screen
- Per item minimum version (title plus optional duration)
- Per item salience tier: ALARM, NOTIFY, SILENT, TIMELINE
- Per item weekday mask (gym on Mon, Wed, Fri only)
- Inline value capture for measurement items (weight, reps, pages)

**Execution**
- Tiered alarm delivery with runtime capability detection and fallback
- Full screen wake alarm with a sixty second volume ramp
- The wake alarm's dismiss action chains directly into the first task
- Notification actions: Done, Minimum, plus 10, plus 30, Skip
- Guided block run screen with per step timer
- Foreground service during an active block
- Reboot, timezone change, time change, and date change recovery

**Adaptation**
- Snooze consequence preview
- Whole day shift
- Day template switching
- Backfill within twenty four hours

**Reflection**
- Optional skip reason: chips plus free text, never mandatory
- Consistency ring over the trailing thirty days, no resettable streak counter
- Weekly report, computed locally, no network
- Delivery audit log and a Reliability screen
- Export to CSV and Markdown

**Platform**
- Light and dark, own colour identity, dynamic colour as an opt in
- Glance home screen widget: next item, one tap done
- Android 16 Live Updates: the day as a live status bar chip
- Full TalkBack support and large font support

### V1.5: after closed testing feedback

- Learning tracks: `Track`, `TrackUnit`, `TrackSession`, "where you stopped"
  note, completion ETA projection
- Median based auto time shift suggestions
- Never miss twice intervention
- Skip reason pattern detection, local, no AI
- Hindi and Hinglish locales
- Paywall live, subscriptions and lifetime purchase enabled

### V2: after traction

- Break mode: risk windows, urge surfing timer, urge log, saved counter
- Temptation bundling via per item deep links
- AI weekly narrative using the user's own API key
- Optional geofence nudges
- Wear OS complication

### Never

- Ads
- Analytics or tracking SDKs
- Selling or transmitting user data
- Social feed, friend streaks, public leaderboards
- Plan or content generation

## 7. Monetization

Locked now so the architecture never needs to change. The gate is enforced only
through `EntitlementRepository.has(Feature)`.

### Tier split

| Capability | Free | Pro |
|---|---|---|
| Active plans | 1 | Unlimited |
| Day templates per plan | 2 | Unlimited |
| Items per template | 15 | Unlimited |
| All alarm and delivery reliability | Yes | Yes |
| Minimum versions | Yes | Yes |
| Day shift, snooze preview, block run | Yes | Yes |
| Widget | Yes | Yes |
| Weekly report | Current week | Full history |
| History retention (visible) | 30 days | Unlimited |
| Data export | Yes | Yes |
| Learning tracks | 1 track | Unlimited |
| AI insights (own key) | No | Yes |
| Themes | Default only | All |

Rationale: a free user with one goal has a complete, excellent product. That is
deliberate. It is what earns the review, the word of mouth, and the eventual
upgrade. We charge the person running three goals across five day templates,
who is by definition already getting value.

### Pricing

| SKU | India | Global (US reference) |
|---|---|---|
| Monthly | INR 149 | USD 3.99 |
| Annual | INR 999 | USD 24.99 |
| Lifetime | INR 1,499 | USD 39.99 |

Regional pricing configured through Play Console. Lifetime is prominent in
India, where subscription resistance is high and one time payment converts
better.

### Implementation

- **RevenueCat**, free up to USD 2,500 monthly tracked revenue, then 1%. It is
  free at our scale and removes weeks of Play Billing edge case work.
- Wrapped entirely inside `:billing`. Features see only our own interface, so
  moving to raw Play Billing later is a one module change.
- Entitlement state is cached locally and readable offline. A network failure
  never downgrades a paying user.
- Paywall triggers are listed in `appflow.md`.

### Honest revenue expectation

Do not plan around a good outcome. HabitKit, the best documented solo habit app,
reached roughly USD 28,000 monthly recurring revenue from about 25,000
subscribers, and it took eighteen months of building in public. Median indie app
revenue is close to zero. Plan for USD 0 to 50 per month in the first quarter
after launch and treat anything above that as information, not income.

The realistic asset being built here is the portfolio piece and the shipped
product. Revenue is a secondary outcome.

## 8. Success metrics

Measured personally and through the twelve closed testers. No analytics SDK.

| Metric | Target |
|---|---|
| Developer daily use | 30 consecutive days without abandoning |
| Alarm delivery within 60 s | 95% or better, from the audit log |
| Testers still opted in at day 14 | 12 of 12 |
| Testers who used the app in week 2 | 8 of 12 or better |
| Crash free sessions | 99.5% or better |
| Cold start | under 500 ms |
| Play production access | granted |

## 9. Risks and mitigations

| Risk | Severity | Mitigation |
|---|---|---|
| OEM battery managers kill alarms on Xiaomi, Oppo, Vivo, Realme | Critical | Tiered delivery, foreground service, device specific onboarding, audit log to prove it works |
| Play rejects the restricted permission declaration | High | Position the listing as an alarm and reminder app. Ship a working fallback so a rejection degrades the app rather than blocking release |
| 22 item plans produce notification fatigue | High | Salience tiers, notification budget warning, blocks that collapse micro steps |
| Twelve tester requirement delays launch | Medium | Start recruiting at milestone M0, run the clock in parallel |
| Developer is the only validated user | Medium | Accepted. Closed testing is the first external signal. Do not assume it generalises |
| Scope creep into plan generation or task management | Medium | Section 5 non goals. Re read before adding anything |

## 10. Competitive positioning

| App | Strength | The gap we fill |
|---|---|---|
| Routinery | Best in class timed step execution, 5M plus users | Fixed start times, aggressive paywall, no adaptation |
| Loop Habit Tracker | Free, offline, open source, trusted | Reminders and checkboxes only, no sequenced execution |
| Fabulous | Behavioural science depth, 37M users | Prescriptive content platform, subscription, cloud |
| Habitify, Way of Life | Skip reason logging | No timed execution, no adaptation |
| Planit, BeeDone, Habit AI | AI plan generation | Weak execution, no reliability work, subscription |

Our sentence in the store listing: a routine alarm and reminder app that
reshapes your day around what actually happened.
