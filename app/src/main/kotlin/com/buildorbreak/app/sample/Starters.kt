package com.buildorbreak.app.sample

import android.content.Context
import com.buildorbreak.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** One routine the first run can begin from, written in the app's own import format. */
data class Starter(val planName: String, val templateName: String, val text: String)

/**
 * The three days a new user can start from, and the blank one.
 *
 * Three rather than one, because the first screen after "what is this app" has
 * to ask something the user can answer about themselves. "Load a sample day"
 * asks nothing and teaches nothing: it produces nine steps belonging to a
 * stranger, and the first thing anybody does with a stranger's routine is
 * delete it. "What are you trying to build" is a question people already have
 * an answer to, and the routine that follows it is recognisably about them.
 *
 * Kept in `strings.xml` and read by the same parser a pasted routine goes
 * through, so a starter cannot succeed by a path a real import would fail on,
 * and so each one is translated like any other copy.
 *
 * Each stays inside the daily alarm budget on purpose. A starter that opened
 * with a warning about making too much noise would be teaching the wrong
 * lesson on the first screen.
 */
class Starters @Inject constructor(@param:ApplicationContext private val context: Context) {

    val morning: Starter get() = starter(R.string.starter_morning_name, R.string.starter_morning_text)

    val study: Starter get() = starter(R.string.starter_study_name, R.string.starter_study_text)

    val fitness: Starter get() = starter(R.string.starter_fitness_name, R.string.starter_fitness_text)

    /** What a plan is called when the user is going to write it themselves. */
    val blank: Starter get() = starter(R.string.starter_blank_name, R.string.starter_blank_text)

    private fun starter(name: Int, text: Int) = Starter(
        planName = context.getString(name),
        templateName = context.getString(R.string.starter_template),
        text = context.getString(text),
    )
}
