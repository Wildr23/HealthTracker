package com.gymtracker.app.data.repo

import androidx.room.withTransaction
import com.gymtracker.app.data.db.GymTrackerDatabase
import com.gymtracker.app.data.db.SupplementCategory
import com.gymtracker.app.data.db.SupplementDao
import com.gymtracker.app.data.db.SupplementItemEntity
import com.gymtracker.app.data.db.SupplementLogEntity
import com.gymtracker.app.data.db.SupplementScheduleEntity
import com.gymtracker.app.data.db.SupplementTrigger
import com.gymtracker.app.data.db.SupplementUnit
import com.gymtracker.app.data.db.SupplementWithSchedules
import com.gymtracker.app.data.db.SyncState
import com.gymtracker.app.data.db.WorkoutDao
import com.gymtracker.app.work.StackReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupplementRepository @Inject constructor(
    private val database: GymTrackerDatabase,
    private val supplementDao: SupplementDao,
    private val workoutDao: WorkoutDao,
    private val reminderScheduler: StackReminderScheduler
) {

    fun observeVisibleStack(): Flow<List<SupplementWithSchedules>> =
        supplementDao.observeActiveWithSchedules().map { list ->
            list.filter { it.item.notes != QUICK_CAFFEINE_TAG }
        }

    fun observeAllActiveStack(): Flow<List<SupplementWithSchedules>> =
        supplementDao.observeActiveWithSchedules()

    fun observeLogsSince(since: Instant): Flow<List<SupplementLogEntity>> =
        supplementDao.observeLogsSince(since)

    fun observeRecentPreWorkoutLog(windowMinutes: Long = 90L): Flow<SupplementLogEntity?> =
        supplementDao.observeRecentPreWorkoutLog(Instant.now().minusSeconds(windowMinutes * 60L))

    suspend fun findRecentPreWorkoutLog(windowMinutes: Long = 90L): SupplementLogEntity? =
        withContext(Dispatchers.IO) {
            supplementDao.findRecentPreWorkoutLog(Instant.now().minusSeconds(windowMinutes * 60L))
        }

    suspend fun seedDefaultUserStack() = withContext(Dispatchers.IO) {
        val existingVisible = supplementDao.getActiveWithSchedulesOnce()
            .filter { it.item.notes != QUICK_CAFFEINE_TAG }
        if (existingVisible.isNotEmpty()) return@withContext

        val now = Instant.now()
        val presets = listOf(
            SupplementItemEntity(
                nameEn = "Vitamin D3",
                nameUk = "Вітамін D3",
                category = SupplementCategory.VITAMIN,
                defaultDose = 4000.0,
                unit = SupplementUnit.IU,
                caffeineMgPerServing = 0,
                servingsRemaining = 60.0,
                lowStockThreshold = 10.0,
                updatedAt = now
            ) to SupplementScheduleEntity(
                supplementId = 0L,
                triggerType = SupplementTrigger.WITH_MEAL,
                timeOfDay = LocalTime.of(9, 0),
                updatedAt = now
            ),
            SupplementItemEntity(
                nameEn = "Omega-3",
                nameUk = "Омега-3",
                category = SupplementCategory.OMEGA_3,
                defaultDose = 2.0,
                unit = SupplementUnit.CAPSULE,
                caffeineMgPerServing = 0,
                servingsRemaining = 60.0,
                lowStockThreshold = 10.0,
                updatedAt = now
            ) to SupplementScheduleEntity(
                supplementId = 0L,
                triggerType = SupplementTrigger.WITH_MEAL,
                timeOfDay = LocalTime.of(9, 0),
                updatedAt = now
            ),
            SupplementItemEntity(
                nameEn = "Zinc",
                nameUk = "Цинк",
                category = SupplementCategory.MINERAL,
                defaultDose = 25.0,
                unit = SupplementUnit.MG,
                caffeineMgPerServing = 0,
                servingsRemaining = 60.0,
                lowStockThreshold = 10.0,
                updatedAt = now
            ) to SupplementScheduleEntity(
                supplementId = 0L,
                triggerType = SupplementTrigger.FIXED_TIME,
                timeOfDay = LocalTime.of(14, 0),
                updatedAt = now
            ),
            SupplementItemEntity(
                nameEn = "Pre-Workout (Stim-Free)",
                nameUk = "Передтрен (без кофеїну)",
                category = SupplementCategory.PRE_WORKOUT,
                defaultDose = 1.0,
                unit = SupplementUnit.SCOOP,
                caffeineMgPerServing = 0,
                servingsRemaining = 30.0,
                lowStockThreshold = 5.0,
                updatedAt = now
            ) to SupplementScheduleEntity(
                supplementId = 0L,
                triggerType = SupplementTrigger.BEFORE_WORKOUT,
                offsetMinutes = -30,
                updatedAt = now
            ),
            SupplementItemEntity(
                nameEn = "Whey Protein",
                nameUk = "Протеїн",
                category = SupplementCategory.PROTEIN,
                defaultDose = 1.0,
                unit = SupplementUnit.SCOOP,
                caffeineMgPerServing = 0,
                servingsRemaining = 30.0,
                lowStockThreshold = 5.0,
                updatedAt = now
            ) to SupplementScheduleEntity(
                supplementId = 0L,
                triggerType = SupplementTrigger.AFTER_WORKOUT,
                offsetMinutes = 15,
                updatedAt = now
            ),
            SupplementItemEntity(
                nameEn = "Creatine Monohydrate",
                nameUk = "Креатин моногідрат",
                category = SupplementCategory.CREATINE,
                defaultDose = 5.0,
                unit = SupplementUnit.G,
                caffeineMgPerServing = 0,
                servingsRemaining = 60.0,
                lowStockThreshold = 10.0,
                updatedAt = now
            ) to SupplementScheduleEntity(
                supplementId = 0L,
                triggerType = SupplementTrigger.AFTER_WORKOUT,
                offsetMinutes = 15,
                updatedAt = now
            ),
            SupplementItemEntity(
                nameEn = "Magnesium",
                nameUk = "Магній",
                category = SupplementCategory.MINERAL,
                defaultDose = 400.0,
                unit = SupplementUnit.MG,
                caffeineMgPerServing = 0,
                servingsRemaining = 60.0,
                lowStockThreshold = 10.0,
                updatedAt = now
            ) to SupplementScheduleEntity(
                supplementId = 0L,
                triggerType = SupplementTrigger.BEDTIME,
                timeOfDay = LocalTime.of(23, 15),
                updatedAt = now
            )
        )

        database.withTransaction {
            for ((item, sched) in presets) {
                val id = supplementDao.upsertItem(item)
                supplementDao.upsertSchedule(sched.copy(supplementId = id))
            }
        }
        reminderScheduler.rescheduleAll()
    }

    suspend fun saveSupplement(
        item: SupplementItemEntity,
        schedule: SupplementScheduleEntity?
    ): Long = withContext(Dispatchers.IO) {
        val now = Instant.now()
        val itemId = database.withTransaction {
            val savedId = supplementDao.upsertItem(
                item.copy(updatedAt = now, deletedAt = null, syncState = SyncState.PENDING_UPLOAD)
            )
            if (schedule != null) {
                val existingScheds = supplementDao.getSchedulesForItem(savedId)
                val firstExisting = existingScheds.firstOrNull()
                if (existingScheds.size > 1) {
                    supplementDao.softDeleteSchedulesForItem(savedId, now)
                }
                supplementDao.upsertSchedule(
                    schedule.copy(
                        id = if (existingScheds.size == 1 && firstExisting != null) firstExisting.id else schedule.id,
                        remoteId = if (existingScheds.size == 1 && firstExisting != null) firstExisting.remoteId else schedule.remoteId,
                        supplementId = savedId,
                        updatedAt = now,
                        deletedAt = null,
                        syncState = SyncState.PENDING_UPLOAD
                    )
                )
            }
            savedId
        }
        reminderScheduler.rescheduleAll()
        itemId
    }

    suspend fun deleteSupplement(itemId: Long) = withContext(Dispatchers.IO) {
        val now = Instant.now()
        database.withTransaction {
            val existing = supplementDao.getItemById(itemId) ?: return@withTransaction
            supplementDao.upsertItem(
                existing.copy(deletedAt = now, updatedAt = now, syncState = SyncState.PENDING_UPLOAD)
            )
            supplementDao.softDeleteSchedulesForItem(itemId, now)
        }
        reminderScheduler.rescheduleAll()
    }

    suspend fun restock(itemId: Long, addServings: Double) = withContext(Dispatchers.IO) {
        if (addServings <= 0.0) return@withContext
        val existing = supplementDao.getItemById(itemId) ?: return@withContext
        val now = Instant.now()
        val current = (existing.servingsRemaining ?: 0.0).coerceAtLeast(0.0)
        supplementDao.upsertItem(
            existing.copy(
                servingsRemaining = current + addServings,
                updatedAt = now,
                syncState = SyncState.PENDING_UPLOAD
            )
        )
    }

    suspend fun logIntake(
        itemId: Long,
        doseOverride: Double? = null,
        caffeineMgOverride: Int? = null,
        takenAt: Instant = Instant.now(),
        linkedWorkoutId: Long? = null
    ): Long = withContext(Dispatchers.IO) {
        val logId = database.withTransaction {
            val item = supplementDao.getItemById(itemId) ?: return@withTransaction -1L
            val now = Instant.now()
            val dose = doseOverride ?: item.defaultDose
            val caffeine = caffeineMgOverride ?: item.caffeineMgPerServing

            var targetWorkoutId = linkedWorkoutId
            if (item.category == SupplementCategory.PRE_WORKOUT && targetWorkoutId == null) {
                val active = workoutDao.getActiveWorkoutOnce()
                if (active != null) {
                    targetWorkoutId = active.workout.id
                    if (!active.workout.tookPreWorkout) {
                        workoutDao.updateWorkout(
                            active.workout.copy(
                                tookPreWorkout = true,
                                updatedAt = now,
                                syncState = SyncState.PENDING_UPLOAD
                            )
                        )
                    }
                }
            }

            val insertedLogId = supplementDao.upsertLog(
                SupplementLogEntity(
                    supplementId = item.id,
                    takenAt = takenAt,
                    dose = dose,
                    caffeineMg = caffeine,
                    linkedWorkoutId = targetWorkoutId,
                    updatedAt = now,
                    syncState = SyncState.PENDING_UPLOAD
                )
            )

            item.servingsRemaining?.let { rem ->
                val ratio = if (item.defaultDose > 0.0) (dose / item.defaultDose).coerceAtLeast(0.1) else 1.0
                supplementDao.upsertItem(
                    item.copy(
                        servingsRemaining = (rem - ratio).coerceAtLeast(0.0),
                        updatedAt = now,
                        syncState = SyncState.PENDING_UPLOAD
                    )
                )
            }
            insertedLogId
        }
        logId
    }

    suspend fun logQuickCaffeine(
        mg: Int,
        labelEn: String,
        labelUk: String,
        takenAt: Instant = Instant.now()
    ): Long = withContext(Dispatchers.IO) {
        val now = Instant.now()
        database.withTransaction {
            val existing = supplementDao.getAllItemsOnce()
                .firstOrNull { it.deletedAt == null && it.notes == QUICK_CAFFEINE_TAG }
            val itemId = existing?.id ?: supplementDao.upsertItem(
                SupplementItemEntity(
                    nameEn = "Coffee / Caffeine",
                    nameUk = "Кава / Кофеїн",
                    category = SupplementCategory.OTHER,
                    defaultDose = 1.0,
                    unit = SupplementUnit.SCOOP,
                    caffeineMgPerServing = 100,
                    notes = QUICK_CAFFEINE_TAG,
                    updatedAt = now,
                    syncState = SyncState.PENDING_UPLOAD
                )
            )
            supplementDao.upsertLog(
                SupplementLogEntity(
                    supplementId = itemId,
                    takenAt = takenAt,
                    dose = 1.0,
                    caffeineMg = mg.coerceAtLeast(0),
                    updatedAt = now,
                    syncState = SyncState.PENDING_UPLOAD
                )
            )
        }
    }

    suspend fun undoLog(logId: Long) = withContext(Dispatchers.IO) {
        database.withTransaction {
            val log = supplementDao.getLogById(logId) ?: return@withTransaction
            if (log.deletedAt != null) return@withTransaction
            val now = Instant.now()
            supplementDao.upsertLog(
                log.copy(deletedAt = now, updatedAt = now, syncState = SyncState.PENDING_UPLOAD)
            )

            val item = supplementDao.getItemById(log.supplementId)
            if (item != null && item.servingsRemaining != null) {
                val ratio = if (item.defaultDose > 0.0) (log.dose / item.defaultDose).coerceAtLeast(0.1) else 1.0
                supplementDao.upsertItem(
                    item.copy(
                        servingsRemaining = item.servingsRemaining + ratio,
                        updatedAt = now,
                        syncState = SyncState.PENDING_UPLOAD
                    )
                )
            }

            val linkedWId = log.linkedWorkoutId
            if (linkedWId != null && item?.category == SupplementCategory.PRE_WORKOUT) {
                val remainingForWorkout = supplementDao.findPreWorkoutLogsForWorkout(linkedWId)
                if (remainingForWorkout.isEmpty()) {
                    val w = workoutDao.getWorkoutById(linkedWId)
                    if (w != null && w.tookPreWorkout) {
                        workoutDao.updateWorkout(
                            w.copy(
                                tookPreWorkout = false,
                                updatedAt = now,
                                syncState = SyncState.PENDING_UPLOAD
                            )
                        )
                    }
                }
            }
        }
    }

    suspend fun syncPreWorkoutOnWorkoutStart(workoutId: Long, requestedByUser: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            val now = Instant.now()
            val recentLog = supplementDao.findRecentPreWorkoutLog(now.minusSeconds(90 * 60L))
            if (recentLog != null) {
                if (recentLog.linkedWorkoutId != workoutId) {
                    supplementDao.upsertLog(
                        recentLog.copy(
                            linkedWorkoutId = workoutId,
                            updatedAt = now,
                            syncState = SyncState.PENDING_UPLOAD
                        )
                    )
                }
                return@withContext true
            }
            if (!requestedByUser) return@withContext false

            val preItem = supplementDao.getActiveWithSchedulesOnce()
                .firstOrNull { it.item.deletedAt == null && it.item.category == SupplementCategory.PRE_WORKOUT }
                ?.item
            if (preItem != null) {
                logIntake(itemId = preItem.id, linkedWorkoutId = workoutId, takenAt = now)
            }
            true
        }

    suspend fun syncPreWorkoutOnWorkoutToggle(workoutId: Long, took: Boolean) = withContext(Dispatchers.IO) {
        val now = Instant.now()
        if (took) {
            val alreadyLinked = supplementDao.findPreWorkoutLogsForWorkout(workoutId)
            if (alreadyLinked.isNotEmpty()) return@withContext
            val recentUnlinked = supplementDao.findRecentPreWorkoutLog(now.minusSeconds(90 * 60L))
            if (recentUnlinked != null) {
                supplementDao.upsertLog(
                    recentUnlinked.copy(
                        linkedWorkoutId = workoutId,
                        updatedAt = now,
                        syncState = SyncState.PENDING_UPLOAD
                    )
                )
            } else {
                val preItem = supplementDao.getActiveWithSchedulesOnce()
                    .firstOrNull { it.item.deletedAt == null && it.item.category == SupplementCategory.PRE_WORKOUT }
                    ?.item
                if (preItem != null) {
                    logIntake(itemId = preItem.id, linkedWorkoutId = workoutId, takenAt = now)
                }
            }
        } else {
            val linked = supplementDao.findPreWorkoutLogsForWorkout(workoutId)
            for (log in linked) {
                undoLog(log.id)
            }
        }
    }

    companion object {
        const val QUICK_CAFFEINE_TAG = "__QUICK_CAFFEINE__"
    }
}
