# Build or Break - Data Model and Schema

Read `rules.md` and `techspec.md` first.

Two layers exist and they are not the same thing:

- **Domain model** in `:core:model`, pure Kotlin, what the engine reasons about
- **Persistence model** in `:core:data`, Room entities, what is stored

Never leak a Room entity above `:core:data`. Mappers live in `:core:data`.

---

## 1. Enums

All of these ship in V1 even where a value is not yet used, because adding an
enum value later is free and adding a column later is a migration.

```kotlin
enum class ItemKind {
    DO,             // a scheduled action to complete
    AVOID,          // a risk window to abstain through (V2, Break mode)
    TRACK_SESSION,  // a slot that advances through a Track (V1.5)
}

enum class AnchorType {
    FIXED,     // absolute clock time
    RELATIVE,  // offset from another item's actual completion
    WINDOW,    // any time inside a range, with a nag ladder
    INTERVAL,  // repeats every N minutes inside a window
}

enum class Salience {
    ALARM,     // full screen, sound, volume ramp. Max 3 per day
    NOTIFY,    // heads up with sound. Max 10 per day
    SILENT,    // notification without sound
    TIMELINE,  // never scheduled, visible in app and widget only
}

enum class OccurrenceState {
    PENDING, FIRED, DONE, DONE_MINIMUM,
    SNOOZED, SKIPPED, MISSED, CANCELLED,
}

enum class DeliveryTier {
    FULL_SCREEN_ALARM, EXACT_HEADS_UP, INEXACT_NOTIFICATION, IN_APP_ONLY,
}

enum class DayMode {
    NORMAL, SHIFTED, REDUCED,   // REDUCED is the "sick day" mode
}

enum class ValueKind {
    NONE, WEIGHT_KG, REPS, PAGES, MINUTES, COUNT, FREE_NUMBER,
}

enum class SkipChip {
    WORK_CAME_UP, FORGOT, NOT_IN_MOOD, UNWELL,
    TRAVELLING, NO_TIME, DID_IT_LATER, OTHER,
}

enum class TrackUnitState { PENDING, IN_PROGRESS, DONE, SKIPPED }
```

---

## 2. Domain model (`:core:model`)

```kotlin
data class Plan(
    val id: Long,
    val name: String,
    val isActive: Boolean,
    val zone: ZoneId,
    val createdAt: Instant,
)

data class DayTemplate(
    val id: Long,
    val planId: Long,
    val name: String,          // "Office day", "Rest day", "Sick day"
    val weekdayMask: Int,      // bitmask, bit 0 = Monday
    val isDefault: Boolean,
    val mode: DayMode,
    val sortOrder: Int,
)

data class Block(
    val id: Long,
    val templateId: Long,
    val title: String,
    val anchor: Anchor,
    val salience: Salience,
    val sortOrder: Int,
)

sealed interface Anchor {
    data class Fixed(val at: LocalTime) : Anchor
    data class Relative(val parentItemId: Long, val offset: Duration) : Anchor
    data class Window(val from: LocalTime, val to: LocalTime, val nagLadder: List<Duration>) : Anchor
    data class Interval(val every: Duration, val from: LocalTime, val to: LocalTime) : Anchor
}

data class Item(
    val id: Long,
    val templateId: Long,
    val blockId: Long?,        // null when standalone
    val kind: ItemKind,
    val title: String,
    val detail: String?,
    val anchor: Anchor,
    val duration: Duration?,   // drives the in block timer
    val salience: Salience,
    val weekdayMask: Int,      // gym only on Mon, Wed, Fri
    val pinned: Boolean,       // a whole day shift does not move this
    val minimum: MinimumVersion?,
    val valueKind: ValueKind,
    val bundleUri: String?,    // temptation bundling deep link, V2
    val trackId: Long?,        // set when kind == TRACK_SESSION
    val sortOrder: Int,
    val archivedAt: Instant?,  // soft delete, history stays intact
)

data class MinimumVersion(
    val title: String,
    val duration: Duration?,
)

data class Occurrence(
    val id: Long,
    val itemId: Long,
    val date: LocalDate,
    val plannedAt: LocalDateTime,   // what resolve() computed
    val scheduledAt: Instant?,      // what was handed to AlarmManager
    val firedAt: Instant?,
    val settledAt: Instant?,        // done, skipped or missed
    val state: OccurrenceState,
    val shiftMinutes: Int,          // accumulated snooze
    val snoozeCount: Int,
    val sequenceInDay: Int,         // for INTERVAL expansion
)

data class SkipReason(
    val id: Long,
    val occurrenceId: Long,
    val chip: SkipChip?,
    val text: String?,
    val createdAt: Instant,
)

data class Measurement(
    val id: Long,
    val itemId: Long,
    val occurrenceId: Long?,
    val date: LocalDate,
    val value: Double,
    val kind: ValueKind,
    val note: String?,
)

data class DayLog(
    val date: LocalDate,
    val planId: Long,
    val templateId: Long,
    val dayShiftMinutes: Int,
    val mode: DayMode,
    val chosenAt: Instant,
)

data class DeliveryAudit(
    val id: Long,
    val occurrenceId: Long,
    val scheduledFor: Instant,
    val firedAt: Instant?,
    val tier: DeliveryTier,
    val deviceModel: String,
    val manufacturer: String,
    val sdkInt: Int,
    val wasDeviceIdle: Boolean,
    val latencySeconds: Long?,   // computed, denormalised for fast reporting
)

// V1.5, tables created in V1 so no migration is needed later
data class Track(
    val id: Long,
    val planId: Long,
    val name: String,
    val sourceText: String?,   // the pasted syllabus, kept verbatim
    val createdAt: Instant,
)

data class TrackUnit(
    val id: Long,
    val trackId: Long,
    val ordinal: Int,
    val title: String,
    val estimateMinutes: Int?,
    val state: TrackUnitState,
)

data class TrackSession(
    val id: Long,
    val occurrenceId: Long,
    val trackUnitId: Long,
    val minutesSpent: Int,
    val completedUnit: Boolean,
    val leftOffNote: String?,   // shown at the start of the next session
)
```

