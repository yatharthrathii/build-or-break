package com.buildorbreak.core.domain.resolver

import com.buildorbreak.core.model.enums.Salience
import com.buildorbreak.core.model.plan.Block
import com.buildorbreak.core.model.resolved.BudgetWarning
import com.buildorbreak.core.model.resolved.ResolvedEntry
import com.buildorbreak.core.testing.fixtures.ExecutionFixtures
import com.buildorbreak.core.testing.fixtures.PlanFixtures
import com.buildorbreak.core.testing.fixtures.PlanFixtures.item
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class SalienceBudgetTest {

    private val budget = SalienceBudget()
    private val date = ExecutionFixtures.DATE

    private fun entry(id: Long, salience: Salience, block: Block? = null): ResolvedEntry = ResolvedEntry(
        item = item(id = id, salience = salience),
        block = block,
        at = date.atTime(8, 0),
        occurrence = null,
    )

    private fun entries(count: Int, salience: Salience): List<ResolvedEntry> = (1L..count).map { entry(it, salience) }

    @Test
    fun `an empty day makes no noise and needs no warning`() {
        assertThat(budget.evaluate(emptyList())).isNull()
    }

    @Test
    fun `three alarms is the limit, not one past it`() {
        assertThat(budget.evaluate(entries(BudgetWarning.MAX_ALARMS, Salience.ALARM))).isNull()
    }

    @Test
    fun `a fourth alarm trips the warning and reports the count`() {
        val warning = budget.evaluate(entries(BudgetWarning.MAX_ALARMS + 1, Salience.ALARM))

        assertThat(warning).isNotNull()
        assertThat(warning?.alarmCount).isEqualTo(4)
    }

    @Test
    fun `ten notifications is the limit`() {
        assertThat(budget.evaluate(entries(BudgetWarning.MAX_NOTIFY, Salience.NOTIFY))).isNull()
    }

    @Test
    fun `an eleventh notification trips the warning`() {
        val warning = budget.evaluate(entries(BudgetWarning.MAX_NOTIFY + 1, Salience.NOTIFY))

        assertThat(warning?.notifyCount).isEqualTo(11)
    }

    @Test
    fun `silent entries cost nothing`() {
        assertThat(budget.evaluate(entries(50, Salience.SILENT))).isNull()
    }

    @Test
    fun `timeline entries are never scheduled, so a long quiet day stays quiet`() {
        assertThat(budget.evaluate(entries(50, Salience.TIMELINE))).isNull()
    }

    @Test
    fun `a block decides how loud its items are`() {
        // Four silent items inside an alarm block are four alarms.
        val loudBlock = PlanFixtures.block(id = 10, salience = Salience.ALARM)
        val grouped = (1L..4L).map { entry(it, Salience.SILENT, block = loudBlock) }

        assertThat(budget.evaluate(grouped)?.alarmCount).isEqualTo(4)
    }

    @Test
    fun `alarms and notifications are counted against their own limits`() {
        val mixed = entries(2, Salience.ALARM) + (10L..20L).map { entry(it, Salience.NOTIFY) }

        val warning = budget.evaluate(mixed)

        assertThat(warning?.alarmCount).isEqualTo(2)
        assertThat(warning?.notifyCount).isEqualTo(11)
    }
}
