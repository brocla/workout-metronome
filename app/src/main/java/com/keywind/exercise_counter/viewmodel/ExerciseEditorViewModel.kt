package com.keywind.exercise_counter.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.keywind.exercise_counter.data.AppDatabase
import com.keywind.exercise_counter.data.Exercise
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ExerciseEditorViewModel(
    application: Application,
    private val savedState: SavedStateHandle,
) : AndroidViewModel(application) {

    private val dao = AppDatabase.getInstance(application).exerciseDao()

    private val exerciseId: Long? = savedState.get<Long>("id")
    val isEditing: Boolean = exerciseId != null

    val name: StateFlow<String> = savedState.getStateFlow(KEY_NAME, "")
    val sets: StateFlow<Int> = savedState.getStateFlow(KEY_SETS, DEFAULT_SETS)
    val duration: StateFlow<Int> = savedState.getStateFlow(KEY_DURATION, DEFAULT_DURATION)
    val gap: StateFlow<Int> = savedState.getStateFlow(KEY_GAP, DEFAULT_GAP)
    val beat: StateFlow<Int> = savedState.getStateFlow(KEY_BEAT, DEFAULT_BEAT)

    private val loaded: StateFlow<Boolean> = savedState.getStateFlow(KEY_LOADED, false)

    init {
        if (exerciseId != null && !loaded.value) {
            viewModelScope.launch {
                dao.getById(exerciseId)?.let { exercise ->
                    savedState[KEY_NAME] = exercise.name
                    savedState[KEY_SETS] = exercise.sets
                    savedState[KEY_DURATION] = exercise.duration
                    savedState[KEY_GAP] = exercise.gap
                    savedState[KEY_BEAT] = exercise.beat
                }
                savedState[KEY_LOADED] = true
            }
        }
    }

    fun updateName(value: String) { savedState[KEY_NAME] = value }
    fun updateSets(value: Int) { savedState[KEY_SETS] = value }
    fun updateDuration(value: Int) { savedState[KEY_DURATION] = value }
    fun updateGap(value: Int) { savedState[KEY_GAP] = value }
    fun updateBeat(value: Int) { savedState[KEY_BEAT] = value }

    fun save(onComplete: () -> Unit) {
        val currentName = name.value.trim()
        if (currentName.isBlank()) return

        viewModelScope.launch {
            if (exerciseId != null) {
                val existing = dao.getById(exerciseId) ?: return@launch
                dao.update(
                    existing.copy(
                        name = currentName,
                        sets = sets.value,
                        duration = duration.value,
                        gap = gap.value,
                        beat = beat.value,
                    )
                )
            } else {
                dao.insert(
                    Exercise(
                        name = currentName,
                        sets = sets.value,
                        duration = duration.value,
                        gap = gap.value,
                        beat = beat.value,
                        sortOrder = dao.getNextSortOrder(),
                    )
                )
            }
            onComplete()
        }
    }

    companion object {
        private const val KEY_NAME = "name"
        private const val KEY_SETS = "sets"
        private const val KEY_DURATION = "duration"
        private const val KEY_GAP = "gap"
        private const val KEY_BEAT = "beat"
        private const val KEY_LOADED = "loaded"

        private const val DEFAULT_SETS = 3
        private const val DEFAULT_DURATION = 10
        private const val DEFAULT_GAP = 3
        private const val DEFAULT_BEAT = 1
    }
}
