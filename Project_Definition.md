# Exercise Counter — Project Definition

A personal Android workout timer. The user builds a routine — an ordered list of exercises — and the app runs through them automatically: counting the beat with a metronome, announcing set completions aloud, and waiting for the user to say they're ready before each exercise starts so they can reposition hands-free.

## Product Overview

### Routine screen

The home screen. Shows an ordered list of exercises. Each exercise has a checkbox to include or exclude it from the next playback without deleting it. Exercises can be reordered by drag. Tapping an exercise opens the editor. The Play button is disabled unless at least one exercise is checked. A FAB opens the editor for a new exercise.

The database is prepopulated with a single "Example Exercise" on first launch.

### Exercise editor screen

A name text field plus four scroll-wheel pickers (SETS, WORK, REST, BEAT). Used for both create and edit. The top bar has a back arrow (cancel) and a Save button. Save is disabled while the name is blank. The title reads "New Exercise" or "Edit Exercise" depending on context.

### Playback screen

Runs the routine. No navigation chrome — full-screen content centered vertically. The screen stays on during playback (`view.keepScreenOn`).

**Flow for each exercise:**
1. Announce the exercise name, set count, duration, and gap via TTS.
2. Enter `WAITING_FOR_READY` — show the Ready button and start listening for a voice trigger.
3. On ready (tap or voice): start the exercise sets.
4. For each set: tick the metronome for the work duration, stop, announce the completed set count, wait out the rest gap.
5. After the last set, move to the next exercise (back to step 1), or announce "Routine complete" and enter `DONE`.

**Controls during playback:**
- **Pause/Resume** (green, 64dp): Pauses mid-interval, preserving remaining time. Disabled in `WAITING_FOR_READY` (nothing to pause). Shows play arrow when paused, pause icon otherwise.
- **Skip ⏭** (secondary color, 64dp): Cancels the current exercise (including any sets remaining or the ready wait) and advances immediately to the next exercise's ready prompt. Always available until `DONE`.
- **Ready** (green button, visible only in `WAITING_FOR_READY`): Tap fallback for voice activation.
- **Done** (green button, replaces controls in `DONE`): Returns to the routine screen.

Pressing Back during playback calls `stop()` (cancels the job, resets to IDLE) then navigates back.

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

Three screens, three ViewModels, one database, no dependency injection. Each ViewModel is an `AndroidViewModel` obtained via `viewModel()` from the NavBackStackEntry — a fresh instance per navigation destination visit.

## Data Layer

### Exercise entity

```kotlin
@Entity(tableName = "exercises")
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sets: Int,        // 1..20
    val duration: Int,    // 1..99 seconds per set
    val gap: Int,         // 1..30 seconds between sets
    val beat: Int,        // 0..9 seconds between ticks (0 = metronome off)
    val enabled: Boolean = true,
    val sortOrder: Int = 0,
)
```

### ExerciseDao

| Method | Notes |
|--------|-------|
| `getAll(): Flow<List<Exercise>>` | Ordered by `sortOrder ASC` |
| `getById(id): Exercise?` | Nullable; used by editor to load for editing |
| `insert(exercise): Long` | Returns generated id |
| `update(exercise)` | Full row replace |
| `delete(exercise)` | |
| `updateSortOrder(id, sortOrder)` | Called in batch after drag-to-reorder |
| `getNextSortOrder(): Int` | `COALESCE(MAX(sortOrder), -1) + 1` — returns 0 on empty table |

### AppDatabase

Room singleton with double-checked locking (`@Volatile instance`, `synchronized`). A `PrepopulateCallback` inserts the example exercise the first time the database file is created.

## ViewModels

### RoutineViewModel

Exposes `exercises: StateFlow<List<Exercise>>` from `dao.getAll().stateIn(...)`. Provides `deleteExercise`, `toggleEnabled`, and `moveExercise(from, to)`.

`moveExercise` delegates to the pure top-level function `reorderExercises(list, from, to)` (for testability), then persists the new order by calling `dao.updateSortOrder` for each item in a batch.

Pre-warms TTS in `init` by calling `VoiceAnnouncer.getInstance(application)` so the engine is ready before the user taps Play.

### ExerciseEditorViewModel

Holds all form state in `SavedStateHandle` (name, sets, duration, gap, beat, loaded flag). The `loaded` flag prevents re-fetching on config change — the exercise is loaded from the DAO exactly once into `SavedStateHandle`, after which it survives rotation natively.

