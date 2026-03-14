package com.keywind.exercise_counter.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.keywind.exercise_counter.audio.MetronomeEngine
import com.keywind.exercise_counter.audio.VoiceAnnouncer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

enum class ExerciseState {
    IDLE,
    EXERCISING,
    GAP,
    PAUSED,
    DONE,
}

class ExerciseViewModel(
    application: Application,
    private val savedState: SavedStateHandle,
) : AndroidViewModel(application) {

    private val metronome = MetronomeEngine(viewModelScope)
    private val announcer = VoiceAnnouncer(application)

    val sets: StateFlow<Int> = savedState.getStateFlow(KEY_SETS, DEFAULT_SETS)
    val duration: StateFlow<Int> = savedState.getStateFlow(KEY_DURATION, DEFAULT_DURATION)
    val gap: StateFlow<Int> = savedState.getStateFlow(KEY_GAP, DEFAULT_GAP)
    val beat: StateFlow<Int> = savedState.getStateFlow(KEY_BEAT, DEFAULT_BEAT)

    val currentSet: StateFlow<Int> = savedState.getStateFlow(KEY_CURRENT_SET, 0)

    private val _stateRaw: StateFlow<String> =
        savedState.getStateFlow(KEY_STATE, ExerciseState.IDLE.name)

    val state: StateFlow<ExerciseState> = _stateRaw
        .map { name ->
            ExerciseState.entries.firstOrNull { it.name == name } ?: ExerciseState.IDLE
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ExerciseState.IDLE)

    val isRunning: StateFlow<Boolean> = state
        .map { it == ExerciseState.EXERCISING || it == ExerciseState.GAP }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private var exerciseJob: Job? = null

    fun updateSets(value: Int) { savedState[KEY_SETS] = value }
    fun updateDuration(value: Int) { savedState[KEY_DURATION] = value }
    fun updateGap(value: Int) { savedState[KEY_GAP] = value }
    fun updateBeat(value: Int) { savedState[KEY_BEAT] = value }

    private fun setExerciseState(newState: ExerciseState) {
        savedState[KEY_STATE] = newState.name
    }

    fun play() {
        if (state.value == ExerciseState.DONE) {
            reset()
        }
        if (state.value == ExerciseState.IDLE) {
            savedState[KEY_CURRENT_SET] = 0
        }
        startExerciseLoop()
    }

    fun pause() {
        setExerciseState(ExerciseState.PAUSED)
        metronome.stop()
        exerciseJob?.cancel()
        exerciseJob = null
    }

    fun reset() {
        pause()
        setExerciseState(ExerciseState.IDLE)
        savedState[KEY_CURRENT_SET] = 0
    }

    private fun startExerciseLoop() {
        exerciseJob?.cancel()
        exerciseJob = viewModelScope.launch {
            val totalSets = sets.value
            val durationSec = duration.value
            val gapSec = gap.value
            val beatSec = beat.value

            var set = currentSet.value

            while (set < totalSets) {
                // Exercise phase
                setExerciseState(ExerciseState.EXERCISING)
                if (beatSec > 0) metronome.start(beatSec.seconds)
                delay(durationSec.seconds)
                if (beatSec > 0) metronome.stop()

                set++
                savedState[KEY_CURRENT_SET] = set

                if (set >= totalSets) {
                    // All sets complete
                    announcer.announce("Done")
                    setExerciseState(ExerciseState.DONE)
                } else {
                    announcer.announce("$set")

                    // Gap phase
                    setExerciseState(ExerciseState.GAP)
                    delay(gapSec.seconds)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        pause()
        announcer.shutdown()
    }

    companion object {
        private const val KEY_SETS = "sets"
        private const val KEY_DURATION = "duration"
        private const val KEY_GAP = "gap"
        private const val KEY_BEAT = "beat"
        private const val KEY_CURRENT_SET = "currentSet"
        private const val KEY_STATE = "exerciseState"

        private const val DEFAULT_SETS = 3
        private const val DEFAULT_DURATION = 10
        private const val DEFAULT_GAP = 3
        private const val DEFAULT_BEAT = 1
    }
}
