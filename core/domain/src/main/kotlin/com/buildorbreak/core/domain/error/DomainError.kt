package com.buildorbreak.core.domain.error

/**
 * Every way an operation can fail, as a reason rather than as an exception.
 *
 * architecture.md section 8: no exception crosses a module boundary, and the
 * domain never holds a message. A reason maps to a string resource at the UI
 * layer, which is what keeps the domain testable without a `Context` and what
 * stops the same failure being worded three different ways on three screens.
 */
sealed interface DomainError {

    sealed interface DataError : DomainError {
        data object NotFound : DataError
        data object WriteFailed : DataError
        data class Corrupt(val detail: String) : DataError
    }

    /**
     * None of these are error screens.
     *
     * `ExactAlarmDenied` in particular drops the delivery tier and surfaces on
     * the Reliability screen. techspec.md section 7: degrade, do not fail.
     */
    sealed interface AlarmError : DomainError {
        data object ExactAlarmDenied : AlarmError
        data object NotificationsDenied : AlarmError
        data object TooManyScheduled : AlarmError
    }

    sealed interface ParseError : DomainError {
        data object NothingRecognised : ParseError
        data class PartialParse(val recognised: Int, val total: Int) : ParseError
    }

    sealed interface PlanError : DomainError {
        data class AnchorCycle(val itemIds: List<Long>) : PlanError
        data class BudgetExceeded(val alarms: Int) : PlanError
    }
}
