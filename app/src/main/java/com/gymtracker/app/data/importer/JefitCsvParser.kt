package com.gymtracker.app.data.importer

import com.gymtracker.app.data.db.ExerciseEntity
import com.gymtracker.app.data.db.ExerciseType
import com.gymtracker.app.data.db.SetType
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.round

@Serializable
data class SetCountDisagreement(
    val exerciseLogId: String,
    val date: String,
    val eid: Int,
    val exerciseName: String,
    val logsStringCount: Int,
    val setLogsCount: Int,
    val logsRaw: String
)

@Serializable
data class SpecialMappingEntry(
    val exerciseLogId: String,
    val date: String,
    val eid: Int,
    val originalName: String,
    val mappedSlug: String
)

@Serializable
data class BodyWeightOutlierReport(
    val date: String,
    val kg: Double
)

@Serializable
data class JefitImportReport(
    val sessionsRead: Int,
    val sessionsImported: Int,
    val sessionsSkippedEmpty: Int,
    val skippedEmptySessionIds: List<String>,
    val exerciseLogsCount: Int,
    val setLogRowsCount: Int,
    val timestampedStrengthWorkoutsCount: Int,
    val timestampedStrengthSetsCount: Int,
    val externalHcDuplicatesCount: Int,
    val distinctEidsMapped: Int,
    val unmappedEids: List<Int>,
    val routinesCount: Int,
    val routineDaysCount: Int,
    val bodyWeightsCount: Int,
    val bodyWeightOutliers: List<BodyWeightOutlierReport>,
    val notesAttachedCount: Int,
    val specialMappings: List<SpecialMappingEntry>,
    val setCountDisagreements: List<SetCountDisagreement>,
    val firstWorkoutDate: String?,
    val lastWorkoutDate: String?
)

data class ParsedBodyWeight(
    val date: LocalDate,
    val kg: Double,
    val fatPercent: Double? = null,
    val chestCm: Double? = null,
    val armsCm: Double? = null,
    val waistCm: Double? = null,
    val calvesCm: Double? = null,
    val hipsCm: Double? = null,
    val thighsCm: Double? = null,
    val shouldersCm: Double? = null,
    val neckCm: Double? = null,
    val forearmsCm: Double? = null,
    val flaggedOutlier: Boolean
)

data class ParsedRoutineDayExercise(
    val exerciseSlug: String,
    val orderIndex: Int,
    val targetSets: Int,
    val targetReps: String?,
    val restSeconds: Int?,
    val supersetGroup: Int?
)

data class ParsedRoutineDay(
    val externalId: String,
    val name: String,
    val orderIndex: Int,
    val exercises: List<ParsedRoutineDayExercise>
)

data class ParsedRoutine(
    val externalId: String,
    val name: String,
    val days: List<ParsedRoutineDay>
)

data class ParsedWorkoutSet(
    val setIndex: Int,
    val weightKg: Double?,
    val reps: Int?,
    val durationSec: Int?,
    val distanceKm: Double?,
    val calories: Double?,
    val setType: SetType = SetType.WORK,
    val completedAt: Instant?,
    val isEstimatedTime: Boolean = false
)

data class ParsedWorkoutExercise(
    val jefitLogId: String,
    val exerciseSlug: String,
    val orderIndex: Int,
    val note: String?,
    val isExternalHcDuplicate: Boolean,
    val sets: List<ParsedWorkoutSet>
)

data class ParsedWorkout(
    val externalId: String,
    val startedAt: Instant,
    val endedAt: Instant?,
    val routineDayExternalId: String?,
    val title: String?,
    val excludedFromStats: Boolean,
    val exercises: List<ParsedWorkoutExercise>
)

data class JefitParsedBundle(
    val bodyWeights: List<ParsedBodyWeight>,
    val routines: List<ParsedRoutine>,
    val workouts: List<ParsedWorkout>,
    val report: JefitImportReport
)

/**
 * Parses a full JEFIT CSV export (with `### SECTION ####` banners, multi-line ROUTINES,
 * WORKOUT SESSIONS, EXERCISE LOGS, EXERCISE SET LOGS, CARDIO EXERCISE LOGS, PROFILE, NOTES).
 */
