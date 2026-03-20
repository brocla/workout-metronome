# Routine Feature Requirements

Extends the Exercise Counter app to support **routines** — ordered lists of exercises that play back-to-back without manual intervention.

## Overview

Today the app times a single exercise. The user adjusts pickers and presses play. To do a second exercise, they stop, change the pickers, and press play again. The routine feature removes that friction: the user builds a list of exercises once, then plays the whole list in sequence.

## Screens

The app grows from one screen to three:

### 1. Routine Screen (new home screen)

Displays a single routine as an ordered list of exercises. This is the screen the app opens to.

Each list item shows:
- **Checkbox** (left side) — enabled/disabled toggle. Unchecked exercises are skipped during playback but remain in the list.
- **Exercise name** — the user-assigned name (e.g., "Push-ups", "Plank").
- **Summary line** — compact display of settings: "3 sets / 10s work / 3s rest / beat 1s" or similar.
- **Drag handle** (right side) — for reordering.
- **Trash icon** — deletes the exercise from the routine.

Below the list:
- **Play button** — starts playback of all checked exercises in order. If no exercises are checked (or the list is empty), the play button does nothing.
- **Add button** — navigates to the Exercise Editor screen to create a new exercise.

Tapping an exercise item navigates to the Exercise Editor to edit it.

Duplicate exercise names are allowed — users may want to repeat an exercise later in the routine.

### 2. Exercise Editor Screen (replaces current exercise screen for editing)

Looks like the current single-exercise screen with these additions:
- **Exercise name text field** at the top — required, plain text, no character limit enforced in UI.
- **Save button** — validates that name is non-blank, saves to database, returns to Routine Screen.
- **Cancel button** — discards changes, returns to Routine Screen.

The four scroll wheel pickers (SETS, WORK, REST, BEAT) remain exactly as they are today.

When editing an existing exercise, the pickers and name are pre-populated.

### 3. Playback Screen (new)

Shown during routine playback. This is NOT the editor — the pickers are not shown. The playback screen shows:

- **Exercise name** — large, prominent.
- **Status text** — same as today ("Set 2 of 5", "Rest — 1 of 5 complete", etc.)
- **Overall progress** — "Exercise 2 of 6" or a progress bar.
- **Pause button** — pauses the current exercise. User can resume.
- **Skip/Next button** — jumps to the next exercise in the routine.
- **Back button** — stops playback and returns to the Routine Screen. No confirmation needed — if the user wanted to keep going, they would have used Pause instead.

## Transition Between Exercises

Between exercises during routine playback, the app pauses and waits for the user to confirm they are ready. This replaces the old "Get set" announcement + REST countdown for between-exercise transitions.

The flow between exercises:
1. Current exercise finishes (TTS announces set completion).
2. App announces the **next exercise name** (e.g., "Push-ups").
3. Playback screen shows a prominent **"Ready" button**.
4. App waits indefinitely until the user taps the "Ready" button.
5. Exercise begins immediately (metronome starts on first set).

For the **first exercise** in a routine, the same flow applies: the app announces the exercise name, shows the "Ready" button, and waits for the user to tap it before starting.

The REST picker value is still used for rest **between sets within a single exercise** (unchanged from today). It is NOT used for transitions between exercises.

### Voice-Activated Ready (Phase 5)

In a later phase, speech recognition will be added alongside the "Ready" button:
- Use Android's `SpeechRecognizer` API to listen for a go-word ("Ready", "Go", or "Next" — TBD).
- Prefer the on-device speech model (`EXTRA_PREFER_OFFLINE`) for faster response and offline use. Fall back to cloud if the on-device model is not installed.
- Only listen while the "Ready" button is shown (between exercises). Speech recognition is inactive during exercise playback.
- Requires `RECORD_AUDIO` permission, requested at runtime.
- The "Ready" button remains as a parallel input — voice is an enhancement, not a replacement.

## Voice Announcements

The announcement sequence for each exercise in a routine:

