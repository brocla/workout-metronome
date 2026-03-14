# Exercise Counter

An exercise timer that provides audible counting for timed sets. The user configures sets, duration, rest gap, and metronome beat interval, then presses play. The app plays a metronome tick during each set, announces the completed set count aloud via text-to-speech, pauses for the rest gap, and repeats until all sets are done.

## Example

3 sets, 10-second duration, 3-second rest gap, 1-second beat interval:

1. Metronome ticks every 1 second for 10 seconds.
2. Metronome stops. TTS announces "1". Status shows "Rest -- 1 of 3 complete".
3. 3-second rest gap (silence).
4. Metronome ticks for another 10 seconds.
5. TTS announces "2". Rest gap.
6. Metronome ticks for another 10 seconds.
7. TTS announces "Done". State becomes DONE.

Setting BEAT to 0 disables the metronome (silent timing).

## Stack

- Android, Kotlin, Jetpack Compose
- Single-activity architecture (`ComponentActivity`)
- Material 3 with dark-only theme (no dynamic color, `forceDarkAllowed="false"`)
- Edge-to-edge enabled
- minSdk 29, targetSdk 36
- Gradle version catalog (`libs.versions.toml`)

## Package Structure

```
com.keywind.exercise_counter
  MainActivity.kt
  audio/
    MetronomeEngine.kt
    VoiceAnnouncer.kt
  ui/
    ExerciseScreen.kt
    ScrollWheelPicker.kt
    theme/
      Color.kt
      Theme.kt
      Type.kt
  viewmodel/
    ExerciseViewModel.kt
```

## Screen Layout

A single screen with all content vertically centered. Uses `BoxWithConstraints` to adapt between portrait (5 visible picker items) and landscape (3 visible items) when `maxHeight < 400.dp`.

### Scroll Wheel Pickers

Four pickers in a horizontal `Row` with `Arrangement.SpaceEvenly`:

| Picker | Label | Range  | Default |
|--------|-------|--------|---------|
| SETS   | SETS  | 1..20  | 3       |
| WORK   | WORK  | 1..99  | 10      |
| REST   | REST  | 1..30  | 3       |
| BEAT   | BEAT  | 0..9   | 1       |

SETS is the number of exercise repetitions. WORK is duration in seconds per set. REST is the gap between sets in seconds. BEAT is the metronome interval in seconds (0 = off).

Pickers are disabled (visually dimmed, scroll blocked) while exercising, in a gap, or paused. They are only interactive in IDLE and DONE states.

### Status Text

Below the pickers, a single line of `titleMedium` text:

- IDLE: "Ready"
- EXERCISING: "Set {current+1} of {total}"
- GAP: "Rest -- {completed} of {total} complete"
- PAUSED: "Paused"
- DONE: "Done!"

### Control Buttons

Two 64dp circular `FilledIconButton`s in a horizontal row, spaced 24dp apart:

- **Play/Pause** (green `#4CAF50`, white icon): Shows play arrow when stopped/paused, pause icon when running. Toggles between `play()` and `pause()`.
- **Reset** (red `#FF1744`, white icon): Stop icon. Disabled in IDLE and DONE states. Calls `reset()`.

## ScrollWheelPicker Component

A reusable composable that renders an iOS-style vertical number wheel.

**Parameters:** `range: IntRange`, `selectedValue: Int`, `onValueChange: (Int) -> Unit`, `label: String`, `modifier: Modifier`, `enabled: Boolean`, `visibleItems: Int`.

**Visual structure** (layered inside a `Box`):
1. Light gray `Surface` (`#D0D0D0`) with sharp corners (`RectangleShape`), 1dp border (`#999999`).
2. Fixed width 76dp, height = `itemHeightDp (48dp) * visibleItems`.
3. Selection highlight: white at 70% opacity (`#B3FFFFFF`) behind the center row.
4. `LazyColumn` with `SnapFlingBehavior` for snapping scroll. Buffer items (empty boxes) above and below the data items so the first/last values can reach the center.
5. Horizontal dividers above and below the center row (`#4D000000`, black at 30%).
6. Top and bottom gradient overlays (`#73000000` to transparent) for a 3D cylinder effect.
7. Label text below the surface in white, `labelMedium` style, 8dp top padding.

**Number styling:** Center item is 28sp bold, adjacent items 20sp normal at 0.7 alpha, distant items at 0.35 alpha. When disabled, all alpha values halved. Numbers are royal blue (`#4169E1`).

**Scroll coordination:** Two `LaunchedEffect`s manage bidirectional sync between scroll position and `selectedValue`. A `lastScrolledValue` `mutableIntStateOf` breaks the feedback loop: user scroll updates the value, programmatic value changes scroll the list, and each path records its value so the other path ignores the redundant change.

