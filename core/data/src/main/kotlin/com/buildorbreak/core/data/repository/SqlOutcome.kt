package com.buildorbreak.core.data.repository

import android.database.SQLException
import com.buildorbreak.core.common.result.Outcome
import com.buildorbreak.core.domain.error.DomainError.DataError
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Runs a write on the io dispatcher and turns a database failure into a reason.
 *
 * architecture.md section 8: no exception crosses a module boundary, and the
 * domain holds a reason rather than a message. Everything above this line deals
 * in `Outcome`, so this is the one place a `SQLException` is allowed to exist.
 *
 * The exception is intentionally not logged here. Section 8 again: a failure the
 * user cannot act on is logged and swallowed at one place, in the caller. A
 * repository that logged as well would produce the same failure twice in the
 * output, from two different layers, with no extra information.
 *
 * Room already dispatches, but the dispatcher is passed in explicitly because
 * `AppDispatchers` is what makes every one of these testable on a test
 * scheduler, and because rules.md forbids naming `Dispatchers.IO` directly.
 */
@Suppress("SwallowedException")
internal suspend fun <T> sqlOutcome(io: CoroutineDispatcher, block: suspend () -> T): Outcome<T, DataError> =
    withContext(io) {
        try {
            Outcome.Success(block())
        } catch (failure: SQLException) {
            Outcome.Failure(DataError.WriteFailed)
        }
    }
