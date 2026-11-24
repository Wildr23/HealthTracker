package com.gymtracker.app.data.db

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class Converters {

    @TypeConverter
    fun fromInstant(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun toInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun fromLocalDate(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? = value?.takeIf { it.isNotBlank() }?.let(LocalDate::parse)

    @TypeConverter
    fun fromLocalTime(value: LocalTime?): String? = value?.toString()

    @TypeConverter
    fun toLocalTime(value: String?): LocalTime? = value?.takeIf { it.isNotBlank() }?.let(LocalTime::parse)

    @TypeConverter
    fun fromMuscleList(value: List<Muscle>?): String {
        return value?.joinToString(",") { it.name }.orEmpty()
    }

    @TypeConverter
    fun toMuscleList(value: String?): List<Muscle> {
        if (value.isNullOrBlank()) return emptyList()
        return value.split(",")
            .mapNotNull { token -> runCatching { Muscle.valueOf(token.trim()) }.getOrNull() }
    }

    @TypeConverter
    fun fromIntArray(value: IntArray?): String {
        return value?.joinToString(",").orEmpty()
    }

    @TypeConverter
    fun toIntArray(value: String?): IntArray {
        if (value.isNullOrBlank()) return intArrayOf(0, 0, 0, 0, 0)
        return value.split(",").mapNotNull { it.trim().toIntOrNull() }.toIntArray()
    }

    @TypeConverter fun fromExerciseType(v: ExerciseType?): String? = v?.name
    @TypeConverter fun toExerciseType(v: String?): ExerciseType? = v?.let(ExerciseType::valueOf)

    @TypeConverter fun fromMuscle(v: Muscle?): String? = v?.name
    @TypeConverter fun toMuscle(v: String?): Muscle? = v?.let(Muscle::valueOf)

    @TypeConverter fun fromCalorieTier(v: CalorieTier?): String? = v?.name
    @TypeConverter fun toCalorieTier(v: String?): CalorieTier? = v?.let(CalorieTier::valueOf)

    @TypeConverter fun fromDataSource(v: DataSource?): String? = v?.name
    @TypeConverter fun toDataSource(v: String?): DataSource? = v?.let(DataSource::valueOf)

    @TypeConverter fun fromHcStatus(v: HcStatus?): String? = v?.name
    @TypeConverter fun toHcStatus(v: String?): HcStatus? = v?.let(HcStatus::valueOf)

    @TypeConverter fun fromSetType(v: SetType?): String? = v?.name
    @TypeConverter fun toSetType(v: String?): SetType? = v?.let(SetType::valueOf)

    @TypeConverter fun fromCalorieKind(v: CalorieKind?): String? = v?.name
    @TypeConverter fun toCalorieKind(v: String?): CalorieKind? = v?.let(CalorieKind::valueOf)

    @TypeConverter fun fromSyncState(v: SyncState?): String? = v?.name
    @TypeConverter fun toSyncState(v: String?): SyncState? = v?.let(SyncState::valueOf)

    @TypeConverter fun fromSupplementCategory(v: SupplementCategory?): String? = v?.name
    @TypeConverter fun toSupplementCategory(v: String?): SupplementCategory? = v?.let(SupplementCategory::valueOf)

    @TypeConverter fun fromSupplementUnit(v: SupplementUnit?): String? = v?.name
    @TypeConverter fun toSupplementUnit(v: String?): SupplementUnit? = v?.let(SupplementUnit::valueOf)

    @TypeConverter fun fromSupplementTrigger(v: SupplementTrigger?): String? = v?.name
    @TypeConverter fun toSupplementTrigger(v: String?): SupplementTrigger? = v?.let(SupplementTrigger::valueOf)

    @TypeConverter fun fromHealthEventType(v: HealthEventType?): String? = v?.name
    @TypeConverter fun toHealthEventType(v: String?): HealthEventType? = v?.let(HealthEventType::valueOf)

    @TypeConverter fun fromBodyZone(v: BodyZone?): String? = v?.name
    @TypeConverter fun toBodyZone(v: String?): BodyZone? = v?.let(BodyZone::valueOf)

    @TypeConverter fun fromReportPeriodType(v: ReportPeriodType?): String? = v?.name
    @TypeConverter fun toReportPeriodType(v: String?): ReportPeriodType? = v?.let(ReportPeriodType::valueOf)
}
