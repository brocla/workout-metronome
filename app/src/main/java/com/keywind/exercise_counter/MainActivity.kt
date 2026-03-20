package com.keywind.exercise_counter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.keywind.exercise_counter.ui.ExerciseEditorScreen
import com.keywind.exercise_counter.ui.RoutineScreen
import com.keywind.exercise_counter.ui.theme.ExerciseCounterTheme
import com.keywind.exercise_counter.viewmodel.ExerciseEditorViewModel
import com.keywind.exercise_counter.viewmodel.RoutineViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ExerciseCounterTheme {
                val navController = rememberNavController()

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "routine",
                        modifier = Modifier.padding(innerPadding),
                    ) {
                        composable("routine") {
                            val viewModel: RoutineViewModel = viewModel()
                            RoutineScreen(
                                viewModel = viewModel,
                                onAddExercise = { navController.navigate("exercise/new") },
                                onEditExercise = { id -> navController.navigate("exercise/$id") },
                                onPlay = { /* Phase 3 */ },
                            )
                        }
                        composable("exercise/new") {
                            val viewModel: ExerciseEditorViewModel = viewModel()
                            ExerciseEditorScreen(
                                viewModel = viewModel,
                                onSaveComplete = { navController.popBackStack() },
                                onCancel = { navController.popBackStack() },
                            )
                        }
                        composable(
                            route = "exercise/{id}",
                            arguments = listOf(navArgument("id") { type = NavType.LongType }),
                        ) {
                            val viewModel: ExerciseEditorViewModel = viewModel()
                            ExerciseEditorScreen(
                                viewModel = viewModel,
                                onSaveComplete = { navController.popBackStack() },
                                onCancel = { navController.popBackStack() },
                            )
                        }
                    }
                }
            }
        }
    }
}
