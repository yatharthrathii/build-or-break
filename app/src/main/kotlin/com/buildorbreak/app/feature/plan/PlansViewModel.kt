package com.buildorbreak.app.feature.plan

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buildorbreak.app.sample.Starters
import com.buildorbreak.core.common.result.Outcome
import com.buildorbreak.core.domain.usecase.AddPlanUseCase
import com.buildorbreak.core.domain.usecase.DeletePlanUseCase
import com.buildorbreak.core.domain.usecase.ObservePlansUseCase
import com.buildorbreak.core.domain.usecase.RenamePlanUseCase
import com.buildorbreak.core.domain.usecase.SwitchPlanUseCase
import com.buildorbreak.core.model.plan.Plan
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** "Sep 2026", for the line that says how old a plan is. */
private val MONTH: DateTimeFormatter
    get() = DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault())

/** One plan, as the list draws it. */
@Immutable
data class PlanRowUi(val id: Long, val name: String, val isRunning: Boolean, val since: String)

@Immutable
data class PlansUiState(
    val plans: ImmutableList<PlanRowUi>,
    /** The last write did not land. Said once, cleared by the next tap. */
    val failed: Boolean = false,
) {
    /** The last plan stays. Today has to resolve from something. */
    val canDelete: Boolean get() = plans.size > 1

    companion object {
        val Empty = PlansUiState(plans = persistentListOf())
    }
}

/**
 * Every plan, and the one that runs.
 *
 * Switching is the whole screen. The rest, adding, renaming and deleting,
 * is here because a list that can only be read is a list somebody has to
 * leave to change.
 */
@HiltViewModel
class PlansViewModel @Inject constructor(
    observePlans: ObservePlansUseCase,
    private val switchPlan: SwitchPlanUseCase,
    private val addPlan: AddPlanUseCase,
    private val renamePlan: RenamePlanUseCase,
    private val deletePlan: DeletePlanUseCase,
    private val starters: Starters,
) : ViewModel() {

    private val failed = MutableStateFlow(false)

    val state: StateFlow<PlansUiState> = combine(observePlans(), failed) { plans, failure ->
        PlansUiState(plans = plans.map(::toRow).toImmutableList(), failed = failure)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = PlansUiState.Empty,
    )

    fun onUse(planId: Long) = viewModelScope.launch { report(switchPlan(planId)) }

    /** A new plan starts with one day, named the way the blank first run names it. */
    fun onAdd(name: String) = viewModelScope.launch {
        report(addPlan(name, starters.blank.templateName))
    }

    fun onRename(planId: Long, name: String) = viewModelScope.launch { report(renamePlan(planId, name)) }

    /** Refused by the use case when it is the last one. The screen never offers that. */
    fun onDelete(planId: Long) = viewModelScope.launch { report(deletePlan(planId)) }

    private fun report(outcome: Outcome<*, *>) {
        failed.value = outcome is Outcome.Failure
    }

    private fun toRow(plan: Plan) = PlanRowUi(
        id = plan.id,
        name = plan.name,
        isRunning = plan.isActive,
        since = plan.createdAt.atZone(plan.zone).format(MONTH),
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
