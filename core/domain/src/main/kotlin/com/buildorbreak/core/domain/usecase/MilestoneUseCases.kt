package com.buildorbreak.core.domain.usecase

import com.buildorbreak.core.common.coroutines.AppDispatchers
import com.buildorbreak.core.common.result.Outcome
import com.buildorbreak.core.common.time.TimeProvider
import com.buildorbreak.core.domain.error.DomainError.DataError
import com.buildorbreak.core.domain.goal.ConsistencyScore
import com.buildorbreak.core.domain.goal.MilestoneEvaluator
import com.buildorbreak.core.domain.repository.DayCloseRepository
import com.buildorbreak.core.domain.repository.MilestoneRepository
import com.buildorbreak.core.model.enums.Milestone
import com.buildorbreak.core.model.goal.MilestoneAward
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Thirty days, matching `MilestoneEvaluator.consistency`. */
private const val CONSISTENCY_WINDOW_DAYS = 30L

/**
 * The one milestone that has been earned and not yet shown.
 *
 * One, never a queue. Milestones are already rationed hard in the domain, at
 * most one a day and never twice from the same category, and a screen that
 * stacked three of them would undo all of that in one morning.
 *
 * The award was written by the daily close, so this is only the reading half:
 * nothing here decides whether anything was earned, which is why a poor day
 * cannot be congratulated by a bug on a screen.
 */
class ObserveEarnedMilestoneUseCase @Inject constructor(
    private val milestones: MilestoneRepository,
    private val dispatchers: AppDispatchers,
) {

    operator fun invoke(): Flow<MilestoneAward?> = milestones.observeUnseen()
        .map { unseen -> unseen.minByOrNull { it.awardedOn } }
        .flowOn(dispatchers.io)
}

/** Closes a milestone for good. Seen once is seen; it never comes back. */
class MarkMilestoneSeenUseCase @Inject constructor(
    private val milestones: MilestoneRepository,
    private val dispatchers: AppDispatchers,
) {

    suspend operator fun invoke(milestone: Milestone): Outcome<Unit, DataError> =
        withContext(dispatchers.io) { milestones.markSeen(milestone) }
}

/**
 * How many of the last thirty days went well.
 *
 * The number this app shows instead of a consecutive day streak. A streak is a
 * reward that turns into a punishment the moment it breaks, and the person this
 * app is for is the person who already misses things. One bad day takes this
 * from twenty four to twenty three, and tomorrow it can go back up. Nothing is
 * ever wiped, and that is the point.
 */
class ObserveConsistencyUseCase @Inject constructor(
    private val closes: DayCloseRepository,
    private val evaluator: MilestoneEvaluator,
    private val time: TimeProvider,
    private val dispatchers: AppDispatchers,
) {

    operator fun invoke(today: LocalDate = time.today()): Flow<ConsistencyScore> =
        closes.observeRange(today.minusDays(CONSISTENCY_WINDOW_DAYS), today)
            .map { history -> evaluator.consistency(history, today) }
            .flowOn(dispatchers.default)
}
