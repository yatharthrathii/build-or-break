package com.buildorbreak.core.domain.usecase

import com.buildorbreak.core.common.coroutines.AppDispatchers
import com.buildorbreak.core.common.result.Outcome
import com.buildorbreak.core.common.time.TimeProvider
import com.buildorbreak.core.domain.error.DomainError.DataError
import com.buildorbreak.core.domain.repository.ItemRepository
import com.buildorbreak.core.domain.repository.MeasurementRepository
import com.buildorbreak.core.model.enums.ValueKind
import com.buildorbreak.core.model.execution.Measurement
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Writes one number against one step.
 *
 * Always optional. A step that asks for a weight or a rep count can still be
 * completed without one, and the day is settled either way: making the number
 * compulsory is how the numbers stop arriving at all, exactly as with skip
 * reasons.
 *
 * The occurrence is recorded alongside the date so an interval item logged
 * twice in a day keeps both readings distinct, and so a measurement can be
 * undone with the settle it belongs to.
 */
class LogMeasurementUseCase @Inject constructor(
    private val measurements: MeasurementRepository,
    private val items: ItemRepository,
    private val time: TimeProvider,
    private val dispatchers: AppDispatchers,
) {

    suspend operator fun invoke(
        itemId: Long,
        occurrenceId: Long?,
        value: Double,
        date: LocalDate = time.today(),
    ): Outcome<Unit, DataError> = withContext(dispatchers.io) {
        val item = items.byId(itemId) ?: return@withContext Outcome.Failure(DataError.NotFound)
        if (item.valueKind == ValueKind.NONE) return@withContext Outcome.Failure(DataError.ConstraintViolation)

        // A second number for the same settle replaces the first. There is no
        // screen for deleting a reading, so typing it again has to be the way
        // a slip of the thumb is corrected.
        val settle = occurrenceId?.takeIf { it > 0 }
        settle?.let { measurements.clearMeasurementFor(it) }

        measurements.upsert(
            Measurement(
                id = 0,
                itemId = itemId,
                occurrenceId = settle,
                date = date,
                value = value,
                kind = item.valueKind,
            ),
        )
    }
}

/** Everything logged against one step, oldest first, for the little chart on its editor. */
class ObserveReadingsUseCase @Inject constructor(
    private val measurements: MeasurementRepository,
    private val dispatchers: AppDispatchers,
) {

    operator fun invoke(itemId: Long): Flow<List<Measurement>> =
        measurements.observeForItem(itemId).flowOn(dispatchers.io)
}
