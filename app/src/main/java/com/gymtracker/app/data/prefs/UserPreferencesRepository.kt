package com.gymtracker.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gymtracker.app.BuildConfig
import com.gymtracker.app.data.db.OneRmFormula
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "gym_tracker_prefs")

enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    EN("en"),
    UK("uk")
}

enum class AppThemeMode {
    DARK,
    LIGHT,
    SYSTEM
}

data class UserPreferences(
    val language: AppLanguage = AppLanguage.SYSTEM,
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val watchSourcePackage: String? = null,
    val hrMax: Int = 185,
    val estSecPerRep: Int = 3,
    val defaultRestSec: Int = 90,
    val preferredFormula: OneRmFormula = OneRmFormula.EPLEY,
    val keepScreenAwake: Boolean = true,
    val showExternalCardioInHistory: Boolean = false,
    val defaultBodyWeightKg: Double = 105.0,
    val heightCm: Int = 182,
    val birthYear: Int = 1993,
    val isMale: Boolean = true,
    val scaleMacAddress: String? = null,
    val scaleName: String? = null,
    val scaleBackgroundEnabled: Boolean = true,
    val scaleMinWeightKg: Double? = null,
    val scaleMaxWeightKg: Double? = null,
    val scaleLastAutoEpochSec: Long = 0L,
    val scaleLastAutoHadImpedance: Boolean = false,
    val scaleLastWakeEpochSec: Long = 0L,
    val scaleIgnoredFamilyCount: Int = 0,
    val dailyStepGoal: Int = 10000,
    val bedtimeMinutesOfDay: Int = 0, // 0 = 24:00 (00:00)
    val caffeineHalfLifeHours: Double = 5.0,
    val caffeineSleepThresholdMg: Int = 50,
    val stackRemindersEnabled: Boolean = true,
    val supabaseUrl: String = BuildConfig.SUPABASE_URL,
    val supabasePublishableKey: String = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
    val supabaseUserEmail: String? = null,
    val supabaseUserId: String? = null,
    val supabaseAccessToken: String? = null,
    val supabaseRefreshToken: String? = null,
    val supabaseAccessExpiresAtSec: Long = 0L,
    val supabaseLastSyncEpochSec: Long = 0L,
    val supabaseAutoSyncEnabled: Boolean = true,
    val exerciseGoalsKg: Map<Long, Double> = emptyMap(),
    val injuredMuscles: Map<String, String> = emptyMap(),
    val stackLogScheduleMap: Map<Long, Long> = emptyMap()
)

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val LANGUAGE = stringPreferencesKey("app_language")
        val THEME_MODE = stringPreferencesKey("app_theme_mode")
        val WATCH_PKG = stringPreferencesKey("watch_source_package")
        val HR_MAX = intPreferencesKey("hr_max")
        val EST_SEC_PER_REP = intPreferencesKey("est_sec_per_rep")
        val DEFAULT_REST_SEC = intPreferencesKey("default_rest_sec")
        val FORMULA = stringPreferencesKey("preferred_formula")
        val KEEP_SCREEN_AWAKE = booleanPreferencesKey("keep_screen_awake")
        val SHOW_EXTERNAL_CARDIO = booleanPreferencesKey("show_external_cardio")
        val DEFAULT_BW_KG = doublePreferencesKey("default_bw_kg")
        val HEIGHT_CM = intPreferencesKey("height_cm")
        val BIRTH_YEAR = intPreferencesKey("birth_year")
        val IS_MALE = booleanPreferencesKey("is_male")
        val SCALE_MAC = stringPreferencesKey("scale_mac_address")
        val SCALE_NAME = stringPreferencesKey("scale_device_name")
        val SCALE_BG_ENABLED = booleanPreferencesKey("scale_background_enabled")
        val SCALE_MIN_KG = doublePreferencesKey("scale_min_weight_kg")
        val SCALE_MAX_KG = doublePreferencesKey("scale_max_weight_kg")
        val SCALE_LAST_AUTO_SEC = longPreferencesKey("scale_last_auto_epoch_sec")
        val SCALE_LAST_AUTO_IMP = booleanPreferencesKey("scale_last_auto_had_imp")
        val SCALE_LAST_WAKE_SEC = longPreferencesKey("scale_last_wake_epoch_sec")
        val SCALE_IGNORED_FAMILY = intPreferencesKey("scale_ignored_family_count")
        val DAILY_STEP_GOAL = intPreferencesKey("daily_step_goal")
        val BEDTIME_MINUTES = intPreferencesKey("bedtime_minutes_of_day")
        val CAFFEINE_HALF_LIFE_H = doublePreferencesKey("caffeine_half_life_hours")
        val CAFFEINE_SLEEP_THRESH_MG = intPreferencesKey("caffeine_sleep_threshold_mg")
        val STACK_REMINDERS_ENABLED = booleanPreferencesKey("stack_reminders_enabled")
        val SUPABASE_URL = stringPreferencesKey("supabase_url")
        val SUPABASE_KEY = stringPreferencesKey("supabase_publishable_key")
        val SUPABASE_EMAIL = stringPreferencesKey("supabase_user_email")
        val SUPABASE_USER_ID = stringPreferencesKey("supabase_user_id")
        val SUPABASE_ACCESS_TOKEN = stringPreferencesKey("supabase_access_token")
        val SUPABASE_REFRESH_TOKEN = stringPreferencesKey("supabase_refresh_token")
        val SUPABASE_EXPIRES_AT_SEC = longPreferencesKey("supabase_expires_at_sec")
        val SUPABASE_LAST_SYNC_SEC = longPreferencesKey("supabase_last_sync_sec")
        val SUPABASE_AUTO_SYNC = booleanPreferencesKey("supabase_auto_sync")
        val EXERCISE_GOALS_JSON = stringPreferencesKey("exercise_goals_json")
        val INJURED_MUSCLES_JSON = stringPreferencesKey("injured_muscles_json")
        val STACK_LOG_SCHEDULE_MAP_JSON = stringPreferencesKey("stack_log_schedule_map_json")
    }

    val preferences: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        val goalsMap = prefs[Keys.EXERCISE_GOALS_JSON]?.let { raw ->
            runCatching { Json.decodeFromString<Map<String, Double>>(raw) }.getOrNull()
        }?.mapNotNull { (k, v) -> k.toLongOrNull()?.let { it to v } }?.toMap() ?: emptyMap()

        val injuredMap = prefs[Keys.INJURED_MUSCLES_JSON]?.let { raw ->
            runCatching { Json.decodeFromString<Map<String, String>>(raw) }.getOrNull()
        } ?: emptyMap()

        val logScheduleMap = prefs[Keys.STACK_LOG_SCHEDULE_MAP_JSON]?.let { raw ->
            runCatching { Json.decodeFromString<Map<String, Long>>(raw) }.getOrNull()
        }?.mapNotNull { (k, v) -> k.toLongOrNull()?.let { it to v } }?.toMap() ?: emptyMap()

        UserPreferences(
            language = prefs[Keys.LANGUAGE]?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() }
                ?: AppLanguage.SYSTEM,
            themeMode = prefs[Keys.THEME_MODE]?.let { runCatching { AppThemeMode.valueOf(it) }.getOrNull() }
                ?: AppThemeMode.DARK,
            watchSourcePackage = prefs[Keys.WATCH_PKG]?.takeIf { it.isNotBlank() },
            hrMax = prefs[Keys.HR_MAX] ?: 185,
            estSecPerRep = prefs[Keys.EST_SEC_PER_REP] ?: 3,
            defaultRestSec = prefs[Keys.DEFAULT_REST_SEC] ?: 90,
            preferredFormula = prefs[Keys.FORMULA]?.let { runCatching { OneRmFormula.valueOf(it) }.getOrNull() }
                ?: OneRmFormula.EPLEY,
            keepScreenAwake = prefs[Keys.KEEP_SCREEN_AWAKE] ?: true,
            showExternalCardioInHistory = prefs[Keys.SHOW_EXTERNAL_CARDIO] ?: false,
            defaultBodyWeightKg = prefs[Keys.DEFAULT_BW_KG] ?: 105.0,
            heightCm = prefs[Keys.HEIGHT_CM] ?: 182,
            birthYear = prefs[Keys.BIRTH_YEAR] ?: 1993,
            isMale = prefs[Keys.IS_MALE] ?: true,
            scaleMacAddress = prefs[Keys.SCALE_MAC]?.takeIf { it.isNotBlank() },
            scaleName = prefs[Keys.SCALE_NAME]?.takeIf { it.isNotBlank() },
            scaleBackgroundEnabled = prefs[Keys.SCALE_BG_ENABLED] ?: true,
            scaleMinWeightKg = prefs[Keys.SCALE_MIN_KG],
            scaleMaxWeightKg = prefs[Keys.SCALE_MAX_KG],
            scaleLastAutoEpochSec = prefs[Keys.SCALE_LAST_AUTO_SEC] ?: 0L,
            scaleLastAutoHadImpedance = prefs[Keys.SCALE_LAST_AUTO_IMP] ?: false,
            scaleLastWakeEpochSec = prefs[Keys.SCALE_LAST_WAKE_SEC] ?: 0L,
            scaleIgnoredFamilyCount = prefs[Keys.SCALE_IGNORED_FAMILY] ?: 0,
            dailyStepGoal = prefs[Keys.DAILY_STEP_GOAL] ?: 10000,
            bedtimeMinutesOfDay = prefs[Keys.BEDTIME_MINUTES] ?: 0,
            caffeineHalfLifeHours = prefs[Keys.CAFFEINE_HALF_LIFE_H] ?: 5.0,
            caffeineSleepThresholdMg = prefs[Keys.CAFFEINE_SLEEP_THRESH_MG] ?: 50,
            stackRemindersEnabled = prefs[Keys.STACK_REMINDERS_ENABLED] ?: true,
            supabaseUrl = prefs[Keys.SUPABASE_URL]?.takeIf { it.isNotBlank() } ?: BuildConfig.SUPABASE_URL,
            supabasePublishableKey = prefs[Keys.SUPABASE_KEY]?.takeIf { it.isNotBlank() }
                ?: BuildConfig.SUPABASE_PUBLISHABLE_KEY,
            supabaseUserEmail = prefs[Keys.SUPABASE_EMAIL]?.takeIf { it.isNotBlank() },
            supabaseUserId = prefs[Keys.SUPABASE_USER_ID]?.takeIf { it.isNotBlank() },
            supabaseAccessToken = prefs[Keys.SUPABASE_ACCESS_TOKEN]?.takeIf { it.isNotBlank() },
            supabaseRefreshToken = prefs[Keys.SUPABASE_REFRESH_TOKEN]?.takeIf { it.isNotBlank() },
            supabaseAccessExpiresAtSec = prefs[Keys.SUPABASE_EXPIRES_AT_SEC] ?: 0L,
            supabaseLastSyncEpochSec = prefs[Keys.SUPABASE_LAST_SYNC_SEC] ?: 0L,
            supabaseAutoSyncEnabled = prefs[Keys.SUPABASE_AUTO_SYNC] ?: true,
            exerciseGoalsKg = goalsMap,
            injuredMuscles = injuredMap,
            stackLogScheduleMap = logScheduleMap
        )
    }

    suspend fun getOnce(): UserPreferences = preferences.first()

    suspend fun setLanguage(lang: AppLanguage) {
        context.dataStore.edit { it[Keys.LANGUAGE] = lang.name }
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setHeightCm(cm: Int) {
        context.dataStore.edit { it[Keys.HEIGHT_CM] = cm.coerceIn(100, 240) }
    }

    suspend fun setBirthYear(year: Int) {
        context.dataStore.edit { it[Keys.BIRTH_YEAR] = year.coerceIn(1920, 2020) }
    }

    suspend fun setIsMale(isMale: Boolean) {
        context.dataStore.edit { it[Keys.IS_MALE] = isMale }
    }

    suspend fun setScaleDevice(macAddress: String, name: String?) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SCALE_MAC] = macAddress.uppercase()
            if (name.isNullOrBlank()) prefs.remove(Keys.SCALE_NAME) else prefs[Keys.SCALE_NAME] = name
        }
    }

    suspend fun clearScaleDevice() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.SCALE_MAC)
            prefs.remove(Keys.SCALE_NAME)
        }
    }

    suspend fun setScaleBackgroundEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SCALE_BG_ENABLED] = enabled }
    }

    suspend fun setScaleWeightRange(minKg: Double?, maxKg: Double?) {
        context.dataStore.edit { prefs ->
            if (minKg == null || maxKg == null) {
                prefs.remove(Keys.SCALE_MIN_KG)
                prefs.remove(Keys.SCALE_MAX_KG)
            } else {
                val lo = minOf(minKg, maxKg).coerceIn(20.0, 240.0)
                val hi = maxOf(minKg, maxKg).coerceIn(20.0, 240.0)
                prefs[Keys.SCALE_MIN_KG] = lo
                prefs[Keys.SCALE_MAX_KG] = hi
            }
        }
    }

    suspend fun recordScaleAutoCapture(epochSec: Long, hadImpedance: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SCALE_LAST_AUTO_SEC] = epochSec
            prefs[Keys.SCALE_LAST_AUTO_IMP] = hadImpedance
        }
    }

    suspend fun recordScaleWake(epochSec: Long) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SCALE_LAST_WAKE_SEC] = epochSec
        }
    }

    suspend fun incrementScaleIgnoredFamily() {
        context.dataStore.edit { prefs ->
            val cur = prefs[Keys.SCALE_IGNORED_FAMILY] ?: 0
            prefs[Keys.SCALE_IGNORED_FAMILY] = cur + 1
        }
    }

    suspend fun setDailyStepGoal(goal: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DAILY_STEP_GOAL] = goal.coerceIn(1000, 50000)
        }
    }

    suspend fun setBedtimeMinutesOfDay(minutes: Int) {
        val normalized = ((minutes % 1440) + 1440) % 1440
        context.dataStore.edit { prefs ->
            prefs[Keys.BEDTIME_MINUTES] = normalized
        }
    }

    suspend fun setCaffeineHalfLifeHours(hours: Double) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CAFFEINE_HALF_LIFE_H] = hours.coerceIn(3.0, 9.0)
        }
    }

    suspend fun setStackRemindersEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.STACK_REMINDERS_ENABLED] = enabled
        }
    }

    suspend fun setSupabaseConfig(url: String, publishableKey: String) {
        context.dataStore.edit { prefs ->
            val trimmedUrl = url.trim().trimEnd('/')
            val trimmedKey = publishableKey.trim()
            if (trimmedUrl.isEmpty()) prefs.remove(Keys.SUPABASE_URL) else prefs[Keys.SUPABASE_URL] = trimmedUrl
            if (trimmedKey.isEmpty()) prefs.remove(Keys.SUPABASE_KEY) else prefs[Keys.SUPABASE_KEY] = trimmedKey
        }
    }

    suspend fun setSupabaseSession(
        userId: String,
        email: String?,
        accessToken: String,
        refreshToken: String?,
        expiresAtSec: Long
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SUPABASE_USER_ID] = userId
            if (email.isNullOrBlank()) prefs.remove(Keys.SUPABASE_EMAIL) else prefs[Keys.SUPABASE_EMAIL] = email
            prefs[Keys.SUPABASE_ACCESS_TOKEN] = accessToken
            if (refreshToken.isNullOrBlank()) prefs.remove(Keys.SUPABASE_REFRESH_TOKEN)
            else prefs[Keys.SUPABASE_REFRESH_TOKEN] = refreshToken
            prefs[Keys.SUPABASE_EXPIRES_AT_SEC] = expiresAtSec
        }
    }

    suspend fun clearSupabaseSession() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.SUPABASE_USER_ID)
            prefs.remove(Keys.SUPABASE_EMAIL)
            prefs.remove(Keys.SUPABASE_ACCESS_TOKEN)
            prefs.remove(Keys.SUPABASE_REFRESH_TOKEN)
            prefs.remove(Keys.SUPABASE_EXPIRES_AT_SEC)
        }
    }

    suspend fun setSupabaseLastSync(epochSec: Long) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SUPABASE_LAST_SYNC_SEC] = epochSec
        }
    }

    suspend fun setSupabaseAutoSyncEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SUPABASE_AUTO_SYNC] = enabled
        }
    }

    suspend fun applyCloudSyncedPrefs(
        heightCm: Int,
        birthYear: Int,
        isMale: Boolean,
        dailyStepGoal: Int,
        hrMax: Int,
        preferredFormula: OneRmFormula,
        exerciseGoalsJson: String,
        injuredMusclesJson: String,
        scaleMinKg: Double?,
        scaleMaxKg: Double?
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.HEIGHT_CM] = heightCm.coerceIn(100, 240)
            prefs[Keys.BIRTH_YEAR] = birthYear.coerceIn(1920, 2020)
            prefs[Keys.IS_MALE] = isMale
            prefs[Keys.DAILY_STEP_GOAL] = dailyStepGoal.coerceIn(1000, 50000)
            prefs[Keys.HR_MAX] = hrMax.coerceIn(120, 230)
            prefs[Keys.FORMULA] = preferredFormula.name
            prefs[Keys.EXERCISE_GOALS_JSON] = exerciseGoalsJson
            prefs[Keys.INJURED_MUSCLES_JSON] = injuredMusclesJson
            if (scaleMinKg != null && scaleMaxKg != null) {
                prefs[Keys.SCALE_MIN_KG] = minOf(scaleMinKg, scaleMaxKg)
                prefs[Keys.SCALE_MAX_KG] = maxOf(scaleMinKg, scaleMaxKg)
            }
        }
    }

    suspend fun setWatchSourcePackage(pkg: String?) {
        context.dataStore.edit {
            if (pkg.isNullOrBlank()) it.remove(Keys.WATCH_PKG) else it[Keys.WATCH_PKG] = pkg
        }
    }

    suspend fun setHrMax(hrMax: Int) {
        context.dataStore.edit { it[Keys.HR_MAX] = hrMax.coerceIn(120, 230) }
    }

    suspend fun setEstSecPerRep(sec: Int) {
        context.dataStore.edit { it[Keys.EST_SEC_PER_REP] = sec.coerceIn(1, 10) }
    }

    suspend fun setDefaultRestSec(sec: Int) {
        context.dataStore.edit { it[Keys.DEFAULT_REST_SEC] = sec.coerceIn(15, 600) }
    }

    suspend fun setPreferredFormula(formula: OneRmFormula) {
        context.dataStore.edit { it[Keys.FORMULA] = formula.name }
    }

    suspend fun setKeepScreenAwake(keep: Boolean) {
        context.dataStore.edit { it[Keys.KEEP_SCREEN_AWAKE] = keep }
    }

    suspend fun setShowExternalCardio(show: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_EXTERNAL_CARDIO] = show }
    }

    suspend fun setExerciseGoal(exerciseId: Long, goalKg: Double?) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.EXERCISE_GOALS_JSON]?.let { raw ->
                runCatching { Json.decodeFromString<Map<String, Double>>(raw) }.getOrNull()
            }?.toMutableMap() ?: mutableMapOf()

            if (goalKg == null || goalKg <= 0.0) {
                current.remove(exerciseId.toString())
            } else {
                current[exerciseId.toString()] = goalKg
            }
            prefs[Keys.EXERCISE_GOALS_JSON] = Json.encodeToString(current)
        }
    }

    suspend fun toggleInjuredMuscle(groupKey: String, dateIso: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.INJURED_MUSCLES_JSON]?.let { raw ->
                runCatching { Json.decodeFromString<Map<String, String>>(raw) }.getOrNull()
            }?.toMutableMap() ?: mutableMapOf()

            if (groupKey in current) {
                current.remove(groupKey)
            } else {
                current[groupKey] = dateIso
            }
            prefs[Keys.INJURED_MUSCLES_JSON] = Json.encodeToString(current)
        }
    }

    suspend fun getLogScheduleMapOnce(): Map<Long, Long> = getOnce().stackLogScheduleMap

    suspend fun recordLogScheduleMapping(logId: Long, scheduleId: Long) {
        recordLogScheduleMappings(listOf(logId to scheduleId))
    }

    suspend fun recordLogScheduleMappings(pairs: List<Pair<Long, Long>>) {
        if (pairs.isEmpty()) return
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.STACK_LOG_SCHEDULE_MAP_JSON]?.let { raw ->
                runCatching { Json.decodeFromString<Map<String, Long>>(raw) }.getOrNull()
            }?.toMutableMap() ?: mutableMapOf()
            for ((logId, schedId) in pairs) {
                current[logId.toString()] = schedId
            }
            val trimmed = if (current.size > 200) {
                current.entries
                    .sortedByDescending { it.key.toLongOrNull() ?: 0L }
                    .take(200)
                    .associate { it.key to it.value }
            } else {
                current
            }
            prefs[Keys.STACK_LOG_SCHEDULE_MAP_JSON] = Json.encodeToString(trimmed)
        }
    }

    suspend fun removeLogScheduleMappings(logIds: Collection<Long>) {
        if (logIds.isEmpty()) return
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.STACK_LOG_SCHEDULE_MAP_JSON]?.let { raw ->
                runCatching { Json.decodeFromString<Map<String, Long>>(raw) }.getOrNull()
            }?.toMutableMap() ?: return@edit
            var changed = false
            for (logId in logIds) {
                if (current.remove(logId.toString()) != null) changed = true
            }
            if (changed) {
                prefs[Keys.STACK_LOG_SCHEDULE_MAP_JSON] = Json.encodeToString(current)
            }
        }
    }
}
