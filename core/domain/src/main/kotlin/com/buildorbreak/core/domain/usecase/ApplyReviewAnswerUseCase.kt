package com.buildorbreak.core.domain.usecase

import com.buildorbreak.core.common.coroutines.AppDispatchers
import com.buildorbreak.core.common.result.Outcome
import com.buildorbreak.core.common.time.TimeProvider
import com.buildorbreak.core.domain.error.DomainError.DataError
import com.buildorbreak.core.domain.repository.ItemRepository
import com.buildorbreak.core.domain.repository.OccurrenceRepository
import com.buildorbreak.core.domain.repository.SettingsRepository
import com.buildorbreak.core.domain.review.TimeShiftDetector
import com.buildorbreak.core.model.enums.Salience
import com.buildorbreak.core.model.plan.Anchor
import com.buildorbreak.core.model.plan.Item
import com.buildorbreak.core.model.review.ReviewAnswer
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration
import kotlinx.coroutines.withContext

/** How far back the move looks for the real time. Four weeks, like the review. */
private const val LOOKBACK_DAYS = 28L

/** A widened window opens this much before the old time and closes this much after. */
private val WIDEN_BEFORE: Duration = 30.minutes
private val WIDEN_AFTER: Duration = 60.minutes

/**
 * Turns the weekly review's answer into an edit, in one tap.
 *
 * Each answer is a specific change to one item, and the change is the one the
 * question implied. Moving means moving to when it actually happens, which the
 * time shift detector already knows. Widening means a window around the old
 * minute, so an ordinary day can no longer break it. Nothing here guesses:
 * an answer that has no sensible edit for the item's shape leaves the item
 * alone and only closes the question for the week.
 */
class ApplyReviewAnswerUseCase @Inject constructor(
    private val items: ItemRepository,
    private val occurrences: OccurrenceRepository,
    private val settings: SettingsRepository,
    private val saveItem: SaveItemUseCase,
    private val archiveItem: ArchiveItemUseCase,
    private val time: TimeProvider,
    private val dispatchers: AppDispatchers,
) {

    /** Two samples and no threshold: the review already decided a move is warranted. */
    private val slips = TimeShiftDetector(minimumSamples = 2, threshold = Duration.ZERO)

    suspend operator fun invoke(itemId: Long, answer: ReviewAnswer, weekStart: LocalDate): Outcome<Unit, DataError> =
        withContext(dispatchers.io) {
            val item = items.byId(itemId) ?: return@withContext Outcome.Failure(DataError.NotFound)

            val outcome: Outcome<Unit, DataError> = when (answer) {
                ReviewAnswer.MOVE_TIME -> save(moved(item))
                ReviewAnswer.WIDEN_WINDOW -> save(widened(item))
                ReviewAnswer.RAISE_SALIENCE -> save(item.copy(salience = louder(item.salience)))
                ReviewAnswer.USE_MINIMUM -> save(asMinimum(item))
                ReviewAnswer.REMOVE_ITEM -> archiveItem(itemId)
                ReviewAnswer.KEEP_AND_FOCUS, ReviewAnswer.LEAVE_IT -> Outcome.Success(Unit)
            }

            // Whatever happened, the question is answered for this week.
            settings.setDismissedReviewWeek(weekStart)

            outcome
        }

    private suspend fun save(item: Item?): Outcome<Unit, DataError> = when {
        item == null -> Outcome.Success(Unit)
        else -> when (val written = saveItem(item)) {
            is Outcome.Success -> Outcome.Success(Unit)
            is Outcome.Failure -> written
        }
    }

    /** To when it actually happens: the median of the last four weeks. */
    private suspend fun moved(item: Item): Item? {
        val today = time.today()
        val recent = occurrences.between(today.minusDays(LOOKBACK_DAYS), today)
        val shift = slips.detect(item.id, recent, time.zone())?.median ?: return null
        val by = shift.toJavaDuration()

        val anchor = when (val anchor = item.anchor) {
            is Anchor.Fixed -> Anchor.Fixed(anchor.at.plus(by))
            is Anchor.Relative -> anchor.copy(offset = anchor.offset + shift)
            is Anchor.Window -> anchor.copy(from = anchor.from.plus(by), to = anchor.to.plus(by))
            is Anchor.Interval -> anchor.copy(from = anchor.from.plus(by), to = anchor.to.plus(by))
        }

        return item.copy(anchor = anchor)
    }

    /** A range instead of a minute. A relative or repeating step has no minute to widen. */
    private fun widened(item: Item): Item? = when (val anchor = item.anchor) {
        is Anchor.Fixed -> item.copy(anchor = window(anchor.at, anchor.at))
        is Anchor.Window -> item.copy(anchor = window(anchor.from, anchor.to))
        is Anchor.Relative, is Anchor.Interval -> null
    }

    private fun window(from: LocalTime, to: LocalTime): Anchor.Window = Anchor.Window(
        from = from.minus(WIDEN_BEFORE.toJavaDuration()),
        to = to.plus(WIDEN_AFTER.toJavaDuration()),
    )

    private fun louder(salience: Salience): Salience = when (salience) {
        Salience.TIMELINE -> Salience.SILENT
        Salience.SILENT -> Salience.NOTIFY
        Salience.NOTIFY, Salience.ALARM -> Salience.ALARM
    }

    /** The smaller version becomes the step. Nothing left to scale down to. */
    private fun asMinimum(item: Item): Item? {
        val minimum = item.minimum ?: return null

        return item.copy(title = minimum.title, duration = minimum.duration ?: item.duration, minimum = null)
    }
}
