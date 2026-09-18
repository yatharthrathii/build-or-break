package com.buildorbreak.core.domain.usecase

import com.buildorbreak.core.common.coroutines.AppDispatchers
import com.buildorbreak.core.common.result.Outcome
import com.buildorbreak.core.common.result.getOrNull
import com.buildorbreak.core.common.time.TimeProvider
import com.buildorbreak.core.domain.export.BackupProblem
import com.buildorbreak.core.domain.export.BackupUnreadable
import com.buildorbreak.core.domain.export.ExportAnchor
import com.buildorbreak.core.domain.export.ExportDocument
import com.buildorbreak.core.domain.export.ExportGoal
import com.buildorbreak.core.domain.export.ExportItem
import com.buildorbreak.core.domain.export.ExportReader
import com.buildorbreak.core.domain.export.ExportTemplate
import com.buildorbreak.core.domain.gateway.AlarmGateway
import com.buildorbreak.core.domain.gateway.WidgetGateway
import com.buildorbreak.core.domain.repository.DayCloseRepository
import com.buildorbreak.core.domain.repository.GoalRepository
import com.buildorbreak.core.domain.repository.ItemRepository
import com.buildorbreak.core.domain.repository.MeasurementRepository
import com.buildorbreak.core.domain.repository.MilestoneRepository
import com.buildorbreak.core.domain.repository.OccurrenceRepository
import com.buildorbreak.core.domain.repository.PlanRepository
import com.buildorbreak.core.domain.repository.ResetRepository
import com.buildorbreak.core.domain.repository.SettingsRepository
import com.buildorbreak.core.domain.repository.TemplateRepository
import com.buildorbreak.core.model.enums.AnchorType
import com.buildorbreak.core.model.enums.DayMode
import com.buildorbreak.core.model.enums.DayQuality
import com.buildorbreak.core.model.enums.GoalKind
import com.buildorbreak.core.model.enums.ItemKind
import com.buildorbreak.core.model.enums.Milestone
import com.buildorbreak.core.model.enums.OccurrenceState
import com.buildorbreak.core.model.enums.Salience
import com.buildorbreak.core.model.enums.ValueKind
import com.buildorbreak.core.model.execution.Measurement
import com.buildorbreak.core.model.execution.Occurrence
import com.buildorbreak.core.model.goal.DayClose
import com.buildorbreak.core.model.goal.Goal
import com.buildorbreak.core.model.goal.MilestoneAward
import com.buildorbreak.core.model.plan.Anchor
import com.buildorbreak.core.model.plan.Block
import com.buildorbreak.core.model.plan.DayTemplate
import com.buildorbreak.core.model.plan.Item
import com.buildorbreak.core.model.plan.MinimumVersion
import com.buildorbreak.core.model.plan.Plan
import com.buildorbreak.core.model.plan.Weekdays
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** What came back, so the screen can say it in the user's own numbers. */
data class RestoreSummary(
    val steps: Int,
    val days: Int,
    val hasGoal: Boolean,
    val readings: Int,
    val badges: Int = 0,
)

/**
 * Puts a backup file back.
 *
 * The export has always promised that it round trips, and until now nothing
 * completed the circle: the file could be written and shared and never read.
 * That makes the promise "your data is yours" a promise about a document
 * rather than about a routine, which is not what somebody changing phone, or
 * reinstalling after a wipe, is asking for.
 *
 * **It replaces rather than merges.** Everything is deleted first and written
 * again from the file. Merging would have to answer questions the file cannot:
 * whether a step in both is the same step, which of two readings for a Tuesday
 * is right, whether a day closed twice. A restore that quietly guessed would
 * produce a routine the user never had and could not undo. The screen says so
 * before this is called, and the wipe is the same one the settings screen
 * already offers.
 *
 * **Ids are rebuilt, never reused.** The numbers in the file belonged to a
 * database that no longer exists. Everything that pointed at one is repointed
 * through a map built as the rows are written, which is why the writes are one
 * at a time and in this order: template, block, item, and only then the things
 * that refer to an item.
 */
