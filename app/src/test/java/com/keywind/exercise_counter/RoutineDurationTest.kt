package com.keywind.exercise_counter

import com.keywind.exercise_counter.data.Exercise
import com.keywind.exercise_counter.ui.formatDuration
import com.keywind.exercise_counter.ui.routineDurationSeconds
import org.junit.Assert.assertEquals
import org.junit.Test

class RoutineDurationTest {

    private fun exercise(
        sets: Int = 1,
        duration: Int = 10,
        gap: Int = 5,
        enabled: Boolean = true,
    ) = Exercise(name = "E", sets = sets, duration = duration, gap = gap, beat = 1, enabled = enabled)

    @Test
    fun singleExercise() {
        val exercises = listOf(exercise(sets = 3, duration = 20, gap = 10))
        assertEquals(90, routineDurationSeconds(exercises))
    }

    @Test
    fun multipleExercises() {
        val exercises = listOf(
            exercise(sets = 2, duration = 10, gap = 5),
            exercise(sets = 3, duration = 15, gap = 5),
        )
        assertEquals(30 + 60, routineDurationSeconds(exercises))
    }

    @Test
    fun disabledExercisesExcluded() {
        val exercises = listOf(
            exercise(sets = 2, duration = 10, gap = 5, enabled = true),
            exercise(sets = 3, duration = 15, gap = 5, enabled = false),
        )
        assertEquals(30, routineDurationSeconds(exercises))
    }

    @Test
    fun emptyList() {
        assertEquals(0, routineDurationSeconds(emptyList<Exercise>()))
    }

    @Test
    fun formatZero() {
        assertEquals("0m 0s", formatDuration(0))
    }

    @Test
    fun formatSecondsOnly() {
        assertEquals("0m 45s", formatDuration(45))
    }

    @Test
    fun formatMinutesAndSeconds() {
        assertEquals("3m 20s", formatDuration(200))
    }

    @Test
    fun formatExactMinutes() {
        assertEquals("2m 0s", formatDuration(120))
    }
}
