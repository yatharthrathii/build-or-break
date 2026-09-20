package com.buildorbreak.app.format

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

/**
 * Times the way the phone shows them.
 *
 * The phone has a twelve or twenty four hour setting and the user has already
 * chosen it. An app that prints 18:02 on a phone whose own clock says 6:02 PM
 * is asking the user to translate. Read on every call rather than cached,
 * because the setting can change while the app is open.
 *
 * The am and pm marker is always the English one. In some locales the
 * platform's own marker is a word long enough to push a time onto two lines.
 */
class ClockFormat @Inject constructor(@param:ApplicationContext private val context: Context) {

    val is24Hour: Boolean get() = DateFormat.is24HourFormat(context)

    private val formatter: DateTimeFormatter
        get() = if (is24Hour) {
            DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
        } else {
            DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
        }

    fun format(time: LocalTime): String = time.format(formatter)

    fun format(time: LocalDateTime): String = time.format(formatter)
}

/** The same thing for a composable that has no ViewModel to ask. */
@Composable
fun rememberClockFormat(): ClockFormat {
    val context = LocalContext.current.applicationContext
    return remember(context) { ClockFormat(context) }
}
