# Build or Break - Architecture

Read `rules.md` first, then `techspec.md` for the stack. This document is the
blueprint. It defines what talks to what, what every contract looks like, and
how a change flows through the system.

If something you are about to write does not fit here, stop and change this
document first. Code that quietly breaks the architecture is how a project
becomes unmaintainable in month three.

---

## 1. The one idea everything else follows from

**The plan is stored. What happened is stored. The day is computed.**

```
Item, Block, DayTemplate   ->  stored.   This is the plan
Occurrence, Measurement    ->  stored.   This is what happened
ResolvedDay                ->  computed. Never stored, ever
```

`ResolvedDay` is a projection of `plan + occurrences + dayShift + date + zone`.
It is recomputed on every read. It is never cached in the database and never
written to disk.

**Why this matters more than any other decision here:** the moment a resolved
schedule is stored, it can disagree with the plan. The user edits an item, the
stored schedule still holds the old time, an alarm fires at the wrong moment,
and nobody can tell which of the two is correct. Recomputing removes that entire
class of bug permanently.

The one exception: an `Occurrence` row is materialised when an item is first
scheduled or first interacted with, because an alarm needs a concrete row to
point at. It records `plannedAt`, which is what the resolver said at scheduling
time, and it is reconciled on every resolve.

---

## 2. Layers

```
        Compose UI
            |  events up as method calls
            v
        ViewModel                        holds UiState, no logic
            |
            v
        UseCase                          orchestration, suspend
            |                    \
            v                     v
    Repository (interface)    Domain service (pure, sync)
            |                     ^
            v                     |
    Repository (impl)         no IO, no Android, fully testable
            |
    Room DAO / DataStore
```

Platform work sits beside this, never inside it:

```
    UseCase  ->  AlarmGateway (interface, in :core:domain)
                     ^
                     |
                 AlarmGatewayImpl (in :scheduler, touches AlarmManager)
```

### Hard rules

1. A composable never calls a repository, a use case, or a gateway
2. A ViewModel never touches Room, DataStore, AlarmManager or any Android
   framework class other than what Compose and Hilt need
3. A domain service is a pure function. Same input, same output, no IO, no time
   lookup except through the injected `TimeProvider`
4. A repository interface lives in `:core:domain`. Its implementation lives in
   `:core:data`. Nothing else may implement it
5. Room entities, DataStore keys and any SDK type never leave `:core:data`
6. `:core:domain` cannot import Android. This is enforced by the build, not by
   discipline, because it is a `kotlin("jvm")` module

---

## 3. Module contract matrix

Read this as "the row may depend on the column".

| | model | common | domain | data | designsystem | scheduler | billing | widget |
|---|---|---|---|---|---|---|---|---|
| **app** | yes | yes | yes | yes | yes | yes | yes | yes |
| **domain** | yes | yes | - | **no** | no | no | no | no |
| **data** | yes | yes | yes | - | no | no | no | no |
| **scheduler** | yes | yes | yes | yes | no | - | no | no |
| **billing** | yes | yes | yes | no | no | no | - | no |
| **widget** | yes | yes | yes | no | yes | no | no | - |
| **designsystem** | yes | no | no | no | - | no | no | no |
| **model** | - | no | no | no | no | no | no | no |
| **common** | no | - | no | no | no | no | no | no |

The single most important cell: **domain to data is `no`.** The domain declares
what it needs as an interface. Data satisfies it. Never the other way round.

---

## 4. Package structure

### `:core:model`

Pure data. No logic beyond derived properties that cannot fail.

```
com.buildorbreak.core.model/
    plan/        Plan, DayTemplate, Block, Item, Anchor, MinimumVersion
    execution/   Occurrence, SkipReason, Measurement, DayLog
    goal/        Goal, GoalProgress, DayClose, MilestoneAward
    track/       Track, TrackUnit, TrackSession
    resolved/    ResolvedDay, ResolvedEntry, BudgetWarning, CascadePreview
    audit/       DeliveryAudit, DeliveryTier
    enums/       every enum in schema.md section 1
```

### `:core:domain`

