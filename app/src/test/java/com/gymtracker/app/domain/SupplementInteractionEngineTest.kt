package com.gymtracker.app.domain

import com.gymtracker.app.data.db.SupplementCategory
import com.gymtracker.app.data.db.SupplementItemEntity
import com.gymtracker.app.data.db.SupplementLogEntity
import com.gymtracker.app.data.db.SupplementScheduleEntity
import com.gymtracker.app.data.db.SupplementTrigger
import com.gymtracker.app.data.db.SupplementUnit
import com.gymtracker.app.data.db.SupplementWithSchedules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        caffeineMg: Int? = 0,
        dose: Double = 1.0
    ): SupplementWithSchedules {
        val item = SupplementItemEntity(
            id = id,
            nameEn = nameEn,
            nameUk = nameUk,
            category = cat,
            defaultDose = dose,
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
            stackEntry(3, "Zinc", "Цинк", SupplementCategory.MINERAL, SupplementTrigger.FIXED_TIME, LocalTime.of(14, 0), dose = 25.0),
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

        assertTrue(interactions.none { it.kind == InteractionKind.CONFLICT })
        assertTrue(interactions.any { it.id == "synergy_d3_omega" && it.kind == InteractionKind.SYNERGY })
        assertTrue(interactions.any { it.id == "synergy_d3_mag" && it.kind == InteractionKind.SYNERGY })
        assertTrue(interactions.any { it.id == "synergy_creatine_protein" && it.kind == InteractionKind.SYNERGY })
        // 25 mg zinc with no copper emits mild long-term copper balance caution
        assertTrue(interactions.any { it.id == "caution_zinc_no_copper" && it.kind == InteractionKind.CAUTION })
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
        assertTrue(scheduledOnly.any { it.id.startsWith("caution_late_") && 3L in it.involvedItemIds })
        assertTrue(scheduledOnly.any { it.id.startsWith("caution_food_") && 1L in it.involvedItemIds })

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

    @Test
    fun broadCatalogRules_detectFiberIronCaffeineCopperMultiAndSynergies() {
        val now = Instant.parse("2026-05-01T10:00:00Z")
        val stack = listOf(
            stackEntry(1, "Iron Bisglycinate", "Залізо", SupplementCategory.MINERAL, SupplementTrigger.FIXED_TIME, LocalTime.of(8, 30)),
            stackEntry(2, "Calcium", "Кальцій", SupplementCategory.MINERAL, SupplementTrigger.FIXED_TIME, LocalTime.of(9, 0)),
            stackEntry(3, "Vitamin C", "Вітамін C", SupplementCategory.VITAMIN, SupplementTrigger.FIXED_TIME, LocalTime.of(8, 30)),
            stackEntry(4, "Coffee", "Кофеїн", SupplementCategory.OTHER, SupplementTrigger.FIXED_TIME, LocalTime.of(9, 15), caffeineMg = 100),
            stackEntry(5, "Psyllium Fiber", "Псиліум Клітковина", SupplementCategory.OTHER, SupplementTrigger.FIXED_TIME, LocalTime.of(9, 0)),
            stackEntry(6, "Vitamin D3", "Вітамін D3", SupplementCategory.VITAMIN, SupplementTrigger.WITH_MEAL, LocalTime.of(9, 0)),
            stackEntry(7, "Vitamin K2", "Вітамін K2", SupplementCategory.VITAMIN, SupplementTrigger.WITH_MEAL, LocalTime.of(9, 0)),
            stackEntry(8, "Collagen", "Колаген", SupplementCategory.PROTEIN, SupplementTrigger.FIXED_TIME, LocalTime.of(8, 30)),
            stackEntry(9, "L-Theanine", "L-Теанін", SupplementCategory.OTHER, SupplementTrigger.FIXED_TIME, LocalTime.of(9, 15)),
            stackEntry(10, "Multivitamin", "Мультивітаміни", SupplementCategory.VITAMIN, SupplementTrigger.WITH_MEAL, LocalTime.of(9, 0))
        )

        val interactions = SupplementInteractionEngine.analyzeStack(
            items = stack,
            todayLogs = emptyList(),
            now = now,
            bedtimeMinutesOfDay = 0,
            zoneId = ZoneOffset.UTC
        )

        // Conflicts
        assertTrue(interactions.any { it.id == "sep_iron_calcium_sched" && it.kind == InteractionKind.CONFLICT })
        assertTrue(interactions.any { it.id == "sep_iron_caffeine_sched" && it.kind == InteractionKind.CONFLICT })
        assertTrue(interactions.any { it.id == "conflict_fiber_1" && it.kind == InteractionKind.CONFLICT })

        // Cautions
        assertTrue(interactions.any { it.id == "caution_multi_overlap" && it.kind == InteractionKind.CAUTION })

        // Synergies
        assertTrue(interactions.any { it.id == "synergy_iron_vitc" && it.kind == InteractionKind.SYNERGY })
        assertTrue(interactions.any { it.id == "synergy_collagen_vitc" && it.kind == InteractionKind.SYNERGY })
        assertTrue(interactions.any { it.id == "synergy_d3_k2" && it.kind == InteractionKind.SYNERGY })
        assertTrue(interactions.any { it.id == "synergy_caffeine_theanine" && it.kind == InteractionKind.SYNERGY })
    }

    @Test
    fun stimulantsAndSedatives_plusCatalogCompletenessAndCandidatePreview() {
        val existing = listOf(
            stackEntry(1, "Zinc", "Цинк", SupplementCategory.MINERAL, SupplementTrigger.FIXED_TIME, LocalTime.of(14, 0), dose = 25.0),
            stackEntry(2, "Melatonin", "Мелатонін", SupplementCategory.MEDICATION, SupplementTrigger.BEDTIME, LocalTime.of(23, 30))
        )

        val copperCandidate = SupplementItemEntity(
            nameEn = "Copper",
            nameUk = "Мідь",
            category = SupplementCategory.MINERAL,
            defaultDose = 2.0,
            unit = SupplementUnit.MG
        )
        val copperPreview = SupplementInteractionEngine.previewForCandidate(
            candidate = copperCandidate,
            schedule = SupplementScheduleEntity(supplementId = 0L, triggerType = SupplementTrigger.FIXED_TIME, timeOfDay = LocalTime.of(14, 30)),
            existingStack = existing,
            zoneId = ZoneOffset.UTC
        )
        assertTrue(copperPreview.any { it.id.startsWith("sep_zinc_copper") && it.kind == InteractionKind.CONFLICT })

        val rhodiolaEvening = SupplementItemEntity(
            nameEn = "Rhodiola Rosea",
            nameUk = "Родіола рожева",
            category = SupplementCategory.OTHER,
            defaultDose = 300.0,
            unit = SupplementUnit.MG
        )
        val rhodiolaPreview = SupplementInteractionEngine.previewForCandidate(
            candidate = rhodiolaEvening,
            schedule = SupplementScheduleEntity(supplementId = 0L, triggerType = SupplementTrigger.BEDTIME, timeOfDay = LocalTime.of(23, 0)),
            existingStack = existing,
            zoneId = ZoneOffset.UTC
        )
        assertTrue(rhodiolaPreview.any { it.id == "conflict_rhodiola_sleep" && it.kind == InteractionKind.CAUTION })

        val catalog = SupplementInteractionEngine.referenceCatalog()
        assertTrue("Expected 30+ reference entries, got ${catalog.size}", catalog.size >= 30)
        catalog.forEach { entry ->
            assertFalse(entry.nameUk.isBlank())
            assertFalse(entry.nameEn.isBlank())
            assertFalse(entry.recommendation.timingUk.isBlank())
            assertFalse(entry.recommendation.tipUk.isBlank())
            assertFalse(entry.recommendation.synergiesUk.isBlank())
            assertFalse(entry.recommendation.avoidUk.isBlank())
        }
    }
}
