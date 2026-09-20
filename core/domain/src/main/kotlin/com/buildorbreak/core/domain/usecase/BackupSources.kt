package com.buildorbreak.core.domain.usecase

import com.buildorbreak.core.domain.repository.DayCloseRepository
import com.buildorbreak.core.domain.repository.GoalRepository
import com.buildorbreak.core.domain.repository.ItemRepository
import com.buildorbreak.core.domain.repository.MeasurementRepository
import com.buildorbreak.core.domain.repository.MilestoneRepository
import com.buildorbreak.core.domain.repository.OccurrenceRepository
import com.buildorbreak.core.domain.repository.PlanRepository
import com.buildorbreak.core.domain.repository.TemplateRepository
import javax.inject.Inject

/**
 * Every table a backup touches, in one injectable bag.
 *
 * Export and restore are the same feature read in two directions, and they
 * need the same eight repositories. Listed one by one they gave both use
 * cases a constructor nobody could read, which is what `GoalSources` and
 * `CloseSources` exist to avoid next door.
 *
 * A bag, not a facade: it holds the repositories and answers no questions of
 * its own. Anything that decides something belongs in the use case.
 */
class BackupSources @Inject constructor(
    val plans: PlanRepository,
    val templates: TemplateRepository,
    val items: ItemRepository,
    val goals: GoalRepository,
    val occurrences: OccurrenceRepository,
    val measurements: MeasurementRepository,
    val milestones: MilestoneRepository,
    val closes: DayCloseRepository,
)
