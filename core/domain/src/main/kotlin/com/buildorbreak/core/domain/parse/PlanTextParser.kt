package com.buildorbreak.core.domain.parse

import com.buildorbreak.core.model.enums.Salience
import com.buildorbreak.core.model.plan.Anchor
import java.time.LocalTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

private const val NOON = 12
private const val HOURS_IN_DAY = 24
private const val MINUTES_IN_HOUR = 60

/** Bullets, dashes and numbering people paste without thinking about it. */
private val LEADING_MARKER = Regex("""^\s*(?:[-*•·–—]|\d+[.)])\s*""")

/** A section heading: text ending in a colon with no clock time in it. */
private val SECTION = Regex("""^([A-Za-z][^:]{0,40}):\s*$""")

private val TIME = Regex("""(\d{1,2})(?::(\d{2}))?\s*([ap])\.?m\.?""", RegexOption.IGNORE_CASE)

/** Capture group holding the a or the p of am and pm. */
private const val MERIDIEM_GROUP = 3
private val TIME_24 = Regex("""(\d{1,2}):(\d{2})""")

private val DURATION = Regex("""(\d+)\s*(h|hr|hrs|hour|hours|m|min|mins|minute|minutes)\b""", RegexOption.IGNORE_CASE)

private val RELATIVE = Regex("""^\+\s*(\d+\s*\w+)""")
private val AFTER = Regex("""^after\s+(\d+\s*\w+)""", RegexOption.IGNORE_CASE)
private val EVERY = Regex("""^every\s+(\d+\s*\w+)""", RegexOption.IGNORE_CASE)

private val RANGE_SEPARATOR = Regex("""\s*(?:-|–|—|to|until)\s*""", RegexOption.IGNORE_CASE)

/** Everything between the time part and the title on an ordinary list line. */
private val TITLE_SEPARATOR = Regex("""^\s*(?:[|:\-–—]|\t)\s*""")

private val MINIMUM = Regex("""\bmin(?:imum)?\s*[:=]\s*(.+?)\s*$""", RegexOption.IGNORE_CASE)
private val PINNED = Regex("""[\[(]?\bpinned\b[])]?""", RegexOption.IGNORE_CASE)
private val SALIENCE = Regex("""[\[(]?\b(alarm|notify|silent|timeline)\b[])]?""", RegexOption.IGNORE_CASE)

/** Trailing punctuation left behind once the markers have been pulled out. */
private val TRAILING_JUNK = Regex("""[\s|,;.\-–—]+$""")

/**
 * Reads a pasted routine.
 *
 * Two paths, on purpose. The strict shape in [PlanFormat] is what the app asks
 * an AI tool to produce, and it always parses. Everything below is the fallback
 * for the person who pastes something else anyway, and it is best effort by
 * design rather than by accident.
 *
 * **This never throws and never drops a line silently.** A line it cannot read
 * comes back in [ParseResult.unrecognised]. That property is what makes an
 * imperfect parser safe to ship: the confirm screen shows what was understood
 * and what was not, so a bad read is caught by the user at import time rather
 * than discovered at six in the morning when an alarm does not fire.
 *
 * What it understands, in the order it tries:
 *
 * ```
 * every 45m 11:00-15:00 | Stand up      repeats inside a window
 * 07:30-09:30 Study block               any time in a window
 * +15m Drink water                      relative to the line above
 * 06:30 Wake up                          a fixed time
 * Morning:                               a section heading
 * ```
 *
 * Times may be 24 hour or 12 hour with am/pm. Bullets, dashes and numbering are
 * stripped. Duration, `min:` and `pinned` are picked out of the rest of the line
 * wherever they appear.
 */
