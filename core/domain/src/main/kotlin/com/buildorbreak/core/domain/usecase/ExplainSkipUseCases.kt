package com.buildorbreak.core.domain.usecase

import com.buildorbreak.core.common.coroutines.AppDispatchers
import com.buildorbreak.core.common.result.Outcome
import com.buildorbreak.core.common.time.TimeProvider
import com.buildorbreak.core.domain.error.DomainError.DataError
import com.buildorbreak.core.domain.repository.ItemRepository
import com.buildorbreak.core.domain.repository.MeasurementRepository
import com.buildorbreak.core.domain.repository.OccurrenceRepository
import com.buildorbreak.core.model.enums.OccurrenceState
import com.buildorbreak.core.model.enums.SkipChip
import com.buildorbreak.core.model.execution.Occurrence
import com.buildorbreak.core.model.execution.SkipReason
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** One skip nobody has been asked about yet. */
data class UnexplainedSkip(val occurrenceId: Long, val itemId: Long, val title: String)

/**
 * Today's skips that were never asked about.
 *
 * A skip from inside the app is asked at the moment it happens, and even
 * "skip without a reason" is recorded, as an empty row, so the question is
 * never put twice. A skip from a notification or the widget has no such
 * moment: the shade has room for four buttons and none of them can be a
 * bottom sheet, so the step is settled and nothing is ever learned about it.
 *
 * That gap is not small. Most of the point of this app is that the routine can
 * be run without opening it, which means most skips arrive by the one route
 * that cannot ask. Left alone, the weekly review would be built out of the
 * minority of skips made by somebody who happened to have the app open, which
 * is a biased sample dressed up as a finding.
 *
 * So the question is deferred rather than dropped, and asked once, quietly, the
 * next time the app is open.
 */
class ObserveUnexplainedSkipsUseCase @Inject constructor(
    private val occurrences: OccurrenceRepository,
    private val measurements: MeasurementRepository,
    private val items: ItemRepository,
    private val time: TimeProvider,
    private val dispatchers: AppDispatchers,
) {

    operator fun invoke(date: LocalDate = time.today()): Flow<List<UnexplainedSkip>> =
        occurrences.observeForDate(date).map(::unexplained).flowOn(dispatchers.io)

    private suspend fun unexplained(rows: List<Occurrence>): List<UnexplainedSkip> {
        val skipped = rows.filter { it.state == OccurrenceState.SKIPPED }
        if (skipped.isEmpty()) return emptyList()

        // Any row at all counts as asked, including an empty one. Somebody who
        // chose "no reason" has answered, and asking again would be nagging.
        val asked = measurements.skipReasonsFor(skipped.map { it.id }).map { it.occurrenceId }.toSet()

        return skipped.filterNot { it.id in asked }
            .mapNotNull { row -> items.byId(row.itemId)?.let { UnexplainedSkip(row.id, row.itemId, it.title) } }
    }
}

/**
 * Writes down why a step did not happen, after the fact.
 *
 * Separate from [SkipItemUseCase] because the step is already settled by the
 * time this runs. Nothing is rescheduled and no notification moves: the only
 * thing that changes is what next Sunday's report can say.
 *
 * [chip] is null when the answer was "no reason". The row is written anyway, and
 * `SkipReason.isEmpty` is what tells the two apart. Recording the refusal is
 * what stops the app asking a second time, and an app that asks twice about the
 * same bad morning is one people stop answering.
 */
class ExplainSkipUseCase @Inject constructor(
    private val measurements: MeasurementRepository,
    private val time: TimeProvider,
) {

    suspend operator fun invoke(occurrenceId: Long, chip: SkipChip?): Outcome<Unit, DataError> =
        measurements.recordSkipReason(
            SkipReason(id = 0, occurrenceId = occurrenceId, chip = chip, text = null, createdAt = time.now()),
        )
}
