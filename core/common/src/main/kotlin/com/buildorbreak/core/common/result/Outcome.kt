package com.buildorbreak.core.common.result

/**
 * A result type for operations that can fail in a way the user needs to know
 * about.
 *
 * Named [Outcome] rather than `Result` so it never collides with
 * `kotlin.Result`, which carries a `Throwable` and encourages swallowing it.
 * rules.md section 4 forbids swallowed exceptions, so failures here carry a
 * typed reason rather than an exception.
 */
sealed interface Outcome<out T, out E> {
    data class Success<out T>(val value: T) : Outcome<T, Nothing>

    data class Failure<out E>(val reason: E) : Outcome<Nothing, E>
}

inline fun <T, E, R> Outcome<T, E>.map(transform: (T) -> R): Outcome<R, E> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(value))
    is Outcome.Failure -> this
}

inline fun <T, E> Outcome<T, E>.onSuccess(action: (T) -> Unit): Outcome<T, E> = apply {
    if (this is Outcome.Success) action(value)
}

inline fun <T, E> Outcome<T, E>.onFailure(action: (E) -> Unit): Outcome<T, E> = apply {
    if (this is Outcome.Failure) action(reason)
}

fun <T, E> Outcome<T, E>.getOrNull(): T? = (this as? Outcome.Success)?.value

fun <T, E> Outcome<T, E>.getOrElse(fallback: (E) -> T): T = when (this) {
    is Outcome.Success -> value
    is Outcome.Failure -> fallback(reason)
}
