package com.buildorbreak.core.model.plan

import com.buildorbreak.core.model.enums.DayMode
import com.buildorbreak.core.model.enums.ItemKind
import com.buildorbreak.core.model.enums.Salience
import com.buildorbreak.core.model.enums.ValueKind
import java.time.Instant
import java.time.ZoneId
import kotlin.time.Duration

/** A named routine. Only one is active at a time on the free tier. */
data class Plan(val id: Long, val name: String, val isActive: Boolean, val zone: ZoneId, val createdAt: Instant)

/**
 * One shape of a day: office day, working from home, rest day, sick day.
 *
 * This is the answer to the loudest complaint in the category. A routine bound
 * to fixed clock times forces two routines when your day starts at six on
 * Monday and eight on Saturday. A template is chosen in one tap and the whole
 * timeline reshapes.
 */
data class DayTemplate(
    val id: Long,
    val planId: Long,
    val name: String,
    val weekdays: Weekdays,
    val isDefault: Boolean,
    val mode: DayMode,
    val sortOrder: Int,
)

/**
 * A container for consecutive micro steps.
 *
 * Five things between 08:00 and 08:30 are one notification and one guided
 * screen, not five alarms. rules.md section 1 rule 4 exists because the
 * alternative is a muted app inside a week.
 */
data class Block(
    val id: Long,
    val templateId: Long,
    val title: String,
    val anchor: Anchor,
    val salience: Salience,
    val sortOrder: Int,
)

/** The smaller version of an item, defined in advance, offered on a bad day. */
data class MinimumVersion(val title: String, val duration: Duration? = null)

/**
 * One thing to do.
 *
 * [pinned] is what keeps a booked gym slot in place when the rest of the day is
 * shifted ninety minutes. [minimum] is what keeps a bad day from becoming a
 * broken week.
 */
data class Item(
    val id: Long,
    val templateId: Long,
    val blockId: Long?,
    val kind: ItemKind,
    val title: String,
    val detail: String?,
    val anchor: Anchor,
    val duration: Duration?,
    val salience: Salience,
    val weekdays: Weekdays,
    val pinned: Boolean,
    val minimum: MinimumVersion?,
    val valueKind: ValueKind,
    val bundleUri: String?,
    val trackId: Long?,
    val sortOrder: Int,
    val archivedAt: Instant? = null,
) {
    val isArchived: Boolean get() = archivedAt != null

    val hasMinimum: Boolean get() = minimum != null

    /** TIMELINE items are never handed to the scheduler at all. */
    val isSchedulable: Boolean get() = salience != Salience.TIMELINE
}