### The engine output

```kotlin
data class ResolvedDay(
    val date: LocalDate,
    val template: DayTemplate,
    val entries: List<ResolvedEntry>,
    val dayShift: Duration,
    val budgetWarning: BudgetWarning?,
)

data class ResolvedEntry(
    val item: Item,
    val block: Block?,
    val at: LocalDateTime,
    val occurrence: Occurrence?,   // null when not yet materialised
    val isNow: Boolean,
)

data class BudgetWarning(
    val alarmCount: Int,
    val notifyCount: Int,
)
```

---

## 3. Room entities (`:core:data`)

Table names are snake_case. Every foreign key cascades on delete except
history tables, which use `RESTRICT` so history is never silently lost.

```
plan
  id                INTEGER  PK autoincrement
  name              TEXT     not null
  is_active         INTEGER  not null
  zone_id           TEXT     not null
  created_at        INTEGER  not null

day_template
  id                INTEGER  PK
  plan_id           INTEGER  FK -> plan.id  CASCADE   [index]
  name              TEXT     not null
  weekday_mask      INTEGER  not null
  is_default        INTEGER  not null
  mode              TEXT     not null
  sort_order        INTEGER  not null

block
  id                INTEGER  PK
  template_id       INTEGER  FK -> day_template.id  CASCADE  [index]
  title             TEXT     not null
  anchor_type       TEXT     not null
  anchor_fixed_at   INTEGER  nullable   minutes from midnight
  anchor_parent_id  INTEGER  nullable
  anchor_offset_min INTEGER  nullable
  anchor_from       INTEGER  nullable
  anchor_to         INTEGER  nullable
  anchor_every_min  INTEGER  nullable
  salience          TEXT     not null
  sort_order        INTEGER  not null

item
  id                INTEGER  PK
  template_id       INTEGER  FK -> day_template.id  CASCADE  [index]
  block_id          INTEGER  FK -> block.id  CASCADE  nullable  [index]
  kind              TEXT     not null
  title             TEXT     not null
  detail            TEXT     nullable
  anchor_type       TEXT     not null
  anchor_fixed_at   INTEGER  nullable
  anchor_parent_id  INTEGER  nullable
  anchor_offset_min INTEGER  nullable
  anchor_from       INTEGER  nullable
  anchor_to         INTEGER  nullable
  anchor_every_min  INTEGER  nullable
  anchor_nag_ladder TEXT     nullable   comma separated minutes
  duration_min      INTEGER  nullable
  salience          TEXT     not null
  weekday_mask      INTEGER  not null
  pinned            INTEGER  not null
  minimum_title     TEXT     nullable
  minimum_duration  INTEGER  nullable
  value_kind        TEXT     not null
  bundle_uri        TEXT     nullable
  track_id          INTEGER  FK -> track.id  SET NULL  nullable
  sort_order        INTEGER  not null
  archived_at       INTEGER  nullable

occurrence
  id                INTEGER  PK
  item_id           INTEGER  FK -> item.id  RESTRICT  [index]
  date              INTEGER  not null   epoch day   [index]
  planned_at        INTEGER  not null
  scheduled_at      INTEGER  nullable
  fired_at          INTEGER  nullable
  settled_at        INTEGER  nullable
  state             TEXT     not null
  shift_minutes     INTEGER  not null default 0
  snooze_count      INTEGER  not null default 0
  sequence_in_day   INTEGER  not null default 0
  UNIQUE(item_id, date, sequence_in_day)
  INDEX(date, state)

skip_reason
  id                INTEGER  PK
  occurrence_id     INTEGER  FK -> occurrence.id  CASCADE  [index]
  chip              TEXT     nullable
  text              TEXT     nullable
  created_at        INTEGER  not null

measurement
  id                INTEGER  PK
  item_id           INTEGER  FK -> item.id  RESTRICT  [index]
  occurrence_id     INTEGER  FK -> occurrence.id  SET NULL  nullable
  date              INTEGER  not null   [index]
  value             REAL     not null
  kind              TEXT     not null
  note              TEXT     nullable

day_log
  date              INTEGER  PK   epoch day
  plan_id           INTEGER  FK -> plan.id  CASCADE
  template_id       INTEGER  FK -> day_template.id  RESTRICT
  day_shift_minutes INTEGER  not null default 0
  mode              TEXT     not null
  chosen_at         INTEGER  not null

delivery_audit
  id                INTEGER  PK
  occurrence_id     INTEGER  FK -> occurrence.id  CASCADE  [index]
  scheduled_for     INTEGER  not null
  fired_at          INTEGER  nullable
  tier              TEXT     not null
  device_model      TEXT     not null
  manufacturer      TEXT     not null
  sdk_int           INTEGER  not null
  was_device_idle   INTEGER  not null
  latency_seconds   INTEGER  nullable
  INDEX(scheduled_for)

track
  id                INTEGER  PK
  plan_id           INTEGER  FK -> plan.id  CASCADE  [index]
  name              TEXT     not null
  source_text       TEXT     nullable
  created_at        INTEGER  not null

track_unit
  id                INTEGER  PK
  track_id          INTEGER  FK -> track.id  CASCADE  [index]
  ordinal           INTEGER  not null
  title             TEXT     not null
  estimate_minutes  INTEGER  nullable
  state             TEXT     not null
  UNIQUE(track_id, ordinal)

track_session
  id                INTEGER  PK
  occurrence_id     INTEGER  FK -> occurrence.id  CASCADE  [index]
  track_unit_id     INTEGER  FK -> track_unit.id  RESTRICT  [index]
  minutes_spent     INTEGER  not null
  completed_unit    INTEGER  not null
  left_off_note     TEXT     nullable
```