class RestoreBackupUseCase @Inject constructor(
    private val reader: ExportReader,
    private val reset: ResetRepository,
    private val plans: PlanRepository,
    private val templates: TemplateRepository,
    private val items: ItemRepository,
    private val goals: GoalRepository,
    private val occurrences: OccurrenceRepository,
    private val measurements: MeasurementRepository,
    private val milestones: MilestoneRepository,
    private val closes: DayCloseRepository,
    private val settings: SettingsRepository,
    private val reschedule: RescheduleAllUseCase,
    private val alarms: AlarmGateway,
    private val widget: WidgetGateway,
    private val time: TimeProvider,
    private val dispatchers: AppDispatchers,
) {

    suspend operator fun invoke(text: String): Outcome<RestoreSummary, BackupProblem> =
        withContext(dispatchers.io) {
            val document = reader.read(text).getOrElse { failure ->
                return@withContext Outcome.Failure(
                    (failure as? BackupUnreadable)?.problem ?: BackupProblem.NOT_READABLE,
                )
            }

            clearEverything()

            val planId = writePlan(document) ?: return@withContext Outcome.Failure(BackupProblem.NOT_READABLE)
            val itemIds = writeTemplates(document.templates, planId)

            pointRelativeAnchorsAtTheirParents(document.templates, itemIds)
            document.goals.forEach { writeGoal(it, planId, itemIds) }
            writeHistory(document, planId, itemIds)

            reschedule()
            widget.refresh()

            Outcome.Success(
                RestoreSummary(
                    steps = itemIds.size,
                    days = document.history.dayCloses.size,
                    hasGoal = document.goals.isNotEmpty(),
                    readings = document.history.measurements.size,
                    badges = document.history.milestones.size,
                ),
            )
        }

    /**
     * The wipe the settings screen offers, alarms first, preferences kept.
     *
     * Same reasoning as `WipeDataUseCase` for the alarms: one lives in
     * `AlarmManager` rather than in the database, and one left behind would
     * ring for a row that no longer exists.
     *
     * The preferences are read back afterwards because the wipe clears them
     * too, and they are not in the file. Losing the theme on a restore would
     * be a puzzle; losing the first-run flag is worse than a puzzle, because
     * it drops somebody who has just restored nine steps back onto "what are
     * you trying to build". The first run is over by definition: there is a
     * plan, and they put it there.
     */
    private suspend fun clearEverything() {
        val today = time.today()
        occurrences.between(today.minusDays(1), today.plusDays(1)).forEach { alarms.cancel(it.id) }

        val theme = settings.themeMode.first()
        val tolerance = settings.lateTolerance.first()

        reset.wipeEverything()

        settings.setOnboardingComplete(true)
        settings.setThemeMode(theme)
        settings.setLateTolerance(tolerance)
    }

    private suspend fun writePlan(document: ExportDocument): Long? {
        val zone = runCatching { ZoneId.of(document.plan.zone) }.getOrNull() ?: time.zone()

        val planId = plans.upsert(
            Plan(
                id = 0,
                name = document.plan.name,
                isActive = true,
                zone = zone,
                // The file's own date. A restored plan that claimed to be
                // created today would make its first week look like week one.
                createdAt = document.plan.createdAt.toInstantOrNull() ?: time.now(),
            ),
        ).getOrNull() ?: return null

        plans.setActive(planId)

        return planId
    }

    /** Returns old item id to new item id, for everything that points at a step. */
    private suspend fun writeTemplates(sources: List<ExportTemplate>, planId: Long): Map<Long, Long> {
        val itemIds = mutableMapOf<Long, Long>()

        sources.forEach { source ->
            val templateId = templates.upsert(
                DayTemplate(
                    id = 0,
                    planId = planId,
                    name = source.name,
                    weekdays = Weekdays(source.weekdays),
                    isDefault = source.isDefault,
                    mode = enumOrNull<DayMode>(source.mode) ?: DayMode.NORMAL,
                    sortOrder = source.sortOrder,
                ),
            ).getOrNull() ?: return@forEach

            val blockIds = source.blocks.associate { block ->
                block.id to items.upsertBlock(
                    Block(
                        id = 0,
                        templateId = templateId,
                        title = block.title,
                        anchor = block.anchor.toAnchor(),
                        salience = enumOrNull<Salience>(block.salience) ?: Salience.NOTIFY,
                        sortOrder = block.sortOrder,
                    ),
                ).getOrNull()
            }

            source.items.forEach { item ->
                // Written with the anchor as the file has it. A RELATIVE one
                // still points at an old id here; the second pass repoints it
                // once every step in the file has a new id to point at.
                val written = items.upsert(item.toItem(templateId, blockIds[item.blockId])).getOrNull()

                if (written != null) itemIds[item.id] = written
            }
        }

        return itemIds
    }

    /**
     * The second pass over relative steps.
     *
     * "Fifteen minutes after breakfast" is stored as the parent's id, and a
     * parent written after its child has no id yet at the moment the child is
     * written. One pass could only work if the file were always in dependency
     * order, which is a property of the data rather than of the format, and a
     * chain that came back pointing at a stranger's row would be a plan that
     * is subtly not the one that was exported.
     */
    private suspend fun pointRelativeAnchorsAtTheirParents(
        sources: List<ExportTemplate>,
        itemIds: Map<Long, Long>,
    ) {
        sources.flatMap { it.items }
            .filter { it.anchor.type == AnchorType.RELATIVE.name }
            .forEach { source ->
                val id = itemIds[source.id] ?: return@forEach
                val parent = itemIds[source.anchor.parentItemId] ?: return@forEach
                val current = items.byId(id) ?: return@forEach
                val anchor = current.anchor as? Anchor.Relative ?: return@forEach

                items.upsert(current.copy(anchor = anchor.copy(parentItemId = parent)))
            }
    }

    private suspend fun writeGoal(source: ExportGoal, planId: Long, itemIds: Map<Long, Long>) {
        goals.upsert(
            Goal(
                id = 0,
                planId = planId,
                kind = enumOrNull<GoalKind>(source.kind) ?: return,
                title = source.title,
                itemId = source.itemId?.let { itemIds[it] },
                valueKind = enumOrNull<ValueKind>(source.valueKind) ?: ValueKind.NONE,
                startValue = source.startValue,
                targetValue = source.targetValue,
                startDate = source.startDate.toDateOrNull() ?: return,
                targetDate = source.targetDate.toDateOrNull() ?: return,
                isActive = source.isActive,
            ),
        )
    }

    /**
     * What happened, as opposed to what was planned.
     *
     * The closes are written as they stand rather than recomputed from the
     * occurrences. A close is a record of a day that ended, `GoalProgressWriter`
     * says a row is written once, and a restore that recounted them could hand
     * somebody a streak they did not have.
     */
    private suspend fun writeHistory(document: ExportDocument, planId: Long, itemIds: Map<Long, Long>) {
        val restored = document.history.occurrences.mapNotNull { source ->
            Occurrence(
                id = 0,
                itemId = itemIds[source.itemId] ?: return@mapNotNull null,
                date = source.date.toDateOrNull() ?: return@mapNotNull null,
                plannedAt = source.plannedAt.toDateTimeOrNull() ?: return@mapNotNull null,
                scheduledAt = null,
                firedAt = null,
                settledAt = source.settledAt?.toInstantOrNull(),
                state = enumOrNull<OccurrenceState>(source.state) ?: OccurrenceState.PENDING,
                shiftMinutes = source.shiftMinutes,
                sequenceInDay = source.sequenceInDay,
            )
        }

        occurrences.restore(restored)

        document.history.measurements.forEach { source ->
            measurements.upsert(
                Measurement(
                    id = 0,
                    itemId = itemIds[source.itemId] ?: return@forEach,
                    // The file does not carry occurrence ids, and the new rows
                    // have new ones. A reading stands on its own date anyway.
                    occurrenceId = null,
                    date = source.date.toDateOrNull() ?: return@forEach,
                    value = source.value,
                    kind = enumOrNull<ValueKind>(source.kind) ?: ValueKind.NONE,
                    note = source.note,
                ),
            )
        }

        document.history.milestones.forEach { source ->
            milestones.award(
                MilestoneAward(
                    milestone = enumOrNull<Milestone>(source.milestone) ?: return@forEach,
                    goalId = null,
                    itemId = null,
                    awardedOn = source.awardedOn.toDateOrNull() ?: return@forEach,
                    // Awarded before the restore, so it is not announced again.
                    // A banner for a badge earned in August is noise, and the
                    // badge wall already says which ones are earned.
                    seenAt = if (source.seen) time.now() else null,
                ),
            )
        }

        document.history.dayCloses.forEach { source ->
            val date = source.date.toDateOrNull() ?: return@forEach

            closes.upsert(
                DayClose(
                    date = date,
                    planId = planId,
                    itemsDone = source.itemsDone,
                    itemsMinimum = source.itemsMinimum,
                    itemsMissed = source.itemsMissed,
                    itemsTotal = source.itemsTotal,
                    quality = enumOrNull<DayQuality>(source.quality) ?: DayQuality.OK,
                    // The file does not say what time of night it closed, and
                    // nothing reads the hour. The day itself is the fact.
                    closedAt = date.plusDays(1).atStartOfDay(time.zone()).toInstant(),
                ),
            )
        }
    }
}

