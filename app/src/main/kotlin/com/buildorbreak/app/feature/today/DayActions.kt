package com.buildorbreak.app.feature.today

import com.buildorbreak.core.domain.usecase.CompleteItemUseCase
import com.buildorbreak.core.domain.usecase.ExplainSkipUseCase
import com.buildorbreak.core.domain.usecase.RescheduleAllUseCase
import com.buildorbreak.core.domain.usecase.ShiftDayUseCase
import com.buildorbreak.core.domain.usecase.SkipItemUseCase
import com.buildorbreak.core.domain.usecase.SnoozeItemUseCase
import com.buildorbreak.core.domain.usecase.SwitchDayTemplateUseCase
import com.buildorbreak.core.domain.usecase.UndoSettleUseCase
import com.buildorbreak.core.domain.usecase.UndoStepForPointsUseCase
import javax.inject.Inject

/**
 * Everything Today can do to the day, in one injectable bag.
 *
 * Use cases that always travel together. Injecting them one by one gave the
 * ViewModel a constructor nobody could read; this keeps each use case where it
 * was and hands the ViewModel a single handle.
 */
class DayActions @Inject constructor(
    val complete: CompleteItemUseCase,
    val snooze: SnoozeItemUseCase,
    val skip: SkipItemUseCase,
    val undo: UndoSettleUseCase,
    /** The other undo: long after the bar has gone, and it costs points. */
    val undoForPoints: UndoStepForPointsUseCase,
    val explainSkip: ExplainSkipUseCase,
    /** Writes the rows a new day needs. Called when the date turns over under an open app. */
    val rollOver: RescheduleAllUseCase,
    val shiftDay: ShiftDayUseCase,
    val switchTemplate: SwitchDayTemplateUseCase,
)
