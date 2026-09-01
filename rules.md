# Build or Break - Global Rules

This is the constitution of the project. Every other document, every commit, and
every design decision answers to this file. If a rule here conflicts with
something in another doc, this file wins and the other doc gets fixed.

Read this first, every session.

---

## 0. Document map

| File | What it holds | When to read it |
|---|---|---|
| `rules.md` | This file. Non negotiables. | Every session, first |
| `prd.md` | What we are building and why. Scope, features, monetization | Before adding any feature |
| `techspec.md` | Stack, modules, architecture, platform strategy | Before writing code |
| `schema.md` | Domain model, Room entities, migrations | Before touching data |
| `design.md` | Design system, tokens, tone of voice | Before touching UI |
| `appflow.md` | Every screen and every flow, including notifications | Before building a screen |
| `implementationPlan.md` | Phased build order with exit criteria | At the start of a milestone |
| `tracker.md` | Live progress, updated as work happens | Every working session |

Sections 10 and 11 cover the git history and the README. Read them before the
first commit, not after fifty of them.

---

## 1. Product non negotiables

1. **This app is an execution layer, not a content platform.**
   The user brings the plan. We schedule it, fire it, track it, and report on
   it. We do not author fitness advice, diet plans, or curricula. Ever.

2. **The app must be fully useful with zero network access.**
   No account. No signup wall. No cloud dependency for any core function.
   If a feature cannot work offline, it is not a core feature.

3. **The app bends to the user's day. It never punishes the user.**
   Every scheduling decision has an escape hatch: shift, minimum version,
   snooze with visible consequence, or skip without guilt.

4. **Notification budget is sacred.**
   A single day must never fire more than 3 ALARM tier and 10 NOTIFY tier
   events. If a plan exceeds this, the app tells the user and helps them
   downgrade items. It does not silently fire everything.

5. **Never gate reliability behind a paywall.**
   Alarms, notifications, delivery reliability, and data export are free for
   everyone, forever. We monetize breadth, never the core promise.

6. **The user's data belongs to the user.**
   One tap export, human readable format, no lock in. Uninstalling the app
   removes everything.

---

## 2. Writing rules (code comments, UI copy, docs, commits)

1. **Never use an em dash or an en dash.** Not in code, not in UI strings, not
   in docs, not in commit messages. Use a plain hyphen, a comma, a colon, or
   split the sentence into two.

2. **No filler adjectives in UI copy.** Banned: seamless, effortless, powerful,
   revolutionary, smart (as a marketing word), journey, unlock your potential,
   transform your life, level up.

3. **Second person, present tense, short.** "Shake time" not "It is time for
   your shake". "3 of 12 done" not "You have completed 3 out of 12 habits".

4. **Never compare the user to an ideal. Only to their own past.**
   Good: "2 more than last week". Bad: "You are 40% below target".

5. **A miss is never described as a failure.** Use "missed", "did not happen",
   "skipped". Never "failed", "you broke your streak", "you lost".

6. **No exclamation marks in the app.** One exception: the first ever completed
   step. That is the only place it earns its keep.

7. **Numbers before adjectives.** "47 minutes" beats "a good session".

---

## 3. Design non negotiables (the anti generic rules)

The single most important design goal: **this app must not look generated.**
The following are hard bans. Full detail and the positive system live in
`design.md`.

**Banned outright:**
- Purple or violet as a primary or accent colour
- Gradients used as decoration: gradient buttons, gradient orbs, gradient heroes
- Glassmorphism, frosted blur panels, glowing borders
- Inter as a typeface
- Neon on dark, cyan on dark
- Dark mode as the product default. Light is the default, dark follows system
- A hairline border paired with a wide diffuse shadow on the same element
- One uniform corner radius applied to every element
- Emoji used as a functional icon anywhere in the product UI
- A centred hero block with three equal rounded cards below it
- Stock illustration of any kind

**Required instead:**
- One committed colour identity with a warm, unusual accent (see `design.md`)
- Elevation expressed through tonal surface shifts, not shadows
- A deliberate radius scale where different roles get different radii
- An asymmetric, left anchored timeline with a real rail, not stacked cards
- Tabular figures for every number that changes

---

## 4. Code rules

1. **`:core:domain` has zero Android imports.** It is a pure JVM module. If you
   are tempted to import `android.*` there, the design is wrong.

2. **No business logic in composables.** Composables read state and emit events.
   That is all.

3. **UI state is a single immutable data class per screen**, exposed as
   `StateFlow<UiState>`. No mutable state escapes a ViewModel.

4. **Every dependency is injected, including the clock.**
   Never call `LocalDateTime.now()` directly. Use the injected `TimeProvider`.
   This is not optional. Time is the core of this app, and untestable time makes
   the whole product untestable.

