package com.keywind.exercise_counter.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.keywind.exercise_counter.audio.VoiceAnnouncer
import com.keywind.exercise_counter.data.AppDatabase
import com.keywind.exercise_counter.data.Exercise
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RoutineViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.getInstance(application).exerciseDao()

    init {
        // Pre-warm TTS so it's ready when the user taps Play
        VoiceAnnouncer.getInstance(application)
    }

    val exercises: StateFlow<List<Exercise>> = dao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun deleteExercise(exercise: Exercise) {
        viewModelScope.launch { dao.delete(exercise) }
    }

    fun toggleEnabled(exercise: Exercise) {
        viewModelScope.launch { dao.update(exercise.copy(enabled = !exercise.enabled)) }
    }

    fun moveExercise(from: Int, to: Int) {
        val reordered = reorderExercises(exercises.value, from, to)
        viewModelScope.launch {
            reordered.forEachIndexed { index, exercise ->
                dao.updateSortOrder(exercise.id, index)
            }
        }
    }
}

/** Returns a new list with the item at [from] moved to [to]. Both indices must be in-bounds. */
internal fun reorderExercises(list: List<Exercise>, from: Int, to: Int): List<Exercise> {
    val mutable = list.toMutableList()
    mutable.add(to, mutable.removeAt(from))
    return mutable
}
