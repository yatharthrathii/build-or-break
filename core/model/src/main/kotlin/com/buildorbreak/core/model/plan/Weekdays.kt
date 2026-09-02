package com.buildorbreak.core.model.plan

import java.time.DayOfWeek

private const val ALL_BITS = 0b111_1111

private val DayOfWeek.bit: Int get() = 1 shl (value - 1)

/**
 * Which weekdays an item or a template applies to, held as a seven bit mask.
 *
 * Bit 0 is Monday, matching [DayOfWeek.getValue] minus one, so a membership test
 * is one shift and one and. Gym on Monday, Wednesday and Friday is a value, not
 * three rows.
 */
@JvmInline
value class Weekdays(val bits: Int) {

    init {
        require(bits in 0..ALL_BITS) { "Weekday mask out of range: $bits" }
    }

    operator fun contains(day: DayOfWeek): Boolean = bits and day.bit != 0

    operator fun plus(day: DayOfWeek): Weekdays = Weekdays(bits or day.bit)

    operator fun minus(day: DayOfWeek): Weekdays =
        Weekdays(bits and day.bit.inv() and ALL_BITS)

    val isEmpty: Boolean get() = bits == 0

    val isEveryDay: Boolean get() = bits == ALL_BITS

    // values() rather than entries, because DayOfWeek is a Java enum.
    val days: List<DayOfWeek> get() = DayOfWeek.values().filter { it in this }

    val count: Int get() = Integer.bitCount(bits)

    companion object {
        val None = Weekdays(0)
        val EveryDay = Weekdays(ALL_BITS)

        val MonToFri: Weekdays = of(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
        )

        val Weekend: Weekdays = of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

        fun of(vararg days: DayOfWeek): Weekdays =
            Weekdays(days.fold(0) { acc, day -> acc or day.bit })
    }
}