// Mapping ---------------------------------------------------------------------
//
// Written out rather than generated, to match `ExportBuilder` going the other
// way. Anything unreadable falls back rather than throwing: one bad field in a
// ninety day file should cost that field, not the routine.

private fun ExportItem.toItem(templateId: Long, blockId: Long?) = Item(
    id = 0,
    templateId = templateId,
    blockId = blockId,
    kind = enumOrNull<ItemKind>(kind) ?: ItemKind.DO,
    title = title,
    detail = detail,
    anchor = anchor.toAnchor(),
    duration = durationMinutes?.minutes,
    salience = enumOrNull<Salience>(salience) ?: Salience.NOTIFY,
    weekdays = Weekdays(weekdays),
    pinned = pinned,
    minimum = minimum?.let { MinimumVersion(title = it.title, duration = it.durationMinutes?.minutes) },
    valueKind = enumOrNull<ValueKind>(valueKind) ?: ValueKind.NONE,
    bundleUri = null,
    trackId = null,
    sortOrder = sortOrder,
    archivedAt = archivedAt?.toInstantOrNull(),
    catchable = catchable,
)

/**
 * One anchor, unflattened.
 *
 * A shape that cannot be read becomes midnight rather than nothing. The step
 * is visible at the top of the day, where it is obviously wrong and one tap
 * from being fixed; dropped, it would be a step the user never learns they
 * have lost.
 */