**Center tracking:** `centerDataIndex` uses `derivedStateOf` outside the items lambda to efficiently track which item is centered.

## State Management

### ExerciseState Enum

```
IDLE, EXERCISING, GAP, PAUSED, DONE
```

### ExerciseViewModel

`AndroidViewModel` with `SavedStateHandle` for state persistence across config changes and process death.

**Persisted in SavedStateHandle:** sets, duration, gap, beat (picker values), currentSet, exerciseState (as String name), remainingMs (Long), pausedPhase (String name).

**Derived StateFlows:**
- `state: StateFlow<ExerciseState>` -- mapped from the raw String with safe deserialization (`entries.firstOrNull`, fallback to IDLE).
- `isRunning: StateFlow<Boolean>` -- true when EXERCISING or GAP.
- Both use `SharingStarted.WhileSubscribed(5_000)`.

**Init block:** After process death, if the restored state is EXERCISING, GAP, or PAUSED, reset to IDLE (no coroutine is running to back it). DONE is preserved.

**Pause/resume with timer preservation:** `pause()` calculates remaining milliseconds in the current phase using `SystemClock.elapsedRealtime()` against a tracked `phaseDeadline`, saves it and the current phase name. `startExerciseLoop()` checks for saved remaining time and resumes mid-phase if present, then continues the normal loop for remaining sets.

**Exercise loop:** Runs in `viewModelScope.launch`. Snapshots all picker values at launch time. For each set: sets state to EXERCISING, starts metronome if beat > 0, delays for duration, stops metronome, increments set, announces via TTS, sets state to GAP, delays for gap. After all sets: announces "Done", sets state to DONE.

**Cleanup:** `onCleared()` calls `pause()` (stops metronome, cancels job) and `announcer.shutdown()`.

## Audio

### MetronomeEngine

Takes a `CoroutineScope` (receives `viewModelScope`). Two public methods: `start(beatInterval: Duration)` and `stop()`.

`start()` cancels any existing job, then launches on `Dispatchers.IO`:
1. Generates a tick sound (`generateTick`).
2. Builds a beat buffer: tick samples followed by silence to fill the beat interval.
3. Creates an `AudioTrack` (44100 Hz, mono, 16-bit PCM, streaming mode, `USAGE_MEDIA` / `CONTENT_TYPE_SONIFICATION`).
4. Calls `track.play()`, then writes the beat buffer in a loop while `isActive`.
5. `finally` block calls `track.stop()` and `track.release()` -- sole owner of teardown.

`stop()` simply cancels the job. The coroutine's `finally` handles all AudioTrack cleanup.

**Tick synthesis:** A 40ms hollow woodblock sound. Three sine partials (500 Hz fundamental, 700 Hz, 1100 Hz) with exponential decay (rate 145), plus noise burst with fast decay (rate 1800). Amplitude 0.75. Seeded `Random(42)` for deterministic noise.

### VoiceAnnouncer

Wraps Android `TextToSpeech`. Created with `applicationContext` to avoid activity leaks. Implements `OnInitListener` -- sets locale to `Locale.US`, pitch 0.85, speech rate 0.9 on success. Logs a warning on failure.

`announce(text)` calls `tts.speak` with `QUEUE_FLUSH` and a unique utterance ID. Silently no-ops if TTS isn't ready.

`shutdown()` stops and shuts down the TTS engine.

## Theme

Dark-only. `ExerciseCounterTheme` applies `darkColorScheme()` with no color overrides (Material 3 defaults) and default `Typography`. `android:forceDarkAllowed="false"` in the manifest prevents the system from applying Force Dark.

### Colors (Color.kt)

All custom colors are top-level vals, not part of the Material color scheme:

- Wheel: surface `#D0D0D0`, border `#999999`, numbers `#4169E1`, highlight `#B3FFFFFF`, divider `#4D000000`, gradient edge `#73000000`
- Buttons: play `#4CAF50`, stop `#FF1744`

## Dependencies

Managed via Gradle version catalog. Key dependencies beyond the standard Compose/Material 3 set:

- `lifecycle-runtime-compose` -- for `collectAsStateWithLifecycle`
- `lifecycle-viewmodel-compose` -- for `viewModel()` in composables
- `compose-material-icons-extended` -- for Pause, PlayArrow, Stop icons

## Intended Audience

Personal use for myself, family, and friends. Not intended for Google Play publication -- no privacy policy, monetization, or user data collection. Code is published on GitHub as open source. No API keys or secrets in the codebase.
