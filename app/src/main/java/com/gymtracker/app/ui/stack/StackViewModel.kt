package com.gymtracker.app.ui.stack

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtracker.app.data.db.SupplementItemEntity
import com.gymtracker.app.data.db.SupplementLogEntity
import com.gymtracker.app.data.db.SupplementScheduleEntity
import com.gymtracker.app.data.db.SupplementWithSchedules
import com.gymtracker.app.data.prefs.UserPreferencesRepository
import com.gymtracker.app.data.repo.SupplementRepository
import com.gymtracker.app.domain.CaffeineAnalysis
import com.gymtracker.app.domain.CaffeineDose
import com.gymtracker.app.domain.CaffeineHalfLifeEngine
import com.gymtracker.app.domain.CyclePhase
import com.gymtracker.app.domain.InteractionKind
import com.gymtracker.app.domain.StackInteraction
import com.gymtracker.app.domain.StackTimeSlot
import com.gymtracker.app.domain.SupplementInteractionEngine
import com.gymtracker.app.domain.SupplementRecommendation
import com.gymtracker.app.domain.SupplementScheduleEngine
import com.gymtracker.app.work.NextReminderInfo
import com.gymtracker.app.work.StackReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class StackDoseItemUi(
    val item: SupplementItemEntity,
    val withSchedules: SupplementWithSchedules,
    val schedule: SupplementScheduleEntity?,
    val slot: StackTimeSlot,
    val takenTodayLogId: Long?,
    val takenAt: Instant?,
    val liveWarning: StackInteraction?,
    val cyclePhase: CyclePhase,
    val recommendation: SupplementRecommendation,
    val isLowStock: Boolean,
    val daysRemaining: Int?
)

data class StackSlotGroupUi(
    val slot: StackTimeSlot,
    val doses: List<StackDoseItemUi>,
    val takenCount: Int,
    val totalCount: Int
) {
    val isComplete: Boolean get() = totalCount > 0 && takenCount >= totalCount
    val pendingCount: Int get() = (totalCount - takenCount).coerceAtLeast(0)
}

data class StackShelfItemUi(
    val withSchedules: SupplementWithSchedules,
    val cyclePhase: CyclePhase,
    val recommendation: SupplementRecommendation,
    val daysRemaining: Int?,
    val isLowStock: Boolean,
    val takenTodayCount: Int,
    val dueTodayCount: Int,
    val liveWarning: StackInteraction?
)

data class StackLogEntryUi(
    val log: SupplementLogEntity,
    val item: SupplementItemEntity?
)

data class StackUiState(
    val slotGroups: List<StackSlotGroupUi> = emptyList(),
    val shelfItems: List<StackShelfItemUi> = emptyList(),
    val lowStockItems: List<StackShelfItemUi> = emptyList(),
    val recentLogs: List<StackLogEntryUi> = emptyList(),
    val todayCaffeineLogs: List<StackLogEntryUi> = emptyList(),
    val interactions: List<StackInteraction> = emptyList(),
    val activeConflictCount: Int = 0,
    val caffeineAnalysis: CaffeineAnalysis? = null,
    val takenTodayCount: Int = 0,
    val totalDueTodayCount: Int = 0,
    val streakDays: Int = 0,
    val adherence14dPercent: Int = 0,
    val bedtimeMinutesOfDay: Int = 0,
    val remindersEnabled: Boolean = true,
    val nextReminder: NextReminderInfo? = null,
    val isStackEmpty: Boolean = true
)

sealed interface StackSnackbarEvent {
    data class LoggedWithUndo(
        val logIds: List<Long>,
        val itemNameEn: String,
        val itemNameUk: String,
        val isBatch: Boolean = false
    ) : StackSnackbarEvent
    data object Restocked : StackSnackbarEvent
    data object TestReminderScheduled : StackSnackbarEvent
}

