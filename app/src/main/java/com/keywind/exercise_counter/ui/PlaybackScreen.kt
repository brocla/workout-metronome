package com.keywind.exercise_counter.ui

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keywind.exercise_counter.ui.theme.PlayGreen
import com.keywind.exercise_counter.viewmodel.PlaybackState
import com.keywind.exercise_counter.viewmodel.PlaybackViewModel

@Composable
fun PlaybackScreen(
    viewModel: PlaybackViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isActive by viewModel.isActive.collectAsStateWithLifecycle()
    val exerciseIndex by viewModel.currentExerciseIndex.collectAsStateWithLifecycle()
    val currentSet by viewModel.currentSet.collectAsStateWithLifecycle()
    val totalExercises by viewModel.totalExercises.collectAsStateWithLifecycle()
    val loaded by viewModel.loaded.collectAsStateWithLifecycle()
    val isListening by viewModel.isListening.collectAsStateWithLifecycle()
    val exercise by viewModel.currentExercise.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.startVoiceRecognition()
    }

    LaunchedEffect(state) {
        if (state == PlaybackState.WAITING_FOR_READY) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    BackHandler {
        viewModel.stop()
        onBack()
    }

    // Keep screen on during playback
    val view = LocalView.current
    DisposableEffect(isActive) {
        view.keepScreenOn = isActive
        onDispose { view.keepScreenOn = false }
    }

    // Navigate back if no exercises (e.g., after process death reset)
    LaunchedEffect(loaded, state, totalExercises) {
        if (loaded && state == PlaybackState.IDLE && totalExercises == 0) {
            onBack()
        }
    }

    // Wait for exercises to load before rendering
    if (!loaded) return

    Surface(modifier = modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Exercise progress
        Text(
            text = "Exercise ${exerciseIndex + 1} of $totalExercises",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Exercise name
        Text(
            text = exercise?.name ?: "",
            style = MaterialTheme.typography.headlineLarge,
        )

        // Exercise summary
        exercise?.let { ex ->
            Text(
                text = "${ex.sets} ${if (ex.sets == 1) "set" else "sets"} / ${ex.duration}s work / ${ex.gap}s rest / beat ${ex.beat}s",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Status text
        Text(
            text = statusText(state, currentSet, exercise?.sets ?: 0),
            style = MaterialTheme.typography.titleMedium,
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Ready button
        if (state == PlaybackState.WAITING_FOR_READY) {
            Button(
                onClick = viewModel::onReady,
                enabled = isListening,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PlayGreen,
                    contentColor = Color.White,
                ),
                modifier = Modifier.height(56.dp),
            ) {
                Text(
                    text = "Ready",
                    style = MaterialTheme.typography.headlineSmall,
                )
            }

            if (isListening) {
                Text(
                    text = "Listening...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

        // Done button
        if (state == PlaybackState.DONE) {
            Button(
                onClick = {
                    viewModel.stop()
                    onBack()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = PlayGreen,
                    contentColor = Color.White,
                ),
                modifier = Modifier.height(56.dp),
            ) {
                Text(
                    text = "Done",
                    style = MaterialTheme.typography.headlineSmall,
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

        // Control buttons
        if (state != PlaybackState.DONE) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Pause / Resume
                FilledIconButton(
                    onClick = {
                        if (state == PlaybackState.PAUSED) viewModel.resume()
                        else viewModel.pause()
                    },
                    modifier = Modifier.size(64.dp),
                    enabled = state != PlaybackState.WAITING_FOR_READY,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = PlayGreen,
                        contentColor = Color.White,
                    ),
                ) {
                    Icon(
                        imageVector = if (state == PlaybackState.PAUSED) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        contentDescription = if (state == PlaybackState.PAUSED) "Resume" else "Pause",
                        modifier = Modifier.size(32.dp),
                    )
                }

                // Skip / Next
                FilledIconButton(
                    onClick = viewModel::skipNext,
                    modifier = Modifier.size(64.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = "Skip to next exercise",
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        // Alarm-stream volume slider (controls tock + voice volume independently of music)
        val audioManager = remember { context.getSystemService(AudioManager::class.java) }
        val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM) }
        var volume by remember {
            mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_ALARM).toFloat())
        }
        Text(
            text = "Tock volume",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = volume,
            onValueChange = { newVolume ->
                volume = newVolume
                audioManager.setStreamVolume(
                    AudioManager.STREAM_ALARM,
                    newVolume.toInt(),
                    0,
                )
            },
            valueRange = 0f..maxVolume.toFloat(),
            steps = maxVolume - 1,
            modifier = Modifier.width(240.dp),
        )
    }
    }
}

internal fun statusText(state: PlaybackState, currentSet: Int, totalSets: Int): String =
    when (state) {
        PlaybackState.IDLE -> ""
        PlaybackState.WAITING_FOR_READY -> ""
        PlaybackState.EXERCISING -> "Set ${currentSet + 1} of $totalSets"
        PlaybackState.GAP -> "Rest — $currentSet of $totalSets complete"
        PlaybackState.PAUSED -> "Paused"
        PlaybackState.DONE -> "Routine complete!"
    }
