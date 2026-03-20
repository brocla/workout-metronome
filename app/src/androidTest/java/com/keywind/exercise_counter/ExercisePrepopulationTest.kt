package com.keywind.exercise_counter

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.keywind.exercise_counter.data.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExercisePrepopulationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dbName = "test_prepopulate.db"

    @After
    fun cleanup() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun defaultExerciseIsPrePopulated() = runTest {
        // Build a real database (not in-memory) so the onCreate callback fires
        val db = androidx.room.Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            dbName,
        )
            .addCallback(AppDatabase.Companion.PrepopulateCallback())
            .allowMainThreadQueries()
            .build()

        try {
            val exercises = db.exerciseDao().getAll().first()
            assertEquals(1, exercises.size)

            val exercise = exercises[0]
            assertEquals("Example Exercise", exercise.name)
            assertEquals(3, exercise.sets)
            assertEquals(4, exercise.duration)
            assertEquals(2, exercise.gap)
            assertEquals(1, exercise.beat)
            assertEquals(true, exercise.enabled)
            assertEquals(0, exercise.sortOrder)
        } finally {
            db.close()
        }
    }
}