```
com.buildorbreak.core.domain/
    resolver/    TimelineResolver, AnchorResolver, IntervalExpander,
                 CycleDetector, DayShifter, SalienceBudget, CascadeCalculator
    goal/        GoalCalculator, MovingAverage, PaceProjector,
                 DayQualityClassifier, MilestoneEvaluator
    review/      WeeklyReviewBuilder, ReviewStory, TimeShiftDetector,
                 SkipPatternDetector, CatchUpPlanner
    parse/       PlanTextParser, SyllabusParser, ParseResult
    export/      ExportBuilder
    repository/  every repository interface
    gateway/     AlarmGateway, NotificationGateway, WidgetGateway
    usecase/     one file per use case
    error/       DomainError sealed hierarchy
```

### `:core:data`

```
com.buildorbreak.core.data/
    database/    BuildOrBreakDatabase, Converters, migrations
    entity/      every @Entity, suffixed Entity
    dao/         every @Dao
    mapper/      entity to model and back, one file per aggregate
    repository/  every repository implementation, suffixed Impl
    datastore/   PreferencesDataSource, SecurePreferences
    di/          DataModule, DatabaseModule
```

### `:scheduler`

```
com.buildorbreak.scheduler/
    alarm/       AlarmGatewayImpl, TierDetector, AlarmScheduling,
                 AlarmReceiver, ExactAlarmPermission
    notification/ NotificationGatewayImpl, Channels, Builders,
                 NotificationActionReceiver, RemoteInputHandler
    service/     BlockRunnerService
    receiver/    BootReceiver, TimeChangeReceiver, PackageReplacedReceiver
    work/        DailyMaintenanceWorker
    oem/         OemGuide, VendorIntents
    live/        LiveUpdateController
    di/          SchedulerModule
```

### `:app`

```
com.buildorbreak.app/
    MainActivity, BuildOrBreakApplication
    navigation/  Routes, NavGraph
    di/          CoreModule, AppModule
    feature/
        today/       TodayScreen, TodayViewModel, TodayUiState, components/
        plan/        PlanScreen, editors, import/
        insights/    InsightsScreen, review/, charts/
        settings/    SettingsScreen, reliability/, appearance/, data/
        onboarding/  OnboardingScreen, permission/, oem/
        paywall/     PaywallScreen
    common/      shared composables that are not design system primitives
```

`techspec.md` section 3 gives the rule for promoting a feature package to its
own module. Do not do it early.

---

## 5. The contracts

These signatures are the design. Write them first, in this order, before any
implementation.

### 5.1 Domain services, pure and synchronous

```kotlin
fun interface TimelineResolver {
    fun resolve(input: ResolveInput): ResolvedDay
}

data class ResolveInput(
    val template: DayTemplate,
    val blocks: List<Block>,
    val items: List<Item>,
    val occurrences: List<Occurrence>,
    val date: LocalDate,
    val zone: ZoneId,
    val dayShift: Duration,
    val mode: DayMode,
)

fun interface CascadeCalculator {
    /** Runs resolve twice and diffs. Drives the snooze consequence preview. */
    fun preview(input: ResolveInput, itemId: Long, shift: Duration): CascadePreview
}

interface GoalCalculator {
    fun smooth(readings: List<Reading>): List<Double>          // 7 day window
    fun paceTarget(goal: Goal, on: LocalDate): Double
    fun project(goal: Goal, progress: List<GoalProgress>): Double
    fun percentComplete(goal: Goal, current: Double): Float
}

fun interface DayQualityClassifier {
    fun classify(done: Int, minimum: Int, total: Int): DayQuality
}

interface MilestoneEvaluator {
    /**
     * Returns at most one milestone, already filtered by the four suppression
     * rules in appflow.md section 8.3. A POOR day always returns null.
     */
    fun evaluate(context: MilestoneContext): Milestone?
}

interface WeeklyReviewBuilder {
    fun build(input: ReviewInput): WeeklyReview
}

/** The seven stories in techspec.md. The report has no other shape. */
enum class ReviewStory {
    ON_TRACK, PLAN_TOO_SMALL, TIMING_PROBLEM,
    REMINDER_PROBLEM, LOSING_GRIP, SETTLING_IN, MIXED,
}
```

Every one of these is a pure function. Every one is unit tested with fixed
inputs and a `FakeTimeProvider`. None of them can perform IO, so none of them
can fail in a way that needs an error type.

### 5.2 Repositories

Interfaces in `:core:domain`, implementations in `:core:data`. Reads return
`Flow`. Writes are `suspend` and return `Outcome`.

