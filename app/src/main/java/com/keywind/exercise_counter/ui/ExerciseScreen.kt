package com.keywind.exercise_counter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.keywind.exercise_counter.ui.theme.PlayGreen
import com.keywind.exercise_counter.ui.theme.StopRed
import com.keywind.exercise_counter.viewmodel.ExerciseState
import com.keywind.exercise_counter.viewmodel.ExerciseViewModel

@Composable
fun ExerciseScreen(
    viewModel: ExerciseViewModel,
    modifier: Modifier = Modifier,
) {
    val sets by viewModel.sets.collectAsState()
    val duration by viewModel.duration.collectAsState()
    val gap by viewModel.gap.collectAsState()
    val beat by viewModel.beat.collectAsState()
    val currentSet by viewModel.currentSet.collectAsState()
    val state by viewModel.state.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()

    val pickersEnabled = state == ExerciseState.IDLE || state == ExerciseState.DONE

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Scroll wheel pickers
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ScrollWheelPicker(
                range = 1..20,
                selectedValue = sets,
                onValueChange = viewModel::updateSets,
                label = "SETS",
                enabled = pickersEnabled,
            )
            ScrollWheelPicker(
                range = 1..99,
                selectedValue = duration,
                onValueChange = viewModel::updateDuration,
                label = "WORK",
                enabled = pickersEnabled,
            )
            ScrollWheelPicker(
                range = 1..30,
                selectedValue = gap,
                onValueChange = viewModel::updateGap,
                label = "REST",
                enabled = pickersEnabled,
            )
            ScrollWheelPicker(
                range = 1..10,
                selectedValue = beat,
                onValueChange = viewModel::updateBeat,
                label = "BEAT",
                enabled = pickersEnabled,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Status text
        Text(
            text = statusText(state, currentSet, sets),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Control buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Play / Pause
            FilledIconButton(
                onClick = { if (isRunning) viewModel.pause() else viewModel.play() },
                modifier = Modifier.size(64.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = PlayGreen,
                    contentColor = Color.White,
                ),
            ) {
                Icon(
                    imageVector = if (isRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isRunning) "Pause" else "Play",
                    modifier = Modifier.size(32.dp),
                )
            }

            // Reset
            FilledIconButton(
                onClick = viewModel::reset,
                modifier = Modifier.size(64.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = StopRed,
                    contentColor = Color.White,
                ),
                enabled = state != ExerciseState.IDLE,
            ) {
                Icon(
                    imageVector = Icons.Filled.Stop,
                    contentDescription = "Reset",
                    modifier = Modifier.size(32.dp),
                )
            }
        }
    }
}

private fun statusText(state: ExerciseState, currentSet: Int, totalSets: Int): String =
    when (state) {
        ExerciseState.IDLE -> "Ready"
        ExerciseState.EXERCISING -> "Set ${currentSet + 1} of $totalSets"
        ExerciseState.GAP -> "Rest — $currentSet of $totalSets complete"
        ExerciseState.DONE -> "Done!"
    }
