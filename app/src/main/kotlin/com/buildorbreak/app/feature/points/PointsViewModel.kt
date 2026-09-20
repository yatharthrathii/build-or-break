package com.buildorbreak.app.feature.points

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buildorbreak.core.domain.goal.Prices
import com.buildorbreak.core.domain.usecase.ObserveAdAvailableUseCase
import com.buildorbreak.core.domain.usecase.ObserveLedgerUseCase
import com.buildorbreak.core.domain.usecase.ObserveWalletUseCase
import com.buildorbreak.core.domain.usecase.PointMovement
import com.buildorbreak.core.model.enums.PointReason
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** One line of the ledger, ready to draw. */
@Immutable
data class MovementUi(val date: LocalDate, val delta: Int, val reason: PointReason?)

/**
 * One thing points are for.
 *
 * [cost] is null for an entry that is planned rather than built, which is
 * the honest way to show somebody what the points are heading towards
 * without letting them spend on something that does not exist.
 */
@Immutable
data class UnlockUi(val kind: UnlockKind, val cost: Int?, val affordable: Boolean)

/** Everything points buy, built or not. The screen turns each into a sentence. */
enum class UnlockKind { UNDO_STEP, STREAK_FREEZE, EXTRA_ROUTINE, SECOND_GOAL }

@Immutable
data class PointsUiState(
    val loaded: Boolean = false,
    val balance: Int = 0,
    val earned: Int = 0,
    val spent: Int = 0,
    val adReward: Int = Prices.AD_REWARD,
    val adAvailable: Boolean = true,
    val unlocks: ImmutableList<UnlockUi> = persistentListOf(),
    val movements: ImmutableList<MovementUi> = persistentListOf(),
    /** Set when the ad button is tapped, because the ads are not wired up yet. */
    val adNotReady: Boolean = false,
)

/**
 * The points screen: what is in the wallet, what it buys, and where it went.
 *
 * The three unlocks that exist are bought where they are used, not from
 * here. Buying a freeze from a list of prices would mean choosing a day
 * from a second list, and the day is already on screen on Insights; buying
 * an undo would mean choosing a step. This screen is the statement, not the
 * shop, which is what makes the ledger the point of it.
 */
@HiltViewModel
class PointsViewModel @Inject constructor(
    observeWallet: ObserveWalletUseCase,
    observeLedger: ObserveLedgerUseCase,
    observeAdAvailable: ObserveAdAvailableUseCase,
) : ViewModel() {

    private val adTapped = MutableStateFlow(false)

    val state: StateFlow<PointsUiState> = combine(
        observeWallet(),
        observeLedger(),
        observeAdAvailable(),
        adTapped,
    ) { wallet, ledger, adReady, tapped ->
        PointsUiState(
            loaded = true,
            balance = wallet.balance,
            earned = wallet.earned,
            spent = wallet.spent,
            adAvailable = adReady,
            unlocks = unlocksFor(wallet.balance),
            movements = ledger.map { it.toUi() }.toImmutableList(),
            adNotReady = tapped,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = PointsUiState(),
    )

    /**
     * Says so, rather than quietly doing nothing.
     *
     * The ads SDK is deliberately not wired up. A button that looks live and
     * does nothing is worse than one that explains itself, and this app's
     * rule everywhere else is that a control either works or says why not.
     */
    fun onWatchAd() {
        adTapped.value = true
    }

    fun onDismissAd() {
        adTapped.value = false
    }

    private fun unlocksFor(balance: Int): ImmutableList<UnlockUi> = listOf(
        UnlockUi(UnlockKind.UNDO_STEP, Prices.UNDO_STEP, balance >= Prices.UNDO_STEP),
        UnlockUi(UnlockKind.STREAK_FREEZE, Prices.STREAK_FREEZE, balance >= Prices.STREAK_FREEZE),
        UnlockUi(UnlockKind.EXTRA_ROUTINE, Prices.EXTRA_ROUTINE, balance >= Prices.EXTRA_ROUTINE),
        UnlockUi(UnlockKind.SECOND_GOAL, null, false),
    ).toImmutableList()

    private fun PointMovement.toUi() = MovementUi(date = date, delta = delta, reason = reason)

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