```kotlin
interface OccurrenceRepository {
    fun observeForDate(date: LocalDate): Flow<List<Occurrence>>
    suspend fun materialise(items: List<Item>, date: LocalDate): Outcome<Unit, DataError>
    suspend fun settle(id: Long, state: OccurrenceState, at: Instant): Outcome<Unit, DataError>
    suspend fun shift(id: Long, by: Duration): Outcome<Occurrence, DataError>
    suspend fun pendingBefore(instant: Instant): List<Occurrence>
}

interface GoalRepository {
    fun observeActive(planId: Long): Flow<Goal?>
    fun observeProgress(goalId: Long): Flow<List<GoalProgress>>
    suspend fun upsertProgress(progress: GoalProgress): Outcome<Unit, DataError>
    suspend fun setWeekCounted(goalId: Long, week: LocalDate, counted: Boolean): Outcome<Unit, DataError>
}
```

The rest follow the same shape: `PlanRepository`, `TemplateRepository`,
`ItemRepository`, `MeasurementRepository`, `DayCloseRepository`,
`MilestoneRepository`, `TrackRepository`, `DeliveryAuditRepository`,
`PreferencesRepository`, `EntitlementRepository`.

**A repository never contains business logic.** It reads, writes and maps. If
you find yourself writing an `if` about the product in a repository, that logic
belongs in a domain service.

### 5.3 Gateways

The platform, expressed as an interface the domain owns.

```kotlin
interface AlarmGateway {
    fun currentTier(): DeliveryTier
    suspend fun schedule(occurrence: Occurrence, item: Item): Outcome<Unit, AlarmError>
    suspend fun cancel(occurrenceId: Long)
    suspend fun cancelAll()
}

interface NotificationGateway {
    suspend fun show(occurrence: Occurrence, item: Item, preview: CascadePreview?)
    suspend fun dismiss(occurrenceId: Long)
    suspend fun showMilestone(milestone: Milestone)
    fun canPostNotifications(): Boolean
    fun canUseFullScreenIntent(): Boolean
}

interface WidgetGateway {
    suspend fun refresh()
}
```

This is what lets the whole scheduling flow be tested without an Android device:
substitute a fake gateway and assert what it was asked to do.

### 5.4 Use cases

One per meaningful operation. **Not one per repository call.** A use case that
forwards a single call to a single repository is deleted, and the ViewModel
calls the repository directly.

```kotlin
class ObserveTodayUseCase(
    private val plans: PlanRepository,
    private val templates: TemplateRepository,
    private val items: ItemRepository,
    private val occurrences: OccurrenceRepository,
    private val dayLogs: DayLogRepository,
    private val resolver: TimelineResolver,
    private val time: TimeProvider,
) {
    operator fun invoke(): Flow<ResolvedDay>
}

class CompleteItemUseCase(...)     // settle, reschedule downstream, refresh widget
class SnoozeItemUseCase(...)       // shift, recascade, reschedule, renotify
class SkipItemUseCase(...)         // settle, offer reason, reschedule downstream
class ShiftDayUseCase(...)         // write dayLog, resolve, reschedule everything
class SwitchDayTemplateUseCase(...)
class CloseDayUseCase(...)         // day_close, goal_progress, milestone, notify
class RescheduleAllUseCase(...)    // the idempotent workhorse, see section 6.4
class BuildWeeklyReviewUseCase(...)
class ImportPlanUseCase(...)
```

---

## 6. The four flows that matter

Everything else is a variation of one of these. Get these right and the app
works.

### 6.1 App opens, Today renders

```
MainActivity
  -> TodayViewModel.init
     -> ObserveTodayUseCase()
        -> combine(
             dayLogs.observe(today),        // which template, what shift
             templates.observe(planId),
             items.observeForTemplate(id),  // the single Today query
             occurrences.observeForDate(today)
           )
        -> resolver.resolve(input)          // pure, synchronous, sub millisecond
     -> map to TodayUiState
  -> Compose renders

  In parallel, not blocking the first frame:
  -> RescheduleAllUseCase()                 // reconcile anything that did not fire
```

The resolver runs inside the `combine`, on `dispatchers.default`. Because it is
pure and fast, there is no loading state after the first emission. Any edit to
any input re emits the whole day, correctly, with no invalidation logic to get
wrong.

### 6.2 Alarm fires, user taps Done in the notification

