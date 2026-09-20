package com.buildorbreak.app.feature.plan

import com.buildorbreak.core.domain.usecase.ObserveRoutineCostUseCase
import com.buildorbreak.core.domain.usecase.ObserveWalletUseCase
import com.buildorbreak.core.model.goal.Wallet
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * What a new routine costs, and what there is to pay with.
 *
 * Two readers in one injectable bag, for the same reason `DayWatch` exists
 * on Today: the screen asks one question, "can I add another day", and
 * answering it needs both halves. Injecting them separately pushed the
 * ViewModel's constructor past the point anyone could read it.
 */
class RoutinePrice @Inject constructor(
    private val observeCost: ObserveRoutineCostUseCase,
    private val observeWallet: ObserveWalletUseCase,
) {
    /** Zero while the free routines last. */
    fun cost(): Flow<Int> = observeCost()

    fun wallet(): Flow<Wallet> = observeWallet()
}
