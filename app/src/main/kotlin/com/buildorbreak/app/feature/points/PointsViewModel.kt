package com.buildorbreak.app.feature.points

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buildorbreak.core.common.result.Outcome
import com.buildorbreak.core.domain.goal.Prices
import com.buildorbreak.core.domain.usecase.FreezeDayUseCase
import com.buildorbreak.core.domain.usecase.ObserveAdAvailableUseCase
import com.buildorbreak.core.domain.usecase.ObserveFreezeOfferUseCase
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
import kotlinx.coroutines.launch

/** One line of the ledger, ready to draw. */
@Immutable
data class MovementUi(val date: LocalDate, val delta: Int, val reason: PointReason?)

/** One thing points are for, and whether there are enough of them for it. */
@Immutable
data class UnlockUi(val kind: UnlockKind, val cost: Int, val affordable: Boolean)

/**
 * The one missed day a freeze is worth buying for, and what it would do.
 *
 * [asking] is true while the confirmation is open. Points are not taken on
 * the first tap: spending is the one thing on this screen that cannot be
 * undone, so it is said out loud first.
 */
@Immutable
data class FreezeUi(
    val date: LocalDate,
    val runNow: Int,
    val runAfter: Int,
    val cost: Int,
    val affordable: Boolean,
    val asking: Boolean = false,
    val failed: Boolean = false,
)

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
    /** Null when no missed day is breaking the run, which is most days. */
    val freeze: FreezeUi? = null,
)

/**
 * The points screen: what is in the wallet, what it buys, and where it went.
 *
 * An undo and a routine are bought where they are used, because each needs
 * something chosen first: a step, a name. A freeze is the exception and is
 * bought here. There is never a day to choose, since only the one missed
 * day that broke the run is worth covering, so the row can name that day
 * and carry the button itself.
 */
@HiltViewModel
class PointsViewModel @Inject constructor(
    observeWallet: ObserveWalletUseCase,
    observeLedger: ObserveLedgerUseCase,
    observeAdAvailable: ObserveAdAvailableUseCase,
    observeFreezeOffer: ObserveFreezeOfferUseCase,
    private val freezeDay: FreezeDayUseCase,
) : ViewModel() {

    private val adTapped = MutableStateFlow(false)
    private val freezeAsk = MutableStateFlow(FreezeAsk())

    /** Where the freeze confirmation has got to. Two flags, kept together so they combine as one. */
    private data class FreezeAsk(val open: Boolean = false, val failed: Boolean = false)

    /** The offer with the wallet beside it, so the row knows whether it can be afforded. */
    private val freeze = combine(observeFreezeOffer(), observeWallet(), freezeAsk) { offer, wallet, ask ->
        offer?.let {
            FreezeUi(
                date = it.date,
                runNow = it.runNow,
                runAfter = it.runAfter,
                cost = Prices.STREAK_FREEZE,
                affordable = wallet.canAfford(Prices.STREAK_FREEZE),
                asking = ask.open,
                failed = ask.failed,
            )
        }
    }

    val state: StateFlow<PointsUiState> = combine(
        observeWallet(),
        observeLedger(),
        observeAdAvailable(),
        adTapped,
        freeze,
    ) { wallet, ledger, adReady, tapped, freezeOffer ->
        PointsUiState(
            loaded = true,
            balance = wallet.balance,
            earned = wallet.earned,
            spent = wallet.spent,
            adAvailable = adReady,
            unlocks = unlocksFor(wallet.balance),
            movements = ledger.map { it.toUi() }.toImmutableList(),
            adNotReady = tapped,
            freeze = freezeOffer,
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

    fun onAskFreeze() {
        freezeAsk.value = FreezeAsk(open = true)
    }

    fun onDismissFreeze() {
        freezeAsk.value = FreezeAsk()
    }

    /**
     * The day comes from the offer, never from the screen.
     *
     * When it works the offer itself goes away, because the day is no
     * longer a gap, and the dialog goes with it. Only a failure has to be
     * said.
     */
    fun onConfirmFreeze() = viewModelScope.launch {
        val day = state.value.freeze?.date ?: return@launch
        val bought = freezeDay(day) is Outcome.Success

        freezeAsk.value = FreezeAsk(open = !bought, failed = !bought)
    }

    private fun unlocksFor(balance: Int): ImmutableList<UnlockUi> = listOf(
        UnlockUi(UnlockKind.UNDO_STEP, Prices.UNDO_STEP, balance >= Prices.UNDO_STEP),
        UnlockUi(UnlockKind.STREAK_FREEZE, Prices.STREAK_FREEZE, balance >= Prices.STREAK_FREEZE),
        UnlockUi(UnlockKind.EXTRA_ROUTINE, Prices.EXTRA_ROUTINE, balance >= Prices.EXTRA_ROUTINE),
        UnlockUi(UnlockKind.SECOND_GOAL, Prices.SECOND_GOAL, balance >= Prices.SECOND_GOAL),
    ).toImmutableList()

    private fun PointMovement.toUi() = MovementUi(date = date, delta = delta, reason = reason)

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
