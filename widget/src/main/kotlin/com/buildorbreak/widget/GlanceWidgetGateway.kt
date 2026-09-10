package com.buildorbreak.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.buildorbreak.core.common.coroutines.AppDispatchers
import com.buildorbreak.core.domain.gateway.WidgetGateway
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext

/**
 * Redraws every copy of the widget the user has placed.
 *
 * Called from the use cases rather than from the screens, so a step completed
 * from a notification at six in the morning updates the home screen without the
 * app ever being opened. That is the whole reason the gateway exists in the
 * domain at all.
 *
 * `updateAll` is a no op when no widget has been added, which is the common
 * case, so this costs nothing for the people who never place one.
 */
class GlanceWidgetGateway @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dispatchers: AppDispatchers,
) : WidgetGateway {

    override suspend fun refresh() = withContext(dispatchers.io) {
        @Suppress("SwallowedException", "TooGenericExceptionCaught")
        try {
            TodayWidget().updateAll(context)
        } catch (unavailable: Exception) {
            // A launcher that has gone away, or a widget host mid update. The
            // day is already written; a stale home screen is not worth taking
            // a completion down with it.
            Unit
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class WidgetModule {

    @Binds
    @Singleton
    abstract fun bindWidgetGateway(impl: GlanceWidgetGateway): WidgetGateway
}
