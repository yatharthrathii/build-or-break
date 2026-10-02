package com.buildorbreak.core.domain.track

private const val MINUTES_IN_HOUR = 60

/** Bullets, dashes and numbering people paste without thinking about it. */
private val LEADING_MARKER = Regex("""^\s*(?:[-*•·–—]|\d+[.)])\s*""")

/**
 * A length of time at the end of a line: "· 30 min", "(1 h)", "- 45m".
 *
 * Only at the end. "Read 2 hours of Kafka" in the middle of a line is part
 * of the title, and a parser that pulled it out would hand back a part
 * called "Read of Kafka".
 */
private val TRAILING_TIME = Regex(
    """(?:[·\-–—(,]\s*)?(\d+)\s*(h|hr|hrs|hour|hours|m|min|mins|minute|minutes)\)?\s*$""",
    RegexOption.IGNORE_CASE,
)

/** Punctuation left dangling once a time has been pulled off the end. */
private val TRAILING_JUNK = Regex("""[\s|,;:.\-–—(]+$""")

/** One part of a syllabus, as the text said it. */
data class ParsedUnit(val title: String, val estimateMinutes: Int?)

/**
 * Reads a pasted syllabus: one part per line, in the order they will be done.
 *
 * Deliberately simple. A course outline arrives as a numbered list, a
 * bulleted list, or lines copied out of a PDF, and all three say the same
 * thing: these, in this order. A heading, a line that ends in a colon, is
 * skipped rather than made a part, because "Week 2:" is not a thing to sit
 * down and do. A time on the end of a line becomes the estimate.
 *
 * It never throws and it never invents. A line it reads as empty is left
 * out, and the screen says how many parts were understood before anything
 * is saved.
 */
class TrackTextParser {

    fun parse(text: String): List<ParsedUnit> = text.lines().mapNotNull(::parseLine)

    private fun parseLine(raw: String): ParsedUnit? {
        val line = LEADING_MARKER.replace(raw.trim(), "")
        if (line.isEmpty() || line.endsWith(":")) return null

        val time = TRAILING_TIME.find(line)
        val title = (time?.let { line.substring(0, it.range.first) } ?: line).replace(TRAILING_JUNK, "").trim()
        if (title.isEmpty()) return null

        return ParsedUnit(title = title, estimateMinutes = time?.let(::minutesOf))
    }

    private fun minutesOf(match: MatchResult): Int {
        val amount = match.groupValues[1].toInt()
        val unit = match.groupValues[2].lowercase()

        return if (unit.startsWith("h")) amount * MINUTES_IN_HOUR else amount
    }
}
