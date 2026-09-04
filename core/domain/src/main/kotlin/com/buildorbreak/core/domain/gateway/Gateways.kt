package com.buildorbreak.core.domain.gateway

import com.buildorbreak.core.common.result.Outcome
import com.buildorbreak.core.domain.error.DomainError.AlarmError
import com.buildorbreak.core.model.enums.DeliveryTier
import com.buildorbreak.core.model.enums.Milestone
import com.buildorbreak.core.model.execution.Occurrence
import com.buildorbreak.core.model.plan.Item
import com.buildorbreak.core.model.resolved.CascadePreview

/**
 * The platform, expressed as an interface the domain owns.
 *
 * architecture.md section 5.3. The direction matters more than the shape: the
 * domain declares what it needs and `:scheduler` satisfies it, never the other
 * way round. That is what lets the whole scheduling flow be tested without an
 * Android device, by substituting a fake and asserting what it was asked to do.
 */
interface AlarmGateway {
    /**
     * What the scheduler is allowed to do right now, detected rather than
     * assumed. Exact alarms are denied by default from Android 14, and an OEM
     * battery manager can take the rest away at any time.
     */
    fun currentTier(): DeliveryTier

    suspend fun schedule(occurrence: Occurrence, item: Item): Outcome<Unit, AlarmError>

    suspend fun cancel(occurrenceId: Long)

    suspend fun cancelAll()
}

interface NotificationGateway {
    /**
     * [preview] is the snooze consequence text. It is passed in rather than
     * computed here, because deciding what a snooze costs is domain work and
     * this interface only knows how to draw things.
     */
    suspend fun show(occurrence: Occurrence, item: Item, preview: CascadePreview?)

    suspend fun dismiss(occurrenceId: Long)

    suspend fun showMilestone(milestone: Milestone)

    fun canPostNotifications(): Boolean

    fun canUseFullScreenIntent(): Boolean
}

interface WidgetGateway {
    suspend fun refresh()
}
