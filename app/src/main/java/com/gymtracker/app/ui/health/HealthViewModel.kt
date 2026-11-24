package com.gymtracker.app.ui.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gymtracker.app.data.db.BodyWeightDao
import com.gymtracker.app.data.db.BodyWeightEntity
import com.gymtracker.app.data.db.DataSource
import com.gymtracker.app.data.prefs.UserPreferencesRepository
import com.gymtracker.app.data.repo.WorkoutRepository
import com.gymtracker.app.data.scale.ScaleReadingSnapshot
import com.gymtracker.app.data.scale.ScaleReadingsRepository
import com.gymtracker.app.data.scale.XiaomiScaleBleController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

data class HealthUiState(
    val bodyWeights: List<BodyWeightEntity> = emptyList(),
    val latestStats: BodyWeightEntity? = null,
    val heightCm: Int = 182,
    val birthYear: Int = 1990,
    val isMale: Boolean = true,
    val savedBannerVisible: Boolean = false,
    val scaleReadingsByDate: Map<LocalDate, ScaleReadingSnapshot> = emptyMap(),
    val latestScaleReading: ScaleReadingSnapshot? = null,
    val scaleBoundName: String? = null,
    val scaleBoundMac: String? = null,
    val scaleBackgroundEnabled: Boolean = true,
    val scaleWeightRange: Pair<Double, Double> = 93.0 to 117.0
)

@HiltViewModel
class HealthViewModel @Inject constructor(
    private val bodyWeightDao: BodyWeightDao,
    private val workoutRepository: WorkoutRepository,
    private val prefsRepo: UserPreferencesRepository,
    private val scaleReadingsRepository: ScaleReadingsRepository,
    private val bleController: XiaomiScaleBleController
) : ViewModel() {

    private val savedFlag = MutableStateFlow(false)

    val uiState: StateFlow<HealthUiState> = combine(
        bodyWeightDao.observeAll(),
        prefsRepo.preferences,
        scaleReadingsRepository.readingsByDate,
        savedFlag
    ) { list, prefs, scaleMap, saved ->
        val valid = list.firstOrNull { !it.flaggedOutlier } ?: list.firstOrNull()
        val latestScale = scaleMap.entries.maxByOrNull { it.key }?.value
        val range = bleController.resolveWeightRange(prefs, valid?.kg)
        HealthUiState(
            bodyWeights = list,
            latestStats = valid,
            heightCm = prefs.heightCm,
            birthYear = prefs.birthYear,
            isMale = prefs.isMale,
            savedBannerVisible = saved,
            scaleReadingsByDate = scaleMap,
            latestScaleReading = latestScale,
            scaleBoundName = prefs.scaleName,
            scaleBoundMac = prefs.scaleMacAddress,
            scaleBackgroundEnabled = prefs.scaleBackgroundEnabled,
            scaleWeightRange = range
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HealthUiState())

    fun addBodyWeight(kg: Double, date: LocalDate = LocalDate.now()) {
        viewModelScope.launch {
            workoutRepository.addManualBodyWeight(date, kg)
        }
    }

    fun toggleOutlier(entry: BodyWeightEntity) {
        viewModelScope.launch {
            workoutRepository.toggleBodyWeightOutlier(entry)
        }
    }

    fun saveBodyStats(
        kg: Double,
        fatPercent: Double?,
        waistCm: Double?,
        chestCm: Double?,
        armsCm: Double?,
        forearmsCm: Double?,
        shouldersCm: Double?,
        hipsCm: Double?,
        thighsCm: Double?,
        calvesCm: Double?,
        neckCm: Double?,
        heightCm: Int
    ) {
        viewModelScope.launch {
            prefsRepo.setHeightCm(heightCm)
            val today = LocalDate.now()
            val existing = bodyWeightDao.findByDateAndSource(today, DataSource.APP)
            bodyWeightDao.upsert(
                BodyWeightEntity(
                    id = existing?.id ?: 0,
                    date = today,
                    kg = kg,
                    source = DataSource.APP,
                    fatPercent = fatPercent,
                    chestCm = chestCm,
                    armsCm = armsCm,
                    waistCm = waistCm,
                    hipsCm = hipsCm,
                    thighsCm = thighsCm,
                    calvesCm = calvesCm,
                    shouldersCm = shouldersCm,
                    neckCm = neckCm,
                    forearmsCm = forearmsCm,
                    flaggedOutlier = false,
                    remoteId = existing?.remoteId ?: java.util.UUID.randomUUID().toString(),
                    updatedAt = Instant.now()
                )
            )
            savedFlag.value = true
        }
    }

    fun clearSavedBanner() {
        savedFlag.value = false
    }
}
