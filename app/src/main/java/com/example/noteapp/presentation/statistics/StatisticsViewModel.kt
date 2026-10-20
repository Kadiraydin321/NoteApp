package com.example.noteapp.presentation.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.noteapp.domain.model.AppStatistics
import com.example.noteapp.domain.repository.NoteRepository
import com.example.noteapp.domain.util.StatisticsCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val repository: NoteRepository
) : ViewModel() {

    private val _statistics = MutableStateFlow(AppStatistics())
    val statistics: StateFlow<AppStatistics> = _statistics.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadStatistics()
    }

    fun loadStatistics() {
        viewModelScope.launch {
            _isLoading.value = true
            combine(
                repository.getActiveNotes(),
                repository.getArchivedNotes(),
                repository.getTrashNotes(),
                repository.getAllCategories()
            ) { active, archived, trash, categories ->
                val allNotes = active + archived + trash
                StatisticsCalculator.calculate(allNotes, categories)
            }.collect { stats ->
                _statistics.value = stats
                _isLoading.value = false
            }
        }
    }
}
