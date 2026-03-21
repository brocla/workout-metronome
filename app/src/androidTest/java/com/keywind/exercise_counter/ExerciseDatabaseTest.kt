package com.keywind.exercise_counter

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.keywind.exercise_counter.data.AppDatabase
import com.keywind.exercise_counter.data.Exercise
import com.keywind.exercise_counter.data.ExerciseDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExerciseDatabaseTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: ExerciseDao

    @Before
    fun createDb() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.exerciseDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun insertAndQueryExercise() = runTest {
        val exercise = Exercise(
            name = "Push-ups",
            sets = 5,
            duration = 30,
            gap = 10,
            beat = 1,
        )
        val id = dao.insert(exercise)
        val loaded = dao.getById(id)

        assertEquals("Push-ups", loaded?.name)
        assertEquals(5, loaded?.sets)
        assertEquals(30, loaded?.duration)
        assertEquals(10, loaded?.gap)
        assertEquals(1, loaded?.beat)
        assertEquals(true, loaded?.enabled)
        assertEquals(0, loaded?.sortOrder)
    }

    @Test
    fun getAllReturnsOrderedBySortOrder() = runTest {
        dao.insert(Exercise(name = "Second", sets = 1, duration = 1, gap = 1, beat = 0, sortOrder = 2))
        dao.insert(Exercise(name = "First", sets = 1, duration = 1, gap = 1, beat = 0, sortOrder = 1))
        dao.insert(Exercise(name = "Third", sets = 1, duration = 1, gap = 1, beat = 0, sortOrder = 3))

        val all = dao.getAll().first()
        assertEquals(listOf("First", "Second", "Third"), all.map { it.name })
    }

    @Test
    fun updateExercise() = runTest {
        val id = dao.insert(Exercise(name = "Plank", sets = 3, duration = 60, gap = 5, beat = 0))
        val loaded = dao.getById(id)!!
        dao.update(loaded.copy(name = "Side Plank", sets = 4))

        val updated = dao.getById(id)
        assertEquals("Side Plank", updated?.name)
        assertEquals(4, updated?.sets)
    }

    @Test
    fun deleteExercise() = runTest {
        val id = dao.insert(Exercise(name = "Burpees", sets = 2, duration = 20, gap = 5, beat = 1))
        val loaded = dao.getById(id)!!
        dao.delete(loaded)

        assertNull(dao.getById(id))
    }

    @Test
    fun updateSortOrder() = runTest {
        val id = dao.insert(Exercise(name = "Squats", sets = 3, duration = 10, gap = 3, beat = 1, sortOrder = 0))
        dao.updateSortOrder(id, 5)

        val loaded = dao.getById(id)
        assertEquals(5, loaded?.sortOrder)
    }

    // Test #8: getNextSortOrder

    @Test
    fun getNextSortOrderReturnsZeroOnEmptyTable() = runTest {
        // To demonstrate failure: change COALESCE default from -1 to 0 → returns 1 instead of 0.
        assertEquals(0, dao.getNextSortOrder())
    }

    @Test
    fun getNextSortOrderReturnsOneAfterSingleInsert() = runTest {
        dao.insert(Exercise(name = "A", sets = 1, duration = 10, gap = 3, beat = 1, sortOrder = 0))
        assertEquals(1, dao.getNextSortOrder())
    }

    @Test
    fun getNextSortOrderReturnsMaxPlusOne() = runTest {
        // To demonstrate failure: change MAX(sortOrder) to MIN(sortOrder) → returns 1 instead of 6.
        dao.insert(Exercise(name = "A", sets = 1, duration = 10, gap = 3, beat = 1, sortOrder = 2))
        dao.insert(Exercise(name = "B", sets = 1, duration = 10, gap = 3, beat = 1, sortOrder = 5))
        dao.insert(Exercise(name = "C", sets = 1, duration = 10, gap = 3, beat = 1, sortOrder = 1))
        assertEquals(6, dao.getNextSortOrder())
    }

    @Test
    fun getNextSortOrderAfterDeleteRecomputesFromRemaining() = runTest {
        val id1 = dao.insert(Exercise(name = "A", sets = 1, duration = 10, gap = 3, beat = 1, sortOrder = 0))
        val id2 = dao.insert(Exercise(name = "B", sets = 1, duration = 10, gap = 3, beat = 1, sortOrder = 3))
        dao.delete(dao.getById(id2)!!)
        // Only sortOrder 0 remains; next should be 1
        assertEquals(1, dao.getNextSortOrder())
    }
}
