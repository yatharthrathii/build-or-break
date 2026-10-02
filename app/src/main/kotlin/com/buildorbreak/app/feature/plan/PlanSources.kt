package com.buildorbreak.app.feature.plan

import com.buildorbreak.core.domain.usecase.ObservePlanUseCase
import com.buildorbreak.core.domain.usecase.ObservePlansUseCase
import com.buildorbreak.core.domain.usecase.ObserveTracksUseCase
import javax.inject.Inject

/**
 * The three things the plan screen reads, in one injectable bag.
 *
 * The plan being edited, every plan there is, and the syllabuses the steps
 * follow. Injecting them one by one pushed the ViewModel's constructor past
 * the point anyone could read, which is what `RoutinePrice` exists to avoid
 * next door.
 */
class PlanSources @Inject constructor(
    val plan: ObservePlanUseCase,
    val plans: ObservePlansUseCase,
    val tracks: ObserveTracksUseCase,
)
