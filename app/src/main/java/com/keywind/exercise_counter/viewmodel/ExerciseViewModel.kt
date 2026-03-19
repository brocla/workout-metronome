package com.keywind.exercise_counter.viewmodel

import android.app.Application
import android.os.SystemClock
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
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseState.IDLE)

    val isRunning: StateFlow<Boolean> = state
        .map { it == ExerciseState.EXERCISING || it == ExerciseState.GAP }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        // After process death, no coroutine is running — reset active states to IDLE
        // so the user must deliberately press play to restart.
        val restored = ExerciseState.entries.firstOrNull { it.name == _stateRaw.value }
            ?: ExerciseState.IDLE
        if (restored != ExerciseState.IDLE && restored != ExerciseState.DONE) {
            savedState[KEY_STATE] = ExerciseState.IDLE.name
            savedState[KEY_CURRENT_SET] = 0
            savedState[KEY_REMAINING_MS] = 0L
        }
    }

    private var exerciseJob: Job? = null
    private var phaseDeadline = 0L

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
            savedState[KEY_REMAINING_MS] = 0L
        }
        startExerciseLoop()
    }

    fun pause() {
        val remaining = (phaseDeadline - SystemClock.elapsedRealtime()).coerceAtLeast(0)
        savedState[KEY_REMAINING_MS] = remaining
        savedState[KEY_PAUSED_PHASE] = state.value.name
        setExerciseState(ExerciseState.PAUSED)
        metronome.stop()
        exerciseJob?.cancel()
        exerciseJob = null
    }

    fun reset() {
        pause()
        setExerciseState(ExerciseState.IDLE)
        savedState[KEY_CURRENT_SET] = 0
        savedState[KEY_REMAINING_MS] = 0L
    }

    private suspend fun delayTracked(ms: Long) {
        phaseDeadline = SystemClock.elapsedRealtime() + ms
        delay(ms)
    }

    private fun startExerciseLoop() {
        exerciseJob?.cancel()
        exerciseJob = viewModelScope.launch {
            val totalSets = sets.value
            val durationMs = duration.value * 1000L
            val gapMs = gap.value * 1000L
            val beatSec = beat.value

            var set = currentSet.value

            // When resuming from PAUSED, pick up where we left off
            val remainingMs = savedState.get<Long>(KEY_REMAINING_MS) ?: 0L
            val pausedPhase = savedState.get<String>(KEY_PAUSED_PHASE)?.let { name ->
                ExerciseState.entries.firstOrNull { it.name == name }
            }
            savedState[KEY_REMAINING_MS] = 0L

            if (remainingMs > 0 && pausedPhase == ExerciseState.EXERCISING) {
                // Resume mid-exercise: finish remaining time, then complete the set
                setExerciseState(ExerciseState.EXERCISING)
                if (beatSec > 0) metronome.start(beatSec.seconds)
                delayTracked(remainingMs)
                if (beatSec > 0) metronome.stop()

                set++
                savedState[KEY_CURRENT_SET] = set

                if (set >= totalSets) {
                    announcer.announce("Done")
                    setExerciseState(ExerciseState.DONE)
                    return@launch
                }
                announcer.announce("$set")
                setExerciseState(ExerciseState.GAP)
                delayTracked(gapMs)
            } else if (remainingMs > 0 && pausedPhase == ExerciseState.GAP) {
                // Resume mid-gap: finish remaining gap time
                setExerciseState(ExerciseState.GAP)
                delayTracked(remainingMs)
            } else if (set == 0) {
                // Initial countdown so user can get into position after pressing play
                announcer.announce("Get set")
                setExerciseState(ExerciseState.GAP)
                delayTracked(gapMs)
            }

            // Continue with remaining sets
            while (set < totalSets) {
                setExerciseState(ExerciseState.EXERCISING)
                if (beatSec > 0) metronome.start(beatSec.seconds)
                delayTracked(durationMs)
                if (beatSec > 0) metronome.stop()

                set++
                savedState[KEY_CURRENT_SET] = set

                if (set >= totalSets) {
                    announcer.announce("Done")
                    setExerciseState(ExerciseState.DONE)
                } else {
                    announcer.announce("$set")
                    setExerciseState(ExerciseState.GAP)
                    delayTracked(gapMs)
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
        private const val KEY_REMAINING_MS = "remainingMs"
        private const val KEY_PAUSED_PHASE = "pausedPhase"

        private const val DEFAULT_SETS = 3
        private const val DEFAULT_DURATION = 10
        private const val DEFAULT_GAP = 3
        private const val DEFAULT_BEAT = 1
    }
}
