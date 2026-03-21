package com.keywind.exercise_counter

import com.keywind.exercise_counter.viewmodel.ExerciseEditorViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Test #6: ExerciseEditorViewModel.save() blank-name rejection — via validateAndTrimName().
 *
 * To demonstrate failure:
 * - Remove the .trim() call → testLeadingTrailingSpaceIsTrimmed returns untrimmed string.
 * - Change isBlank() to isEmpty() → testWhitespaceOnlyIsRejected fails (spaces are not empty).
 * - Return name instead of null for blank → testEmptyStringIsRejected and
 *   testWhitespaceOnlyIsRejected fail.
 */
class NameValidationTest {

    @Test
    fun testValidNameIsAccepted() {
        assertEquals("Push-ups", ExerciseEditorViewModel.validateAndTrimName("Push-ups"))
    }

    @Test
    fun testLeadingTrailingSpaceIsTrimmed() {
        assertEquals("Squats", ExerciseEditorViewModel.validateAndTrimName("  Squats  "))
        assertEquals("Lunges", ExerciseEditorViewModel.validateAndTrimName("\tLunges\n"))
    }

    @Test
    fun testEmptyStringIsRejected() {
        assertNull(ExerciseEditorViewModel.validateAndTrimName(""))
    }

    @Test
    fun testWhitespaceOnlyIsRejected() {
        assertNull(ExerciseEditorViewModel.validateAndTrimName("   "))
        assertNull(ExerciseEditorViewModel.validateAndTrimName("\t"))
        assertNull(ExerciseEditorViewModel.validateAndTrimName("\n"))
    }

    @Test
    fun testNameWithInternalSpacesIsAccepted() {
        assertEquals("Side Plank", ExerciseEditorViewModel.validateAndTrimName("Side Plank"))
    }

    @Test
    fun testSingleCharacterNameIsAccepted() {
        assertEquals("A", ExerciseEditorViewModel.validateAndTrimName("A"))
    }
}
