package com.gymtracker.app.data.scale

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gymtracker.app.data.db.BodyWeightDao
import com.gymtracker.app.data.db.BodyWeightEntity
import com.gymtracker.app.data.db.DataSource
import com.gymtracker.app.data.db.SyncState
import com.gymtracker.app.domain.ScaleBodyComposition
import com.gymtracker.app.domain.ScaleBodyType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

private val Context.scaleReadingsStore: DataStore<Preferences> by preferencesDataStore(name = "xiaomi_scale_readings")

@Serializable
data class ScaleReadingSnapshot(
    val dateIso: String,
    val capturedAtEpochSec: Long,
    val weightKg: Double,
    val impedanceOhm: Int? = null,
    val bmi: Double,
    val idealWeightKg: Double,
    val bmrKcal: Int,
    val visceralFat: Int,
    val fatPercent: Double? = null,
    val leanBodyMassKg: Double? = null,
    val waterPercent: Double? = null,
    val muscleMassKg: Double? = null,
    val boneMassKg: Double? = null,
    val proteinPercent: Double? = null,
    val metabolicAge: Int? = null,
    val bodyTypeName: String? = null,
    val deviceName: String? = null,
    val macAddress: String? = null
) {
    fun bodyTypeOrNull(): ScaleBodyType? =
        bodyTypeName?.let { runCatching { ScaleBodyType.valueOf(it) }.getOrNull() }
}

@Singleton
class ScaleReadingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bodyWeightDao: BodyWeightDao
) {
    private object Keys {
        val SNAPSHOTS_JSON = stringPreferencesKey("snapshots_by_date_json")
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val readingsByDate: Flow<Map<LocalDate, ScaleReadingSnapshot>> =
        context.scaleReadingsStore.data.map { prefs ->
            val raw = prefs[Keys.SNAPSHOTS_JSON] ?: return@map emptyMap()
            val decoded = runCatching { json.decodeFromString<Map<String, ScaleReadingSnapshot>>(raw) }
                .getOrDefault(emptyMap())
            decoded.mapNotNull { (k, v) ->
                runCatching { LocalDate.parse(k) to v }.getOrNull()
            }.toMap()
        }

    val latestReading: Flow<ScaleReadingSnapshot?> = readingsByDate.map { byDate ->
        byDate.entries.maxByOrNull { it.key }?.value
    }

    suspend fun saveWeighIn(
        date: LocalDate,
        composition: ScaleBodyComposition,
        deviceName: String?,
        macAddress: String?
    ) {
        val existingBw = bodyWeightDao.findByDateAndSource(date, DataSource.APP)
        val now = Instant.now()
        bodyWeightDao.upsert(
            BodyWeightEntity(
                id = existingBw?.id ?: 0,
                date = date,
                kg = composition.weightKg,
                source = DataSource.APP,
                fatPercent = composition.fatPercent ?: existingBw?.fatPercent,
                chestCm = existingBw?.chestCm,
                armsCm = existingBw?.armsCm,
                waistCm = existingBw?.waistCm,
                hipsCm = existingBw?.hipsCm,
                thighsCm = existingBw?.thighsCm,
                calvesCm = existingBw?.calvesCm,
                shouldersCm = existingBw?.shouldersCm,
                neckCm = existingBw?.neckCm,
                forearmsCm = existingBw?.forearmsCm,
                flaggedOutlier = false,
                remoteId = existingBw?.remoteId ?: UUID.randomUUID().toString(),
                updatedAt = now,
                deletedAt = null,
                syncState = SyncState.PENDING_UPLOAD
            )
        )

        val dateIso = date.toString()
        val nowEpoch = now.epochSecond
        context.scaleReadingsStore.edit { prefs ->
            val current = prefs[Keys.SNAPSHOTS_JSON]?.let { raw ->
                runCatching { json.decodeFromString<Map<String, ScaleReadingSnapshot>>(raw) }.getOrNull()
            }?.toMutableMap() ?: mutableMapOf()

            val prevSameDay = current[dateIso]
            val keepExistingImpedance = composition.impedanceOhm == null &&
                prevSameDay != null &&
                prevSameDay.impedanceOhm != null &&
                (nowEpoch - prevSameDay.capturedAtEpochSec) in 0L..300L &&
                abs(prevSameDay.weightKg - composition.weightKg) < 0.3

            if (!keepExistingImpedance) {
                current[dateIso] = ScaleReadingSnapshot(
                    dateIso = dateIso,
                    capturedAtEpochSec = nowEpoch,
                    weightKg = composition.weightKg,
                    impedanceOhm = composition.impedanceOhm,
                    bmi = composition.bmi,
                    idealWeightKg = composition.idealWeightKg,
                    bmrKcal = composition.bmrKcal,
                    visceralFat = composition.visceralFat,
                    fatPercent = composition.fatPercent,
                    leanBodyMassKg = composition.leanBodyMassKg,
                    waterPercent = composition.waterPercent,
                    muscleMassKg = composition.muscleMassKg,
                    boneMassKg = composition.boneMassKg,
                    proteinPercent = composition.proteinPercent,
                    metabolicAge = composition.metabolicAge,
                    bodyTypeName = composition.bodyType?.name,
                    deviceName = deviceName,
                    macAddress = macAddress?.uppercase()
                )
                prefs[Keys.SNAPSHOTS_JSON] = json.encodeToString(current)
            }
        }
    }

    suspend fun upsertSnapshots(snapshots: List<ScaleReadingSnapshot>) {
        if (snapshots.isEmpty()) return
        context.scaleReadingsStore.edit { prefs ->
            val current = prefs[Keys.SNAPSHOTS_JSON]?.let { raw ->
                runCatching { json.decodeFromString<Map<String, ScaleReadingSnapshot>>(raw) }.getOrNull()
            }?.toMutableMap() ?: mutableMapOf()

            snapshots.forEach { snap ->
                val existing = current[snap.dateIso]
                if (existing == null || snap.capturedAtEpochSec >= existing.capturedAtEpochSec) {
                    current[snap.dateIso] = snap
                }
            }
            prefs[Keys.SNAPSHOTS_JSON] = json.encodeToString(current)
        }
    }
}
