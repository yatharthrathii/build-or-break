package com.buildorbreak.app

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.buildorbreak.core.domain.repository.SettingsRepository
import com.buildorbreak.core.model.enums.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * The two facts the activity needs before it can draw anything.
 *
 * Null until both have been read. The splash screen stays up for that long,
 * which is a few milliseconds, so the first frame is already the right screen
 * in the right palette rather than Today flashing before the first run.
 */
@Immutable
data class ShellState(val onboardingComplete: Boolean, val themeMode: ThemeMode)

@HiltViewModel
class MainViewModel @Inject constructor(settings: SettingsRepository) : ViewModel() {

    val state: StateFlow<ShellState?> = combine(settings.onboardingComplete, settings.themeMode) { done, mode ->
        ShellState(onboardingComplete = done, themeMode = mode)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = null,
    )
}
