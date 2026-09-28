package com.example.noteapp.presentation.settings

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.noteapp.data.settings.AppSettings
import com.example.noteapp.data.settings.AppSettingsManager
import com.example.noteapp.data.settings.WidgetFilterMode
import com.example.noteapp.widget.NotesWidgetProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val app: Application,
    private val settingsManager: AppSettingsManager
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsManager.settings

    fun setWidgetFilterMode(mode: WidgetFilterMode) {
        settingsManager.setWidgetFilterMode(mode)
        NotesWidgetProvider.updateAllWidgets(app)
    }

    fun setWidgetShowLocked(show: Boolean) {
        settingsManager.setWidgetShowLockedNotes(show)
        NotesWidgetProvider.updateAllWidgets(app)
    }

    fun setDefaultNoteColor(color: Int) {
        settingsManager.setDefaultNoteColor(color)
    }

    fun setDynamicColor(enabled: Boolean) {
        settingsManager.setDynamicColor(enabled)
    }

    fun refreshWidget() {
        NotesWidgetProvider.updateAllWidgets(app)
    }
}