```
AlarmManager
  -> AlarmReceiver.onReceive
     -> goAsync()
     -> read occurrence, item
     -> DeliveryAuditRepository.recordFired(...)
     -> CascadeCalculator.preview(...)      // for the snooze consequence text
     -> NotificationGateway.show(...)

User taps [ Done ]
  -> NotificationActionReceiver.onReceive
     -> goAsync()
     -> CompleteItemUseCase(occurrenceId)
        -> occurrences.settle(DONE, time.now())
        -> resolve the day again
        -> alarms.cancel(this) and reschedule downstream RELATIVE items
        -> widget.refresh()
     -> NotificationGateway.dismiss(...)
```

**The activity is never launched.** `appflow.md` requires eighty percent of
interactions to complete without opening the app, and that requirement is what
forces the use case layer to be callable from a `BroadcastReceiver` rather than
only from a ViewModel. This is the main reason use cases exist at all.

### 6.3 Midnight, the daily close

```
WorkManager, 00:05
  -> DailyMaintenanceWorker
     -> CloseDayUseCase(yesterday)
        -> settle every still PENDING occurrence to MISSED
        -> DayQualityClassifier.classify(...)      -> day_close row
        -> GoalCalculator.smooth / paceTarget / project  -> goal_progress row
        -> MilestoneEvaluator.evaluate(...)        -> milestone_award row or null
     -> materialise today's occurrences
     -> RescheduleAllUseCase()
     -> prune delivery_audit older than 180 days
     -> widget.refresh()
```

`MilestoneEvaluator` returning `null` on a `POOR` day is not a UI concern. It is
enforced here, in the domain, so no screen can accidentally show it.

### 6.4 Rescheduling, and why it must be idempotent

`RescheduleAllUseCase` runs on: app open, boot, locked boot, package replaced,
timezone change, time change, date change, any completion, any snooze, any plan
edit, and the daily job. That is often, and sometimes twice in a second.

Therefore:

- It computes the full desired set of alarms for the rolling window
- It compares against what is currently scheduled
- It cancels what should not exist, schedules what is missing, leaves the rest
- **Running it twice in a row produces no change and no duplicate alarms**

A `PendingIntent` request code is derived deterministically from
`occurrenceId`, so the same occurrence always maps to the same slot. This is
what makes cancellation reliable.

---

## 7. Threading

| Layer | Thread | Rule |
|---|---|---|
| Domain services | caller's thread | Pure and fast enough to run anywhere |
| Repositories | `dispatchers.io` | Room and DataStore already dispatch, but be explicit |
| Use cases | `suspend`, switch to `io` or `default` internally | Never assume the caller's context |
| ViewModels | `viewModelScope`, main | Collect only, never compute |
| Receivers | `goAsync()` plus a scope on `io` | Ten second budget, keep the work small |
| Foreground service | own scope on `default` | Cancelled in `onDestroy` |

Never `Dispatchers.IO` by name in the codebase. Always the injected
`AppDispatchers`. Detekt enforces this alongside the clock rule.

---

## 8. Errors

No exceptions cross a module boundary. Every fallible operation returns
`Outcome<T, E>` with a typed reason.

```kotlin
sealed interface DomainError {
    sealed interface DataError : DomainError {
        data object NotFound : DataError
        data object WriteFailed : DataError
        data class Corrupt(val detail: String) : DataError
    }
    sealed interface AlarmError : DomainError {
        data object ExactAlarmDenied : AlarmError
        data object NotificationsDenied : AlarmError
        data object TooManyScheduled : AlarmError
    }
    sealed interface ParseError : DomainError {
        data object NothingRecognised : ParseError
        data class PartialParse(val recognised: Int, val total: Int) : ParseError
    }
    sealed interface PlanError : DomainError {
        data class AnchorCycle(val itemIds: List<Long>) : PlanError
        data class BudgetExceeded(val alarms: Int) : PlanError
    }
}
```

**Rules:**

- A failure the user must see maps to a string resource at the UI layer. The
  domain never holds a message, only a reason
- A failure the user cannot act on is logged and swallowed **at one place**, in
  the caller, never inside a repository
- `AlarmError.ExactAlarmDenied` is not an error state on screen. It drops the
  delivery tier and surfaces on the Reliability screen. Degrade, do not fail

---

## 9. UI state

One immutable data class per screen. One `StateFlow`. One channel for one shot
effects.

