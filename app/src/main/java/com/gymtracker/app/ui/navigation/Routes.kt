package com.gymtracker.app.ui.navigation

object Routes {
    const val TODAY = "today"
    const val GYM = "gym"
    const val STACK = "stack"
    const val HEALTH = "health"
    const val INSIGHTS = "insights"
    const val SETTINGS = "settings"
    const val JEFIT_IMPORT = "jefit_import"
    const val ACTIVE_WORKOUT = "active_workout/{workoutId}"
    const val WORKOUT_SUMMARY = "workout_summary/{workoutId}"
    const val EXERCISE_DETAIL = "exercise_detail/{exerciseId}"
    const val MUSCLE_RECOVERY = "muscle_recovery"
    const val XIAOMI_SCALE = "xiaomi_scale"
    const val WATCH_ACTIVITY = "watch_activity"
    const val CONNECTED_DEVICES = "connected_devices"

    fun activeWorkout(workoutId: Long) = "active_workout/$workoutId"
    fun workoutSummary(workoutId: Long) = "workout_summary/$workoutId"
    fun exerciseDetail(exerciseId: Long) = "exercise_detail/$exerciseId"
}
