package com.buildorbreak.core.domain.usecase

import com.buildorbreak.core.domain.gateway.AlarmGateway
import com.buildorbreak.core.domain.gateway.WidgetGateway
import javax.inject.Inject

/**
 * Everything that has to be put right once the rows are back.
 *
 * A restore writes tables. These are the four things outside the tables
 * that then disagree with them: the goals' worked out history, the alarms
 * already set for a plan that no longer exists, the alarms the new plan
 * needs, and the widget still showing the old day.
 *
 * A bag, like `BackupSources` beside it: it holds them and decides nothing.
 */
class RestoreAftermath @Inject constructor(
    val recompute: RecomputeGoalHistoryUseCase,
    val reschedule: RescheduleAllUseCase,
    val alarms: AlarmGateway,
    val widget: WidgetGateway,
)