class JefitCsvParser(
    private val catalog: List<ExerciseEntity>,
    private val zoneId: ZoneId = ZoneId.of("Europe/Kyiv")
) {
    companion object {
        private const val LBS_TO_KG = 2.20462262
        private val EXTERNAL_HC_EIDS = setOf(315, 317, 322)
        private val BANNER_REGEX = Regex("^###\\s+([A-Z ]+?)\\s+#+\\s*$")
        private val SEPARATOR_REGEX = Regex("^#{10,}\\s*$")
    }

    private val defaultByEid: Map<Int, ExerciseEntity> = catalog
        .filter { it.jefitEid != null && it.jefitNameMatch == null }
        .associateBy { it.jefitEid!! }

    private val specialByEidAndName: Map<Pair<Int, String>, ExerciseEntity> = catalog
        .filter { it.jefitEid != null && it.jefitNameMatch != null }
        .associateBy { it.jefitEid!! to it.jefitNameMatch!!.lowercase() }

    private val cardioEids: Set<Int> = catalog
        .filter { it.type == ExerciseType.CARDIO && it.jefitEid != null }
        .map { it.jefitEid!! }
        .toSet()

    fun parse(rawCsv: String): JefitParsedBundle {
        val sections = splitSections(rawCsv)

        val bodyWeights = parseProfile(sections["PROFILE"].orEmpty())
        val routines = parseRoutines(sections["ROUTINES"].orEmpty())
        val dayNameByExternalId = routines
            .flatMap { it.days }
            .associate { it.externalId to it.name }

        val cardioEnrichmentByElId = parseCardioExerciseLogs(sections["CARDIO EXERCISE LOGS"].orEmpty())
        val notesByDateAndEid = parseNotes(sections["NOTES"].orEmpty())

        val setLogsRows = parseTableWithHeader(sections["EXERCISE SET LOGS"].orEmpty())
        val setLogsByElId = groupSetLogsByExerciseLogId(setLogsRows)

        val exerciseLogRows = parseTableWithHeader(sections["EXERCISE LOGS"].orEmpty())
        val sessionRows = parseTableWithHeader(sections["WORKOUT SESSIONS"].orEmpty())

        val parsedLogsBySessionId = mutableMapOf<String, MutableList<Pair<Long, ParsedWorkoutExercise>>>()
        val seenEids = mutableSetOf<Int>()
        val unmappedEids = mutableSetOf<Int>()
        val specialMappings = mutableListOf<SpecialMappingEntry>()
        val disagreements = mutableListOf<SetCountDisagreement>()
        var externalHcCount = 0
        var notesAttached = 0

        for (row in exerciseLogRows) {
            val elId = row["_id"].orEmpty().trim()
            val belongSession = row["belongsession"].orEmpty().trim()
            val eid = row["eid"]?.trim()?.toIntOrNull() ?: continue
            val ename = row["ename"].orEmpty().trim()
            val myDate = row["mydate"].orEmpty().trim()
            val logsRaw = row["logs"].orEmpty().trim()
            val logTime = row["logTime"]?.trim()?.toLongOrNull() ?: 0L

            seenEids += eid
            val mappedExercise = resolveExercise(eid, ename)
            if (mappedExercise == null) {
                unmappedEids += eid
                continue
            }
            if (mappedExercise.jefitNameMatch != null) {
                specialMappings += SpecialMappingEntry(
                    exerciseLogId = elId,
                    date = myDate,
                    eid = eid,
                    originalName = ename,
                    mappedSlug = mappedExercise.slug
                )
            }

            val isExternalHc = ename.isBlank() && eid in EXTERNAL_HC_EIDS
            if (isExternalHc) externalHcCount++

            val setLogsForEl = setLogsByElId[elId].orEmpty().sortedBy { it.setIndex }
            val logsTokenCount = if (logsRaw.isBlank()) 0 else logsRaw.split(",").size
            if (setLogsForEl.isNotEmpty() && logsTokenCount != setLogsForEl.size) {
                disagreements += SetCountDisagreement(
                    exerciseLogId = elId,
                    date = myDate,
                    eid = eid,
                    exerciseName = ename.ifBlank { mappedExercise.nameEn },
                    logsStringCount = logsTokenCount,
                    setLogsCount = setLogsForEl.size,
                    logsRaw = logsRaw
                )
            }

            val sets = buildSets(
                exerciseType = mappedExercise.type,
                logsRaw = logsRaw,
                setLogs = setLogsForEl,
                cardioEnrichment = cardioEnrichmentByElId[elId]
            )

            val noteKey = myDate to eid
            val noteText = notesByDateAndEid[noteKey]?.takeIf { it.isNotBlank() }
            if (noteText != null) {
                notesAttached++
                notesByDateAndEid.remove(noteKey)
            }

            val parsedExercise = ParsedWorkoutExercise(
                jefitLogId = elId,
                exerciseSlug = mappedExercise.slug,
                orderIndex = 0, // assigned after sorting per session
                note = noteText,
                isExternalHcDuplicate = isExternalHc,
                sets = sets
            )
            parsedLogsBySessionId.getOrPut(belongSession) { mutableListOf() }
                .add(logTime to parsedExercise)
        }

        val workouts = mutableListOf<ParsedWorkout>()
        val skippedEmptySessionIds = mutableListOf<String>()
        var timestampedStrengthWorkouts = 0
        var timestampedStrengthSets = 0

        for (row in sessionRows) {
            val sessionId = row["_id"].orEmpty().trim()
            if (sessionId.isBlank()) continue
            val startSec = row["starttime"]?.trim()?.toLongOrNull() ?: continue
            val endSec = row["endtime"]?.trim()?.toLongOrNull()
            val dayId = row["day_id"]?.trim().orEmpty()

            val logsForSession = parsedLogsBySessionId[sessionId]
            if (logsForSession.isNullOrEmpty()) {
                skippedEmptySessionIds += sessionId
                continue
            }

            val orderedExercises = logsForSession
                .sortedBy { it.first }
                .mapIndexed { idx, (_, ex) -> ex.copy(orderIndex = idx) }

            val allExternalHc = orderedExercises.all { it.isExternalHcDuplicate }
            val routineDayExtId = if (dayId.isNotBlank() && dayId != "0") "jefit:day:$dayId" else null
            val dayTitle = routineDayExtId?.let { dayNameByExternalId[it] }
                ?: if (allExternalHc) "External Cardio (HC)" else null

            val strengthSetsWithTime = orderedExercises.sumOf { ex ->
                val exType = catalog.firstOrNull { it.slug == ex.exerciseSlug }?.type
                if (exType != ExerciseType.CARDIO) {
                    ex.sets.count { it.completedAt != null }
                } else 0
            }
            if (strengthSetsWithTime > 0) {
                timestampedStrengthWorkouts++
                timestampedStrengthSets += strengthSetsWithTime
            }

            workouts += ParsedWorkout(
                externalId = "jefit:session:$sessionId",
                startedAt = Instant.ofEpochSecond(startSec),
                endedAt = endSec?.takeIf { it > startSec }?.let { Instant.ofEpochSecond(it) },
                routineDayExternalId = routineDayExtId,
                title = dayTitle,
                excludedFromStats = allExternalHc,
                exercises = orderedExercises
            )
        }

        val sortedWorkouts = workouts.sortedBy { it.startedAt }
        val nonExcluded = sortedWorkouts.filter { !it.excludedFromStats }
        val firstDate = nonExcluded.firstOrNull()?.startedAt?.atZone(zoneId)?.toLocalDate()?.toString()
        val lastDate = nonExcluded.lastOrNull()?.startedAt?.atZone(zoneId)?.toLocalDate()?.toString()

        val report = JefitImportReport(
            sessionsRead = sessionRows.size,
            sessionsImported = workouts.size,
            sessionsSkippedEmpty = skippedEmptySessionIds.size,
            skippedEmptySessionIds = skippedEmptySessionIds,
            exerciseLogsCount = exerciseLogRows.size,
            setLogRowsCount = setLogsRows.size,
            timestampedStrengthWorkoutsCount = timestampedStrengthWorkouts,
            timestampedStrengthSetsCount = timestampedStrengthSets,
            externalHcDuplicatesCount = externalHcCount,
            distinctEidsMapped = seenEids.size,
            unmappedEids = unmappedEids.sorted(),
            routinesCount = routines.size,
            routineDaysCount = routines.sumOf { it.days.size },
            bodyWeightsCount = bodyWeights.size,
            bodyWeightOutliers = bodyWeights.filter { it.flaggedOutlier }.map {
                BodyWeightOutlierReport(it.date.toString(), it.kg)
            },
            notesAttachedCount = notesAttached,
            specialMappings = specialMappings,
            setCountDisagreements = disagreements,
            firstWorkoutDate = firstDate,
            lastWorkoutDate = lastDate
        )

        return JefitParsedBundle(
            bodyWeights = bodyWeights,
            routines = routines,
            workouts = sortedWorkouts,
            report = report
        )
    }

    private fun resolveExercise(eid: Int, ename: String): ExerciseEntity? {
        if (ename.isNotBlank()) {
            specialByEidAndName[eid to ename.lowercase()]?.let { return it }
        }
        return defaultByEid[eid]
    }

    private fun splitSections(rawCsv: String): Map<String, String> {
        val sections = linkedMapOf<String, String>()
        var currentName: String? = null
        val currentBody = StringBuilder()

        rawCsv.lineSequence().forEach { rawLine ->
            val line = rawLine.removePrefix("\uFEFF")
            val match = BANNER_REGEX.matchEntire(line.trim())
            if (match != null) {
                currentName?.let { name ->
                    sections[name] = currentBody.toString().trim()
                }
                currentName = match.groupValues[1].trim()
                currentBody.setLength(0)
            } else if (currentName != null) {
                if (!SEPARATOR_REGEX.matches(line.trim())) {
                    currentBody.append(line).append('\n')
                }
            }
        }
        currentName?.let { name ->
            sections[name] = currentBody.toString().trim()
        }
        return sections
    }

    private fun parseTableWithHeader(body: String): List<Map<String, String>> {
        val rows = Rfc4180CsvParser.parse(body)
        if (rows.size < 2) return emptyList()
        val header = rows.first()
        return rows.drop(1).map { values ->
            buildMap {
                header.forEachIndexed { idx, colName ->
                    put(colName.trim(), values.getOrElse(idx) { "" })
                }
            }
        }
    }

    private fun parseProfile(body: String): List<ParsedBodyWeight> {
        val rows = parseTableWithHeader(body)
        data class RawProfile(val date: LocalDate, val logTime: Long, val row: Map<String, String>, val kg: Double)

        val validByDate = linkedMapOf<LocalDate, RawProfile>()
        for (row in rows) {
            val weight = row["weight"]?.trim()?.toDoubleOrNull() ?: continue
            if (weight <= 0.0) continue
            val dateStr = row["mydate"]?.trim().orEmpty()
            val date = runCatching { LocalDate.parse(dateStr) }.getOrNull() ?: continue
            val logTime = row["logTime"]?.trim()?.toLongOrNull() ?: 0L
            val existing = validByDate[date]
            if (existing == null || logTime >= existing.logTime) {
                validByDate[date] = RawProfile(date, logTime, row, round2(weight))
            }
        }

        val sorted = validByDate.values.sortedBy { it.date }
        if (sorted.isEmpty()) return emptyList()

        return sorted.mapIndexed { idx, item ->
            // Compare against median of other measurements within +/- 180 days
            val nearby = sorted.filterIndexed { otherIdx, other ->
                otherIdx != idx && abs(java.time.temporal.ChronoUnit.DAYS.between(item.date, other.date)) <= 180L
            }.map { it.kg }.sorted()

            val isOutlier = if (nearby.size >= 2) {
                val median = nearby[nearby.size / 2]
                median > 0.0 && abs(item.kg - median) / median > 0.20
            } else {
                false
            }

            fun optPos(key: String): Double? = item.row[key]?.trim()?.toDoubleOrNull()?.takeIf { it > 0.0 }

            ParsedBodyWeight(
                date = item.date,
                kg = item.kg,
                fatPercent = optPos("fatpercent"),
                chestCm = optPos("chest"),
                armsCm = optPos("arms"),
                waistCm = optPos("waist"),
                calvesCm = optPos("calves"),
                hipsCm = optPos("hips"),
                thighsCm = optPos("thighs"),
                shouldersCm = optPos("shoulders"),
                neckCm = optPos("neck"),
                forearmsCm = optPos("forearms"),
                flaggedOutlier = isOutlier
            )
        }
    }

    private fun parseRoutines(body: String): List<ParsedRoutine> {
        val rows = Rfc4180CsvParser.parse(body)
        if (rows.isEmpty()) return emptyList()

        data class DayMutable(
            val externalId: String,
            val packageId: String,
            val name: String,
            val orderIndex: Int,
            val exercises: MutableList<ParsedRoutineDayExercise> = mutableListOf()
        )

        val routines = mutableMapOf<String, String>() // extId -> name
        val days = linkedMapOf<String, DayMutable>()

        var schema: String? = null
        var headerMap: Map<String, Int> = emptyMap()

        for (row in rows) {
            if (row.firstOrNull()?.trim() == "row_id") {
                headerMap = row.mapIndexed { idx, col -> col.trim() to idx }.toMap()
                schema = when {
                    headerMap.containsKey("difficulty") && headerMap.containsKey("dayaweek") -> "ROUTINE"
                    headerMap.containsKey("package") && headerMap.containsKey("dayIndex") -> "DAY"
                    headerMap.containsKey("belongplan") && headerMap.containsKey("exercise_id") -> "DAY_EX"
                    else -> null
                }
                continue
            }
            fun col(name: String): String = headerMap[name]?.let { row.getOrElse(it) { "" } }?.trim().orEmpty()

            when (schema) {
                "ROUTINE" -> {
                    val id = col("_id")
                    val name = col("name")
                    if (id.isNotBlank() && name.isNotBlank()) {
                        routines[id] = name
                    }
                }
                "DAY" -> {
                    val id = col("_id")
                    val pkg = col("package")
                    val name = col("name")
                    val sort = col("sort_order").toIntOrNull() ?: days.size
                    if (id.isNotBlank() && pkg.isNotBlank()) {
                        days[id] = DayMutable(
                            externalId = "jefit:day:$id",
                            packageId = pkg,
                            name = name.ifBlank { "Day" },
                            orderIndex = sort
                        )
                    }
                }
                "DAY_EX" -> {
                    val dayId = col("belongplan")
                    val eid = col("exercise_id").toIntOrNull() ?: continue
                    val exName = col("exercisename")
                    val mapped = resolveExercise(eid, exName) ?: continue
                    val day = days[dayId] ?: continue
                    val order = col("mysort").toIntOrNull() ?: day.exercises.size
                    val sets = col("setcount").toIntOrNull() ?: 3
                    val targetReps = col("targetrep").takeIf { it.isNotBlank() && it != "0" }
                    val timer = col("timer").toIntOrNull()?.takeIf { it > 0 }
                    val superset = col("superset").toIntOrNull()?.takeIf { it > 0 }
                    day.exercises += ParsedRoutineDayExercise(
                        exerciseSlug = mapped.slug,
                        orderIndex = order,
                        targetSets = sets,
                        targetReps = targetReps,
                        restSeconds = timer,
                        supersetGroup = superset
                    )
                }
            }
        }

        return routines.map { (rId, rName) ->
            val rDays = days.values
                .filter { it.packageId == rId }
                .sortedBy { it.orderIndex }
                .map { d ->
                    ParsedRoutineDay(
                        externalId = d.externalId,
                        name = d.name,
                        orderIndex = d.orderIndex,
                        exercises = d.exercises.sortedBy { it.orderIndex }
                    )
                }
            ParsedRoutine(
                externalId = "jefit:routine:$rId",
                name = rName,
                days = rDays
            )
        }
    }

    private data class CardioEnrichment(
        val calories: Double?,
        val distanceKm: Double?,
        val durationSec: Int?
    )

    private fun parseCardioExerciseLogs(body: String): Map<String, CardioEnrichment> {
        val rows = parseTableWithHeader(body)
        return rows.mapNotNull { row ->
            val elId = row["el_id"]?.trim().orEmpty()
            if (elId.isBlank()) return@mapNotNull null
            val cal = row["calorie_logs"]?.trim()?.toDoubleOrNull()?.takeIf { it > 0.0 }
            val dist = row["distance_logs"]?.trim()?.toDoubleOrNull()?.takeIf { it > 0.0 }
            val dur = row["cardio_logs"]?.trim()?.removePrefix("0x")?.toIntOrNull()?.takeIf { it > 0 }
            elId to CardioEnrichment(cal, dist, dur)
        }.toMap()
    }

    private fun parseNotes(body: String): MutableMap<Pair<String, Int>, String> {
        val map = mutableMapOf<Pair<String, Int>, String>()
        for (row in parseTableWithHeader(body)) {
            val eid = row["eid"]?.trim()?.toIntOrNull() ?: continue
            if (eid <= 0) continue
            val date = row["mydate"]?.trim().orEmpty()
            val note = row["mynote"]?.trim().orEmpty()
            if (date.isNotBlank() && note.isNotBlank()) {
                val key = date to eid
                val existing = map[key]
                map[key] = if (existing == null) note else "$existing\n$note"
            }
        }
        return map
    }

    private data class RawSetLog(
        val setIndex: Int,
        val weightKg: Double?,
        val reps: Int?,
        val durationSec: Int?,
        val calories: Double?,
        val distanceKm: Double?,
        val completedAt: Instant?
    )

    private fun groupSetLogsByExerciseLogId(rows: List<Map<String, String>>): Map<String, List<RawSetLog>> {
        val result = mutableMapOf<String, MutableList<RawSetLog>>()
        for (row in rows) {
            val elId = row["exercise_log_id"]?.trim().orEmpty()
            if (elId.isBlank()) continue
            val setIdx = row["set_index"]?.trim()?.toIntOrNull() ?: 0
            val reps = row["reps"]?.trim()?.toIntOrNull()
            val weightLbs = row["weight_lbs"]?.trim()?.toDoubleOrNull()
            val weightKg = when {
                weightLbs != null -> roundQuarterKg(weightLbs / LBS_TO_KG)
                reps != null -> 0.0
                else -> null
            }
            val duration = row["duration"]?.trim()?.toIntOrNull()?.takeIf { it > 0 }
            val cal = row["calories"]?.trim()?.toDoubleOrNull()?.takeIf { it > 0.0 }
            val dist = row["distance_mi"]?.trim()?.toDoubleOrNull()?.takeIf { it > 0.0 }
            val logTimeSec = row["log_time"]?.trim()?.toLongOrNull()?.takeIf { it > 1_000_000_000L }

            result.getOrPut(elId) { mutableListOf() }.add(
                RawSetLog(
                    setIndex = setIdx,
                    weightKg = weightKg,
                    reps = reps,
                    durationSec = duration,
                    calories = cal,
                    distanceKm = dist,
                    completedAt = logTimeSec?.let(Instant::ofEpochSecond)
                )
            )
        }
        return result
    }

    private fun buildSets(
        exerciseType: ExerciseType,
        logsRaw: String,
        setLogs: List<RawSetLog>,
        cardioEnrichment: CardioEnrichment?
    ): List<ParsedWorkoutSet> {
        if (exerciseType == ExerciseType.CARDIO) {
            val cardioFromLogs = parseCardioLogsString(logsRaw)
            val setLogFirst = setLogs.firstOrNull()
            val cal = setLogFirst?.calories ?: cardioEnrichment?.calories ?: cardioFromLogs.calories
            val dist = setLogFirst?.distanceKm ?: cardioEnrichment?.distanceKm ?: cardioFromLogs.distanceKm
            val dur = setLogFirst?.durationSec ?: cardioEnrichment?.durationSec ?: cardioFromLogs.durationSec
            return listOf(
                ParsedWorkoutSet(
                    setIndex = 0,
                    weightKg = null,
                    reps = null,
                    durationSec = dur,
                    distanceKm = dist,
                    calories = cal,
                    setType = SetType.WORK,
                    completedAt = setLogFirst?.completedAt
                )
            )
        }

        if (setLogs.isNotEmpty()) {
            return setLogs.mapIndexed { idx, s ->
                ParsedWorkoutSet(
                    setIndex = idx,
                    weightKg = s.weightKg,
                    reps = s.reps,
                    durationSec = s.durationSec,
                    distanceKm = s.distanceKm,
                    calories = s.calories,
                    setType = SetType.WORK,
                    completedAt = s.completedAt
                )
            }
        }

        if (logsRaw.isBlank()) return emptyList()
        return logsRaw.split(",").mapIndexedNotNull { idx, token ->
            val parts = token.trim().split('x', 'X')
            if (parts.size != 2) return@mapIndexedNotNull null
            val weight = parts[0].toDoubleOrNull() ?: return@mapIndexedNotNull null
            val reps = parts[1].toIntOrNull() ?: return@mapIndexedNotNull null
            ParsedWorkoutSet(
                setIndex = idx,
                weightKg = roundQuarterKg(weight),
                reps = reps,
                durationSec = null,
                distanceKm = null,
                calories = null,
                setType = SetType.WORK,
                completedAt = null
            )
        }
    }

    private fun parseCardioLogsString(logsRaw: String): CardioEnrichment {
        if (logsRaw.isBlank()) return CardioEnrichment(null, null, null)
        val tokens = logsRaw.split(",").map { it.trim().removePrefix("0x").toDoubleOrNull() ?: 0.0 }
        return when {
            tokens.size >= 5 -> CardioEnrichment(
                calories = tokens[0].takeIf { it > 0.0 },
                distanceKm = tokens[1].takeIf { it > 0.0 },
                durationSec = tokens[4].toInt().takeIf { it > 0 }
            )
            tokens.size == 1 -> CardioEnrichment(
                calories = null,
                distanceKm = null,
                durationSec = tokens[0].toInt().takeIf { it > 0 }
            )
            else -> CardioEnrichment(null, null, null)
        }
    }

    private fun roundQuarterKg(kg: Double): Double = round(kg * 4.0) / 4.0
    private fun round2(v: Double): Double = round(v * 100.0) / 100.0
}
