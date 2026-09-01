package com.buildorbreak.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application entry point.
 *
 * rules.md section 5 budgets cold start at under 500 ms, so nothing expensive
 * runs here. Work that needs to happen at launch is either lazy, or scheduled
 * through androidx.startup with an explicit dependency order, never dropped
 * into onCreate because it was convenient.
 */
@HiltAndroidApp
class BuildOrBreakApplication : Application()
