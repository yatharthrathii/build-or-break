package com.buildorbreak.core.domain.usecase

import com.buildorbreak.core.common.coroutines.AppDispatchers
import com.buildorbreak.core.common.time.TimeProvider
import com.buildorbreak.core.domain.goal.Points
import com.buildorbreak.core.domain.goal.PointsTally
import com.buildorbreak.core.domain.repository.DayCloseRepository
import com.buildorbreak.core.domain.repository.MilestoneRepository
import com.buildorbreak.core.model.enums.Milestone
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * The points in the bank, kept up to date as days close.
 *
 * Reads every close rather than a running total, because there is no
 * running total: the number is recomputed from the rows each time, which is
 * cheap at one row a day and means a corrected close corrects the score
 * with it. Only closed days count. Today is the screen's to add.
 */
class ObservePointsUseCase @Inject constructor(
    private val closes: DayCloseRepository,
    private val time: TimeProvider,
    private val dispatchers: AppDispatchers,
) {

    operator fun invoke(today: LocalDate = time.today()): Flow<PointsTally> {
        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        return closes.observeAll()
            .map { rows ->
                val settled = rows.filter { it.date < today }
                val perDay = settled.map(Points::forDay)

                PointsTally(
                    banked = perDay.sum(),
                    thisWeek = settled.filter { it.date >= weekStart }.sumOf(Points::forDay),
                    bestDay = perDay.maxOrNull(),
                )
            }
            .flowOn(dispatchers.default)
    }
}

/** One of the nine, and the day it was earned if it has been. */
data class Badge(val milestone: Milestone, val earnedOn: LocalDate?) {
    val isEarned: Boolean get() = earnedOn != null
}

/**
 * All nine milestones, earned or not, in the order they are declared.
 *
 * The full set rather than the earned ones, so the screen can show what is
 * still to come. A wall with three badges on it is a wall; a wall with three
 * lit and six dark is a reason to keep going.
 */
class ObserveBadgesUseCase @Inject constructor(
    private val milestones: MilestoneRepository,
    private val dispatchers: AppDispatchers,
) {

    operator fun invoke(): Flow<List<Badge>> = milestones.observeAwarded()
        .map { awards ->
            val earned = awards.associate { it.milestone to it.awardedOn }

            Milestone.entries.map { Badge(it, earned[it]) }
        }
        .flowOn(dispatchers.default)
}
