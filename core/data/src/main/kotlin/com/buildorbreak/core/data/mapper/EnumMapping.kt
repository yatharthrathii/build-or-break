package com.buildorbreak.core.data.mapper

/**
 * Reads an enum out of a stored string without ever throwing.
 *
 * `valueOf` would be shorter and would crash. A value the current build does not
 * recognise can only get into the database two ways: the app was downgraded, or
 * the file was edited. Neither is worth losing somebody's whole history over, and
 * a day that renders with one step marked wrongly is recoverable in a way that a
 * crash loop on launch is not.
 *
 * The fallback is always the safest member rather than the first one declared, so
 * every call site has to say what safe means for that column.
 */
internal inline fun <reified T : Enum<T>> String?.toEnum(fallback: T): T =
    this?.let { stored -> enumValues<T>().firstOrNull { it.name == stored } } ?: fallback
