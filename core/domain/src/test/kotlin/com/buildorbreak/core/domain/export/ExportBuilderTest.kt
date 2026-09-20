package com.buildorbreak.core.domain.export

import com.buildorbreak.core.model.enums.Salience
import com.buildorbreak.core.model.plan.Anchor
import com.buildorbreak.core.model.plan.Plan
import com.buildorbreak.core.testing.fixtures.ExecutionFixtures
import com.buildorbreak.core.testing.fixtures.GoalFixtures
import com.buildorbreak.core.testing.fixtures.PlanFixtures
import com.buildorbreak.core.testing.fixtures.PlanFixtures.fixedAt
import com.buildorbreak.core.testing.fixtures.PlanFixtures.item
import com.buildorbreak.core.testing.fixtures.PlanFixtures.relativeTo
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalTime
import kotlin.time.Duration.Companion.minutes
import org.junit.jupiter.api.Test

class ExportBuilderTest {

    private val builder = ExportBuilder()
    private val exportedAt: Instant = Instant.parse("2026-01-05T10:15:30Z")

    private val plan = Plan(
        id = PlanFixtures.PLAN_ID,
        name = "Weekday routine",
        isActive = true,
        zone = ExecutionFixtures.ZONE,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
    )

    private fun inputOf(
        items: List<com.buildorbreak.core.model.plan.Item> = listOf(item(id = 1, anchor = fixedAt(8))),
        occurrences: List<com.buildorbreak.core.model.execution.Occurrence> = emptyList(),
    ) = ExportInput(
        plan = plan,
        templates = listOf(PlanFixtures.template()),
        items = items,
        occurrences = occurrences,
    )

    @Test
    fun `an export is stamped with the schema version it was written against`() {
        val document = builder.build(inputOf(), exportedAt)

        assertThat(document.schemaVersion).isEqualTo(CURRENT_SCHEMA_VERSION)
        assertThat(document.exportedAt).isEqualTo("2026-01-05T10:15:30Z")
    }

    @Test
    fun `the plan keeps the zone it was written against`() {
        val document = builder.build(inputOf(), exportedAt)

        assertThat(document.plan.zone).isEqualTo("Asia/Kolkata")
        assertThat(document.plan.name).isEqualTo("Weekday routine")
    }

    @Test
    fun `items are nested under the template they belong to`() {
        val document = builder.build(inputOf(items = (1L..3L).map { item(id = it) }), exportedAt)

        assertThat(document.templates).hasSize(1)
        assertThat(document.templates.single().items.map { it.id }).containsExactly(1L, 2L, 3L).inOrder()
    }

    // Anchors -----------------------------------------------------------------

    @Test
    fun `a fixed anchor exports its clock time`() {
        val document = builder.build(inputOf(listOf(item(id = 1, anchor = fixedAt(8, 30)))), exportedAt)

        val anchor = document.templates.single().items.single().anchor
        assertThat(anchor.type).isEqualTo("FIXED")
        assertThat(anchor.at).isEqualTo("08:30")
    }

    @Test
    fun `a relative anchor exports its parent and its offset in minutes`() {
        val document = builder.build(inputOf(listOf(item(id = 2, anchor = relativeTo(1, 45.minutes)))), exportedAt)

        val anchor = document.templates.single().items.single().anchor
        assertThat(anchor.type).isEqualTo("RELATIVE")
        assertThat(anchor.parentItemId).isEqualTo(1L)
        assertThat(anchor.offsetMinutes).isEqualTo(45L)
    }

    @Test
    fun `an interval anchor exports its window and its step`() {
        val interval = Anchor.Interval(45.minutes, LocalTime.of(11, 0), LocalTime.of(13, 30))

        val document = builder.build(inputOf(listOf(item(id = 1, anchor = interval))), exportedAt)

        val anchor = document.templates.single().items.single().anchor
        assertThat(anchor.type).isEqualTo("INTERVAL")
        assertThat(anchor.from).isEqualTo("11:00")
        assertThat(anchor.to).isEqualTo("13:30")
        assertThat(anchor.everyMinutes).isEqualTo(45L)
    }

    @Test
    fun `a window anchor exports its nag ladder`() {
        val window = Anchor.Window(LocalTime.of(17, 30), LocalTime.of(19, 30), listOf(30.minutes, 60.minutes))

        val document = builder.build(inputOf(listOf(item(id = 1, anchor = window))), exportedAt)

        assertThat(document.templates.single().items.single().anchor.nagLadderMinutes)
            .containsExactly(30L, 60L).inOrder()
    }

    @Test
    fun `the smaller version travels with the item`() {
        val withMinimum = item(id = 1, minimum = PlanFixtures.minimum(title = "Ten minutes", duration = 10.minutes))

        val document = builder.build(inputOf(listOf(withMinimum)), exportedAt)

        val minimum = document.templates.single().items.single().minimum
        assertThat(minimum?.title).isEqualTo("Ten minutes")
        assertThat(minimum?.durationMinutes).isEqualTo(10L)
    }

    // History -----------------------------------------------------------------

    @Test
    fun `history is included by default`() {
        val document = builder.build(
            inputOf(occurrences = listOf(ExecutionFixtures.done(itemId = 1))),
            exportedAt,
        )

        assertThat(document.history.occurrences).hasSize(1)
        assertThat(document.history.occurrences.single().state).isEqualTo("DONE")
    }

    @Test
    fun `a routine can be exported without handing over months of adherence`() {
        val document = builder.build(
            inputOf(occurrences = listOf(ExecutionFixtures.done(itemId = 1))),
            exportedAt,
            includeHistory = false,
        )

        assertThat(document.history.occurrences).isEmpty()
        assertThat(document.templates.single().items).isNotEmpty()
    }

    @Test
    fun `day closes are exported in date order`() {
        val input = ExportInput(
            plan = plan,
            closes = GoalFixtures.closes(GoalFixtures.START, days = 3).reversed(),
        )

        val document = builder.build(input, exportedAt)

        assertThat(document.history.dayCloses.map { it.date })
            .containsExactly("2026-01-01", "2026-01-02", "2026-01-03").inOrder()
    }

    // The properties that make a backup worth taking --------------------------

    @Test
    fun `the same plan always produces byte identical output`() {
        val ordered = inputOf(items = listOf(item(id = 1), item(id = 2), item(id = 3)))
        val shuffled = inputOf(items = listOf(item(id = 3), item(id = 1), item(id = 2)))

        assertThat(builder.buildJson(shuffled, exportedAt)).isEqualTo(builder.buildJson(ordered, exportedAt))
    }

    @Test
    fun `the json is readable without a schema in hand`() {
        val json = builder.buildJson(
            inputOf(listOf(item(id = 1, title = "Evening walk", salience = Salience.NOTIFY))),
            exportedAt,
        )

        assertThat(json).contains("\"schema_version\": 1")
        assertThat(json).contains("\"title\": \"Evening walk\"")
        assertThat(json).contains("\"salience\": \"NOTIFY\"")
    }

    @Test
    fun `a title with quotes in it does not break the file`() {
        val awkward = item(id = 1, title = "Read \"Atomic Habits\" for 20m\nthen note it")

        val json = builder.buildJson(inputOf(listOf(awkward)), exportedAt)

        assertThat(json).contains("\\\"Atomic Habits\\\"")
        assertThat(json).contains("\\n")
    }
}