@HiltViewModel
class StackViewModel @Inject constructor(
    private val supplementRepository: SupplementRepository,
    private val prefsRepo: UserPreferencesRepository,
    private val reminderScheduler: StackReminderScheduler
) : ViewModel() {

    private val clockTick = MutableStateFlow(0L)
    private val nextReminderFlow = MutableStateFlow<NextReminderInfo?>(null)
    private val _events = MutableSharedFlow<StackSnackbarEvent>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()

    val uiState: StateFlow<StackUiState> = combine(
        supplementRepository.observeAllActiveStack(),
        supplementRepository.observeLogsSince(
            LocalDate.now().minusDays(14).atStartOfDay(ZoneId.systemDefault()).toInstant()
        ),
        prefsRepo.preferences,
        nextReminderFlow,
        clockTick
    ) { allActive, logs14d, prefs, nextRem, _ ->
        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        val today = LocalDate.now(zone)
        val todayStart = today.atStartOfDay(zone).toInstant()

        val visibleStack = allActive.filter { it.item.notes != SupplementRepository.QUICK_CAFFEINE_TAG }
        val itemsById = allActive.associateBy { it.item.id }

        val todayLogs = logs14d.filter { it.deletedAt == null && !it.takenAt.isBefore(todayStart) }

        val interactions = SupplementInteractionEngine.analyzeStack(
            items = visibleStack,
            todayLogs = todayLogs,
            now = now,
            bedtimeMinutesOfDay = prefs.bedtimeMinutesOfDay,
            zoneId = zone
        )

        val conflictWarningsByItemId = interactions
            .filter { it.kind == InteractionKind.CONFLICT || it.kind == InteractionKind.CAUTION }
            .flatMap { inter -> inter.involvedItemIds.map { id -> id to inter } }
            .groupBy({ it.first }, { it.second })

        val allDoses = mutableListOf<StackDoseItemUi>()
        val shelfItems = mutableListOf<StackShelfItemUi>()

        for (sw in visibleStack) {
            val rem = sw.item.servingsRemaining
            val thresh = sw.item.lowStockThreshold ?: 5.0
            val lowStock = rem != null && rem <= thresh
            val liveWarn = conflictWarningsByItemId[sw.item.id]?.firstOrNull()
            val cyclePhase = SupplementScheduleEngine.computeCycleStatus(sw.item, today)
            val rec = SupplementInteractionEngine.recommendationFor(sw.item)
            val daysRem = SupplementScheduleEngine.estimateDaysRemaining(sw.item, sw.schedules)

            val matched = if (cyclePhase !is CyclePhase.Off) {
                SupplementScheduleEngine.matchTodayDoses(
                    sw = sw,
                    todayLogs = todayLogs,
                    logScheduleMap = prefs.stackLogScheduleMap,
                    today = today,
                    bedtimeMinutesOfDay = prefs.bedtimeMinutesOfDay,
                    zoneId = zone
                )
            } else emptyList()

            shelfItems += StackShelfItemUi(
                withSchedules = sw,
                cyclePhase = cyclePhase,
                recommendation = rec,
                daysRemaining = daysRem,
                isLowStock = lowStock,
                takenTodayCount = matched.count { it.matchedLog != null },
                dueTodayCount = matched.size,
                liveWarning = liveWarn
            )

            for (dm in matched) {
                allDoses += StackDoseItemUi(
                    item = sw.item,
                    withSchedules = sw,
                    schedule = dm.schedule,
                    slot = dm.slot,
                    takenTodayLogId = dm.matchedLog?.id,
                    takenAt = dm.matchedLog?.takenAt,
                    liveWarning = liveWarn,
                    cyclePhase = cyclePhase,
                    recommendation = rec,
                    isLowStock = lowStock,
                    daysRemaining = daysRem
                )
            }
        }

        val slotOrder = listOf(
            StackTimeSlot.MORNING,
            StackTimeSlot.AFTERNOON,
            StackTimeSlot.WORKOUT,
            StackTimeSlot.EVENING,
            StackTimeSlot.BEDTIME,
            StackTimeSlot.ANYTIME
        )
        val slotGroups = slotOrder.mapNotNull { slot ->
            val groupDoses = allDoses.filter { it.slot == slot }
            if (groupDoses.isEmpty()) null
            else StackSlotGroupUi(
                slot = slot,
                doses = groupDoses,
                takenCount = groupDoses.count { it.takenTodayLogId != null },
                totalCount = groupDoses.size
            )
        }

        val lowStock = shelfItems.filter { it.isLowStock }

        val (streak, adherence) = SupplementScheduleEngine.computeStreakAndAdherence(
            items = visibleStack,
            logs = logs14d,
            today = today,
            zoneId = zone,
            days = 14
        )

        val cafWindowStart = now.minusSeconds(36 * 3600L)
        val caffeineDoses = logs14d.mapNotNull { log ->
            if (log.deletedAt != null || log.takenAt.isBefore(cafWindowStart)) return@mapNotNull null
            val item = itemsById[log.supplementId]?.item
            val mg = log.caffeineMg ?: item?.caffeineMgPerServing ?: 0
            if (mg <= 0) return@mapNotNull null
            CaffeineDose(
                takenAt = log.takenAt,
                mg = mg,
                label = item?.nameEn ?: "Caffeine"
            )
        }

        val cafAnalysis = CaffeineHalfLifeEngine.analyze(
            doses = caffeineDoses,
            now = now,
            bedtimeMinutesOfDay = prefs.bedtimeMinutesOfDay,
            halfLifeHours = prefs.caffeineHalfLifeHours,
            sleepThresholdMg = prefs.caffeineSleepThresholdMg,
            zoneId = zone
        )

        val recentEntries = logs14d
            .filter { it.deletedAt == null }
            .take(50)
            .map { log -> StackLogEntryUi(log = log, item = itemsById[log.supplementId]?.item) }

        val todayCafEntries = todayLogs
            .filter { log ->
                val item = itemsById[log.supplementId]?.item
                val mg = log.caffeineMg ?: item?.caffeineMgPerServing ?: 0
                mg > 0
            }
            .sortedBy { it.takenAt }
            .map { log -> StackLogEntryUi(log = log, item = itemsById[log.supplementId]?.item) }

        StackUiState(
            slotGroups = slotGroups,
            shelfItems = shelfItems,
            lowStockItems = lowStock,
            recentLogs = recentEntries,
            todayCaffeineLogs = todayCafEntries,
            interactions = interactions,
            activeConflictCount = interactions.count { it.kind == InteractionKind.CONFLICT },
            caffeineAnalysis = cafAnalysis,
            takenTodayCount = allDoses.count { it.takenTodayLogId != null },
            totalDueTodayCount = allDoses.size,
            streakDays = streak,
            adherence14dPercent = adherence,
            bedtimeMinutesOfDay = prefs.bedtimeMinutesOfDay,
            remindersEnabled = prefs.stackRemindersEnabled,
            nextReminder = nextRem,
            isStackEmpty = visibleStack.isEmpty()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StackUiState())

    init {
        refreshNow()
    }

    fun refreshNow() {
        viewModelScope.launch {
            nextReminderFlow.value = runCatching { reminderScheduler.getNextScheduledReminder() }.getOrNull()
            clockTick.value++
        }
    }

    fun seedDefaultStack() {
        viewModelScope.launch {
            supplementRepository.seedDefaultUserStack()
            refreshNow()
        }
    }

    fun toggleDose(doseUi: StackDoseItemUi) {
        viewModelScope.launch {
            val existingLogId = doseUi.takenTodayLogId
            if (existingLogId != null) {
                supplementRepository.undoLog(existingLogId)
            } else {
                val logId = supplementRepository.logIntake(
                    itemId = doseUi.item.id,
                    scheduleId = doseUi.schedule?.id
                )
                if (logId > 0L) {
                    _events.tryEmit(
                        StackSnackbarEvent.LoggedWithUndo(
                            logIds = listOf(logId),
                            itemNameEn = doseUi.item.nameEn,
                            itemNameUk = doseUi.item.nameUk
                        )
                    )
                }
            }
            clockTick.value++
        }
    }

    fun logDoseAtTime(doseUi: StackDoseItemUi, takenAt: Instant) {
        viewModelScope.launch {
            val logId = supplementRepository.logIntake(
                itemId = doseUi.item.id,
                takenAt = takenAt,
                scheduleId = doseUi.schedule?.id
            )
            if (logId > 0L) {
                _events.tryEmit(
                    StackSnackbarEvent.LoggedWithUndo(
                        logIds = listOf(logId),
                        itemNameEn = doseUi.item.nameEn,
                        itemNameUk = doseUi.item.nameUk
                    )
                )
            }
            clockTick.value++
        }
    }

    fun takeAllInSlot(group: StackSlotGroupUi, slotLabelEn: String, slotLabelUk: String) {
        viewModelScope.launch {
            val pending = group.doses.filter { it.takenTodayLogId == null }
            if (pending.isEmpty()) return@launch
            val targets = pending.map { it.item.id to it.schedule?.id }
            val createdIds = supplementRepository.logBatch(targets)
            if (createdIds.isNotEmpty()) {
                _events.tryEmit(
                    StackSnackbarEvent.LoggedWithUndo(
                        logIds = createdIds,
                        itemNameEn = slotLabelEn,
                        itemNameUk = slotLabelUk,
                        isBatch = true
                    )
                )
            }
            clockTick.value++
        }
    }

    fun logQuickCaffeine(
        mg: Int,
        labelEn: String,
        labelUk: String,
        takenAt: Instant = Instant.now()
    ) {
        viewModelScope.launch {
            val logId = supplementRepository.logQuickCaffeine(mg, labelEn, labelUk, takenAt)
            if (logId > 0L) {
                _events.tryEmit(
                    StackSnackbarEvent.LoggedWithUndo(
                        logIds = listOf(logId),
                        itemNameEn = labelEn,
                        itemNameUk = labelUk
                    )
                )
            }
            clockTick.value++
        }
    }

    fun updateLogTime(logId: Long, newTakenAt: Instant, caffeineMgOverride: Int? = null) {
        viewModelScope.launch {
            supplementRepository.updateLogTime(logId, newTakenAt, caffeineMgOverride)
            clockTick.value++
        }
    }

    fun undoLogs(logIds: List<Long>) {
        viewModelScope.launch {
            supplementRepository.undoLogs(logIds)
            clockTick.value++
        }
    }

    fun saveSupplement(item: SupplementItemEntity, schedules: List<SupplementScheduleEntity>) {
        viewModelScope.launch {
            supplementRepository.saveSupplement(item, schedules)
            refreshNow()
        }
    }

    fun deleteSupplement(itemId: Long) {
        viewModelScope.launch {
            supplementRepository.deleteSupplement(itemId)
            refreshNow()
        }
    }

    fun restockSupplement(itemId: Long, addServings: Double) {
        viewModelScope.launch {
            supplementRepository.restock(itemId, addServings)
            _events.tryEmit(StackSnackbarEvent.Restocked)
            clockTick.value++
        }
    }

    fun setBedtimeMinutes(minutes: Int) {
        viewModelScope.launch {
            prefsRepo.setBedtimeMinutesOfDay(minutes)
            reminderScheduler.rescheduleAll()
            refreshNow()
        }
    }

    fun setRemindersEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setStackRemindersEnabled(enabled)
            reminderScheduler.rescheduleAll()
            refreshNow()
        }
    }

    fun scheduleTestReminder() {
        reminderScheduler.scheduleTestReminderIn5Sec()
        _events.tryEmit(StackSnackbarEvent.TestReminderScheduled)
    }
}
