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

data class StackChecklistItemUi(
    val withSchedules: SupplementWithSchedules,
    val slot: StackTimeSlot,
    val cyclePhase: CyclePhase,
    val recommendation: SupplementRecommendation,
    val takenTodayLogId: Long?,
    val daysRemaining: Int?,
    val isLowStock: Boolean,
    val liveWarning: StackInteraction?
)

data class StackLogEntryUi(
    val log: SupplementLogEntity,
    val item: SupplementItemEntity?
)

data class StackUiState(
    val dueTodayItems: List<StackChecklistItemUi> = emptyList(),
    val inventoryItems: List<StackChecklistItemUi> = emptyList(),
    val lowStockItems: List<StackChecklistItemUi> = emptyList(),
    val recentLogs: List<StackLogEntryUi> = emptyList(),
    val interactions: List<StackInteraction> = emptyList(),
    val caffeineAnalysis: CaffeineAnalysis? = null,
    val takenTodayCount: Int = 0,
    val totalDueTodayCount: Int = 0,
    val streakDays: Int = 0,
    val adherence14dPercent: Int = 0,
    val bedtimeMinutesOfDay: Int = 0,
    val remindersEnabled: Boolean = true,
    val isStackEmpty: Boolean = true
)

sealed interface StackSnackbarEvent {
    data class LoggedWithUndo(val logId: Long, val itemNameEn: String, val itemNameUk: String) : StackSnackbarEvent
    data object Restocked : StackSnackbarEvent
}

@HiltViewModel
class StackViewModel @Inject constructor(
    private val supplementRepository: SupplementRepository,
    private val prefsRepo: UserPreferencesRepository,
    private val reminderScheduler: StackReminderScheduler
) : ViewModel() {

    private val clockTick = MutableStateFlow(0L)
    private val _events = MutableSharedFlow<StackSnackbarEvent>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()

    val uiState: StateFlow<StackUiState> = combine(
        supplementRepository.observeAllActiveStack(),
        supplementRepository.observeLogsSince(
            LocalDate.now().minusDays(14).atStartOfDay(ZoneId.systemDefault()).toInstant()
        ),
        prefsRepo.preferences,
        clockTick
    ) { allActive, logs14d, prefs, _ ->
        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        val today = LocalDate.now(zone)
        val todayStart = today.atStartOfDay(zone).toInstant()

        val visibleStack = allActive.filter { it.item.notes != SupplementRepository.QUICK_CAFFEINE_TAG }
        val itemsById = allActive.associateBy { it.item.id }

        val todayLogs = logs14d.filter { it.deletedAt == null && !it.takenAt.isBefore(todayStart) }
        val latestTodayLogByItemId = mutableMapOf<Long, SupplementLogEntity>()
        for (l in todayLogs.sortedBy { it.takenAt }) {
            latestTodayLogByItemId[l.supplementId] = l
        }

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

        val allUi = visibleStack.map { sw ->
            val rem = sw.item.servingsRemaining
            val thresh = sw.item.lowStockThreshold ?: 5.0
            val lowStock = rem != null && rem <= thresh
            val liveWarn = conflictWarningsByItemId[sw.item.id]?.firstOrNull()
            StackChecklistItemUi(
                withSchedules = sw,
                slot = SupplementScheduleEngine.slotFor(sw.schedules, sw.item),
                cyclePhase = SupplementScheduleEngine.computeCycleStatus(sw.item, today),
                recommendation = SupplementInteractionEngine.recommendationFor(sw.item),
                takenTodayLogId = latestTodayLogByItemId[sw.item.id]?.id,
                daysRemaining = SupplementScheduleEngine.estimateDaysRemaining(sw.item, sw.schedules),
                isLowStock = lowStock,
                liveWarning = liveWarn
            )
        }

        val dueToday = allUi.filter { ui ->
            ui.cyclePhase !is CyclePhase.Off &&
                SupplementScheduleEngine.isScheduledForDate(ui.withSchedules.schedules, today)
        }

        val lowStock = allUi.filter { it.isLowStock }

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
            .take(30)
            .map { log -> StackLogEntryUi(log = log, item = itemsById[log.supplementId]?.item) }

        StackUiState(
            dueTodayItems = dueToday,
            inventoryItems = allUi,
            lowStockItems = lowStock,
            recentLogs = recentEntries,
            interactions = interactions,
            caffeineAnalysis = cafAnalysis,
            takenTodayCount = dueToday.count { it.takenTodayLogId != null },
            totalDueTodayCount = dueToday.size,
            streakDays = streak,
            adherence14dPercent = adherence,
            bedtimeMinutesOfDay = prefs.bedtimeMinutesOfDay,
            remindersEnabled = prefs.stackRemindersEnabled,
            isStackEmpty = visibleStack.isEmpty()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StackUiState())

    fun refreshNow() {
        clockTick.value++
    }

    fun seedDefaultStack() {
        viewModelScope.launch {
            supplementRepository.seedDefaultUserStack()
            clockTick.value++
        }
    }

    fun toggleTakenToday(itemUi: StackChecklistItemUi) {
        viewModelScope.launch {
            val existingLogId = itemUi.takenTodayLogId
            if (existingLogId != null) {
                supplementRepository.undoLog(existingLogId)
            } else {
                val item = itemUi.withSchedules.item
                val logId = supplementRepository.logIntake(item.id)
                if (logId > 0L) {
                    _events.tryEmit(StackSnackbarEvent.LoggedWithUndo(logId, item.nameEn, item.nameUk))
                }
            }
            clockTick.value++
        }
    }

    fun logQuickCaffeine(mg: Int, labelEn: String, labelUk: String) {
        viewModelScope.launch {
            val logId = supplementRepository.logQuickCaffeine(mg, labelEn, labelUk)
            if (logId > 0L) {
                _events.tryEmit(StackSnackbarEvent.LoggedWithUndo(logId, labelEn, labelUk))
            }
            clockTick.value++
        }
    }

    fun undoLog(logId: Long) {
        viewModelScope.launch {
            supplementRepository.undoLog(logId)
            clockTick.value++
        }
    }

    fun saveSupplement(item: SupplementItemEntity, schedule: SupplementScheduleEntity?) {
        viewModelScope.launch {
            supplementRepository.saveSupplement(item, schedule)
            clockTick.value++
        }
    }

    fun deleteSupplement(itemId: Long) {
        viewModelScope.launch {
            supplementRepository.deleteSupplement(itemId)
            clockTick.value++
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
            clockTick.value++
        }
    }

    fun setRemindersEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setStackRemindersEnabled(enabled)
            reminderScheduler.rescheduleAll()
        }
    }
}
