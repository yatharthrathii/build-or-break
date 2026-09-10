package com.buildorbreak.scheduler.di

import com.buildorbreak.core.domain.gateway.AlarmGateway
import com.buildorbreak.core.domain.gateway.NotificationGateway
import com.buildorbreak.scheduler.alarm.AlarmGatewayImpl
import com.buildorbreak.scheduler.alarm.AndroidDeliveryCapabilities
import com.buildorbreak.scheduler.alarm.DeliveryCapabilities
import com.buildorbreak.scheduler.notification.NotificationGatewayImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The platform, bound to the interfaces the domain owns.
 *
 * Every binding points from `:core:domain` into `:scheduler`, never the other
 * way. That direction is what lets the whole scheduling flow be tested without a
 * device: substitute a fake gateway and assert what it was asked to do.
 *
 * `DeliveryCapabilities` is bound here rather than constructed inside the tier
 * detector, which is the reason every combination of permissions can be covered
 * in a plain unit test.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SchedulerModule {

    @Binds
    @Singleton
    abstract fun bindCapabilities(impl: AndroidDeliveryCapabilities): DeliveryCapabilities

    @Binds
    @Singleton
    abstract fun bindAlarmGateway(impl: AlarmGatewayImpl): AlarmGateway

    @Binds
    @Singleton
    abstract fun bindNotificationGateway(impl: NotificationGatewayImpl): NotificationGateway
}
