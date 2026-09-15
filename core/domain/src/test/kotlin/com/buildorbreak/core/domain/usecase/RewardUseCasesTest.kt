package com.buildorbreak.core.domain.usecase

import com.buildorbreak.core.common.coroutines.AppDispatchers
import com.buildorbreak.core.domain.fake.FakeDayCloseRepository
import com.buildorbreak.core.domain.fake.FakeMilestoneRepository
import com.buildorbreak.core.domain.goal.Points
import com.buildorbreak.core.model.enums.DayQuality
import com.buildorbreak.core.model.enums.Milestone
import com.buildorbreak.core.model.goal.DayClose
import com.buildorbreak.core.model.goal.MilestoneAward
import com.buildorbreak.core.testing.time.FakeTimeProvider
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

private val ZONE: ZoneId = ZoneId.of("Asia/Kolkata")

/** A Thursday, so the week has three closed days in it and four before it. */
private val TODAY: LocalDate = LocalDate.of(2026, 9, 10)

private val NOW: Instant = TODAY.atStartOfDay(ZONE).toInstant()

/**
 * Points are arithmetic on rows that already exist, and badges are the
 * milestone table read the other way round. Both have to be checkable by
 * hand against the numbers on screen, so every case here is one somebody
 * could work out on paper.
 */
class RewardUseCasesTest {

    private val closes = FakeDayCloseRepository()
    private val milestones = FakeMilestoneRepository()
    private val time = FakeTimeProvider(initial = NOW, currentZone = ZONE)

    private val dispatchers = object : AppDispatchers {
        override val default = Dispatchers.Unconfined
        override val io = Dispatchers.Unconfined
        override val main = Dispatchers.Unconfined
    }

    private val points = ObservePointsUseCase(closes, time, dispatchers)
    private val badges = ObserveBadgesUseCase(milestones, dispatchers)

    @Test
    fun `a step is ten, a smaller version five, and a whole day twenty on top`() {
        assertThat(Points.of(done = 3, minimum = 1, total = 6)).isEqualTo(35)
        assertThat(Points.of(done = 6, minimum = 0, total = 6)).isEqualTo(80)
    }

    @Test
    fun `a minimum version on every step is not a whole day`() {
        assertThat(Points.of(done = 5, minimum = 1, total = 6)).isEqualTo(55)
    }

    @Test
    fun `a day with nothing planned earns nothing, not the bonus`() {
        assertThat(Points.of(done = 0, minimum = 0, total = 0)).isEqualTo(0)
    }

    @Test
    fun `a poor day keeps what it earned and loses nothing`() {
        assertThat(Points.of(done = 1, minimum = 0, total = 9)).isEqualTo(10)
    }

    @Test
    fun `the bank is every closed day, and the week starts on monday`() = runTest {
        // Last Friday, Saturday, then Monday to Wednesday of this week.
        close(TODAY.minusDays(6), done = 2, total = 4)
        close(TODAY.minusDays(5), done = 4, total = 4)
        close(TODAY.minusDays(3), done = 1, total = 4)
        close(TODAY.minusDays(2), done = 3, total = 4)
        close(TODAY.minusDays(1), done = 4, total = 4)

        val tally = points(TODAY).first()

        assertThat(tally.banked).isEqualTo(20 + 60 + 10 + 30 + 60)
        assertThat(tally.thisWeek).isEqualTo(10 + 30 + 60)
        assertThat(tally.bestDay).isEqualTo(60)
    }

    @Test
    fun `today is not banked even if a row for it exists`() = runTest {
        close(TODAY.minusDays(1), done = 2, total = 2)
        close(TODAY, done = 2, total = 2)

        val tally = points(TODAY).first()

        assertThat(tally.banked).isEqualTo(40)
    }

    @Test
    fun `before the first close there is no best day`() = runTest {
        val tally = points(TODAY).first()

        assertThat(tally.banked).isEqualTo(0)
        assertThat(tally.bestDay).isNull()
    }

    @Test
    fun `every badge is listed, earned or not, in declared order`() = runTest {
        milestones.award(award(Milestone.FIRST_FULL_DAY, TODAY.minusDays(2)))
        milestones.award(award(Milestone.FIRST_COMPLETION, TODAY.minusDays(5)))

        val wall = badges().first()

        assertThat(wall.map { it.milestone }).isEqualTo(Milestone.entries)
        assertThat(wall.count { it.isEarned }).isEqualTo(2)
        assertThat(wall.first { it.milestone == Milestone.FIRST_COMPLETION }.earnedOn).isEqualTo(TODAY.minusDays(5))
        assertThat(wall.first { it.milestone == Milestone.GOAL_HALF }.earnedOn).isNull()
    }

    private suspend fun close(
        date: LocalDate,
        done: Int,
        total: Int,
        minimum: Int = 0,
    ) {
        closes.upsert(
            DayClose(
                date = date,
                planId = 1,
                itemsDone = done,
                itemsMinimum = minimum,
                itemsMissed = total - done - minimum,
                itemsTotal = total,
                quality = if (done + minimum >= total / 2) DayQuality.OK else DayQuality.POOR,
                closedAt = NOW,
            ),
        )
    }

    private fun award(milestone: Milestone, on: LocalDate) =
        MilestoneAward(milestone = milestone, goalId = null, itemId = null, awardedOn = on, seenAt = null)
}
