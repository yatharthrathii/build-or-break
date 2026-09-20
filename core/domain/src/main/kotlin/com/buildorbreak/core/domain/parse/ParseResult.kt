package com.buildorbreak.core.domain.parse

import com.buildorbreak.core.common.result.Outcome
import com.buildorbreak.core.domain.error.DomainError.ParseError
import com.buildorbreak.core.model.enums.Salience
import com.buildorbreak.core.model.plan.Anchor
import kotlin.time.Duration

/**
 * One line of pasted text, understood.
 *
 * Not an `Item`. The parser has no idea what template this belongs to, what ids
 * exist, or what order the user will want, and inventing any of that here would
 * put plan decisions inside a text reader. This is the raw understanding; the
 * import use case turns it into rows.
 */
data class ParsedItem(
    val title: String,
    val anchor: Anchor,
    val duration: Duration? = null,
    /** Only when the text said so. Most text does not. */
    val salience: Salience? = null,
    val minimumTitle: String? = null,
    val pinned: Boolean = false,
    /** The section heading this line sat under, when there was one. */
    val section: String? = null,
    /** The line it came from, so a confirm screen can show the original. */
    val sourceLine: String,
)

/**
 * What the parser understood, and what it did not.
 *
 * **[unrecognised] is the important half.** A parser that quietly drops what it
 * cannot read produces a plan that looks complete and is missing the two steps
 * that mattered, and the user finds out at six in the morning when an alarm does
 * not fire. Every line that was not understood is handed back so a confirm
 * screen can show it and ask.
 */
data class ParseResult(
    val items: List<ParsedItem>,
    val unrecognised: List<String>,
) {
    val recognisedCount: Int get() = items.size

    val totalCount: Int get() = items.size + unrecognised.size

    val isComplete: Boolean get() = unrecognised.isEmpty() && items.isNotEmpty()

    val isEmpty: Boolean get() = items.isEmpty()

    /**
     * The same result as an [Outcome], for callers that treat a partial read as
     * something to act on rather than something to display.
     *
     * A partial parse is a success with a caveat, not a failure. Refusing the
     * whole paste because one line was odd is how an import screen loses
     * somebody on their first minute in the app.
     */
    fun asOutcome(): Outcome<ParseResult, ParseError> = when {
        isEmpty -> Outcome.Failure(ParseError.NothingRecognised)
        unrecognised.isNotEmpty() -> Outcome.Failure(ParseError.PartialParse(recognisedCount, totalCount))
        else -> Outcome.Success(this)
    }
}

/**
 * The format the app guarantees it can read, written as something to hand an AI
 * tool.
 *
 * This is the primary import path, and the design decision behind it is worth
 * stating. Parsing arbitrary prose is an unbounded problem: every shape not
 * handled is a user whose first experience of the app is failure, and this is
 * the first thing anybody does. Rather than trying to be cleverer than every
 * possible input, the app hands the user a prompt, their own AI tool does the
 * shaping, and what comes back parses exactly.
 *
 * [PlanTextParser] still reads ordinary lists as a fallback, because somebody
 * will always paste something else. That path is best effort and its output goes
 * to a confirm screen before anything is saved.
 */
object PlanFormat {

    val PROMPT: String = """
        Rewrite my routine as one line per step, in exactly this format:

          HH:MM | Title | duration | notes

        Rules:
          - 24 hour clock, for example 06:30 or 18:00
          - Use "+15m | Title" when a step happens a set time after the one above
          - Use "07:30-09:30 | Title" when a step can happen any time in a window
          - Use "every 45m 11:00-15:00 | Title" for something that repeats
          - Duration is optional, written as 30m or 1h
          - Add "min: something smaller" for a reduced version on a bad day
          - Add "pinned" for anything that must not move if the day runs late
          - One step per line, nothing else, no headings, no commentary

        My routine:
    """.trimIndent()
}
