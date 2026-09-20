package com.buildorbreak.core.domain.usecase

import com.buildorbreak.core.common.coroutines.AppDispatchers
import com.buildorbreak.core.common.result.Outcome
import com.buildorbreak.core.domain.fake.FakeDayCloseRepository
import com.buildorbreak.core.domain.fake.FakePointLedgerRepository
import com.buildorbreak.core.domain.goal.Prices
import com.buildorbreak.core.model.enums.DayQuality
import com.buildorbreak.core.model.enums.PointReason
import com.buildorbreak.core.model.goal.DayClose
import com.buildorbreak.core.testing.fixtures.PlanFixtures
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
private val NOW: Instant = Instant.parse("2026-09-20T05:00:00Z")
private val TODAY: LocalDate = LocalDate.of(2026, 9, 20)

/**
 * The points wallet, and the three things that can be bought with it.
 *
 * The behaviour worth pinning down is that the balance is never stored. It
 * is the closes plus what was granted, minus what was spent, worked out
 * afresh every time, and these tests are what stop somebody optimising that
 * into a column that can drift.
 */
class PointUseCasesTest {

    private val closes = FakeDayCloseRepository()
    private val ledger = FakePointLedgerRepository()
    private val time = FakeTimeProvider(initial = NOW, currentZone = ZONE)

    private val dispatchers = object : AppDispatchers {
        override val default = Dispatchers.Unconfined
        override val io = Dispatchers.Unconfined
        override val main = Dispatchers.Unconfined
    }

    private val wallet = ObserveWalletUseCase(closes, ledger, time, dispatchers)
    private val spend = SpendPointsUseCase(wallet, ledger, time, dispatchers)
    private val grantAd = GrantAdRewardUseCase(ledger, time, dispatchers)
    private val adAvailable = ObserveAdAvailableUseCase(ledger, time, dispatchers)
    private val frozen = ObserveFrozenDaysUseCase(ledger)
    private val freeze = FreezeDayUseCase(spend, ledger, time, dispatchers)
    private val movements = ObserveLedgerUseCase(closes, ledger, time, dispatchers)

    /** A full day is nine done out of nine, which is ninety plus the twenty bonus. */
    private fun givenKeptDays(count: Int) {
        closes.closes.value = (1..count).map { back ->
            DayClose(
                date = TODAY.minusDays(back.toLong()),
                planId = PlanFixtures.PLAN_ID,
                itemsDone = 9,
                itemsMinimum = 0,
                itemsMissed = 0,
                itemsTotal = 9,
                quality = DayQuality.GOOD,
                closedAt = NOW,
            )
        }
    }

    @Test
    fun `a kept day is worth its points and they are all still there to spend`() = runTest {
        givenKeptDays(1)

        val held = wallet().first()
        assertThat(held.earned).isEqualTo(110)
        assertThat(held.spent).isEqualTo(0)
        assertThat(held.balance).isEqualTo(110)
    }

    @Test
    fun `today is left out, because its points are still moving`() = runTest {
        closes.closes.value = listOf(
            DayClose(
                date = TODAY,
                planId = PlanFixtures.PLAN_ID,
                itemsDone = 9,
                itemsMinimum = 0,
                itemsMissed = 0,
                itemsTotal = 9,
                quality = DayQuality.GOOD,
                closedAt = NOW,
            ),
        )

        assertThat(wallet().first().earned).isEqualTo(0)
    }

    @Test
    fun `spending takes the cost off the balance and leaves the earned figure alone`() = runTest {
        givenKeptDays(3)

        assertThat(spend(PointReason.UNDO_STEP)).isInstanceOf(Outcome.Success::class.java)

        val held = wallet().first()
        assertThat(held.earned).isEqualTo(330)
        assertThat(held.spent).isEqualTo(Prices.UNDO_STEP)
        assertThat(held.balance).isEqualTo(330 - Prices.UNDO_STEP)
    }

    @Test
    fun `a purchase nobody can afford is refused and writes nothing`() = runTest {
        givenKeptDays(1)

        assertThat(spend(PointReason.EXTRA_ROUTINE)).isInstanceOf(Outcome.Failure::class.java)
        assertThat(ledger.entries.value).isEmpty()
    }

    @Test
    fun `an ad can be turned into points once a day and no more`() = runTest {
        assertThat(grantAd()).isEqualTo(Outcome.Success(Prices.AD_REWARD))
        assertThat(adAvailable().first()).isFalse()

        assertThat(grantAd()).isInstanceOf(Outcome.Failure::class.java)
        assertThat(wallet().first().earned).isEqualTo(Prices.AD_REWARD)
    }

    @Test
    fun `tomorrow the ad is on offer again`() = runTest {
        grantAd()
        time.advanceByDays(1)

        assertThat(adAvailable().first()).isTrue()
    }

    @Test
    fun `a freeze covers the day it was bought for`() = runTest {
        givenKeptDays(3)
        val missed = TODAY.minusDays(1)

        assertThat(freeze(missed)).isInstanceOf(Outcome.Success::class.java)
        assertThat(frozen().first()).containsExactly(missed)
    }

    @Test
    fun `a day cannot be frozen twice, and today cannot be frozen at all`() = runTest {
        givenKeptDays(5)
        val missed = TODAY.minusDays(2)

        freeze(missed)

        assertThat(freeze(missed)).isInstanceOf(Outcome.Failure::class.java)
        assertThat(freeze(TODAY)).isInstanceOf(Outcome.Failure::class.java)
        assertThat(frozen().first()).hasSize(1)
    }

    @Test
    fun `the ledger shows the days earned and the things bought, newest first`() = runTest {
        givenKeptDays(2)
        spend(PointReason.UNDO_STEP)

        val lines = movements().first()

        assertThat(lines).hasSize(3)
        assertThat(lines.first().reason).isEqualTo(PointReason.UNDO_STEP)
        assertThat(lines.first().delta).isEqualTo(-Prices.UNDO_STEP)
        assertThat(lines.drop(1).map { it.delta }).containsExactly(110, 110)
    }
}
