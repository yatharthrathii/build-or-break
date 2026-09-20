package com.buildorbreak.core.designsystem.component

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * The half of a press the eye cannot see.
 *
 * A flat design with no shadows and no ripple has nothing left to say "that
 * landed" except motion, and motion alone is not enough: on a mid range phone a
 * tap and the frame that answers it can be eighty milliseconds apart, which is
 * long enough to wonder whether the button worked and tap it again. A tick under
 * the finger arrives in single digit milliseconds and settles the question
 * before the screen has redrawn.
 *
 * Three weights, and the difference between them is meaning rather than
 * strength. Committing to something feels heavier than choosing between two
 * things, and both feel different from a value ticking past under a thumb.
 *
 * The phone's own setting is respected throughout. `performHapticFeedback`
 * returns without doing anything when the user has turned touch feedback off,
 * which is the correct behaviour and not something to work around.
 */
@Immutable
class Feedback internal constructor(private val view: View) {

    /** An ordinary tap: a button, a row, a tab. */
    fun tap() = play(HapticFeedbackConstants.VIRTUAL_KEY)

    /**
     * Something is now settled: done, saved, deleted.
     *
     * `CONFIRM` is a fuller, rounder tick than a plain key press and arrived in
     * Android 11. Below that a key press is the closest thing there is.
     */
    fun confirm() = play(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.VIRTUAL_KEY
        },
    )

    /** A value moving one notch: a stepper, a toggle, a picker. */
    fun tick() = play(HapticFeedbackConstants.CLOCK_TICK)

    private fun play(constant: Int) {
        view.performHapticFeedback(constant)
    }
}

/** The feedback for the current view. Cheap, and safe to ask for in any composable. */
@Composable
fun rememberFeedback(): Feedback {
    val view = LocalView.current

    return remember(view) { Feedback(view) }
}
