package com.keywind.exercise_counter.ui

import android.annotation.SuppressLint
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keywind.exercise_counter.data.Exercise
import com.keywind.exercise_counter.ui.theme.PlayGreen
import com.keywind.exercise_counter.ui.theme.WheelNumber
import com.keywind.exercise_counter.viewmodel.RoutineViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun RoutineScreen(
    viewModel: RoutineViewModel,
    onAddExercise: () -> Unit,
    onEditExercise: (Long) -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val exercises by viewModel.exercises.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledIconButton(
                    onClick = onPlay,
                    modifier = Modifier.size(64.dp),
                    enabled = exercises.any { it.enabled },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = PlayGreen,
                        contentColor = Color.White,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Play routine",
                        modifier = Modifier.size(32.dp),
                    )
                }
                FloatingActionButton(onClick = onAddExercise) {
                    Icon(Icons.Filled.Add, contentDescription = "Add exercise")
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "My Routine",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = formatDuration(routineDurationSeconds(exercises)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (exercises.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Tap + to add an exercise",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                val lazyListState = rememberLazyListState()
                val reorderableLazyListState = rememberReorderableLazyListState(lazyListState) { from, to ->
                    viewModel.moveExercise(from.index, to.index)
                }

                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.weight(1f),
                ) {
                    items(exercises, key = { it.id }) { exercise ->
                        ReorderableItem(reorderableLazyListState, key = exercise.id) { isDragging ->
                            val elevation by animateDpAsState(if (isDragging) 4.dp else 0.dp)
                            val backgroundColor by animateColorAsState(
                                if (isDragging) Color(0xFF42A5F5).copy(alpha = 0.25f) else Color.Transparent,
                            )

                            Surface(
                                shadowElevation = elevation,
                                color = backgroundColor,
                            ) {
                                ExerciseListItem(
                                    exercise = exercise,
                                    onToggle = { viewModel.toggleEnabled(exercise) },
                                    onEdit = { onEditExercise(exercise.id) },
                                    onDelete = { viewModel.deleteExercise(exercise) },
                                    dragModifier = Modifier.draggableHandle(),
                                )
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@SuppressLint("ModifierParameter") // dragModifier is a secondary modifier for the drag handle only, not the root element
@Composable
private fun ExerciseListItem(
    exercise: Exercise,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    dragModifier: Modifier = Modifier,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = exercise.enabled,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(checkedColor = WheelNumber),
        )
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = exercise.name,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = exerciseSummary(exercise),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.Filled.DragHandle,
            contentDescription = "Reorder",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = dragModifier.padding(horizontal = 8.dp),
        )
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "Delete",
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

internal fun exerciseSummary(exercise: Exercise): String {
    val setWord = if (exercise.sets == 1) "set" else "sets"
    return "${exercise.sets} $setWord / ${exercise.duration}s work / ${exercise.gap}s rest / beat ${exercise.beat}s"
}

internal fun routineDurationSeconds(exercises: List<Exercise>): Int =
    exercises.filter { it.enabled }.sumOf { it.sets * (it.duration + it.gap) }

internal fun formatDuration(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "${minutes}m ${seconds}s"
}
