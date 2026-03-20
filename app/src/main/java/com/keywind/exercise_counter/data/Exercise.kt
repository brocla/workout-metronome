package com.keywind.exercise_counter.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "exercises")
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sets: Int,
    val duration: Int,
    val gap: Int,
    val beat: Int,
    val enabled: Boolean = true,
    val sortOrder: Int = 0,
)
