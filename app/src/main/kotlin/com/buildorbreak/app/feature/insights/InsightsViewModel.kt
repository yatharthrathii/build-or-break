package com.buildorbreak.app.feature.insights

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buildorbreak.core.domain.repository.SettingsRepository
import com.buildorbreak.core.domain.review.InsightBar
import com.buildorbreak.core.domain.review.Insights
import com.buildorbreak.core.domain.review.InsightsPeriod
import com.buildorbreak.core.domain.review.SkipCount
import com.buildorbreak.core.domain.review.StepStat
import com.buildorbreak.core.domain.review.Suggestion
import com.buildorbreak.core.domain.usecase.ApplyReviewAnswerUseCase
import com.buildorbreak.core.domain.usecase.ObserveInsightsUseCase
import com.buildorbreak.core.model.enums.ReviewStory
import com.buildorbreak.core.model.enums.SkipChip
import com.buildorbreak.core.model.review.ReviewAnswer
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val PERCENT = 100

private val DAY: DateTimeFormatter
    get() = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

@Immutable
data class BarUi(val label: String, val fraction: Float?, val isWeekend: Boolean, val isBest: Boolean)

@Immutable
data class StepRowUi(
    val itemId: Long,
    val title: String,
    val kept: Int,
    val outOf: Int,
    val slipMinutes: Int?,
    val isProblem: Boolean,
)

@Immutable
data class SuggestionUi(
    val itemId: Long,
    val title: String,
    val answer: ReviewAnswer,
    val misses: Int,
    val outOf: Int,
    val slipMinutes: Int?,
    val weekStart: LocalDate,
)

/** One reason and how often it was given, with the bar already worked out. */
@Immutable
data class SkipRowUi(val chip: SkipChip, val count: Int, val fraction: Float)

@Immutable
data class InsightsUiState(
    val period: InsightsPeriod,
    val hasPlan: Boolean,
    /** "1 – 7 Sep", already formatted. */
    val range: String,
    val weekNumber: Int,
    val percent: Int,
    val kept: Int,
    val total: Int,
    val changePoints: Int?,
    val averageSlipMinutes: Int?,
    val bars: ImmutableList<BarUi>,
    val steps: ImmutableList<StepRowUi>,
    /** Why steps were skipped, commonest first. Empty when nobody said. */
    val skipReasons: ImmutableList<SkipRowUi>,
    val suggestion: SuggestionUi?,
    val story: ReviewStory,
) {
    val isEmpty: Boolean get() = hasPlan && total == 0

    companion object {
        val Empty = InsightsUiState(
            period = InsightsPeriod.WEEK,
            hasPlan = false,
            range = "",
            weekNumber = 0,
            percent = 0,
            kept = 0,
            total = 0,
            changePoints = null,
            averageSlipMinutes = null,
            bars = persistentListOf(),
            steps = persistentListOf(),
            skipReasons = persistentListOf(),
            suggestion = null,
            story = ReviewStory.SETTLING_IN,
        )
    }
}

/**
 * One period, formatted.
 *
 * Every number here is read off `Insights`; nothing is worked out. The
 * ViewModel's whole job is to turn dates into labels and durations into
 * minutes, so the screen never needs a locale or a zone.
 */
@HiltViewModel
class InsightsViewModel @Inject constructor(
    observeInsights: ObserveInsightsUseCase,
    private val settings: SettingsRepository,
    private val applyAnswer: ApplyReviewAnswerUseCase,
) : ViewModel() {

    private val period = MutableStateFlow(InsightsPeriod.WEEK)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<InsightsUiState> = period
        .flatMapLatest { chosen ->
            observeInsights(chosen).map {
                it?.let(::toUiState)
                    ?: InsightsUiState.Empty.copy(period = chosen)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = InsightsUiState.Empty,
        )

    fun onPeriod(chosen: InsightsPeriod) {
        period.value = chosen
    }

    /** Not this week. The same suggestion will not be offered again until next Monday. */
    fun onDismissSuggestion(weekStart: LocalDate) = viewModelScope.launch {
        settings.setDismissedReviewWeek(weekStart)
    }

    /** One tap. The use case makes the edit the answer implies and closes the question. */
    fun onApply(suggestion: SuggestionUi) = viewModelScope.launch {
        applyAnswer(suggestion.itemId, suggestion.answer, suggestion.weekStart)
    }

    private fun toUiState(insights: Insights): InsightsUiState {
        val best = insights.bars.mapNotNull { it.fraction }.maxOrNull()
        val problemId = insights.suggestion?.itemId

        return InsightsUiState(
            period = insights.period,
            hasPlan = true,
            range = "${insights.from.format(DAY)} – ${insights.to.format(DAY)}",
            weekNumber = insights.from.get(WeekFields.ISO.weekOfWeekBasedYear()),
            percent = (insights.adherence * PERCENT).toInt(),
            kept = insights.kept,
            total = insights.total,
            changePoints = insights.changeInPoints,
            averageSlipMinutes = insights.averageSlip?.inWholeMinutes?.toInt(),
            bars = insights.bars.map { toBar(it, insights.period, best) }.toImmutableList(),
            steps = insights.steps.map { toRow(it, problemId) }.toImmutableList(),
            skipReasons = toSkipRows(insights.skipReasons),
            suggestion = insights.suggestion?.let(::toSuggestion),
            story = insights.story,
        )
    }

    private fun toBar(bar: InsightBar, period: InsightsPeriod, best: Float?) = BarUi(
        label = if (period == InsightsPeriod.WEEK) {
            bar.start.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault())
        } else {
            bar.start.format(DAY)
        },
        fraction = bar.fraction,
        isWeekend = bar.isWeekend,
        isBest = bar.fraction != null && bar.fraction == best,
    )

    /** Each bar is drawn against the commonest reason, so the top one always fills the row. */
    private fun toSkipRows(reasons: List<SkipCount>): ImmutableList<SkipRowUi> {
        val most = reasons.maxOfOrNull { it.count } ?: return persistentListOf()

        return reasons.map { SkipRowUi(it.chip, it.count, it.count.toFloat() / most) }.toImmutableList()
    }

    private fun toRow(stat: StepStat, problemId: Long?) = StepRowUi(
        itemId = stat.itemId,
        title = stat.title,
        kept = stat.kept,
        outOf = stat.outOf,
        slipMinutes = stat.slip?.inWholeMinutes?.toInt(),
        isProblem = stat.itemId == problemId,
    )

    private fun toSuggestion(suggestion: Suggestion) = SuggestionUi(
        itemId = suggestion.itemId,
        title = suggestion.title,
        answer = suggestion.answer,
        misses = suggestion.misses,
        outOf = suggestion.outOf,
        slipMinutes = suggestion.slip?.inWholeMinutes?.toInt(),
        weekStart = suggestion.weekStart,
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
