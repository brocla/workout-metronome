package com.keywind.exercise_counter

import com.keywind.exercise_counter.data.Exercise
import com.keywind.exercise_counter.viewmodel.reorderExercises
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Test #9: reorderExercises() — list reorder correctness.
 *
 * To demonstrate failure:
 * - Swap `add(to, ...)` and `removeAt(from)` order → all reorder tests fail (wrong indices).
 * - Use `current[from] = current[to]` (swap) instead of move → testMoveFirstToLast fails.
 * - Remove the toMutableList() copy → mutations affect original list.
 */
class ReorderExercisesTest {

    private fun makeList(vararg names: String) = names.mapIndexed { i, name ->
        Exercise(id = i.toLong(), name = name, sets = 1, duration = 10, gap = 3, beat = 1)
    }

    @Test
    fun testMoveFirstToLast() {
        val result = reorderExercises(makeList("A", "B", "C"), from = 0, to = 2)
        assertEquals(listOf("B", "C", "A"), result.map { it.name })
    }

    @Test
    fun testMoveLastToFirst() {
        val result = reorderExercises(makeList("A", "B", "C"), from = 2, to = 0)
        assertEquals(listOf("C", "A", "B"), result.map { it.name })
    }

    @Test
    fun testMoveMiddleUp() {
        val result = reorderExercises(makeList("A", "B", "C"), from = 1, to = 0)
        assertEquals(listOf("B", "A", "C"), result.map { it.name })
    }

    @Test
    fun testMoveMiddleDown() {
        val result = reorderExercises(makeList("A", "B", "C"), from = 1, to = 2)
        assertEquals(listOf("A", "C", "B"), result.map { it.name })
    }

    @Test
    fun testMoveToSamePosition() {
        val original = makeList("A", "B", "C")
        val result = reorderExercises(original, from = 1, to = 1)
        assertEquals(listOf("A", "B", "C"), result.map { it.name })
    }

    @Test
    fun testOriginalListIsNotMutated() {
        val original = makeList("A", "B", "C")
        reorderExercises(original, from = 0, to = 2)
        assertEquals(listOf("A", "B", "C"), original.map { it.name })
    }

    @Test
    fun testResultHasSameSize() {
        val result = reorderExercises(makeList("A", "B", "C", "D"), from = 0, to = 3)
        assertEquals(4, result.size)
    }

    @Test
    fun testSingleItemList() {
        val result = reorderExercises(makeList("A"), from = 0, to = 0)
        assertEquals(listOf("A"), result.map { it.name })
    }
}