1. **Exercise name** — e.g., "Push-ups" (spoken before the "Ready" button appears).
2. User taps "Ready" (or says the go-word in Phase 5).
3. Metronome / counting as today.
4. Set announcements as today ("1", "2", ... ).
5. REST gap between sets (within an exercise) as today.

When the entire routine finishes, announce **"Routine complete"** instead of "Done" on the last exercise.

## Data Model

### Exercise Entity

| Field    | Type   | Notes                                    |
|----------|--------|------------------------------------------|
| id       | Long   | Auto-generated primary key               |
| name     | String | User-assigned exercise name              |
| sets     | Int    | Number of sets (1-20)                    |
| duration | Int    | Work duration in seconds (1-99)          |
| gap      | Int    | Rest gap in seconds (1-30)               |
| beat     | Int    | Metronome interval in seconds (0-9)      |
| enabled  | Boolean| Whether included in playback             |
| sortOrder| Int    | Position in the routine list             |

### Persistence

The app currently has no database. This feature requires adding **Room** (SQLite via AndroidX).

- One `@Entity` class, one `@Dao`, one `@Database`.
- The DAO needs: insert, update, delete, getAll (ordered by sortOrder), updateSortOrders (for drag reorder).

SavedStateHandle continues to manage transient playback state (current set, exercise state, remaining ms). Room stores the exercise definitions.

On first launch, pre-populate with one default exercise: name "Example Exercise", 3 sets, 4s work, 2s rest, beat 1. These short values let the user quickly hear the full voice count sequence without waiting.

## Navigation

Add Jetpack Navigation (Compose) with three destinations:

- `routine` — Routine Screen (start destination)
- `exercise/new` — Exercise Editor for a new exercise
- `exercise/{id}` — Exercise Editor for an existing exercise
- `playback` — Playback Screen

The back button from the Editor returns to the Routine Screen. The back button during Playback stops playback and returns to the Routine Screen.

## State Machine Changes

The existing `ExerciseState` enum gains a new state:

```
IDLE, WAITING_FOR_READY, EXERCISING, GAP, PAUSED, DONE
```

- **WAITING_FOR_READY** — shown between exercises and before the first exercise. The "Ready" button is displayed. The playback loop suspends (via `CompletableDeferred` or similar) until the user taps "Ready". The exercise name has already been announced via TTS.
- **GAP** — now only used for rest between sets within a single exercise. No longer used for between-exercise transitions.
- **PAUSED** — can be entered from EXERCISING, GAP, or WAITING_FOR_READY. Resuming returns to the state that was paused.

The keep-screen-on flag (`View.keepScreenOn`) should be active during WAITING_FOR_READY, EXERCISING, and GAP states on the Playback Screen.

## Architecture Changes

### New Files

| File | Purpose | Phase |
|------|---------|-------|
| `data/Exercise.kt` | Room entity | 1 |
| `data/ExerciseDao.kt` | Room DAO | 1 |
| `data/AppDatabase.kt` | Room database with pre-populated default exercise via `RoomDatabase.Callback` | 1 |
| `ui/RoutineScreen.kt` | Routine list UI — checkbox, name, summary, trash icon, drag handle per item | 2 |
| `ui/ExerciseEditorScreen.kt` | Exercise add/edit UI — name text field, pickers (reuses ScrollWheelPicker), save/cancel | 2 |
| `ui/PlaybackScreen.kt` | Playback UI — exercise name, status, progress, "Ready" button, pause/skip/back controls | 3 |
| `viewmodel/RoutineViewModel.kt` | List management — CRUD, reordering, enable/disable toggle | 2 |
| `viewmodel/PlaybackViewModel.kt` | Routine playback loop — iterates exercises, suspends at WAITING_FOR_READY, coordinates metronome/TTS | 3 |
| `audio/SpeechRecognitionHelper.kt` | Wraps `SpeechRecognizer` — on-device preferred, cloud fallback, start/stop listening, result callback | 5 |

### Modified Files

