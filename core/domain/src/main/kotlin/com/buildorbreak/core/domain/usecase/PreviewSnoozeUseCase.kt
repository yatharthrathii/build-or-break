package com.buildorbreak.core.domain.usecase

import com.buildorbreak.core.common.coroutines.AppDispatchers
import com.buildorbreak.core.common.time.TimeProvider
import com.buildorbreak.core.domain.repository.OccurrenceRepository
import com.buildorbreak.core.domain.resolver.CascadeCalculator
import com.buildorbreak.core.model.resolved.CascadePreview
import javax.inject.Inject
import kotlin.time.Duration
import kotlinx.coroutines.withContext

/**
 * What a snooze would do to the rest of the day, before it is done.
 *
 * Every competing app offers a snooze and none of them say what it costs.
 * This resolves the day twice, once as it is and once with the step moved,
 * and hands back the difference. It runs when the alarm screen opens, not in
 * the broadcast receiver, because resolving a day twice is not ten second
 * work on a cold process.
 */
class PreviewSnoozeUseCase @Inject constructor(
    private val observeToday: ObserveTodayUseCase,
    private val occurrences: OccurrenceRepository,
    private val cascade: CascadeCalculator,
    private val time: TimeProvider,
    private val dispatchers: AppDispatchers,
) {

    /** Null when there is nothing to preview against, such as a day with no plan. */
    suspend operator fun invoke(occurrenceId: Long, by: Duration): CascadePreview? = withContext(dispatchers.default) {
        val input = observeToday.inputFor(time.today()) ?: return@withContext null
        val row = occurrences.byId(occurrenceId) ?: return@withContext null

        // The same arithmetic the snooze itself uses, so the consequence shown
        // is the consequence of what will actually happen.
        cascade.preview(input, row.itemId, snoozeShift(row, by, time.localNow()))
    }
}
