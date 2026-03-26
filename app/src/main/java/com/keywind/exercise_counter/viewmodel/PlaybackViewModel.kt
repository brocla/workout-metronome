package com.keywind.exercise_counter.viewmodel

import android.app.Application
import android.os.SystemClock
import android.speech.SpeechRecognizer
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.keywind.exercise_counter.audio.MetronomeEngine
import com.keywind.exercise_counter.audio.SpeechRecognitionHelper
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
    private val announcer = VoiceAnnouncer.getInstance(application)
    private val speechHelper = SpeechRecognitionHelper(application, ::onReady)

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

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

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

    fun startVoiceRecognition() {
        _isListening.value = speechHelper.startListening()
    }

    fun stopVoiceRecognition() {
        speechHelper.stopListening()
        _isListening.value = false
    }

    fun pause() {
        when (val currentState = state.value) {
            PlaybackState.WAITING_FOR_READY -> {
                savedState[KEY_REMAINING_MS] = 0L
                savedState[KEY_PAUSED_PHASE] = currentState.name
                readyDeferred?.cancel()
                readyDeferred = null
            }
            PlaybackState.EXERCISING, PlaybackState.GAP -> {
                val remaining = (phaseDeadline - SystemClock.elapsedRealtime()).coerceAtLeast(0)
                savedState[KEY_REMAINING_MS] = remaining
                savedState[KEY_PAUSED_PHASE] = currentState.name
            }
            else -> return
        }
        setState(PlaybackState.PAUSED)
        metronome.stop()
        stopVoiceRecognition()
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
        stopVoiceRecognition()
        readyDeferred?.cancel()
        readyDeferred = null

        val nextIndex = currentExerciseIndex.value + 1
        if (nextIndex >= exercises.size) {
            viewModelScope.launch { announcer.announce("Routine complete") }
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
        stopVoiceRecognition()
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
                exercises.getOrNull(index) ?: return@launch
                setState(PlaybackState.WAITING_FOR_READY)
                readyDeferred = CompletableDeferred()
                readyDeferred?.await()
                readyDeferred = null
                stopVoiceRecognition()
                // Fall through to exercise sets
            } else if (remainingMs > 0 && pausedPhase == PlaybackState.EXERCISING) {
                val exercise = exercises.getOrNull(index) ?: return@launch
                setState(PlaybackState.EXERCISING)
                if (exercise.beat > 0) {
                    val maxTicks = (remainingMs / (exercise.beat * 1000L)).toInt()
                    metronome.start(exercise.beat.seconds, maxTicks = maxTicks)
                }
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
                    announcer.announce(exerciseAnnouncement(exercise, SpeechRecognizer.isRecognitionAvailable(getApplication())))
                    setState(PlaybackState.WAITING_FOR_READY)
                    readyDeferred = CompletableDeferred()
                    readyDeferred?.await()
                    readyDeferred = null
                    stopVoiceRecognition()
                }

                // Exercise sets
                while (set < exercise.sets) {
                    setState(PlaybackState.EXERCISING)
                    if (exercise.beat > 0) {
                        val maxTicks = exercise.duration / exercise.beat
                        metronome.start(exercise.beat.seconds, maxTicks = maxTicks)
                    }
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

    internal fun exerciseAnnouncement(exercise: Exercise, voiceAvailable: Boolean): String {
        val setWord = if (exercise.sets == 1) "set" else "sets"
        val readyPrompt = if (voiceAvailable) "Say ready." else "Tap ready."
        return "${exercise.name} exercise. ${exercise.sets} $setWord of ${exercise.duration}, " +
            "with ${exercise.gap} second gaps. $readyPrompt"
    }

    override fun onCleared() {
        super.onCleared()
        metronome.stop()
        announcer.stop()
        speechHelper.destroy()
        exerciseJob?.cancel()
    }

    companion object {
        private const val KEY_STATE = "playbackState"
        private const val KEY_EXERCISE_INDEX = "exerciseIndex"
        private const val KEY_CURRENT_SET = "currentSet"
        private const val KEY_TOTAL_EXERCISES = "totalExercises"
        private const val KEY_REMAINING_MS = "remainingMs"
        private const val KEY_PAUSED_PHASE = "pausedPhase"

        /**
         * Maps a serialized [PlaybackState] name to the state that should be active after process death.
         * Any active state (EXERCISING, GAP, PAUSED, WAITING_FOR_READY) resets to IDLE because the
         * exercise list cannot be restored after process death. IDLE and DONE are preserved.
         * Unknown strings fall back to IDLE.
         */
        internal fun recoverState(serialized: String): PlaybackState {
            val state = PlaybackState.entries.firstOrNull { it.name == serialized } ?: PlaybackState.IDLE
            return if (state == PlaybackState.IDLE || state == PlaybackState.DONE) state else PlaybackState.IDLE
        }
    }
}