| File | Change | Phase |
|------|--------|-------|
| `MainActivity.kt` | Replace single-screen `setContent` with `NavHost` and navigation graph | 2 |
| `app/build.gradle.kts` | Add Room, KSP, and Navigation dependencies | 1 |
| `build.gradle.kts` (project-level) | Add KSP plugin (`com.google.devtools.ksp`) | 1 |
| `libs.versions.toml` | Add version entries for Room, Navigation, and KSP | 1 |
| `AndroidManifest.xml` | Add `RECORD_AUDIO` permission | 5 |

### Files That Stay Unchanged

- `MetronomeEngine.kt` — no changes needed. Receives a `CoroutineScope` and works the same way.
- `VoiceAnnouncer.kt` — no changes needed. `announce()` is called with exercise names and "Routine complete" in addition to set counts.
- `ScrollWheelPicker.kt` — no changes needed. Reused in `ExerciseEditorScreen`.
- `Theme.kt`, `Type.kt` — no changes.
- `Color.kt` — may need new color vals if the routine list or "Ready" button need distinct colors.

### Files That Will Be Retired

- `ExerciseScreen.kt` — splits into `ExerciseEditorScreen.kt` (pickers + name field + save/cancel) and `PlaybackScreen.kt` (status + controls + "Ready" button).
- `ExerciseViewModel.kt` — the exercise loop logic moves into `PlaybackViewModel.kt`, which iterates over a list of exercises and suspends at WAITING_FOR_READY between them.

## Dependencies to Add

| Dependency | Purpose | Phase |
|------------|---------|-------|
| `androidx.room:room-runtime` | Room database runtime | 1 |
| `androidx.room:room-ktx` | Coroutine support for DAO (Flow return types) | 1 |
| `androidx.room:room-compiler` | KSP annotation processor for Room | 1 |
| `com.google.devtools.ksp` | Kotlin Symbol Processing Gradle plugin | 1 |
| `androidx.navigation:navigation-compose` | Jetpack Compose navigation | 2 |

No new dependencies are needed for Phases 3-5. `SpeechRecognizer` is a platform API (android.speech).

## Phasing

Build order. Each phase produces a working app.

1. **Room + data layer** — Add KSP plugin, Room dependencies, entity, DAO, database with pre-populated "Example Exercise" (3 sets, 4s work, 2s rest, beat 1). No UI changes yet. Verify with a unit test or temporary logging that the database creates and the default exercise is present.

2. **Routine Screen + Exercise Editor + Navigation** — Add Navigation dependency. Replace the single-screen `setContent` in `MainActivity` with a `NavHost`. Build `RoutineScreen` (list with checkbox, name, summary, trash icon) and `ExerciseEditorScreen` (name field + pickers + save/cancel). Wire add/edit/delete. Drag handles are visible but non-functional (placeholder for Phase 4). The Play button on the Routine Screen is visible but non-functional (placeholder for Phase 3). Retire `ExerciseScreen.kt` and `ExerciseViewModel.kt`.

3. **Playback with Ready button** — Build `PlaybackScreen` and `PlaybackViewModel`. Add WAITING_FOR_READY state. The playback loop: for each enabled exercise, announce name → show "Ready" button → wait for tap → run sets with metronome/TTS/REST between sets → after last exercise announce "Routine complete". Include pause, skip/next, and back button. Keep-screen-on active during playback. Wire the Routine Screen's Play button to navigate to Playback.

4. **Drag-to-reorder** — Add drag-and-drop reorder to the routine list using a reorderable LazyColumn library. Update `sortOrder` in Room on drop. Until this phase, sort order is based on creation order.

5. **Voice-activated Ready** — Add `SpeechRecognitionHelper` wrapping `SpeechRecognizer`. Request `RECORD_AUDIO` permission at runtime. Prefer on-device model, fall back to cloud. Listen only while WAITING_FOR_READY. On recognition of go-word, trigger the same action as tapping the "Ready" button. The button remains as a parallel fallback.

## Out of Scope (for now)

- Multiple routines
- Exercise-level rest timer display (countdown shown on screen during rest)
- Sharing routines between devices
- Cloud sync
- Statistics or history tracking