5. **Never call a third party SDK outside its own module.**
   RevenueCat only inside `:billing`. Room only inside `:core:data`. This is
   what lets us replace them without touching features.

6. **Every new persisted field ships with a migration and a migration test.**

7. **No `!!`. No swallowed exceptions. No `GlobalScope`.**

8. **Public API of every module gets KDoc.** Internal implementation does not
   need comment noise. Comment why, never what.

9. **Warnings are errors in CI.** Detekt and ktlint must pass. No suppression
   without a one line reason attached to the suppression itself.

10. **One class, one reason to change.** If a file passes 300 lines, stop and
    ask what it is doing twice.

---

## 5. Performance budgets

| Metric | Budget |
|---|---|
| Cold start to first frame | under 500 ms on a mid range device |
| Today screen fully rendered | under 700 ms cold |
| Release APK size | under 12 MB |
| Timeline scroll | zero janky frames in macrobenchmark |
| Alarm delivery accuracy | 95% or better within 60 s, per the audit log |
| Today screen data load | one query, under 16 ms |
| Baseline profile | regenerated and committed before every release |

Release builds always run R8 full mode, `isMinifyEnabled = true`, and
`isShrinkResources = true`. No exceptions.

---

## 6. Monetization rules (locked now so nothing breaks later)

1. **Every gated capability is checked through one interface only:**
   `EntitlementRepository.has(Feature.X)`. Features never talk to billing.
2. **The free tier is genuinely complete for one goal.** We gate breadth
   (multiple plans, multiple day templates, learning tracks, long history,
   AI insights), never depth.
3. **The paywall is contextual, never a startup wall.** It appears at the moment
   the user reaches for a gated capability, with that capability named.
4. **Nothing the user already created is ever taken away** on downgrade. The
   data stays and stays visible. Only editing beyond the free limit locks.
5. **A lifetime purchase ships on day one.** For India it matters more than the
   subscription.
6. Full tier split lives in `prd.md`.

---

## 7. Privacy rules

1. The default state of the app collects and transmits **nothing**. The Play
   Data Safety form must be able to honestly say "no data collected".
2. Crash reporting is **opt in**, off by default, and plainly explained.
3. Any AI feature is opt in, uses the user's own API key, sends aggregated
   summaries only, never raw logs, and shows exactly what will be sent before
   it sends.
4. No analytics SDK. Not now, not later. If we need product signal, we ask
   users directly.
5. No advertising ID, no attribution SDK, no third party trackers.

---

## 8. Play Store rules

1. `targetSdk` is always the current Play requirement. Today that is 36.
2. `USE_EXACT_ALARM` and `USE_FULL_SCREEN_INTENT` are restricted permissions.
   Both need a Play Console declaration. The store listing must present the app
   primarily as a routine alarm and reminder app, so the declaration matches
   what a reviewer actually sees.
3. Every privileged capability has a runtime check and a working fallback. The
   app stays useful at the lowest capability tier.
4. Closed testing with 12 testers for 14 continuous days is required before
   production access. Start that clock early, in parallel with development.

---

## 9. Language and localisation

- V1 ships in **English only**, written by a human, following section 2.
- All user visible strings live in `strings.xml` from the first commit. No
  hardcoded strings in composables, ever.
- Hindi and a Hinglish variant are planned for V1.5. Do not paint us into a
  corner: no string concatenation, use placeholders and proper plurals.

---

## 10. Git history rules

The commit history is read by the same people who read the code. A history that
looks machine written undoes the work the code does. These rules exist to make
it read like a person built this over months, because that is what happened.

### Subject line

- **Imperative mood, lowercase, no full stop.**
  `add interval anchor expansion`, not `Added Interval Anchor Expansion.`
- **Under 60 characters.** If it does not fit, the commit is doing two things
- **No conventional commit prefixes.** No `feat:`, `fix:`, `chore:`, `refactor:`.
  A tidy taxonomy on a solo repo is a machine tell, and it adds nothing a human
  reader needs
- **No gitmoji.** No emoji anywhere in a commit message
- Scope in plain words when it helps: `scheduler: drop to inexact tier when
  permission is revoked`

### Body

- **Most commits have no body.** This is the single most important rule here.
  A history where every commit carries a three bullet body is machine written
  and everybody can tell
- Write a body only when the **why** is not visible in the diff. Then write two
  or three sentences of prose, not bullets
- **Never restate the diff.** `adds a new function called resolveInterval` is
  noise. `Xiaomi fires these up to 40 seconds early, so the window check has to
  be inclusive on both ends` is worth writing down
- Never begin with `This commit`, `This change`, or `In this PR`
- No em dash, no en dash. Section 2 applies here too