`exerciseId` is read from `SavedStateHandle["id"]` — populated by the `exercise/{id}` route argument, absent for new exercises. `isEditing: Boolean` is derived from its presence.

`save()` trims the name and returns early if blank. Dispatches create or update to the DAO on `viewModelScope`, calls `onComplete()` when done.

Default values: SETS = 3, WORK = 10, REST = 3, BEAT = 1.

### PlaybackViewModel

The most complex class. Manages the exercise loop coroutine, coordinates audio, and exposes state to the playback screen.

#### PlaybackState

```
IDLE → WAITING_FOR_READY → EXERCISING ↔ GAP → (next exercise or DONE)
                    ↑                ↓
                 PAUSED ←───────────┘
```

Six states:

| State | Meaning |
|-------|---------|
| `IDLE` | No playback. Starting state and post-`stop()` state. |
| `WAITING_FOR_READY` | Announced the exercise; mic open; waiting for voice or tap. |
| `EXERCISING` | Metronome running; timing the work interval. |
| `GAP` | Between sets; silently timing the rest gap. |
| `PAUSED` | Suspended mid-interval; remaining time saved. |
| `DONE` | All exercises complete. Preserved across process death. |

All state is persisted in `SavedStateHandle`. The state itself is stored as its `.name` string and deserialized with `entries.firstOrNull { it.name == raw } ?: IDLE`.

`isActive` is true for `WAITING_FOR_READY`, `EXERCISING`, and `GAP` — used to control `keepScreenOn`.

#### Process death recovery

After process death the exercises list is empty and cannot be restored. The `init` block calls `recoverState(rawString)` (testable companion function): any active state (`WAITING_FOR_READY`, `EXERCISING`, `GAP`, `PAUSED`) resets to `IDLE`; `DONE` is preserved. If the recovered state is `IDLE`, exercises are loaded from the DAO and playback starts fresh. Otherwise `_loaded` is set true and the screen shows the preserved DONE state.

#### Pause/resume with timer preservation

`pause()` records `(phaseDeadline - SystemClock.elapsedRealtime()).coerceAtLeast(0)` as `remainingMs` and the current state name as `pausedPhase` in `SavedStateHandle`. Special case: pausing from `WAITING_FOR_READY` stores `remainingMs = 0` and cancels `readyDeferred`.

`resume()` calls `startPlaybackLoop()`. The loop reads the saved `remainingMs` and `pausedPhase` at startup and handles three resume cases before falling through to the main loop:

- `pausedPhase == WAITING_FOR_READY` → re-enter the ready wait for the current exercise
- `remainingMs > 0 && pausedPhase == EXERCISING` → complete the remaining work interval, increment set
- `remainingMs > 0 && pausedPhase == GAP` → complete the remaining rest gap

#### Voice activation

When entering `WAITING_FOR_READY`, `PlaybackScreen` checks for `RECORD_AUDIO` permission and either calls `viewModel.startVoiceRecognition()` or launches the permission request (which calls it on grant).

`SpeechRecognitionHelper` opens a `SpeechRecognizer` and listens for go-words in both `onResults` and `onPartialResults`. On match it calls `onReady()`, which completes `readyDeferred` in the ViewModel, unblocking the exercise loop. On no match, it restarts itself. Soft errors (no match, timeout, busy, server disconnect) restart; hard errors set `listening = false`.

Go-words (case-insensitive substring match): `"ready"`, `"go"`, `"next"`, `"okay"`, `"ok"`, `"when"`.

#### Skip

`skipNext()` cancels the current job, stops audio, cancels `readyDeferred`, then either advances to the next exercise (resets index and set, calls `startPlaybackLoop()`) or announces "Routine complete" and sets `DONE` if already on the last exercise.

## Audio

### MetronomeEngine

Takes a `CoroutineScope` (receives `viewModelScope`). Launched on `Dispatchers.IO` to avoid ANR. `stop()` cancels the job; the `finally` block is the sole owner of `track.stop()` / `track.release()`.

**Tick synthesis** (`generateTick`, internal visibility for testing): 40ms hollow woodblock. Three sine partials — 500 Hz fundamental, 700 Hz (ratio 1.4), 1100 Hz (ratio 2.2) — with exponential decay (rate 145). Plus a noise burst at rate 1800. Amplitude 0.75. Fixed seed `Random(42)` for deterministic output. All samples clipped to `Short` range via `coerceIn`.

