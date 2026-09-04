package com.buildorbreak.scheduler.widget

import com.buildorbreak.core.domain.gateway.WidgetGateway
import javax.inject.Inject

/**
 * The widget is not built yet, and this says so honestly.
 *
 * A real implementation of doing nothing rather than a missing binding. Without
 * it every use case that refreshes the widget would fail to construct, which
 * would hold the whole scheduler behind a module that is not on the critical
 * path.
 *
 * It is deliberately not silent about being temporary: when `:widget` lands, the
 * binding in `SchedulerModule` changes to point at the real one and nothing else
 * in the app has to know.
 */
class NoOpWidgetGateway @Inject constructor() : WidgetGateway {
    override suspend fun refresh() = Unit
}
