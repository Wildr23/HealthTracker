package com.gymtracker.app.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.gymtracker.app.R
import com.gymtracker.app.ui.exercise.ExerciseDetailScreen
import com.gymtracker.app.ui.gym.GymScreen
import com.gymtracker.app.ui.health.HealthScreen
import com.gymtracker.app.ui.importcsv.JefitImportScreen
import com.gymtracker.app.ui.insights.InsightsScreen
import com.gymtracker.app.ui.recovery.MuscleRecoveryScreen
import com.gymtracker.app.ui.scale.XiaomiScaleScreen
import com.gymtracker.app.ui.settings.SettingsScreen
import com.gymtracker.app.ui.stack.StackScreen
import com.gymtracker.app.ui.summary.WorkoutSummaryScreen
import com.gymtracker.app.ui.theme.ElectricBlue
import com.gymtracker.app.ui.today.TodayScreen
import com.gymtracker.app.ui.workout.ActiveWorkoutScreen

private data class TopTab(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector
)

private val TOP_TABS = listOf(
    TopTab(Routes.TODAY, R.string.tab_today, Icons.Default.Today),
    TopTab(Routes.GYM, R.string.tab_gym, Icons.Default.FitnessCenter),
    TopTab(Routes.STACK, R.string.tab_stack, Icons.Default.Medication),
    TopTab(Routes.HEALTH, R.string.tab_health, Icons.Default.LocalHospital),
    TopTab(Routes.INSIGHTS, R.string.tab_insights, Icons.Default.AutoAwesome)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GymTrackerApp(
    openRoute: String? = null,
    onOpenRouteHandled: () -> Unit = {}
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val currentTopTab = TOP_TABS.firstOrNull { it.route == currentRoute }

    LaunchedEffect(openRoute) {
        if (!openRoute.isNullOrBlank()) {
            navController.navigate(openRoute) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
            onOpenRouteHandled()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (currentTopTab != null) {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    title = { Text(stringResource(currentTopTab.labelRes), style = MaterialTheme.typography.headlineSmall) },
                    actions = {
                        IconButton(onClick = { navController.navigate(Routes.SETTINGS) }) {
                            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings_title))
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (currentTopTab != null) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    TOP_TABS.forEach { tab ->
                        val selected = backStack?.destination?.hierarchy?.any { it.route == tab.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = ElectricBlue,
                                selectedTextColor = ElectricBlue,
                                indicatorColor = ElectricBlue.copy(alpha = 0.16f)
                            ),
                            icon = { Icon(tab.icon, contentDescription = stringResource(tab.labelRes)) },
                            label = {
                                Text(
                                    text = stringResource(tab.labelRes),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.TODAY,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Routes.TODAY) {
                TodayScreen(
                    onOpenActiveWorkout = { wId -> navController.navigate(Routes.activeWorkout(wId)) },
                    onOpenWorkoutSummary = { wId -> navController.navigate(Routes.workoutSummary(wId)) },
                    onOpenJefitImport = { navController.navigate(Routes.JEFIT_IMPORT) },
                    onOpenMuscleRecovery = { navController.navigate(Routes.MUSCLE_RECOVERY) }
                )
            }
            composable(Routes.GYM) {
                GymScreen(
                    onOpenActiveWorkout = { wId -> navController.navigate(Routes.activeWorkout(wId)) },
                    onOpenWorkoutSummary = { wId -> navController.navigate(Routes.workoutSummary(wId)) },
                    onOpenExerciseDetail = { exId -> navController.navigate(Routes.exerciseDetail(exId)) },
                    onOpenMuscleRecovery = { navController.navigate(Routes.MUSCLE_RECOVERY) }
                )
            }
            composable(Routes.MUSCLE_RECOVERY) {
                MuscleRecoveryScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.STACK) { StackScreen() }
            composable(Routes.HEALTH) {
                HealthScreen(onOpenXiaomiScale = { navController.navigate(Routes.XIAOMI_SCALE) })
            }
            composable(Routes.XIAOMI_SCALE) {
                XiaomiScaleScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.INSIGHTS) { InsightsScreen() }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenJefitImport = { navController.navigate(Routes.JEFIT_IMPORT) },
                    onOpenXiaomiScale = { navController.navigate(Routes.XIAOMI_SCALE) }
                )
            }
            composable(Routes.JEFIT_IMPORT) {
                JefitImportScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = Routes.ACTIVE_WORKOUT,
                arguments = listOf(navArgument("workoutId") { type = NavType.LongType })
            ) {
                ActiveWorkoutScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onWorkoutFinished = { wId ->
                        navController.navigate(Routes.workoutSummary(wId)) {
                            popUpTo(Routes.ACTIVE_WORKOUT) { inclusive = true }
                        }
                    },
                    onOpenExerciseDetail = { exId -> navController.navigate(Routes.exerciseDetail(exId)) }
                )
            }
            composable(
                route = Routes.WORKOUT_SUMMARY,
                arguments = listOf(navArgument("workoutId") { type = NavType.LongType })
            ) {
                WorkoutSummaryScreen(
                    onBack = { navController.popBackStack() },
                    onOpenExercise = { exId -> navController.navigate(Routes.exerciseDetail(exId)) }
                )
            }
            composable(
                route = Routes.EXERCISE_DETAIL,
                arguments = listOf(navArgument("exerciseId") { type = NavType.LongType })
            ) {
                ExerciseDetailScreen(
                    onBack = { navController.popBackStack() },
                    onOpenWorkout = { wId -> navController.navigate(Routes.workoutSummary(wId)) }
                )
            }
        }
    }
}