### Shape of the history

- **Commit at the granularity you actually work at.** Small, frequent, sometimes
  incomplete on a branch. A history of twelve perfect, self contained, equally
  sized commits is not what real work looks like
- Small follow up commits are welcome and expected: `fix rail alignment at
  200% font scale`, `bump room to 2.8.1`, `remove stray log`
- It is fine for a branch to contain a commit that fixes the previous one. Do
  not rewrite history to hide that
- Do not squash a week of work into one commit to make it look tidy

### Identity

**This machine's global git config belongs to the owner's employer.** It must
never be used for this repository.

```
global user.name : yatharthcodevibe
global user.email: yatharth.rathi@codevibe.in     <- company, do not use
```

The owner will supply a personal GitHub username and email. Until then:

- Do not run `git init` in this project
- Do not set any git config, local or global
- Do not create a remote, a branch, a commit, or a push

When the personal details arrive, set them **locally only**, so the employer's
global config stays untouched:

```
git config --local user.name  "<personal name>"
git config --local user.email "<personal email>"
```

Then verify before the first commit:

```
git config --local --get user.email
git log -1 --format='%an <%ae>'
```

**Known trap:** setting `user.email` only changes what is written into the
commit. It does not change which credentials push. Windows Credential Manager
almost certainly has the company GitHub account cached, so the first push can
still land under the wrong account. Check Credential Manager, or use a personal
access token or SSH key scoped to the personal account, before pushing.

**Status:** done. The repository is initialised and `user.name` and `user.email`
are set locally to the owner's personal identity. The global config was not
touched. Nothing has been pushed and no remote has been added.

### Attribution

Whether to record AI assistance in commit trailers is the repository owner's
call, not a default. Ask once, record the answer here, and then apply it
consistently. Do not silently add or silently omit a co author trailer.

**Decision:** no co author trailer. Commits carry the owner's name and email
only. This follows from the standing requirement that nothing about this project
reads as machine written, and the owner is the author of record.

If that call is ever reversed, reverse it going forward rather than rewriting
history.

### Branches

- `main` is always green. Never commit directly to it once CI exists
- Branch names: `m2-timeline-engine`, `m4-alarm-tiers`, `fix-dst-fall-back`.
  Lowercase, hyphens, tied to a milestone in `implementationPlan.md` where one
  applies
- No `feature/`, `bugfix/`, `hotfix/` prefixes

---

## 11. README rules

The README is the first thing a recruiter or an engineer reads, and it is the
easiest thing in the repository to get wrong. The default open source README
template is instantly recognisable and says nothing.

### Banned

- Emoji section headers. No `## ✨ Features`, no `## 🚀 Getting Started`,
  no `## 🛠️ Tech Stack`
- A wall of shields.io badges. Two are enough: CI status and licence
- A table of contents on a document under 300 lines
- `Contributing`, `Code of Conduct`, `Acknowledgements` sections on a solo
  project that takes no contributions
- `Made with ❤️`, `Star this repo`, `Feel free to`, `Happy coding`
- A feature list of twenty bullets, each one a marketing phrase
- Any sentence that could appear in the README of a different app unchanged

### Required

- **Open with why it exists, in first person, in three or four sentences.**
  The real story: a weight gain routine, things being missed, no app that
  adapted. That paragraph is the most valuable thing on the page
- **A real screenshot or a short screen recording above the fold.** Not a
  mockup, not a generated hero image
- **Name the hard problem and what was done about it.** Alarm delivery across
  Doze, OEM battery managers, and Play's restricted permissions, solved with a
  tiered strategy. This is the part an engineer reads
- **Publish the measured numbers**, from the delivery audit log, with the sample
  size and the devices. `98.6% of 412 alarms within 60 s across 4 devices` is
  worth more than every adjective on the page
- **An architecture section with a real diagram** and one line per module
- **State the limitations honestly.** Android only, no sync, no accounts, and
  the reasons. Confidence reads as competence, and it is also just true
- Link to `docs/adr/` so a reader can see the reasoning, not just the result

### Tone

Same as section 2. Short sentences, no filler adjectives, no exclamation marks,
no em dash, no en dash. Write it the way you would explain the project to
another developer sitting next to you.

---

## 12. Definition of done

A task is done when all of the following are true:

- [ ] Compiles with zero warnings
- [ ] Detekt and ktlint pass
- [ ] Unit tests exist for the logic and pass
- [ ] If it touches persistence, a migration and a migration test exist
- [ ] If it touches UI, it renders correctly in light and dark, and at the
      largest system font size
- [ ] If it touches scheduling, it has been verified on a physical device
- [ ] No hardcoded strings, colours, or dimensions
- [ ] `tracker.md` is updated
