# CLAUDE.md

## Project Overview

Exercise Counter is a multi-screen Android workout timer. The user builds a routine (an ordered list of exercises) and the app runs through them: counting the beat with a metronome, announcing set completions via TTS, and waiting for a voice or tap trigger before each exercise. See `Project_Definition.md` for the full specification.

## Build & Run

```bash
./gradlew assembleDebug      # Build debug APK
./gradlew installDebug        # Install on connected device
```

minSdk 29 (Android 10). Use a physical device for testing metronome and TTS.

## Architecture

```
MainActivity (single activity, NavHost)
├── "routine"        → RoutineScreen       ← RoutineViewModel
├── "exercise/new"   → ExerciseEditorScreen ← ExerciseEditorViewModel
├── "exercise/{id}"  → ExerciseEditorScreen ← ExerciseEditorViewModel
└── "playback"       → PlaybackScreen      ← PlaybackViewModel
                                              ├── MetronomeEngine
                                              ├── VoiceAnnouncer (singleton)
                                              └── SpeechRecognitionHelper
```

Three screens, three ViewModels, one Room database, no dependency injection. Each ViewModel is an `AndroidViewModel` obtained via `viewModel()` from the NavBackStackEntry.

### State machine (`PlaybackState`)

```
IDLE → WAITING_FOR_READY → EXERCISING ↔ GAP → (next exercise or DONE)
                ↑                ↓
             PAUSED ←───────────┘
```

Six states. All state is persisted in `SavedStateHandle` as `.name` strings, deserialized with `entries.firstOrNull { it.name == raw } ?: IDLE`.

### Data layer

- **Exercise** — Room entity: id, name, sets, duration, gap, beat, enabled, sortOrder.
- **ExerciseDao** — CRUD + `getNextSortOrder()` (`COALESCE(MAX(sortOrder), -1) + 1`).
- **AppDatabase** — Singleton with `PrepopulateCallback` inserting one example exercise on first launch.

## Key Design Decisions

- **No DI.** ViewModels acquire dependencies directly (`AppDatabase.getInstance(application)`, `VoiceAnnouncer.getInstance(application)`). Tradeoff: ViewModel integration tests need a real device.

- **Extracting pure logic for testability without DI.** Internal helper functions (`containsGoWord`, `reorderExercises`, `validateAndTrimName`, `recoverState`, `exerciseAnnouncement`, `statusText`, `generateTick`) are extracted so they can be tested in JVM unit tests without Android context.

- **`SavedStateHandle` for all persisted state.** No `mutableStateOf` in ViewModels. The `ExerciseEditorViewModel` uses a `loaded` flag to guard against re-fetching after rotation.

- **MetronomeEngine on `Dispatchers.IO`.** Takes `CoroutineScope` (receives `viewModelScope`). AudioTrack work runs on `Dispatchers.IO`. The coroutine's `finally` block is the sole owner of `track.stop()` / `track.release()` — `stop()` just cancels the job.

- **Pause preserves timer position.** `phaseDeadline` tracks when the current phase ends (via `SystemClock.elapsedRealtime()`). On pause, remaining ms and current phase are saved to `SavedStateHandle`. On resume, `startPlaybackLoop()` finishes remaining time before continuing.

- **`readyDeferred` coordination.** The exercise loop awaits a `CompletableDeferred<Unit>`. `onReady()` (voice or tap) completes it. `pause()` and `skipNext()` cancel it. All paths null out the reference after acting.

- **VoiceAnnouncer singleton pre-warming.** TTS init is slow (~200–500ms). Created eagerly in `RoutineViewModel.init` so it's warm before playback starts.

- **Dark-only theme with `onSurface = Color.White`.** Screens without `Scaffold`/`Surface` have no container providing `LocalContentColor`. Explicit `onSurface` ensures `Text` composables render white.

- **ScrollWheelPicker feedback loop prevention.** Two `LaunchedEffect`s (scroll → value, value → scroll) share a `lastScrolledValue` state to break the infinite loop.

