package com.buildorbreak.core.testing.coroutines

import com.buildorbreak.core.common.coroutines.AppDispatchers
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/**
 * Routes every dispatcher to a single [TestDispatcher] so tests stay
 * deterministic and virtual time works everywhere.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TestAppDispatchers(
    private val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : AppDispatchers {
    override val default: CoroutineDispatcher = dispatcher
    override val io: CoroutineDispatcher = dispatcher
    override val main: CoroutineDispatcher = dispatcher

    val testDispatcher: TestDispatcher get() = dispatcher
}
