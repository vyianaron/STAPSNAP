package com.example.daily_walk_journey.ui.screens.timeline

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.daily_walk_journey.data.db.AppDatabase
import com.example.daily_walk_journey.data.db.entity.Checkpoint
import com.example.daily_walk_journey.data.repository.CheckpointRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class TimelineUiState(
    val checkpoints: List<Checkpoint> = emptyList(),
    val pendingCheckpoints: List<Checkpoint> = emptyList()
) {
    val pendingCount: Int get() = pendingCheckpoints.size
    val hasPending: Boolean get() = pendingCheckpoints.isNotEmpty()
    val firstPending: Checkpoint? get() = pendingCheckpoints.firstOrNull()
}

class TimelineViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CheckpointRepository(
        AppDatabase.getInstance(application).checkpointDao()
    )

    val uiState: StateFlow<TimelineUiState> = combine(
        repository.getTodayCheckpoints(),
        repository.getTodayPendingCheckpoints()
    ) { checkpoints, pending ->
        TimelineUiState(
            checkpoints = checkpoints,
            pendingCheckpoints = pending
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TimelineUiState()
    )
}
