package com.keywind.exercise_counter.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.gestures.snapping.snapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged

private const val VISIBLE_ITEMS = 5
private const val BUFFER_ITEMS = VISIBLE_ITEMS / 2

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ScrollWheelPicker(
    range: IntRange,
    selectedValue: Int,
    onValueChange: (Int) -> Unit,
    label: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val items = range.toList()
    val initialDataIndex = (selectedValue - range.first).coerceIn(0, items.lastIndex)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialDataIndex)
    val snappingLayout = remember(listState) { SnapLayoutInfoProvider(listState) }
    val flingBehavior = snapFlingBehavior(
        snapLayoutInfoProvider = snappingLayout,
        decayAnimationSpec = exponentialDecay(frictionMultiplier = 0.5f),
        snapAnimationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
    )
    val itemHeightDp = 48.dp

    val lastScrolledValue = remember { mutableIntStateOf(selectedValue) }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { firstVisible ->
                val dataIndex = firstVisible.coerceIn(0, items.lastIndex)
                val value = items[dataIndex]
                lastScrolledValue.intValue = value
                onValueChange(value)
            }
    }

    LaunchedEffect(selectedValue) {
        if (selectedValue != lastScrolledValue.intValue) {
            val dataIndex = (selectedValue - range.first).coerceIn(0, items.lastIndex)
            lastScrolledValue.intValue = selectedValue
            listState.scrollToItem(dataIndex)
        }
    }

    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(horizontal = 4.dp),
    ) {
        // Inset container with sharp corners
        Surface(
            shape = RectangleShape,
            color = Color.Transparent,
            tonalElevation = 2.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .height(itemHeightDp * VISIBLE_ITEMS)
                    .width(76.dp),
            ) {
                // The scrollable number list
                LazyColumn(
                    state = listState,
                    flingBehavior = flingBehavior,
                    userScrollEnabled = enabled,
                    modifier = Modifier.matchParentSize(),
                ) {
                    items(BUFFER_ITEMS) {
                        Box(modifier = Modifier.height(itemHeightDp))
                    }

                    items(items.size) { index ->
                        val centerDataIndex =
                            listState.firstVisibleItemIndex.coerceIn(0, items.lastIndex)
                        val distanceFromCenter = kotlin.math.abs(index - centerDataIndex)
                        val alpha = when (distanceFromCenter) {
                            0 -> 1f
                            1 -> 0.5f
                            else -> 0.2f
                        }
                        val fontSize = if (distanceFromCenter == 0) 28.sp else 20.sp
                        val fontWeight = if (distanceFromCenter == 0) {
                            FontWeight.Bold
                        } else {
                            FontWeight.Normal
                        }

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .height(itemHeightDp)
                                .fillMaxWidth()
                                .alpha(if (enabled) alpha else alpha * 0.5f),
                        ) {
                            Text(
                                text = items[index].toString(),
                                fontSize = fontSize,
                                fontWeight = fontWeight,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }

                    items(BUFFER_ITEMS) {
                        Box(modifier = Modifier.height(itemHeightDp))
                    }
                }

                // Selection row highlight
                Column(modifier = Modifier.align(Alignment.TopStart)) {
                    Box(modifier = Modifier.height(itemHeightDp * BUFFER_ITEMS))
                    Box(
                        modifier = Modifier
                            .height(itemHeightDp)
                            .fillMaxWidth()
                            .background(primaryContainer.copy(alpha = 0.3f)),
                    )
                }

                // Divider lines
                Column(modifier = Modifier.align(Alignment.TopStart)) {
                    Box(modifier = Modifier.height(itemHeightDp * BUFFER_ITEMS))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Box(modifier = Modifier.height(itemHeightDp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }

                // Top gradient fade (cylinder effect)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .height(itemHeightDp * BUFFER_ITEMS)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(surfaceVariant, Color.Transparent),
                            ),
                        ),
                )

                // Bottom gradient fade (cylinder effect)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .height(itemHeightDp * BUFFER_ITEMS)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, surfaceVariant),
                            ),
                        ),
                )
            }
        }

        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
