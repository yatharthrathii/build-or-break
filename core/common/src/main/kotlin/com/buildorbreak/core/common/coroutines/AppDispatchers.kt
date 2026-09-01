package com.buildorbreak.core.common.coroutines

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Dispatchers as an injected dependency rather than a global.
 *
 * A single interface is used instead of qualifier annotations so that
 * `:core:common` stays free of any injection framework and remains a pure
 * Kotlin module. Tests swap in one object and every dispatcher becomes the test
 * scheduler at once.
 */
interface AppDispatchers {
    /** CPU bound work: the timeline engine, statistics, parsing. */
    val default: CoroutineDispatcher

    /** Disk and database work. */
    val io: CoroutineDispatcher

    /** Android main thread. */
    val main: CoroutineDispatcher
}

/** Production dispatchers. */
object DefaultAppDispatchers : AppDispatchers {
    override val default: CoroutineDispatcher = Dispatchers.Default
    override val io: CoroutineDispatcher = Dispatchers.IO
    override val main: CoroutineDispatcher = Dispatchers.Main
}
