package com.buildorbreak.core.domain.parse

import com.buildorbreak.core.common.result.Outcome
import com.buildorbreak.core.domain.error.DomainError.ParseError
import com.buildorbreak.core.model.enums.Salience
import com.buildorbreak.core.model.plan.Anchor
import com.google.common.truth.Truth.assertThat
import java.time.LocalTime
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import org.junit.jupiter.api.Test

class PlanTextParserTest {

    private val parser = PlanTextParser()

    private fun firstOf(text: String): ParsedItem = parser.parse(text).items.first()

    // The strict format, which always has to work ------------------------------

    @Test
    fun `the format the app hands to an ai tool parses completely`() {
        val pasted = """
            06:30 | Wake up | alarm
            +10m | Drink water | silent
            07:30-09:30 | Study block | 60m | min: fifteen minutes
            every 45m 11:00-15:00 | Stand and stretch | silent
            18:00 | Gym class | alarm | pinned
        """.trimIndent()

        val result = parser.parse(pasted)

        assertThat(result.isComplete).isTrue()
        assertThat(result.unrecognised).isEmpty()
        assertThat(result.items.map { it.title }).containsExactly(
            "Wake up",
            "Drink water",
            "Study block",
            "Stand and stretch",
            "Gym class",
        ).inOrder()
    }

    // Anchors ------------------------------------------------------------------

    @Test
    fun `a plain time is a fixed anchor`() {
        val item = firstOf("06:30 Wake up")

        assertThat(item.anchor).isEqualTo(Anchor.Fixed(LocalTime.of(6, 30)))
        assertThat(item.title).isEqualTo("Wake up")
    }

    @Test
    fun `a range is a window, not two separate items`() {
        val item = firstOf("07:30-09:30 Study block")

        assertThat(item.anchor).isEqualTo(Anchor.Window(LocalTime.of(7, 30), LocalTime.of(9, 30)))
    }

    @Test
    fun `the word to reads as a range just as a dash does`() {
        val item = firstOf("7:30 to 9:30 Study block")

        assertThat(item.anchor).isEqualTo(Anchor.Window(LocalTime.of(7, 30), LocalTime.of(9, 30)))
    }

    @Test
    fun `every reads as an interval inside its window`() {
        val item = firstOf("every 45m 11:00-15:00 Stand and stretch")

        assertThat(item.anchor)
            .isEqualTo(Anchor.Interval(45.minutes, LocalTime.of(11, 0), LocalTime.of(15, 0)))
    }

    @Test
    fun `a plus reads as an offset from the line above`() {
        val result = parser.parse("06:30 Wake up\n+10m Drink water")

        val second = result.items[1].anchor as Anchor.Relative
        assertThat(second.offset).isEqualTo(10.minutes)
        assertThat(second.parentItemId).isEqualTo(PlanTextParser.PARENT_UNRESOLVED)
    }

    @Test
    fun `the word after reads the same way as a plus`() {
        val result = parser.parse("06:30 Wake up\nafter 20 minutes Medicine")

        assertThat((result.items[1].anchor as Anchor.Relative).offset).isEqualTo(20.minutes)
        assertThat(result.items[1].title).isEqualTo("Medicine")
    }

    @Test
    fun `an offset on the very first line has nothing to hang off and is not guessed at`() {
        val result = parser.parse("+10m Drink water")

        assertThat(result.items).isEmpty()
        assertThat(result.unrecognised).containsExactly("+10m Drink water")
    }

    // Times people actually write ----------------------------------------------

    @Test
    fun `twelve hour times with am and pm are understood`() {
        assertThat((firstOf("6:30 AM Wake up").anchor as Anchor.Fixed).at).isEqualTo(LocalTime.of(6, 30))
        assertThat((firstOf("9:00 pm Read").anchor as Anchor.Fixed).at).isEqualTo(LocalTime.of(21, 0))
        assertThat((firstOf("7 p.m. Walk").anchor as Anchor.Fixed).at).isEqualTo(LocalTime.of(19, 0))
    }

    @Test
    fun `midnight and midday do not come out twelve hours wrong`() {
        assertThat((firstOf("12:00 am Sleep").anchor as Anchor.Fixed).at).isEqualTo(LocalTime.of(0, 0))
        assertThat((firstOf("12:00 pm Lunch").anchor as Anchor.Fixed).at).isEqualTo(LocalTime.of(12, 0))
    }

    @Test
    fun `a time that is not a real time is not invented into one`() {
        val result = parser.parse("25:99 Something")

        assertThat(result.items).isEmpty()
        assertThat(result.unrecognised).hasSize(1)
    }

    // Shapes people paste ------------------------------------------------------

