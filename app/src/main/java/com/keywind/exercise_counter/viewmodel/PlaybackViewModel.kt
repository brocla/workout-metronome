package com.keywind.exercise_counter.viewmodel

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.keywind.exercise_counter.audio.MetronomeEngine
import com.keywind.exercise_counter.audio.VoiceAnnouncer
import com.keywind.exercise_counter.data.AppDatabase
import com.keywind.exercise_counter.data.Exercise
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

enum class PlaybackState {
    IDLE,
    WAITING_FOR_READY,
    EXERCISING,
    GAP,
    PAUSED,
    DONE,
}

class PlaybackViewModel(
    application: Application,
    private val savedState: SavedStateHandle,
) : AndroidViewModel(application) {

    private val dao = AppDatabase.getInstance(application).exerciseDao()
    private val metronome = MetronomeEngine(viewModelScope)
    private val announcer = VoiceAnnouncer(application)

    private var exercises: List<Exercise> = emptyList()
    private var exerciseJob: Job? = null
    private var phaseDeadline = 0L
    private var readyDeferred: CompletableDeferred<Unit>? = null

    private val _stateRaw: StateFlow<String> =
        savedState.getStateFlow(KEY_STATE, PlaybackState.IDLE.name)

    val state: StateFlow<PlaybackState> = _stateRaw
        .map { name ->
            PlaybackState.entries.firstOrNull { it.name == name } ?: PlaybackState.IDLE
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaybackState.IDLE)

    val isActive: StateFlow<Boolean> = state
        .map { it == PlaybackState.EXERCISING || it == PlaybackState.GAP || it == PlaybackState.WAITING_FOR_READY }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val currentExerciseIndex: StateFlow<Int> =
        savedState.getStateFlow(KEY_EXERCISE_INDEX, 0)

    val currentSet: StateFlow<Int> =
        savedState.getStateFlow(KEY_CURRENT_SET, 0)

    val totalExercises: StateFlow<Int> =
        savedState.getStateFlow(KEY_TOTAL_EXERCISES, 0)

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    init {
        // After process death, exercises list is empty and can't be restored.
        // Reset to IDLE so user navigates back and taps play again.
        val restored = PlaybackState.entries.firstOrNull { it.name == _stateRaw.value }
            ?: PlaybackState.IDLE
        if (restored != PlaybackState.IDLE && restored != PlaybackState.DONE) {
            savedState[KEY_STATE] = PlaybackState.IDLE.name
        }

        if (restored == PlaybackState.IDLE) {
            viewModelScope.launch {
                exercises = dao.getAll().first().filter { it.enabled }
                savedState[KEY_TOTAL_EXERCISES] = exercises.size
                _loaded.value = true
                if (exercises.isNotEmpty()) {
                    savedState[KEY_EXERCISE_INDEX] = 0
                    savedState[KEY_CURRENT_SET] = 0
                    startPlaybackLoop()
                }
            }
        } else {
            _loaded.value = true
        }
    }

    fun currentExercise(): Exercise? =
        exercises.getOrNull(currentExerciseIndex.value)

    private fun setState(newState: PlaybackState) {
        savedState[KEY_STATE] = newState.name
    }

    private suspend fun delayTracked(ms: Long) {
        phaseDeadline = SystemClock.elapsedRealtime() + ms
        delay(ms)
    }

    fun onReady() {
        readyDeferred?.complete(Unit)
    }

    fun pause() {
        val currentState = state.value
        if (currentState == PlaybackState.WAITING_FOR_READY) {
            savedState[KEY_REMAINING_MS] = 0L
            savedState[KEY_PAUSED_PHASE] = currentState.name
            readyDeferred?.cancel()
            readyDeferred = null
        } else if (currentState == PlaybackState.EXERCISING || currentState == PlaybackState.GAP) {
            val remaining = (phaseDeadline - SystemClock.elapsedRealtime()).coerceAtLeast(0)
            savedState[KEY_REMAINING_MS] = remaining
            savedState[KEY_PAUSED_PHASE] = currentState.name
        } else {
            return
        }
        setState(PlaybackState.PAUSED)
        metronome.stop()
        exerciseJob?.cancel()
        exerciseJob = null
    }

    fun resume() {
        if (state.value != PlaybackState.PAUSED) return
        startPlaybackLoop()
    }

    fun skipNext() {
        exerciseJob?.cancel()
        exerciseJob = null
        metronome.stop()
        readyDeferred?.cancel()
        readyDeferred = null

        val nextIndex = currentExerciseIndex.value + 1
        if (nextIndex >= exercises.size) {
            announcer.announce("Routine complete")
            setState(PlaybackState.DONE)
            return
        }
        savedState[KEY_EXERCISE_INDEX] = nextIndex
        savedState[KEY_CURRENT_SET] = 0
        savedState[KEY_REMAINING_MS] = 0L
        startPlaybackLoop()
    }

    fun stop() {
        exerciseJob?.cancel()
        exerciseJob = null
        metronome.stop()
        readyDeferred?.cancel()
        readyDeferred = null
        setState(PlaybackState.IDLE)
    }

    private fun startPlaybackLoop() {
        exerciseJob?.cancel()
        exerciseJob = viewModelScope.launch {
            var index = currentExerciseIndex.value
            var set = currentSet.value

            // Handle resume from paused state
            val remainingMs = savedState.get<Long>(KEY_REMAINING_MS) ?: 0L
            val pausedPhase = savedState.get<String>(KEY_PAUSED_PHASE)?.let { name ->
                PlaybackState.entries.firstOrNull { it.name == name }
            }
            savedState[KEY_REMAINING_MS] = 0L

            if (pausedPhase == PlaybackState.WAITING_FOR_READY) {
                // Resume into waiting for ready
                val exercise = exercises.getOrNull(index) ?: return@launch
                setState(PlaybackState.WAITING_FOR_READY)
                readyDeferred = CompletableDeferred()
                readyDeferred?.await()
                readyDeferred = null
                // Fall through to exercise sets
            } else if (remainingMs > 0 && pausedPhase == PlaybackState.EXERCISING) {
                val exercise = exercises.getOrNull(index) ?: return@launch
                setState(PlaybackState.EXERCISING)
                if (exercise.beat > 0) metronome.start(exercise.beat.seconds)
                delayTracked(remainingMs)
                if (exercise.beat > 0) metronome.stop()

                set++
                savedState[KEY_CURRENT_SET] = set

                if (set >= exercise.sets) {
                    // This exercise is done, move to next
                    index++
                    savedState[KEY_EXERCISE_INDEX] = index
                    set = 0
                    savedState[KEY_CURRENT_SET] = 0
                } else {
                    announcer.announce("$set")
                    setState(PlaybackState.GAP)
                    delayTracked(exercise.gap * 1000L)
                }
            } else if (remainingMs > 0 && pausedPhase == PlaybackState.GAP) {
                setState(PlaybackState.GAP)
                delayTracked(remainingMs)

                // Gap finished, continue with next set below
            }

            // Main playback loop
            while (index < exercises.size) {
                val exercise = exercises[index]
                savedState[KEY_EXERCISE_INDEX] = index

                // Wait for ready (unless resuming mid-exercise)
                if (set == 0 && pausedPhase != PlaybackState.WAITING_FOR_READY) {
                    announcer.announce(exercise.name)
                    setState(PlaybackState.WAITING_FOR_READY)
                    readyDeferred = CompletableDeferred()
                    readyDeferred?.await()
                    readyDeferred = null
                }

                // Exercise sets
                while (set < exercise.sets) {
                    setState(PlaybackState.EXERCISING)
                    if (exercise.beat > 0) metronome.start(exercise.beat.seconds)
                    delayTracked(exercise.duration * 1000L)
                    if (exercise.beat > 0) metronome.stop()

                    set++
                    savedState[KEY_CURRENT_SET] = set

                    val isLastSet = set >= exercise.sets
                    val isLastExercise = index >= exercises.size - 1

                    if (isLastSet && isLastExercise) {
                        announcer.announce("Routine complete")
                    } else if (isLastSet) {
                        // Exercise done, will announce next exercise name at top of loop
                    } else {
                        announcer.announce("$set")
                        setState(PlaybackState.GAP)
                        delayTracked(exercise.gap * 1000L)
                    }
                }

                index++
                set = 0
                savedState[KEY_CURRENT_SET] = 0
            }

            savedState[KEY_EXERCISE_INDEX] = exercises.size - 1
            setState(PlaybackState.DONE)
        }
    }

    override fun onCleared() {
        super.onCleared()
        metronome.stop()
        announcer.shutdown()
        exerciseJob?.cancel()
    }

    companion object {
        private const val KEY_STATE = "playbackState"
        private const val KEY_EXERCISE_INDEX = "exerciseIndex"
        private const val KEY_CURRENT_SET = "currentSet"
        private const val KEY_TOTAL_EXERCISES = "totalExercises"
        private const val KEY_REMAINING_MS = "remainingMs"
        private const val KEY_PAUSED_PHASE = "pausedPhase"
    }
}
