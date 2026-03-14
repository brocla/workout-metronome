# CLAUDE.md

## Project Overview

Exercise Counter is a single-screen Android app that times exercise sets with a metronome and voice announcements. See `Project_Definition.md` for the full specification.

## Build & Run

```bash
# Build debug APK
./gradlew assembleDebug

# Install and run on connected device
./gradlew installDebug
```

minSdk 29 (Android 10). No emulator audio issues -- use a physical device for testing metronome and TTS.

## Architecture

Single-activity, single-screen app. No navigation, no dependency injection, no database.

```
MainActivity → ExerciseScreen (Compose) → ExerciseViewModel (AndroidViewModel)
                                           ├── MetronomeEngine (AudioTrack on Dispatchers.IO)
                                           └── VoiceAnnouncer (TextToSpeech)
```

**State machine:** `ExerciseState` enum with five states: IDLE → EXERCISING → GAP → (repeat) → DONE. PAUSED can be entered from EXERCISING or GAP.

**State persistence:** All mutable state lives in `SavedStateHandle` (survives config changes and process death). The exercise loop coroutine is NOT restored after process death -- the `init` block resets active states to IDLE.

## Key Design Decisions

- **Dark-only theme.** `forceDarkAllowed="false"` in manifest. `darkColorScheme()` with no overrides. Custom colors are top-level vals in `Color.kt`, not part of the Material color scheme.

- **Scope injection for MetronomeEngine.** Takes `CoroutineScope` as constructor parameter (receives `viewModelScope`). AudioTrack work runs on `Dispatchers.IO` via `scope.launch(Dispatchers.IO)`. The coroutine's `finally` block is the sole owner of `track.stop()` / `track.release()` -- `stop()` just cancels the job.

- **Pause preserves timer position.** `phaseDeadline` tracks when the current phase ends (via `SystemClock.elapsedRealtime()`). On pause, remaining milliseconds and the current phase are saved to `SavedStateHandle`. On resume, `startExerciseLoop()` finishes the remaining time before continuing the normal loop.

- **ScrollWheelPicker feedback loop prevention.** Two `LaunchedEffect`s (one for scroll → value, one for value → scroll) share a `lastScrolledValue` `mutableIntStateOf` to break the infinite loop. The comment in the code explains this in detail.

- **Adaptive landscape layout.** `BoxWithConstraints` chooses 3 or 5 visible picker items based on `maxHeight < 400.dp`. This avoids nested scrolling conflicts (LazyColumn inside a scrollable Column).

## Common Pitfalls

- **ANR from AudioTrack on main thread.** `viewModelScope` defaults to `Dispatchers.Main`. MetronomeEngine must use `Dispatchers.IO` in its `scope.launch` call. If you see an ANR on startup, check that the dispatcher is correct.

- **Missing `getValue` import for `by` delegation.** `derivedStateOf` with `by` requires `import androidx.compose.runtime.getValue`. The compiler error is not obvious -- it says the delegate accessor is missing.

- **`SharingStarted.WhileSubscribed` vs `Eagerly`.** The derived flows (`state`, `isRunning`) use `WhileSubscribed(5_000)`. If you read `state.value` in ViewModel code that runs before any UI subscriber exists, you may get the initial value (`IDLE`) instead of the actual value. The `init` block reads from `_stateRaw.value` (the raw `SavedStateHandle` flow) for exactly this reason.

- **ScrollWheelPicker fixed width.** The inner Box uses `.width(76.dp)`. Previous attempts to use `fillMaxWidth()` or `widthIn(min = 76.dp)` caused layout breakage (one picker consuming all width). The fixed width works with `Arrangement.SpaceEvenly` on the parent Row.

- **Process death does not restore the exercise loop.** This is intentional. The `init` block resets EXERCISING/GAP/PAUSED to IDLE. The user must press play again. DONE is preserved.

## File Reference

| File | Purpose |
|------|---------|
| `MainActivity.kt` | Single activity, sets up Compose with Scaffold and edge-to-edge |
| `ExerciseScreen.kt` | Main UI composable -- pickers, status text, play/pause/reset buttons |
| `ScrollWheelPicker.kt` | Reusable iOS-style vertical number wheel with snap scrolling |
| `ExerciseViewModel.kt` | State management, exercise loop coroutine, coordinates audio |
| `MetronomeEngine.kt` | Synthesized PCM metronome via AudioTrack (woodblock sound) |
| `VoiceAnnouncer.kt` | TextToSpeech wrapper for set count announcements |
| `Color.kt` | All custom colors (wheel, buttons) as top-level vals |
| `Theme.kt` | Dark-only Material 3 theme, no dynamic color |
| `Type.kt` | Default Material typography (unchanged from template) |

## Style

Code has been reviewed in the styles of Chris Banes, Zac Sweers, Gabor Varadi, and Jake Wharton. Follow existing conventions:

- `collectAsStateWithLifecycle()` for all StateFlow collection in composables
- `SavedStateHandle` for any new persisted state (not `mutableStateOf`)
- Safe enum deserialization with `entries.firstOrNull` and fallback
- Named constants in companion objects, private to the class
- No string resources (hardcoded English) -- this is a known tradeoff for a personal app
