package com.gymtracker.app.data.db

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class SeedExerciseJson(
    val slug: String,
    val jefitEid: Int? = null,
    val jefitNameMatch: String? = null,
    val nameEn: String,
    val nameUk: String,
    val type: ExerciseType,
    val primaryMuscle: Muscle,
    val secondaryMuscles: List<Muscle> = emptyList(),
    val calorieTier: CalorieTier
)

@Singleton
class ExerciseCatalogSeeder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exerciseDao: ExerciseDao
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    @Volatile
    private var seeded = false

    suspend fun ensureSeeded() {
        if (seeded) return
        mutex.withLock {
            if (seeded) return
            withContext(Dispatchers.IO) {
                val raw = context.assets.open("exercises.json").bufferedReader().use { it.readText() }
                seedFromJsonString(raw, exerciseDao, json)
                seeded = true
            }
        }
    }

    companion object {
        suspend fun seedFromJsonString(
            rawJson: String,
            exerciseDao: ExerciseDao,
            json: Json = Json { ignoreUnknownKeys = true }
        ) {
            val items = json.decodeFromString<List<SeedExerciseJson>>(rawJson)
            val now = Instant.now()
            for (item in items) {
                val existing = exerciseDao.getBySlug(item.slug)
                if (existing == null) {
                    exerciseDao.insertIgnore(
                        ExerciseEntity(
                            slug = item.slug,
                            nameEn = item.nameEn,
                            nameUk = item.nameUk,
                            type = item.type,
                            primaryMuscle = item.primaryMuscle,
                            secondaryMuscles = item.secondaryMuscles,
                            calorieTier = item.calorieTier,
                            jefitEid = item.jefitEid,
                            jefitNameMatch = item.jefitNameMatch,
                            updatedAt = now,
                            syncState = SyncState.PENDING_UPLOAD
                        )
                    )
                }
            }
        }
    }
}
