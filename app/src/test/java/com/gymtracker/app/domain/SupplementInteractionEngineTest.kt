package com.gymtracker.app.domain

import com.gymtracker.app.data.db.SupplementCategory
import com.gymtracker.app.data.db.SupplementItemEntity
import com.gymtracker.app.data.db.SupplementLogEntity
import com.gymtracker.app.data.db.SupplementScheduleEntity
import com.gymtracker.app.data.db.SupplementTrigger
import com.gymtracker.app.data.db.SupplementUnit
import com.gymtracker.app.data.db.SupplementWithSchedules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset

class SupplementInteractionEngineTest {

    private fun stackEntry(
        id: Long,
        nameEn: String,
        nameUk: String,
        cat: SupplementCategory,
        trigger: SupplementTrigger,
        time: LocalTime? = null,
        caffeineMg: Int? = 0
    ): SupplementWithSchedules {
        val item = SupplementItemEntity(
            id = id,
            nameEn = nameEn,
            nameUk = nameUk,
            category = cat,
            defaultDose = 1.0,
            unit = SupplementUnit.CAPSULE,
            caffeineMgPerServing = caffeineMg
        )
        val sched = SupplementScheduleEntity(
            id = id,
            supplementId = id,
            triggerType = trigger,
            timeOfDay = time
        )
        return SupplementWithSchedules(item, listOf(sched))
    }

    @Test
    fun starterUserStack_showsSynergiesAndSpacingOkWithZeroConflicts() {
        val stack = listOf(
            stackEntry(1, "Vitamin D3", "Вітамін D3", SupplementCategory.VITAMIN, SupplementTrigger.WITH_MEAL, LocalTime.of(9, 0)),
            stackEntry(2, "Omega-3", "Омега-3", SupplementCategory.OMEGA_3, SupplementTrigger.WITH_MEAL, LocalTime.of(9, 0)),
            stackEntry(3, "Zinc", "Цинк", SupplementCategory.MINERAL, SupplementTrigger.FIXED_TIME, LocalTime.of(14, 0)),
            stackEntry(4, "Pre-Workout (Stim-Free)", "Передтрен (без кофеїну)", SupplementCategory.PRE_WORKOUT, SupplementTrigger.BEFORE_WORKOUT, caffeineMg = 0),
            stackEntry(5, "Whey Protein", "Протеїн", SupplementCategory.PROTEIN, SupplementTrigger.AFTER_WORKOUT),
            stackEntry(6, "Creatine", "Креатин", SupplementCategory.CREATINE, SupplementTrigger.AFTER_WORKOUT),
            stackEntry(7, "Magnesium", "Магній", SupplementCategory.MINERAL, SupplementTrigger.BEDTIME, LocalTime.of(23, 15))
        )

        val interactions = SupplementInteractionEngine.analyzeStack(
            items = stack,
            todayLogs = emptyList(),
            now = Instant.parse("2026-05-01T12:00:00Z"),
            bedtimeMinutesOfDay = 0,
            zoneId = ZoneOffset.UTC
        )

        assertTrue(interactions.none { it.kind == InteractionKind.CONFLICT || it.kind == InteractionKind.CAUTION })
        assertTrue(interactions.any { it.id == "synergy_d3_omega" && it.kind == InteractionKind.SYNERGY })
        assertTrue(interactions.any { it.id == "synergy_d3_mag" && it.kind == InteractionKind.SYNERGY })
        assertTrue(interactions.any { it.id == "synergy_creatine_protein" && it.kind == InteractionKind.SYNERGY })
        assertEquals(IngredientTag.PRE_WORKOUT_STIM_FREE, SupplementInteractionEngine.classify(stack[3].item))
    }

    @Test
    fun zincAndMagnesiumTooClose_orTakenWithinTwoHours_flagsConflictAndCautions() {
        val now = Instant.parse("2026-05-01T15:00:00Z")
        val stack = listOf(
            stackEntry(1, "Zinc", "Цинк", SupplementCategory.MINERAL, SupplementTrigger.BEDTIME, LocalTime.of(23, 0)),
            stackEntry(2, "Magnesium", "Магній", SupplementCategory.MINERAL, SupplementTrigger.BEDTIME, LocalTime.of(23, 15)),
            stackEntry(3, "Vitamin D3", "Вітамін D3", SupplementCategory.VITAMIN, SupplementTrigger.BEDTIME, LocalTime.of(22, 30))
        )

        val scheduledOnly = SupplementInteractionEngine.analyzeStack(
            items = stack,
            todayLogs = emptyList(),
            now = now,
            bedtimeMinutesOfDay = 0,
            zoneId = ZoneOffset.UTC
        )
        assertTrue(scheduledOnly.any { it.kind == InteractionKind.CONFLICT && 1L in it.involvedItemIds && 2L in it.involvedItemIds })
        assertTrue(scheduledOnly.any { it.kind == InteractionKind.CAUTION && 3L in it.involvedItemIds })
        assertTrue(scheduledOnly.any { it.kind == InteractionKind.CAUTION && 1L in it.involvedItemIds })

        // Live check when Zinc was taken 30 min ago (should tell user to wait ~90 min)
        val liveCheck = SupplementInteractionEngine.analyzeStack(
            items = stack,
            todayLogs = listOf(
                SupplementLogEntity(id = 10, supplementId = 1L, takenAt = now.minusSeconds(30 * 60L), dose = 1.0)
            ),
            now = now,
            bedtimeMinutesOfDay = 0,
            zoneId = ZoneOffset.UTC
        )
        val liveConflict = liveCheck.firstOrNull { it.id == "sep_zinc_mag_live" }
        assertTrue(liveConflict != null && liveConflict.titleEn.contains("90 min"))
    }
}