class PlanTextParser(
    /** What a line that does not say gets. Most pasted text never says. */
    private val defaultSalience: Salience? = null,
) {

    fun parse(text: String): ParseResult {
        val items = mutableListOf<ParsedItem>()
        val unrecognised = mutableListOf<String>()
        var section: String? = null
        var previousTitle: String? = null

        text.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEach

            SECTION.find(line)?.let {
                section = it.groupValues[1].trim()
                return@forEach
            }

            val parsed = parseLine(line, section, previousTitle)
            if (parsed == null) {
                unrecognised += line
            } else {
                items += parsed
                previousTitle = parsed.title
            }
        }

        return ParseResult(items, unrecognised)
    }

    private fun parseLine(line: String, section: String?, previousTitle: String?): ParsedItem? {
        val body = line.replace(LEADING_MARKER, "").trim()
        if (body.isEmpty()) return null

        val anchored = readAnchor(body, previousTitle) ?: return null
        val details = readDetails(anchored.remainder)
        if (details.title.isEmpty()) return null

        return ParsedItem(
            title = details.title,
            anchor = anchored.anchor,
            duration = details.duration,
            salience = details.salience ?: defaultSalience,
            minimumTitle = details.minimum,
            pinned = details.pinned,
            section = section,
            sourceLine = line,
        )
    }

    // Anchors ------------------------------------------------------------------

    private data class Anchored(val anchor: Anchor, val remainder: String)

    /**
     * Tried most specific first. An interval line also contains a range, and a
     * range also contains a time, so the loosest pattern has to be last or it
     * would claim every line before the right one saw it.
     */
    private fun readAnchor(body: String, previousTitle: String?): Anchored? =
        readInterval(body) ?: readWindow(body) ?: readRelative(body, previousTitle) ?: readFixed(body)

    private fun readInterval(body: String): Anchored? {
        val every = EVERY.find(body) ?: return null
        val step = parseDuration(every.groupValues[1])?.takeIf { it > Duration.ZERO } ?: return null

        val window = readWindow(body.substring(every.range.last + 1))
        val range = window?.anchor as? Anchor.Window ?: return null

        return Anchored(Anchor.Interval(step, range.from, range.to), window.remainder)
    }

    private fun readWindow(body: String): Anchored? {
        val first = findTime(body) ?: return null
        val afterFirst = body.substring(first.end)

        val separator = RANGE_SEPARATOR.find(afterFirst)?.takeIf { it.range.first == 0 } ?: return null
        val rest = afterFirst.substring(separator.range.last + 1)

        // A range has to move forwards. "09:30-07:30" is a typo, not a window.
        val second = findTime(rest)?.takeIf { it.start == 0 && it.time.isAfter(first.time) } ?: return null

        return Anchored(Anchor.Window(first.time, second.time), rest.substring(second.end))
    }

    /** Only meaningful once something has already been read to hang off. */
    private fun readRelative(body: String, previousTitle: String?): Anchored? {
        if (previousTitle == null) return null

        val match = RELATIVE.find(body) ?: AFTER.find(body) ?: return null
        val offset = parseDuration(match.groupValues[1]) ?: return null

        // The parent id is not known here. PARENT_UNRESOLVED is a placeholder the
        // import use case replaces once the rows above have real ids, and it is a
        // sentinel rather than zero so a mistake surfaces instead of pointing at
        // whatever happens to be first.
        return Anchored(
            Anchor.Relative(PARENT_UNRESOLVED, offset),
            body.substring(match.range.last + 1),
        )
    }

    private fun readFixed(body: String): Anchored? {
        val time = findTime(body)?.takeIf { it.start <= LEADING_TIME_SLACK } ?: return null

        return Anchored(Anchor.Fixed(time.time), body.substring(time.end))
    }

    // Times --------------------------------------------------------------------

    private data class FoundTime(val time: LocalTime, val start: Int, val end: Int)

    private fun findTime(text: String): FoundTime? {
        TIME.find(text)?.let { match ->
            val hour = match.groupValues[1].toIntOrNull() ?: return@let
            val minute = match.groupValues[2].toIntOrNull() ?: 0
            val isPm = match.groupValues[MERIDIEM_GROUP].lowercase() == "p"
            toTime(twelveHourToTwentyFour(hour, isPm), minute)?.let {
                return FoundTime(it, match.range.first, match.range.last + 1)
            }
        }

        TIME_24.find(text)?.let { match ->
            toTime(match.groupValues[1].toInt(), match.groupValues[2].toInt())?.let {
                return FoundTime(it, match.range.first, match.range.last + 1)
            }
        }

        return null
    }

    private fun twelveHourToTwentyFour(hour: Int, isPm: Boolean): Int = when {
        isPm && hour < NOON -> hour + NOON
        !isPm && hour == NOON -> 0
        else -> hour
    }

    private fun toTime(hour: Int, minute: Int): LocalTime? =
        if (hour in 0 until HOURS_IN_DAY && minute in 0 until MINUTES_IN_HOUR) LocalTime.of(hour, minute) else null

    private fun parseDuration(text: String): Duration? {
        val match = DURATION.find(text) ?: return null
        val amount = match.groupValues[1].toLongOrNull() ?: return null

        return if (match.groupValues[2].lowercase().startsWith("h")) amount.hours else amount.minutes
    }

    // The rest of the line -----------------------------------------------------

    private data class Details(
        val title: String,
        val duration: Duration?,
        val salience: Salience?,
        val minimum: String?,
        val pinned: Boolean,
    )

    /**
     * Everything after the time, pulled apart from the outside in.
     *
     * `min:` is taken first because it runs to the end of the line and would
     * otherwise swallow anything read after it.
     */
    private fun readDetails(remainder: String): Details {
        var rest = remainder.replace(TITLE_SEPARATOR, "").trim()

        val minimum = MINIMUM.find(rest)?.also { rest = rest.removeRange(it.range).trim() }?.groupValues?.get(1)

        val pinned = PINNED.find(rest)?.also { rest = rest.removeRange(it.range).trim() } != null

        val salience = SALIENCE.find(rest)
            ?.also { rest = rest.removeRange(it.range).trim() }
            ?.let { runCatching { Salience.valueOf(it.groupValues[1].uppercase()) }.getOrNull() }

        val duration = DURATION.find(rest)
            ?.also { rest = rest.removeRange(it.range).trim() }
            ?.let { parseDuration(it.value) }

        return Details(
            title = cleanTitle(rest),
            duration = duration,
            salience = salience,
            minimum = minimum?.takeIf { it.isNotBlank() },
            pinned = pinned,
        )
    }

    /** Strips the brackets and separators the removals left behind. */
    private fun cleanTitle(text: String): String = text
        .replace(TITLE_SEPARATOR, "")
        .replace(Regex("""[(\[]\s*[)\]]"""), "")
        .replace(Regex("""\s{2,}"""), " ")
        .replace(TRAILING_JUNK, "")
        .trim()
        .trim('|', '(', ')', '[', ']')
        .trim()

    companion object {
        /**
         * Stands in for a parent that only exists once the rows have been
         * written. The import use case resolves it against the line above.
         */
        const val PARENT_UNRESOLVED = -1L

        /** How far into a line a leading time may sit and still count as one. */
        private const val LEADING_TIME_SLACK = 2
    }
}
