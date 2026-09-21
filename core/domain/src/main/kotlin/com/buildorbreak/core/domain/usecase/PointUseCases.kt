package com.buildorbreak.core.domain.usecase

import com.buildorbreak.core.common.coroutines.AppDispatchers
import com.buildorbreak.core.common.result.Outcome
import com.buildorbreak.core.common.time.TimeProvider
import com.buildorbreak.core.domain.error.DomainError.DataError
import com.buildorbreak.core.domain.goal.FreezeOffer
import com.buildorbreak.core.domain.goal.Points
import com.buildorbreak.core.domain.goal.Prices
import com.buildorbreak.core.domain.goal.Streaks
import com.buildorbreak.core.domain.repository.DayCloseRepository
import com.buildorbreak.core.domain.repository.PlanRepository
import com.buildorbreak.core.domain.repository.PointLedgerRepository
import com.buildorbreak.core.domain.repository.TemplateRepository
import com.buildorbreak.core.model.enums.PointReason
import com.buildorbreak.core.model.goal.PointEntry
import com.buildorbreak.core.model.goal.Wallet
import com.buildorbreak.core.model.plan.DayTemplate
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * What is left to spend.
 *
 * Earned is the closes plus whatever was granted on top; spent is the
 * ledger's negatives. Neither number is stored as a total, so the balance
 * cannot drift away from the rows it came from. The cost of that is one sum
 * over a table with a few rows a week in it, which is nothing.
 *
 * Today is left out of earned for the same reason the Insights figure
 * leaves it out: the day is still open and its points move with every tap.
 * Spending against a number that changes under the thumb would let a
 * purchase succeed and then leave the balance negative by bedtime.
 */
class ObserveWalletUseCase @Inject constructor(
    private val closes: DayCloseRepository,
    private val ledger: PointLedgerRepository,
    private val time: TimeProvider,
    private val dispatchers: AppDispatchers,
) {

    operator fun invoke(): Flow<Wallet> = combine(
        closes.observeAll(),
        ledger.observeGranted(),
        ledger.observeSpent(),
    ) { history, granted, spent ->
        val today = time.today()
        val banked = history.filter { it.date < today }.sumOf { Points.forDay(it) }

        Wallet(earned = banked + granted, spent = spent)
    }.flowOn(dispatchers.default)
}

/**
 * Buys one thing, or says plainly that there are not enough points.
 *
 * The check and the write are one call so two taps cannot both pass the
 * check and both spend. A balance that can go negative would make every
 * number built on it meaningless, and this is the only door into the table.
 */
class SpendPointsUseCase @Inject constructor(
    private val wallet: ObserveWalletUseCase,
    private val ledger: PointLedgerRepository,
    private val time: TimeProvider,
    private val dispatchers: AppDispatchers,
) {

    /**
     * [on] is the day the purchase is about. A freeze is bought for a
     * particular missed day; everything else is about today.
     */
    suspend operator fun invoke(reason: PointReason, on: LocalDate? = null): Outcome<Unit, DataError> =
        withContext(dispatchers.io) {
            val cost = Prices.of(reason)
            if (!wallet().first().canAfford(cost)) return@withContext Outcome.Failure(DataError.ConstraintViolation)

            ledger.add(
                PointEntry(
                    id = 0,
                    at = time.now(),
                    date = on ?: time.today(),
                    delta = -cost,
                    reason = reason,
                ),
            )
        }
}

/**
 * Turns a watched ad into points, at most once a day.
 *
 * Nothing calls this yet. The ads SDK is deliberately not wired up, because
 * the moment it is the app stops being one that never touches the network,
 * and that is a trade to make when there are users rather than before. The
 * rule that matters is here and tested so that wiring it later is one call
 * from the callback the SDK fires when an ad is genuinely finished.
 *
 * Never call it from a tap. Google's policy rewards a completed view and
 * bans rewarding a click, and the daily cap is what keeps the ceiling from
 * ads below what an ordinary day of the routine earns.
 */
class GrantAdRewardUseCase @Inject constructor(
    private val ledger: PointLedgerRepository,
    private val time: TimeProvider,
    private val dispatchers: AppDispatchers,
) {

    suspend operator fun invoke(): Outcome<Int, DataError> = withContext(dispatchers.io) {
        val today = time.today()
        if (ledger.countFor(PointReason.AD_REWARD, today) >= Prices.ADS_PER_DAY) {
            return@withContext Outcome.Failure(DataError.ConstraintViolation)
        }

        val written = ledger.add(
            PointEntry(
                id = 0,
                at = time.now(),
                date = today,
                delta = Prices.AD_REWARD,
                reason = PointReason.AD_REWARD,
            ),
        )

        when (written) {
            is Outcome.Success -> Outcome.Success(Prices.AD_REWARD)
            is Outcome.Failure -> written
        }
    }
}

/** Whether another ad can still be turned into points today. */
class ObserveAdAvailableUseCase @Inject constructor(
    private val ledger: PointLedgerRepository,
    private val time: TimeProvider,
    private val dispatchers: AppDispatchers,
) {

    operator fun invoke(): Flow<Boolean> = ledger.observeAll()
        .map { entries ->
            val today = time.today()
            entries.count { it.reason == PointReason.AD_REWARD && it.date == today } < Prices.ADS_PER_DAY
        }
        .flowOn(dispatchers.default)
}

/** The days a freeze was bought for, which the run counts as kept. */
class ObserveFrozenDaysUseCase @Inject constructor(
    private val ledger: PointLedgerRepository,
) {

    operator fun invoke(): Flow<Set<LocalDate>> = ledger.observeDatesFor(PointReason.STREAK_FREEZE).map { it.toSet() }
}