    @Test
    fun `bullets, dashes and numbering are stripped`() {
        val pasted = """
            - 06:30 Wake up
            * 07:00 Breakfast
            1. 08:00 Gym
            • 09:00 Study
        """.trimIndent()

        val result = parser.parse(pasted)

        assertThat(result.items.map { it.title }).containsExactly("Wake up", "Breakfast", "Gym", "Study").inOrder()
    }

    @Test
    fun `an en dash between the time and the title is not part of the title`() {
        assertThat(firstOf("06:30 – Wake up").title).isEqualTo("Wake up")
        assertThat(firstOf("06:30: Wake up").title).isEqualTo("Wake up")
    }

    @Test
    fun `a heading groups the lines under it without becoming an item`() {
        val pasted = """
            Morning:
            06:30 Wake up
            Evening:
            21:00 Read
        """.trimIndent()

        val result = parser.parse(pasted)

        assertThat(result.items).hasSize(2)
        assertThat(result.items[0].section).isEqualTo("Morning")
        assertThat(result.items[1].section).isEqualTo("Evening")
    }

    @Test
    fun `blank lines are not failures`() {
        val result = parser.parse("06:30 Wake up\n\n\n21:00 Read\n")

        assertThat(result.isComplete).isTrue()
        assertThat(result.items).hasSize(2)
    }

    // The rest of the line -----------------------------------------------------

    @Test
    fun `a duration is taken out of the line rather than left in the title`() {
        val item = firstOf("21:00 Read 30m")

        assertThat(item.duration).isEqualTo(30.minutes)
        assertThat(item.title).isEqualTo("Read")
    }

    @Test
    fun `hours are understood as well as minutes`() {
        assertThat(firstOf("07:00 Deep work 2 hours").duration).isEqualTo(2.hours)
    }

    @Test
    fun `a smaller version is picked up and does not swallow the title`() {
        val item = firstOf("07:30 Study block | 60m | min: fifteen minutes")

        assertThat(item.title).isEqualTo("Study block")
        assertThat(item.duration).isEqualTo(60.minutes)
        assertThat(item.minimumTitle).isEqualTo("fifteen minutes")
    }

    @Test
    fun `pinned is understood wherever it sits on the line`() {
        assertThat(firstOf("18:00 Gym class pinned").pinned).isTrue()
        assertThat(firstOf("18:00 Gym class (pinned)").pinned).isTrue()
        assertThat(firstOf("18:00 Gym class").pinned).isFalse()
    }

    @Test
    fun `a stated salience is used and an unstated one is left for the caller`() {
        assertThat(firstOf("06:30 Wake up alarm").salience).isEqualTo(Salience.ALARM)
        assertThat(firstOf("06:30 Wake up").salience).isNull()
    }

    @Test
    fun `a default salience fills in for lines that do not say`() {
        val withDefault = PlanTextParser(defaultSalience = Salience.NOTIFY)

        assertThat(withDefault.parse("06:30 Wake up").items.single().salience).isEqualTo(Salience.NOTIFY)
    }

    @Test
    fun `every line keeps the text it came from, so a confirm screen can show it`() {
        val item = firstOf("- 06:30 – Wake up")

        assertThat(item.sourceLine).isEqualTo("- 06:30 – Wake up")
    }

    // Nothing is ever dropped silently -----------------------------------------

    @Test
    fun `a line with no time at all is handed back rather than discarded`() {
        val result = parser.parse("06:30 Wake up\nremember to buy milk\n21:00 Read")

        assertThat(result.items).hasSize(2)
        assertThat(result.unrecognised).containsExactly("remember to buy milk")
        assertThat(result.isComplete).isFalse()
    }

    @Test
    fun `a partial read is a success with a caveat, not a refusal`() {
        val result = parser.parse("06:30 Wake up\nsomething odd")

        val outcome = result.asOutcome()
        assertThat(outcome).isInstanceOf(Outcome.Failure::class.java)
        assertThat((outcome as Outcome.Failure).reason)
            .isEqualTo(ParseError.PartialParse(recognised = 1, total = 2))
    }

    @Test
    fun `text with nothing recognisable in it says so plainly`() {
        val result = parser.parse("hello\nthere is no routine here")

        assertThat(result.isEmpty).isTrue()
        assertThat((result.asOutcome() as Outcome.Failure).reason).isEqualTo(ParseError.NothingRecognised)
    }

    @Test
    fun `empty text is not an error to shout about`() {
        val result = parser.parse("")

        assertThat(result.items).isEmpty()
        assertThat(result.unrecognised).isEmpty()
        assertThat(result.totalCount).isEqualTo(0)
    }

    @Test
    fun `nothing in here throws, whatever is pasted`() {
        val nonsense = listOf("", "   ", ":::", "12:", "-", "every", "+", "07:30-", "min:", "(((")

        nonsense.forEach { parser.parse(it) }
        parser.parse(nonsense.joinToString("\n"))
    }
}