```kotlin
data class TodayUiState(
    val header: DayHeader,
    val entries: ImmutableList<TimelineRow>,
    val nowIndex: Int,
    val close: DailyCloseCard?,
    val milestone: MilestoneCard?,
    val lateBanner: LateBanner?,
    val budgetWarning: BudgetWarning?,
) {
    companion object { val Empty = TodayUiState(...) }
}

sealed interface TodayEffect {
    data class OpenBlock(val blockId: Long) : TodayEffect
    data class ShowSkipReason(val occurrenceId: Long) : TodayEffect
    data object OpenDayShift : TodayEffect
}
```

**Rules:**

- Lists in state are `ImmutableList` from kotlinx.collections.immutable, so
  Compose strong skipping actually skips
- No `isLoading` boolean if the data arrives in under a frame. Today does not
  need one
- Nullable field means absent, not loading. `close == null` means the day is not
  over
- Never pass the whole `UiState` into a leaf composable. Pass the row
- One shot events go through the effect channel. A boolean in state that gets
  reset is a bug waiting for a configuration change

---

## 10. Naming

| Thing | Convention | Example |
|---|---|---|
| Room entity | `Entity` suffix | `OccurrenceEntity` |
| Repository interface | plural noun plus `Repository` | `OccurrenceRepository` |
| Implementation | `Impl` suffix | `OccurrenceRepositoryImpl` |
| Use case | verb phrase plus `UseCase` | `CompleteItemUseCase` |
| Domain service | noun describing the job | `TimelineResolver` |
| Gateway | `Gateway` suffix | `AlarmGateway` |
| UI state | screen plus `UiState` | `TodayUiState` |
| Effect | screen plus `Effect` | `TodayEffect` |
| Composable screen | screen plus `Screen` | `TodayScreen` |
| Test | `method name, condition, expected` in backticks | see below |

```kotlin
@Test
fun `relative anchor resolves from the parent actual time when the parent is done`()
```

---

## 11. How to add a feature, every time

The recipe. Following it means nothing downstream breaks.

1. **Read `prd.md` section 5.** If it is a non goal, stop here
2. **Model.** Add or change types in `:core:model`. No logic
3. **Schema.** Update `schema.md`, then the entity, then the migration, then the
   migration test. In that order
4. **Domain.** Write the pure service and its tests first. It must fail before
   it passes
5. **Repository.** Interface in domain, implementation in data, mapper both ways
6. **Use case.** Only if there is orchestration. Otherwise skip it
7. **Gateway.** Only if the platform is involved. Interface in domain, impl in
   scheduler
8. **State.** Extend the screen's `UiState`. Keep it immutable
9. **UI.** Compose against `design.md`, then run the section 14 checklist
10. **Flow.** Update `appflow.md` if a user visible path changed
11. **Tracker.** Tick the box in `tracker.md`

**Step 4 before step 9, always.** Building the screen first and retrofitting the
logic is how the domain ends up inside a composable.

---

## 12. Things that break this architecture

If any of these appear, the change is wrong. Fix the design, not the symptom.

- A Room entity referenced above `:core:data`
- A repository interface implemented anywhere but `:core:data`
- Business logic inside a composable, a receiver, or a repository
- A domain service that suspends, does IO, or reads the clock directly
- `Dispatchers.IO` written by name instead of injected
- A stored resolved schedule
- A use case that only forwards one call
- A `PendingIntent` request code that is not derived from `occurrenceId`
- A screen that shows a milestone or a countdown without asking the domain
- A billing SDK type outside `:billing`
- A `catch` block with an empty body
- A nullable field used to mean "loading"

---

## 13. Build order for M2

The contracts above, written in this order, each with tests before the next
starts:

```
1.  core:model            every type in schema.md section 2
2.  Anchor + AnchorResolver
3.  CycleDetector
4.  IntervalExpander
5.  TimelineResolver      the nine steps, wired together
6.  DayShifter
7.  SalienceBudget
8.  CascadeCalculator
9.  MovingAverage
10. PaceProjector
11. DayQualityClassifier
12. MilestoneEvaluator
13. TimeShiftDetector
14. SkipPatternDetector
15. CatchUpPlanner
16. WeeklyReviewBuilder
17. PlanTextParser
18. ExportBuilder
19. every repository and gateway interface, no implementations
```

Steps 1 to 18 are pure Kotlin with no Android and no database. They can all be
written and fully tested before the Gradle wrapper problem is solved, before
Room exists, and before a single screen is drawn.

Step 19 is interfaces only. Implementations start in M3.