/**
 * The day a freeze is worth buying for right now, or null when there is none.
 *
 * The screen never picks the day. `Streaks` finds the one missed day that
 * ended the run, and that is the only day on offer, so nobody can spend
 * points on a day that would not bring the run back.
 */
class ObserveFreezeOfferUseCase @Inject constructor(
    private val closes: DayCloseRepository,
    private val frozenDays: ObserveFrozenDaysUseCase,
    private val time: TimeProvider,
    private val dispatchers: AppDispatchers,
) {

    operator fun invoke(): Flow<FreezeOffer?> = combine(closes.observeAll(), frozenDays()) { history, frozen ->
        Streaks.freezeOffer(history, time.today(), frozen)
    }.flowOn(dispatchers.default)
}

/**
 * One line of the ledger.
 *
 * A [reason] of null is a day of the routine, which is not a stored row at
 * all: it is worked out from the close, the same as every other figure the
 * app shows for that day. The list is the two sources read together, because
 * "where did my points go" is a question about both.
 */
data class PointMovement(val date: LocalDate, val delta: Int, val reason: PointReason?)

/**
 * Everything that moved, newest first.
 *
 * Days with no points are left out. A routine with nothing done earns
 * nothing, and a list padded with zeroes is a list nobody scrolls.
 */
class ObserveLedgerUseCase @Inject constructor(
    private val closes: DayCloseRepository,
    private val ledger: PointLedgerRepository,
    private val time: TimeProvider,
    private val dispatchers: AppDispatchers,
) {

    operator fun invoke(): Flow<List<PointMovement>> = combine(
        closes.observeAll(),
        ledger.observeAll(),
    ) { history, entries ->
        val today = time.today()

        val days = history
            .filter { it.date < today }
            .map { PointMovement(date = it.date, delta = Points.forDay(it), reason = null) }
            .filter { it.delta != 0 }

        val moves = entries.map { PointMovement(date = it.date, delta = it.delta, reason = it.reason) }

        (days + moves).sortedWith(compareByDescending<PointMovement> { it.date }.thenBy { it.reason == null })
    }.flowOn(dispatchers.default)
}

/**
 * Puts a step back to not done long after the undo bar has gone.
 *
 * The bar is the free way out and it stays free. This is the other one: the
 * evening realisation that a step was ticked and never actually done. It
 * rewrites a record that the day, the run and the goal have all been built
 * on, so it costs points, and the points are taken first. A spend that
 * succeeded against an undo that then failed would charge for nothing.
 */
class UndoStepForPointsUseCase @Inject constructor(
    private val spend: SpendPointsUseCase,
    private val undo: UndoSettleUseCase,
    private val dispatchers: AppDispatchers,
) {

    suspend operator fun invoke(occurrenceId: Long): Outcome<Unit, DataError> = withContext(dispatchers.io) {
        val paid = spend(PointReason.UNDO_STEP)
        if (paid is Outcome.Failure) return@withContext paid

        undo(occurrenceId)
    }
}

/**
 * Covers one missed day so the run carries on across it.
 *
 * Only for a day that is past and was not kept. Buying a freeze for a day
 * that already counted would take points for nothing, and buying one for
 * today would settle a day that is still being lived.
 */
class FreezeDayUseCase @Inject constructor(
    private val spend: SpendPointsUseCase,
    private val ledger: PointLedgerRepository,
    private val time: TimeProvider,
    private val dispatchers: AppDispatchers,
) {

    suspend operator fun invoke(date: LocalDate): Outcome<Unit, DataError> = withContext(dispatchers.io) {
        if (date >= time.today()) return@withContext Outcome.Failure(DataError.ConstraintViolation)

        val already = ledger.observeDatesFor(PointReason.STREAK_FREEZE).first()
        if (date in already) return@withContext Outcome.Failure(DataError.ConstraintViolation)

        spend(PointReason.STREAK_FREEZE, on = date)
    }
}

/**
 * Adds a day template, and charges for it past the free ones.
 *
 * Two are free because two is what an ordinary week needs: a weekday and a
 * weekend. The third is where a plan stops being one routine and starts
 * being a collection, which is the point at which it is worth something.
 *
 * Separate from `SaveTemplateUseCase` rather than a flag on it. That one is
 * also how an edit is saved and how the importer writes its first template,
 * and a price hidden inside it would be charged on paths nobody asked to pay
 * for.
 */
class AddRoutineUseCase @Inject constructor(
    private val templates: TemplateRepository,
    private val save: SaveTemplateUseCase,
    private val spend: SpendPointsUseCase,
    private val dispatchers: AppDispatchers,
) {

    suspend operator fun invoke(template: DayTemplate): Outcome<Long, DataError> = withContext(dispatchers.io) {
        val existing = templates.observeForPlan(template.planId).first().size

        if (existing >= Prices.FREE_ROUTINES) {
            val paid = spend(PointReason.EXTRA_ROUTINE)
            if (paid is Outcome.Failure) return@withContext Outcome.Failure(paid.reason)
        }

        save(template)
    }
}

/** Whether the next routine would be charged for, and how many are still free. */
class ObserveRoutineCostUseCase @Inject constructor(
    private val templates: TemplateRepository,
    private val plans: PlanRepository,
    private val dispatchers: AppDispatchers,
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<Int> = plans.observeActive()
        .flatMapLatest { plan ->
            if (plan == null) flowOf(0) else templates.observeForPlan(plan.id).map { it.size }
        }
        .map { count -> if (count >= Prices.FREE_ROUTINES) Prices.EXTRA_ROUTINE else 0 }
        .flowOn(dispatchers.default)
}
