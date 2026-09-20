package com.buildorbreak.core.domain.usecase

import com.buildorbreak.core.common.time.TimeProvider
import com.buildorbreak.core.domain.goal.Streaks
import com.buildorbreak.core.domain.repository.DayCloseRepository
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** How far back a run is worth counting. Beyond this the number is the point. */
private const val RUN_WINDOW_DAYS = 90L

/**
 * The current run of kept days, for the line under the ring on Today.
 *
 * A use case rather than a repository call because there is a rule in it:
 * `Streaks` decides what counts, and the ViewModel that shows the number must
 * not. Emits again whenever a day closes, which is once a night.
 */
class ObserveRunUseCase @Inject constructor(
    private val closes: DayCloseRepository,
    private val frozenDays: ObserveFrozenDaysUseCase,
    private val time: TimeProvider,
) {

    operator fun invoke(today: LocalDate = time.today()): Flow<Int> = combine(
        closes.observeRange(today.minusDays(RUN_WINDOW_DAYS), today.minusDays(1)),
        frozenDays(),
    ) { history, frozen -> Streaks.currentRun(history, today, frozen) }
}