**Beat buffer**: tick samples followed by silence to fill the beat interval. Silence is `(totalBeatSamples - tickSamples.size).coerceAtLeast(0)` — coerceAtLeast guards against BEAT=0 (metronome disabled, so `start()` is never called) and edge cases where the tick is longer than the interval.

### VoiceAnnouncer

Singleton (`@Volatile` + `synchronized`). Wraps Android `TextToSpeech`. Initialization is async — a `CompletableDeferred<Boolean>` blocks `announce()` until init completes (or fails). Pitch 0.85, speech rate 0.9, `Locale.US`.

`announce(text)` suspends until the utterance finishes (via `UtteranceProgressListener`) or errors, using a per-call deferred. Uses `QUEUE_FLUSH` so a new announcement always interrupts any prior one.

### SpeechRecognitionHelper

Created per-ViewModel (not a singleton). Manages a single `SpeechRecognizer` instance. `startListening()` returns `false` immediately if `isRecognitionAvailable` is false (no crash, no-op). `stopListening()` is idempotent. `restartListening()` is a no-op when `listening == false` — safe to call from error callbacks.

The go-word matching logic is extracted to `companion object fun containsGoWord(results: List<String>): Boolean` for testability.

## ScrollWheelPicker

Reusable iOS-style vertical number wheel. Used in `ExerciseEditorScreen`.

**Parameters:** `range: IntRange`, `selectedValue: Int`, `onValueChange: (Int) -> Unit`, `label: String`, `modifier: Modifier`, `enabled: Boolean`, `visibleItems: Int`.

**Visual structure** (layered `Box`):
1. Light gray `Surface` (`WheelSurface = #D0D0D0`), sharp corners, 1dp border (`WheelBorder = #999999`).
2. Fixed width **76dp**. Height = `48dp × visibleItems`. (Fixed width prevents one picker from consuming all available space in `Arrangement.SpaceEvenly`. Do not replace with `fillMaxWidth`.)
3. White highlight at 70% opacity (`WheelHighlight = #B3FFFFFF`) behind the center row.
4. `LazyColumn` with `SnapFlingBehavior`. Buffer items above/below allow first/last values to center.
5. Dividers above and below center row (`WheelDivider = #4D000000`).
6. Top/bottom gradient overlays (`WheelGradientEdge = #73000000` → transparent) for 3D depth.
7. Label text (`labelMedium`, white) below the surface, 8dp top padding.

**Number styling:** Center item 28sp bold; adjacent items 20sp at 0.7 alpha; further items at 0.35 alpha. All alphas halved when `enabled = false`. Numbers in `WheelNumber = #4169E1` (Royal Blue).

**Bidirectional scroll sync:** Two `LaunchedEffect`s manage scroll ↔ value sync. A shared `lastScrolledValue: MutableIntState` breaks the feedback loop: user scroll updates the value and records it; the value-to-scroll effect skips scrolling if the value matches `lastScrolledValue` (it just came from a scroll, not an external set).

**Landscape adaptation:** `BoxWithConstraints` selects `visibleItems = 3` when `maxHeight < 400.dp`, otherwise 5. This prevents nested scroll conflicts (LazyColumn inside a scrollable Column).

## Navigation

```
NavHost(startDestination = "routine")
  "routine"          → RoutineScreen
  "exercise/new"     → ExerciseEditorScreen  (no argument)
  "exercise/{id}"    → ExerciseEditorScreen  (id: Long via navArgument)
  "playback"         → PlaybackScreen
```

`NavBackStackEntry`-scoped ViewModels — each `viewModel()` call gets the ViewModel for that entry. `PlaybackViewModel` loads exercises from the DAO on init rather than accepting them as arguments, which keeps the nav API simple and avoids Parcelable serialization of a list.

## Theme

Dark-only. `ExerciseCounterTheme` wraps `MaterialTheme` with `darkColorScheme(onSurface = Color.White)`. No dynamic color. `android:forceDarkAllowed="false"` in the manifest.

**Why `onSurface = Color.White`:** Screens that do not wrap their content in a `Scaffold` or `Surface` have no container providing `LocalContentColor`. Explicitly setting `onSurface` ensures `Text` composables with no `color` argument render in white rather than the Material 3 default tinted gray.

### Custom colors (`Color.kt`)

Top-level vals, not part of the Material color scheme:

