package com.gymtracker.app.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.gymtracker.app.data.db.HcStatus
import com.gymtracker.app.data.health.HealthSyncRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class MergeWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val healthSyncRepository: HealthSyncRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val workoutId = inputData.getLong(KEY_WORKOUT_ID, -1L)
        if (workoutId <= 0L) return Result.failure()

        return runCatching {
            val status = healthSyncRepository.syncWorkout(workoutId)
            when (status) {
                HcStatus.MATCHED, HcStatus.HR_ONLY, HcStatus.NO_DATA -> Result.success()
                HcStatus.PENDING -> {
                    if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.success()
                }
                HcStatus.NOT_ATTEMPTED, HcStatus.ERROR -> {
                    if (runAttemptCount < 2) Result.retry() else Result.success()
                }
            }
        }.getOrElse {
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val KEY_WORKOUT_ID = "workout_id"
        private const val MAX_RETRIES = 4

        fun enqueueForWorkout(context: Context, workoutId: Long, initialDelayMinutes: Long = 2) {
            val req = OneTimeWorkRequestBuilder<MergeWorker>()
                .setInputData(workDataOf(KEY_WORKOUT_ID to workoutId))
                .setInitialDelay(initialDelayMinutes, TimeUnit.MINUTES)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "hc_merge_$workoutId",
                ExistingWorkPolicy.REPLACE,
                req
            )
        }
    }
}