### Storage conventions

- All timestamps are `INTEGER`, epoch milliseconds UTC
- All dates are `INTEGER`, epoch day
- All times of day are `INTEGER`, minutes from midnight, 0 to 1439
- All enums are stored as `TEXT` using the constant name, never the ordinal.
  Ordinals break the moment someone reorders an enum
- Booleans are `INTEGER` 0 or 1

---

## 4. The one Today query

`rules.md` requires the Today screen to load in a single query. This is it:

```sql
SELECT i.*, b.*, o.*
FROM item i
LEFT JOIN block b       ON b.id = i.block_id
LEFT JOIN occurrence o  ON o.item_id = i.id AND o.date = :epochDay
WHERE i.template_id = :templateId
  AND i.archived_at IS NULL
  AND (i.weekday_mask & :weekdayBit) != 0
ORDER BY i.sort_order
```

Returned as a `Flow<List<TodayRow>>` with `@Transaction`. The engine then
resolves anchors in memory. No N plus 1, no per item query, ever.

---

## 5. Preferences (DataStore)

Not in Room. These are settings, not data.

```
theme_mode              : SYSTEM | LIGHT | DARK
use_dynamic_colour      : Boolean, default false
active_plan_id          : Long
onboarding_complete     : Boolean
oem_guide_shown         : Boolean
crash_reporting_opted_in: Boolean, default false
ai_insights_opted_in    : Boolean, default false
last_reschedule_at      : Long
notification_budget_ack : Boolean
first_completion_seen   : Boolean
```

The AI API key lives in `EncryptedSharedPreferences`, never DataStore, never
Room, and is excluded from backup.

---

## 6. Migration policy

- `exportSchema = true`. Every schema JSON is committed under
  `:core:data/schemas/`
- Prefer `@AutoMigration` with `@DeleteColumn` and `@RenameColumn` specs
- Every migration has a test using `MigrationTestHelper` that opens the old
  schema, inserts a representative row, migrates, and asserts the row survived
- Never drop a column that holds user history. Add `archived_at` instead
- Version 1 ships with every table above already present, including the Track
  tables, so the V1.5 learning feature needs no schema migration at all

---

## 7. Export format

`rules.md` requires one tap export. Two files, zipped:

**`occurrences.csv`**
```
date,item_title,template,planned_at,fired_at,settled_at,state,shift_minutes,snooze_count,skip_chip,skip_text
```

**`report.md`** - a plain readable summary: plan name, date range, per item
completion counts, measurement series, and the delivery reliability figure.

Both are generated in `:core:domain` from domain types, so the export logic is
unit testable without Android.
