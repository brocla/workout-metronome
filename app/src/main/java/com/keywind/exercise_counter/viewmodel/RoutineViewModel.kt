package com.keywind.exercise_counter.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.keywind.exercise_counter.data.AppDatabase
import com.keywind.exercise_counter.data.Exercise
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RoutineViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.getInstance(application).exerciseDao()

    val exercises: StateFlow<List<Exercise>> = dao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun deleteExercise(exercise: Exercise) {
        viewModelScope.launch { dao.delete(exercise) }
    }

    fun toggleEnabled(exercise: Exercise) {
        viewModelScope.launch { dao.update(exercise.copy(enabled = !exercise.enabled)) }
    }
}