private fun ExportAnchor.toAnchor(): Anchor = when (enumOrNull<AnchorType>(type)) {
    AnchorType.FIXED -> Anchor.Fixed(at.toTimeOrNull() ?: LocalTime.MIDNIGHT)

    AnchorType.RELATIVE -> Anchor.Relative(
        parentItemId = parentItemId ?: 0L,
        offset = (offsetMinutes ?: 0L).minutes,
    )

    AnchorType.WINDOW -> Anchor.Window(
        from = from.toTimeOrNull() ?: LocalTime.MIDNIGHT,
        to = to.toTimeOrNull() ?: LocalTime.MIDNIGHT,
        nagLadder = nagLadderMinutes.map { it.minutes },
    )

    AnchorType.INTERVAL -> Anchor.Interval(
        every = (everyMinutes ?: 0L).minutes,
        from = from.toTimeOrNull() ?: LocalTime.MIDNIGHT,
        to = to.toTimeOrNull() ?: LocalTime.MIDNIGHT,
    )

    null -> Anchor.Fixed(LocalTime.MIDNIGHT)
}

private inline fun <reified T : Enum<T>> enumOrNull(name: String?): T? =
    name?.let { value -> enumValues<T>().firstOrNull { it.name == value } }

private fun String?.toDateOrNull(): LocalDate? = this?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

private fun String?.toTimeOrNull(): LocalTime? = this?.let { runCatching { LocalTime.parse(it) }.getOrNull() }

private fun String?.toInstantOrNull(): Instant? = this?.let { runCatching { Instant.parse(it) }.getOrNull() }

private fun String?.toDateTimeOrNull(): LocalDateTime? =
    this?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