- **Adaptive landscape layout.** `BoxWithConstraints` chooses 3 or 5 visible picker items based on `maxHeight < 400.dp`.

## Common Pitfalls

- **ANR from AudioTrack on main thread.** `viewModelScope` defaults to `Dispatchers.Main`. MetronomeEngine must use `Dispatchers.IO`. If you see an ANR, check the dispatcher.

- **Missing `getValue` import for `by` delegation.** `derivedStateOf` with `by` requires `import androidx.compose.runtime.getValue`. The compiler error says the delegate accessor is missing.

- **`SharingStarted.WhileSubscribed` vs `Eagerly`.** `state` and `isActive` flows use `WhileSubscribed(5_000)`. Do not read `.value` in ViewModel code that runs before any UI subscriber — use `_stateRaw.value` instead.

- **ScrollWheelPicker fixed width.** The 76dp width is load-bearing. `fillMaxWidth()` causes one picker to consume all available space in `Arrangement.SpaceEvenly`.

- **Process death does not restore the exercise loop.** The exercises list lives only in memory. `recoverState()` resets active states to `IDLE`; `DONE` is preserved. The playback screen navigates back if it finds `IDLE` with zero exercises.

- **`PlaybackScreen` needs a `Surface` wrapper.** Without it, `LocalContentColor` is not set and `Text` composables are invisible against the dark background.

- **`getNextSortOrder` on empty table.** Uses `COALESCE(MAX(sortOrder), -1) + 1` to return 0 rather than null.

## File Reference

| File | Purpose |
|------|---------|
| `MainActivity.kt` | Single activity; NavHost with four routes |
| `data/Exercise.kt` | Room entity; all exercise parameters |
| `data/ExerciseDao.kt` | DAO; CRUD + `getNextSortOrder` |
| `data/AppDatabase.kt` | Room singleton; `PrepopulateCallback` |
| `viewmodel/RoutineViewModel.kt` | Exercises list; delete, toggle, reorder |
| `viewmodel/ExerciseEditorViewModel.kt` | Form state; create/edit; blank-name guard |
| `viewmodel/PlaybackViewModel.kt` | Exercise loop; state machine; pause/resume; skip |
| `audio/MetronomeEngine.kt` | AudioTrack PCM metronome on Dispatchers.IO |
| `audio/VoiceAnnouncer.kt` | TTS singleton; suspending `announce()` |
| `audio/SpeechRecognitionHelper.kt` | SpeechRecognizer; go-word detection; error recovery |
| `ui/RoutineScreen.kt` | Exercise list; drag-to-reorder; enable/disable; play |
| `ui/ExerciseEditorScreen.kt` | Name field + four ScrollWheelPickers; create/edit |
| `ui/PlaybackScreen.kt` | Exercise progress; ready/skip/pause controls; keep-on |
| `ui/ScrollWheelPicker.kt` | Reusable iOS-style snap-scroll number wheel |
| `ui/theme/Color.kt` | All custom colors as top-level vals |
| `ui/theme/Theme.kt` | Dark-only Material 3 theme; `onSurface = White` |
| `ui/theme/Type.kt` | Default Material typography (unchanged) |

## Style

Code has been reviewed in the styles of Chris Banes, Zac Sweers, Gabor Varadi, and Jake Wharton. Follow existing conventions:

- `collectAsStateWithLifecycle()` for all StateFlow collection in composables
- `SavedStateHandle` for any new persisted state (not `mutableStateOf`)
- Safe enum deserialization with `entries.firstOrNull` and fallback
- Named constants in companion objects, private to the class
- No string resources (hardcoded English) — known tradeoff for a personal app

## Stack

- Android, Kotlin, Jetpack Compose, Material 3
- minSdk 29 (Android 10), targetSdk 36
- Single activity, Jetpack Navigation Compose
- Room 2.7 with KSP
- `sh.calvin.reorderable` for drag-to-reorder
- `lifecycle-runtime-compose` for `collectAsStateWithLifecycle`
- Dark-only theme, `forceDarkAllowed="false"`, edge-to-edge
- No DI, no string resources, hardcoded English
