package com.buildorbreak.app.sample

import android.content.Context
import com.buildorbreak.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * The nine step day the first run can load.
 *
 * Written in the app's own import format and kept in `strings.xml`, so it is
 * translated like any other copy and read by the same parser a pasted routine
 * goes through. A sample that took a different path into the database would be
 * a sample that could succeed where a real import fails.
 *
 * This class exists so the ViewModel that loads it never touches a `Context`.
 */
class SampleRoutine @Inject constructor(@param:ApplicationContext private val context: Context) {

    val text: String get() = context.getString(R.string.sample_routine_text)

    val planName: String get() = context.getString(R.string.sample_routine_plan)

    val templateName: String get() = context.getString(R.string.sample_routine_template)
}
