package com.buildorbreak.core.domain.usecase

import com.buildorbreak.core.common.coroutines.AppDispatchers
import com.buildorbreak.core.common.time.TimeProvider
import com.buildorbreak.core.domain.repository.DeliveryAuditRepository
import com.buildorbreak.core.domain.review.DeliveryStats
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Two weeks. Long enough to contain a weekend and a working week, short enough
 * that a phone which has since been fixed is not judged on how it used to be.
 */
private const val WINDOW_DAYS = 14L

/**
 * What the alarms actually did on this phone.
 *
 * The Reliability screen has always been able to say what the app is *allowed*
 * to do. This is what it *did*, which is the only claim worth making: a phone
 * can report every permission granted and still hold alarms for twenty minutes
 * behind a battery manager, and the tier alone would show a confident green
 * while the user was being woken up late every day.
 *
 * Empty until the first alarm has been scheduled and its moment has passed,
 * which the screen has to say rather than draw as a hundred percent of nothing.
 */
class ObserveDeliveryStatsUseCase @Inject constructor(
    private val audits: DeliveryAuditRepository,
    private val time: TimeProvider,
    private val dispatchers: AppDispatchers,
) {

    operator fun invoke(): Flow<DeliveryStats> {
        val since = time.now().minus(Duration.ofDays(WINDOW_DAYS))

        return audits.observeSince(since)
            .map { rows -> DeliveryStats.of(rows, WINDOW_DAYS.toInt(), time.now()) }
            .flowOn(dispatchers.default)
    }
}
