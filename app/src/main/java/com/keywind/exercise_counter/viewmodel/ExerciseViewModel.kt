package com.keywind.exercise_counter.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.keywind.exercise_counter.audio.MetronomeEngine
import com.keywind.exercise_counter.audio.VoiceAnnouncer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ExerciseState {
    IDLE,
    EXERCISING,
    GAP,
    DONE,
}

class ExerciseViewModel(application: Application) : AndroidViewModel(application) {

    private val metronome = MetronomeEngine()
    private val announcer = VoiceAnnouncer(application)

    private val _sets = MutableStateFlow(DEFAULT_SETS)
    val sets: StateFlow<Int> = _sets.asStateFlow()

    private val _duration = MutableStateFlow(DEFAULT_DURATION)
    val duration: StateFlow<Int> = _duration.asStateFlow()

    private val _gap = MutableStateFlow(DEFAULT_GAP)
    val gap: StateFlow<Int> = _gap.asStateFlow()

    private val _beat = MutableStateFlow(DEFAULT_BEAT)
    val beat: StateFlow<Int> = _beat.asStateFlow()

    private val _currentSet = MutableStateFlow(0)
    val currentSet: StateFlow<Int> = _currentSet.asStateFlow()

    private val _state = MutableStateFlow(ExerciseState.IDLE)
    val state: StateFlow<ExerciseState> = _state.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private var exerciseJob: Job? = null

    fun updateSets(value: Int) { _sets.value = value }
    fun updateDuration(value: Int) { _duration.value = value }
    fun updateGap(value: Int) { _gap.value = value }
    fun updateBeat(value: Int) { _beat.value = value }

    fun play() {
        if (_state.value == ExerciseState.DONE) {
            reset()
        }
        _isRunning.value = true
        if (_state.value == ExerciseState.IDLE) {
            _currentSet.value = 0
        }
        startExerciseLoop()
    }

    fun pause() {
        _isRunning.value = false
        metronome.stop()
        exerciseJob?.cancel()
        exerciseJob = null
    }

    fun reset() {
        pause()
        _state.value = ExerciseState.IDLE
        _currentSet.value = 0
    }

    private fun startExerciseLoop() {
        exerciseJob?.cancel()
        exerciseJob = viewModelScope.launch {
            val totalSets = _sets.value
            val durationSec = _duration.value
            val gapSec = _gap.value
            val beatSec = _beat.value

            var set = _currentSet.value

            while (set < totalSets && _isRunning.value) {
                // Exercise phase
                _state.value = ExerciseState.EXERCISING
                metronome.start(beatSec)
                delay(durationSec * 1000L)
                metronome.stop()

                set++
                _currentSet.value = set

                if (set >= totalSets) {
                    // All sets complete
                    announcer.announce("Done")
                    _state.value = ExerciseState.DONE
                    _isRunning.value = false
                } else {
                    announcer.announce("$set")

                    // Gap phase
                    _state.value = ExerciseState.GAP
                    delay(gapSec * 1000L)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        metronome.stop()
        announcer.shutdown()
    }

    companion object {
        const val DEFAULT_SETS = 3
        const val DEFAULT_DURATION = 10
        const val DEFAULT_GAP = 3
        const val DEFAULT_BEAT = 1
    }
}
