package com.buildorbreak.core.model.enums

/**
 * What kind of thing an item is.
 *
 * `AVOID` and `TRACK_SESSION` are declared now even though Break mode is Phase 2
 * and tracks arrive in M7. Adding an enum value later is free. Adding a column
 * later is a migration.
 */
enum class ItemKind {
    /** A scheduled action to complete. */
    DO,

    /** A risk window to abstain through. Phase 2. */
    AVOID,

    /** A slot that advances through a Track. */
    TRACK_SESSION,
}

/** How an item's time is decided. This is what makes the day adaptive. */
enum class AnchorType {
    /** Absolute clock time. A whole day shift leaves it alone if pinned. */
    FIXED,

    /** An offset from another item's completion. */
    RELATIVE,

    /** Any time inside a range, with an escalating nag ladder. */
    WINDOW,

    /** Repeats every N minutes inside a window. */
    INTERVAL,
}

/**
 * How loudly an item announces itself.
 *
 * rules.md section 1 rule 4 caps a day at three ALARM and ten NOTIFY. A plan
 * that exceeds it produces a warning rather than a noisy day.
 */
enum class Salience {
    /** Full screen, sound, volume ramp. */
    ALARM,

    /** Heads up notification with sound. */
    NOTIFY,

    /** Notification without sound. */
    SILENT,

    /** Never scheduled. Visible in the app and the widget only. */
    TIMELINE,
}

enum class OccurrenceState {
    PENDING,
    FIRED,
    DONE,
    DONE_MINIMUM,
    SNOOZED,
    SKIPPED,
    MISSED,
    CANCELLED,
    ;

    val isSettled: Boolean
        get() = this == DONE || this == DONE_MINIMUM || this == SKIPPED ||
            this == MISSED || this == CANCELLED

    /** Counts toward adherence. A minimum version still counts. */
    val isDone: Boolean
        get() = this == DONE || this == DONE_MINIMUM
}

/**
 * What the scheduler is actually allowed to do right now.
 *
 * Never assumed, always detected. The app degrades through these tiers rather
 * than failing. See techspec.md section 7.
 */
enum class DeliveryTier {
    FULL_SCREEN_ALARM,
    EXACT_HEADS_UP,
    INEXACT_NOTIFICATION,
    IN_APP_ONLY,
}

enum class DayMode {
    NORMAL,

    /** The whole day has been moved by a shift. */
    SHIFTED,

    /** Sick day. Every item with a minimum version runs as its minimum. */
    REDUCED,
}

enum class ValueKind {
    NONE,
    WEIGHT_KG,
    REPS,
    PAGES,
    MINUTES,
    COUNT,
    FREE_NUMBER,
}

/** Preset skip reasons. Always optional, never required. */
enum class SkipChip {
    WORK_CAME_UP,
    FORGOT,
    NOT_IN_MOOD,
    UNWELL,
    TRAVELLING,
    NO_TIME,
    DID_IT_LATER,
    OTHER,
}

enum class TrackUnitState { PENDING, IN_PROGRESS, DONE, SKIPPED }

/** The four goal shapes that cover every build habit. */
enum class GoalKind {
    /** A measured value reaching a target. Plus 2 kg, minus 5 kg. */
    NUMBER,

    /** Doing something N times in the period. Twelve gym sessions. */
    COUNT,

    /** Accumulating N minutes. Forty hours of study. */
    DURATION,

    /** Holding an adherence percentage. Medicine on 95 percent of days. */
    CONSISTENCY,
}

/**
 * How a day went.
 *
 * rules.md section 2 rule 8: a POOR day shows no countdown, no progress bar and
 * no milestone. This enum is what enforces that, in the domain, so no screen can
 * get it wrong.
 */
enum class DayQuality {
    /** Eighty percent or more completed. */
    GOOD,

    /** Between fifty and eighty percent. */
    OK,

    /** Under fifty percent. */
    POOR,
    ;

    val allowsCountdown: Boolean get() = this != POOR
    val allowsMilestone: Boolean get() = this != POOR
    val allowsPraise: Boolean get() = this == GOOD
}

/** Each fires once in the lifetime of an install. See appflow.md section 8.3. */
enum class Milestone {
    FIRST_COMPLETION,
    FIRST_FULL_DAY,
    FIRST_WEEK,
    GOAL_QUARTER,
    GOAL_HALF,
    GOAL_THREE_QUARTERS,
    GOAL_REACHED,
    BEST_WEEK,
    ITEM_THIRTY_DAY_RUN,
    ;

    /** Two milestones from the same category never fire on consecutive days. */
    val category: MilestoneCategory
        get() = when (this) {
            FIRST_COMPLETION, FIRST_FULL_DAY, FIRST_WEEK -> MilestoneCategory.FIRST
            GOAL_QUARTER, GOAL_HALF, GOAL_THREE_QUARTERS, GOAL_REACHED -> MilestoneCategory.GOAL
            BEST_WEEK, ITEM_THIRTY_DAY_RUN -> MilestoneCategory.STREAK
        }
}

enum class MilestoneCategory { FIRST, GOAL, STREAK }

/**
 * The seven shapes a weekly review can take.
 *
 * The report has no other form. Each maps to one pre written string with number
 * slots, which is why no model is needed to generate it. See techspec.md
 * section 5b.
 */
enum class ReviewStory {
    /** High adherence, on pace. Nothing to change. */
    ON_TRACK,

    /** High adherence, off pace. The schedule is not the problem. */
    PLAN_TOO_SMALL,

    /** Misses cluster on particular weekdays. */
    TIMING_PROBLEM,

    /** Misses are scattered and the reason given is mostly forgetting. */
    REMINDER_PROBLEM,

    /** Adherence falling week on week. */
    LOSING_GRIP,

    /** First week. Too early to conclude anything. */
    SETTLING_IN,

    /** No clear signal. */
    MIXED,
}
