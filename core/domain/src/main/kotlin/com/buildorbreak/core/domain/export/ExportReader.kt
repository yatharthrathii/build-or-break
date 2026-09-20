package com.buildorbreak.core.domain.export

import javax.inject.Inject
import kotlinx.serialization.json.Json

/** Why a file could not be turned back into a routine. */
enum class BackupProblem {
    /** Not this app's file, or damaged on the way. */
    NOT_READABLE,

    /** Written by a later version of the app than this one. */
    TOO_NEW,

    /** Read, but there is nothing in it to put back. */
    EMPTY,
}

/**
 * Reads a file this app wrote.
 *
 * The mirror of `ExportBuilder`, and deliberately the only thing that knows
 * how to. The builder's promise is that an export round trips, and a promise
 * with no reader on the other end of it is a file format nobody can check.
 *
 * Lenient about fields it does not know and strict about the ones it does. A
 * file from a later version may carry keys this build has never heard of, and
 * refusing the whole routine over one of them would make the backup useless
 * at exactly the moment it is needed, on a new phone with an older app. A
 * field that is present but wrong is a different matter: that is a damaged
 * file, and putting half of it back would be worse than saying so.
 */
class ExportReader @Inject constructor() {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = false
        explicitNulls = false
    }

    fun read(text: String): Result<ExportDocument> {
        val document = runCatching { json.decodeFromString(ExportDocument.serializer(), text) }
            .getOrElse { return Result.failure(BackupUnreadable(BackupProblem.NOT_READABLE)) }

        // A schema this build does not know how to read. Refused by number
        // rather than attempted, because the fields that changed are exactly
        // the ones a silent partial read would get wrong.
        if (document.schemaVersion > CURRENT_SCHEMA_VERSION) {
            return Result.failure(BackupUnreadable(BackupProblem.TOO_NEW))
        }

        if (document.templates.all { it.items.isEmpty() }) {
            return Result.failure(BackupUnreadable(BackupProblem.EMPTY))
        }

        return Result.success(document)
    }
}

/** Carries [problem] so the screen can say which of the three things went wrong. */
class BackupUnreadable(val problem: BackupProblem) : Exception(problem.name)
