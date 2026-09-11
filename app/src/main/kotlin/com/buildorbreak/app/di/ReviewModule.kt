package com.buildorbreak.app.di

import com.buildorbreak.core.domain.goal.DayQualityClassifier
import com.buildorbreak.core.domain.goal.DefaultDayQualityClassifier
import com.buildorbreak.core.domain.goal.DefaultGoalCalculator
import com.buildorbreak.core.domain.goal.DefaultMilestoneEvaluator
import com.buildorbreak.core.domain.goal.GoalCalculator
import com.buildorbreak.core.domain.goal.GoalProgressWriter
import com.buildorbreak.core.domain.goal.MilestoneEvaluator
import com.buildorbreak.core.domain.review.CatchUpPlanner
import com.buildorbreak.core.domain.review.CatchUpViewBuilder
import com.buildorbreak.core.domain.review.DefaultWeeklyReviewBuilder
import com.buildorbreak.core.domain.review.WeeklyReviewBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The services that judge a day rather than lay one out.
 *
 * Split from `DomainModule` because they are a different job and because one
 * module holding every provider becomes a list nobody reads. The timeline
 * engine decides what happens; these decide what it meant.
 *
 * Every one is a singleton because every one is stateless, and each is
 * provided here rather than annotated in `:core:domain` for the reason that
 * module gives: the domain stays plain Kotlin that a test can instantiate
 * with a bare constructor call.
 */
@Module
@InstallIn(SingletonComponent::class)
object ReviewModule {

    @Provides
    @Singleton
    fun provideDayQualityClassifier(): DayQualityClassifier = DefaultDayQualityClassifier()

    @Provides
    @Singleton
    fun provideMilestoneEvaluator(): MilestoneEvaluator = DefaultMilestoneEvaluator()

    @Provides
    @Singleton
    fun provideGoalCalculator(): GoalCalculator = DefaultGoalCalculator()

    /**
     * Given the calculator rather than building its own, so a row written at
     * the daily close and the projection drawn from it on the goal screen can
     * never come from two differently configured copies of the same maths.
     */
    @Provides
    @Singleton
    fun provideGoalProgressWriter(calculator: GoalCalculator): GoalProgressWriter = GoalProgressWriter(calculator)

    @Provides
    @Singleton
    fun provideWeeklyReviewBuilder(): WeeklyReviewBuilder = DefaultWeeklyReviewBuilder()

    @Provides
    @Singleton
    fun provideCatchUpPlanner(): CatchUpPlanner = CatchUpPlanner()

    /**
     * Given the planner for the same reason: what Today offers and what the
     * day close settles to MISSED have to be the same fitter.
     */
    @Provides
    @Singleton
    fun provideCatchUpViewBuilder(planner: CatchUpPlanner): CatchUpViewBuilder = CatchUpViewBuilder(planner)
}
