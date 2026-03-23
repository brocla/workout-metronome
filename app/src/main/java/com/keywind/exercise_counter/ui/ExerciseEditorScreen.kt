package com.keywind.exercise_counter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keywind.exercise_counter.ui.theme.PlayGreen
import com.keywind.exercise_counter.ui.theme.WheelNumber
import com.keywind.exercise_counter.viewmodel.ExerciseEditorViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseEditorScreen(
    viewModel: ExerciseEditorViewModel,
    onSaveComplete: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name by viewModel.name.collectAsStateWithLifecycle()
    val sets by viewModel.sets.collectAsStateWithLifecycle()
    val duration by viewModel.duration.collectAsStateWithLifecycle()
    val gap by viewModel.gap.collectAsStateWithLifecycle()
    val beat by viewModel.beat.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.save(onSaveComplete) },
                        enabled = name.isNotBlank(),
                    ) {
                        Text("Save", color = if (name.isNotBlank()) PlayGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
                    }
                },
            )
        },
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            val compact = maxHeight < 400.dp
            val visibleItems = if (compact) 3 else 5

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = if (viewModel.isEditing) "Edit Exercise" else "New Exercise",
                    style = MaterialTheme.typography.headlineLarge,
                    color = WheelNumber,
                )

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = viewModel::updateName,
                    label = { Text("Exercise name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    ScrollWheelPicker(
                        range = 1..20,
                        selectedValue = sets,
                        onValueChange = viewModel::updateSets,
                        label = "SETS",
                        visibleItems = visibleItems,
                    )
                    ScrollWheelPicker(
                        range = 1..99,
                        selectedValue = duration,
                        onValueChange = viewModel::updateDuration,
                        label = "WORK",
                        visibleItems = visibleItems,
                    )
                    ScrollWheelPicker(
                        range = 1..30,
                        selectedValue = gap,
                        onValueChange = viewModel::updateGap,
                        label = "REST",
                        visibleItems = visibleItems,
                    )
                    ScrollWheelPicker(
                        range = 0..9,
                        selectedValue = beat,
                        onValueChange = viewModel::updateBeat,
                        label = "BEAT",
                        visibleItems = visibleItems,
                    )
                }

                Spacer(modifier = Modifier.weight(3f))
            }
        }
    }
}