```
WheelSurface      = #D0D0D0   (light gray wheel background)
WheelBorder       = #999999
WheelNumber       = #4169E1   (Royal Blue digits)
WheelHighlight    = #B3FFFFFF (center row highlight, 70% white)
WheelDivider      = #4D000000 (center dividers, 30% black)
WheelGradientEdge = #73000000 (top/bottom fade, 45% black)
PlayGreen         = #4CAF50
```

## Key Design Decisions

**No DI.** ViewModels acquire their dependencies directly (`AppDatabase.getInstance(application)`, `VoiceAnnouncer.getInstance(application)`). Kept simple for a personal app. The tradeoff is that ViewModel integration tests require a real device or emulator.

**Extracting pure logic for testability without DI.** Internal helper functions (`containsGoWord`, `reorderExercises`, `validateAndTrimName`, `recoverState`, `exerciseAnnouncement`, `statusText`, `generateTick`) are extracted from their classes so they can be tested in JVM unit tests without needing an Android context.

**`SavedStateHandle` for all persisted state.** No `mutableStateOf` in ViewModels. Everything that needs to survive config change or process death goes into `SavedStateHandle`. The `ExerciseEditorViewModel` uses a `loaded` flag to guard against re-fetching after rotation.

**`SharingStarted.WhileSubscribed(5_000)` on derived flows.** `state` and `isActive` in `PlaybackViewModel` use `WhileSubscribed`. Do not read `.value` on these flows in ViewModel code that runs before any UI subscriber exists — use `_stateRaw.value` (the raw `SavedStateHandle` flow, which is always active) instead.

**MetronomeEngine on `Dispatchers.IO`.** `viewModelScope` defaults to `Dispatchers.Main`. The `AudioTrack` write loop must run on `Dispatchers.IO` or it will ANR. The `finally` block is the sole teardown path — `stop()` only cancels the job.

**`readyDeferred` coordination.** The exercise loop awaits on a `CompletableDeferred<Unit>`. `onReady()` (called by voice detection or button tap) completes it. `pause()` from `WAITING_FOR_READY` cancels it. `skipNext()` also cancels it. All three paths null out the reference after acting on it.

**VoiceAnnouncer singleton.** TTS initialization is slow (~200–500ms). The singleton is created eagerly in `RoutineViewModel.init` so it's warm before the user navigates to playback. If created lazily in `PlaybackViewModel`, the first announcement may be delayed or missed.

## Common Pitfalls

- **ANR from AudioTrack on main thread.** If `MetronomeEngine` uses `viewModelScope` without `Dispatchers.IO`, `AudioTrack.write()` blocks the main thread.
- **Missing `getValue` import for `by` delegation.** `derivedStateOf { ... } by` requires `import androidx.compose.runtime.getValue`. The compiler error mentions a missing delegate accessor, not the missing import.
- **ScrollWheelPicker fixed width.** The 76dp width is load-bearing. `fillMaxWidth()` causes one picker to consume all available width in the parent `Arrangement.SpaceEvenly` row.
- **`getNextSortOrder` on empty table.** Uses `COALESCE(MAX(sortOrder), -1) + 1` to return 0 rather than null when the table is empty.
- **Process death does not restore the exercise loop.** The exercises list lives only in memory. After process death the ViewModel restores `DONE` but resets everything else to `IDLE`. The playback screen navigates back immediately if it finds `IDLE` with zero exercises.
- **`PlaybackScreen` needs a `Surface` wrapper.** Without `Scaffold` or `Surface`, there is no container setting `LocalContentColor`. `Text` composables with no explicit `color` will be invisible against the dark background unless the theme's `onSurface` is set to white.

## File Reference

| File | Purpose |
|------|---------|
| `MainActivity.kt` | Single activity; sets up NavHost with four routes |
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

## Stack

- Android, Kotlin, Jetpack Compose, Material 3
- minSdk 29 (Android 10), targetSdk 36
- Single activity, Jetpack Navigation Compose
- Room 2.7 with KSP
- `sh.calvin.reorderable` for drag-to-reorder
- `lifecycle-runtime-compose` for `collectAsStateWithLifecycle`
- Dark-only theme, `forceDarkAllowed="false"`, edge-to-edge
- No DI, no string resources, hardcoded English

## Intended Audience

Personal use. Not for Play Store publication — no privacy policy, no monetization, no user data collection. Open source on GitHub.
