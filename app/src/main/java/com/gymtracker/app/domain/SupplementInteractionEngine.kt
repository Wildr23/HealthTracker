package com.gymtracker.app.domain

import com.gymtracker.app.data.db.SupplementCategory
import com.gymtracker.app.data.db.SupplementItemEntity
import com.gymtracker.app.data.db.SupplementLogEntity
import com.gymtracker.app.data.db.SupplementScheduleEntity
import com.gymtracker.app.data.db.SupplementTrigger
import com.gymtracker.app.data.db.SupplementWithSchedules
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

enum class IngredientTag {
    VITAMIN_D3,
    OMEGA_3,
    CREATINE,
    PROTEIN,
    ZINC,
    MAGNESIUM,
    PRE_WORKOUT_STIM_FREE,
    PRE_WORKOUT_CAFFEINE,
    CAFFEINE,
    IRON,
    CALCIUM,
    VITAMIN_C,
    MELATONIN,
    OTHER
}

enum class InteractionKind {
    SYNERGY,
    CAUTION,
    CONFLICT
}

data class SupplementRecommendation(
    val timingUk: String,
    val timingEn: String,
    val tipUk: String,
    val tipEn: String,
    val withFood: Boolean
)

data class StackInteraction(
    val id: String,
    val kind: InteractionKind,
    val involvedItemIds: Set<Long>,
    val titleUk: String,
    val titleEn: String,
    val detailUk: String,
    val detailEn: String
)

object SupplementInteractionEngine {

    fun classify(item: SupplementItemEntity): IngredientTag {
        val text = "${item.nameEn} ${item.nameUk}".lowercase()
        return when {
            item.category == SupplementCategory.OMEGA_3 || "omega" in text || "омега" in text || "fish oil" in text || "риб'ячий" in text ->
                IngredientTag.OMEGA_3
            item.category == SupplementCategory.CREATINE || "creatine" in text || "креатин" in text ->
                IngredientTag.CREATINE
            item.category == SupplementCategory.PROTEIN || "protein" in text || "whey" in text || "протеїн" in text ->
                IngredientTag.PROTEIN
            item.category == SupplementCategory.PRE_WORKOUT || "pre-workout" in text || "передтрен" in text || "предтрен" in text ->
                if ((item.caffeineMgPerServing ?: 0) > 0) IngredientTag.PRE_WORKOUT_CAFFEINE else IngredientTag.PRE_WORKOUT_STIM_FREE
            "d3" in text || "d-3" in text || "д3" in text || "vitamin d" in text || "вітамін d" in text || "cholecalciferol" in text ->
                IngredientTag.VITAMIN_D3
            "zinc" in text || "цинк" in text || "zma" in text ->
                IngredientTag.ZINC
            "magnesium" in text || "магній" in text ->
                IngredientTag.MAGNESIUM
            "iron" in text || "залізо" in text ->
                IngredientTag.IRON
            "calcium" in text || "кальцій" in text ->
                IngredientTag.CALCIUM
            "vitamin c" in text || "вітамін c" in text || "ascorb" in text ->
                IngredientTag.VITAMIN_C
            "melatonin" in text || "мелатонін" in text ->
                IngredientTag.MELATONIN
            "caffeine" in text || "кофеїн" in text || "coffee" in text || "кава" in text || (item.caffeineMgPerServing ?: 0) > 0 ->
                IngredientTag.CAFFEINE
            else -> IngredientTag.OTHER
        }
    }

    fun recommendationFor(item: SupplementItemEntity): SupplementRecommendation =
        when (classify(item)) {
            IngredientTag.VITAMIN_D3 -> SupplementRecommendation(
                timingUk = "Вранці з жирною їжею",
                timingEn = "Morning with fatty meal",
                tipUk = "Жиророзчинний вітамін: приймай разом з Омега-3 або сніданком; не на ніч, щоб не збивати мелатонін.",
                tipEn = "Fat-soluble: pair with Omega-3 or breakfast; avoid late evening to protect melatonin.",
                withFood = true
            )
            IngredientTag.OMEGA_3 -> SupplementRecommendation(
                timingUk = "Під час їжі",
                timingEn = "With a main meal",
                tipUk = "Покращує засвоєння D3 та зменшує післясмак, якщо приймати під час основного прийому їжі.",
                tipEn = "Boosts Vitamin D3 absorption and prevents fishy reflux when taken with food.",
                withFood = true
            )
            IngredientTag.CREATINE -> SupplementRecommendation(
                timingUk = "Щодня після залу або з їжею",
                timingEn = "Daily post-workout or with food",
                tipUk = "Накопичувальний ефект (5 г щодня без пропусків). Пий достатньо води протягом дня.",
                tipEn = "Saturation-based (5 g daily every day). Maintain steady hydration.",
                withFood = false
            )
            IngredientTag.PROTEIN -> SupplementRecommendation(
                timingUk = "Після тренування або між прийомами їжі",
                timingEn = "Post-workout or between meals",
                tipUk = "Зручно закриває добову норму білка (1.6–2.2 г/кг) протягом дня чи одразу після залу.",
                tipEn = "Conveniently helps hit daily protein target (1.6–2.2 g/kg) post-workout or as a snack.",
                withFood = false
            )
            IngredientTag.ZINC -> SupplementRecommendation(
                timingUk = "Вдень під час їжі (окремо від Магнію)",
                timingEn = "Midday with food (away from Magnesium)",
                tipUk = "Натщесерце може викликати нудоту. Розноси мінімум на 2 год із Магнієм, Кальцієм і Залізом.",
                tipEn = "Empty stomach can cause nausea. Separate by ≥2h from Magnesium, Calcium, and Iron.",
                withFood = true
            )
            IngredientTag.MAGNESIUM -> SupplementRecommendation(
                timingUk = "Перед сном (окремо від Цинку)",
                timingEn = "Before bed (away from Zinc)",
                tipUk = "Підтримує нервову систему, сон і відновлення м'язів. Допомагає активації вітаміну D3.",
                tipEn = "Supports nervous system, sleep quality, muscle recovery, and Vitamin D3 metabolism.",
                withFood = false
            )
            IngredientTag.PRE_WORKOUT_STIM_FREE -> SupplementRecommendation(
                timingUk = "За 20–40 хв до залу",
                timingEn = "20–40 min pre-workout",
                tipUk = "0 мг кофеїну — безпечний навіть для вечірніх тренувань і не погіршує сон. Запивай 300–400 мл води.",
                tipEn = "Stim-free (0 mg caffeine): safe for late evening workouts without hurting sleep.",
                withFood = false
            )
            IngredientTag.PRE_WORKOUT_CAFFEINE -> SupplementRecommendation(
                timingUk = "За 25–45 хв до залу",
                timingEn = "25–45 min pre-workout",
                tipUk = "Містить стимулятори (T½ ≈ 5 год): стеж за індикатором сну, якщо тренуєшся ввечері.",
                tipEn = "Contains caffeine (T½ ≈ 5h): monitor sleep guard projection for afternoon/evening sessions.",
                withFood = false
            )
            IngredientTag.CAFFEINE -> SupplementRecommendation(
                timingUk = "Перша половина дня",
                timingEn = "Earlier in the day",
                tipUk = "Напіврозпад кофеїну ~5 год: тримай залишок перед сном нижче 50 мг.",
                tipEn = "Caffeine half-life is ~5h; aim for ≤50 mg remaining at bedtime.",
                withFood = false
            )
            IngredientTag.IRON -> SupplementRecommendation(
                timingUk = "Вранці окремо від кави, цинку й кальцію",
                timingEn = "Morning away from coffee, zinc, and calcium",
                tipUk = "Засвоюється краще з Вітаміном C; уникай кави/чаю/цинку протягом 2 год.",
                tipEn = "Pair with Vitamin C; separate by ≥2h from coffee, tea, zinc, calcium, and magnesium.",
                withFood = false
            )
            IngredientTag.CALCIUM -> SupplementRecommendation(
                timingUk = "З їжею (окремо від Цинку та Магнію)",
                timingEn = "With food (away from Zinc & Magnesium)",
                tipUk = "Конкурує з Цинком, Магнієм і Залізом за транспорт; розноси на 2+ години.",
                tipEn = "Competes with Zinc, Magnesium, and Iron uptake; separate by 2+ hours.",
                withFood = true
            )
            IngredientTag.VITAMIN_C -> SupplementRecommendation(
                timingUk = "Вранці або вдень",
                timingEn = "Morning or daytime",
                tipUk = "Підсилює засвоєння заліза та підтримує імунітет і колаген.",
                tipEn = "Enhances iron absorption and supports immune & connective tissue health.",
                withFood = false
            )
            IngredientTag.MELATONIN -> SupplementRecommendation(
                timingUk = "За 30–60 хв до сну",
                timingEn = "30–60 min before bed",
                tipUk = "Працює найкраще при приглушеному світлі без пізнього кофеїну.",
                tipEn = "Works best in dim light without late-day caffeine.",
                withFood = false
            )
            IngredientTag.OTHER -> SupplementRecommendation(
                timingUk = "За індивідуальним графіком",
                timingEn = "Follow your custom schedule",
                tipUk = "Регулярність прийому важливіша за точний час доби.",
                tipEn = "Daily consistency matters most; follow label food instructions.",
                withFood = false
            )
        }

    fun analyzeStack(
        items: List<SupplementWithSchedules>,
        todayLogs: List<SupplementLogEntity>,
        now: Instant,
        bedtimeMinutesOfDay: Int = 0,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<StackInteraction> {
        if (items.isEmpty()) return emptyList()
        val tagged = items.map { it to classify(it.item) }
        val byTag = tagged.groupBy({ it.second }, { it.first })
        val result = mutableListOf<StackInteraction>()

        val latestLogByTag = mutableMapOf<IngredientTag, Instant>()
        for (log in todayLogs) {
            if (log.deletedAt != null || log.takenAt.isAfter(now)) continue
            val tag = tagged.firstOrNull { it.first.item.id == log.supplementId }?.second ?: continue
            val prev = latestLogByTag[tag]
            if (prev == null || log.takenAt.isAfter(prev)) {
                latestLogByTag[tag] = log.takenAt
            }
        }

        // 1. Synergies
        val d3 = byTag[IngredientTag.VITAMIN_D3].orEmpty()
        val omega = byTag[IngredientTag.OMEGA_3].orEmpty()
        if (d3.isNotEmpty() && omega.isNotEmpty()) {
            val sameSlot = hasCloseSchedule(d3, omega, bedtimeMinutesOfDay, maxGapMin = 90)
            result += StackInteraction(
                id = "synergy_d3_omega",
                kind = InteractionKind.SYNERGY,
                involvedItemIds = (d3 + omega).map { it.item.id }.toSet(),
                titleUk = "Синергія: Вітамін D3 + Омега-3",
                titleEn = "Synergy: Vitamin D3 + Omega-3",
                detailUk = if (sameSlot) {
                    "Приймаються разом — жири Омега-3 підвищують засвоєння D3."
                } else {
                    "Добре поєднувати D3 та Омега-3 в один прийом з їжею для кращого засвоєння."
                },
                detailEn = if (sameSlot) {
                    "Scheduled together — dietary fats in Omega-3 boost Vitamin D3 absorption."
                } else {
                    "Pair Vitamin D3 and Omega-3 with the same meal for optimal fat-soluble uptake."
                }
            )
        }

        val mag = byTag[IngredientTag.MAGNESIUM].orEmpty()
        if (d3.isNotEmpty() && mag.isNotEmpty()) {
            result += StackInteraction(
                id = "synergy_d3_mag",
                kind = InteractionKind.SYNERGY,
                involvedItemIds = (d3 + mag).map { it.item.id }.toSet(),
                titleUk = "Синергія: Магній допомагає роботі D3",
                titleEn = "Synergy: Magnesium activates Vitamin D3",
                detailUk = "Магній є кофактором ферментів, що перетворюють D3 в активну форму.",
                detailEn = "Magnesium is a required cofactor for enzymes converting Vitamin D3 into active form."
            )
        }

        val creatine = byTag[IngredientTag.CREATINE].orEmpty()
        val protein = byTag[IngredientTag.PROTEIN].orEmpty()
        if (creatine.isNotEmpty() && protein.isNotEmpty()) {
            result += StackInteraction(
                id = "synergy_creatine_protein",
                kind = InteractionKind.SYNERGY,
                involvedItemIds = (creatine + protein).map { it.item.id }.toSet(),
                titleUk = "Синергія: Креатин + Протеїн після тренування",
                titleEn = "Synergy: Creatine + Protein post-workout",
                detailUk = "Зручна комбінація після залу для відновлення запасів АТФ і синтезу м'язового білка.",
                detailEn = "Ideal post-workout pairing supporting ATP replenishment and muscle protein synthesis."
            )
        }

        // 2. Mineral / Timing Conflicts & Live Separation Checks
        val zinc = byTag[IngredientTag.ZINC].orEmpty()
        val calcium = byTag[IngredientTag.CALCIUM].orEmpty()
        val iron = byTag[IngredientTag.IRON].orEmpty()

        checkSeparationPair(
            id = "sep_zinc_mag",
            a = zinc,
            b = mag,
            tagA = IngredientTag.ZINC,
            tagB = IngredientTag.MAGNESIUM,
            latestLogByTag = latestLogByTag,
            now = now,
            bedtimeMinutesOfDay = bedtimeMinutesOfDay,
            titleUk = "Рознести Цинк та Магній (≥ 2 год)",
            titleEn = "Separate Zinc & Magnesium (≥ 2h)",
            adviceUk = "При високих дозах конкурують при всмоктуванні. Оптимально: Цинк вдень з їжею, Магній перед сном.",
            adviceEn = "High doses compete for intestinal transport. Best: Zinc midday with food, Magnesium at bedtime.",
            result = result
        )

        checkSeparationPair(
            id = "sep_zinc_calcium",
            a = zinc,
            b = calcium,
            tagA = IngredientTag.ZINC,
            tagB = IngredientTag.CALCIUM,
            latestLogByTag = latestLogByTag,
            now = now,
            bedtimeMinutesOfDay = bedtimeMinutesOfDay,
            titleUk = "Рознести Цинк та Кальцій (≥ 2 год)",
            titleEn = "Separate Zinc & Calcium (≥ 2h)",
            adviceUk = "Кальцій знижує всмоктування Цинку при одночасному прийомі.",
            adviceEn = "Calcium can reduce Zinc absorption when taken in the same meal.",
            result = result
        )

        checkSeparationPair(
            id = "sep_zinc_iron",
            a = zinc,
            b = iron,
            tagA = IngredientTag.ZINC,
            tagB = IngredientTag.IRON,
            latestLogByTag = latestLogByTag,
            now = now,
            bedtimeMinutesOfDay = bedtimeMinutesOfDay,
            titleUk = "Рознести Цинк та Залізо (≥ 2 год)",
            titleEn = "Separate Zinc & Iron (≥ 2h)",
            adviceUk = "Цинк і Залізо блокують засвоєння одне одного на порожній шлунок.",
            adviceEn = "Zinc and Iron compete directly for divalent metal transporters.",
            result = result
        )

        checkSeparationPair(
            id = "sep_mag_calcium",
            a = mag,
            b = calcium,
            tagA = IngredientTag.MAGNESIUM,
            tagB = IngredientTag.CALCIUM,
            latestLogByTag = latestLogByTag,
            now = now,
            bedtimeMinutesOfDay = bedtimeMinutesOfDay,
            titleUk = "Рознести Магній та Кальцій (≥ 2 год)",
            titleEn = "Separate Magnesium & Calcium (≥ 2h)",
            adviceUk = "Великі дози краще розділити в часі для максимального засвоєння.",
            adviceEn = "Separate large doses by 2+ hours to avoid absorption competition.",
            result = result
        )

        // 3. Individual timing cautions
        for (sw in d3) {
            val mins = effectiveScheduleMinutes(sw.schedules, bedtimeMinutesOfDay)
            if (mins != null && mins >= 20 * 60) {
                result += StackInteraction(
                    id = "caution_d3_late_${sw.item.id}",
                    kind = InteractionKind.CAUTION,
                    involvedItemIds = setOf(sw.item.id),
                    titleUk = "Вітамін D3 ввечері може погіршити сон",
                    titleEn = "Late-evening Vitamin D3 may affect sleep",
                    detailUk = "Перенеси Вітамін D3 на ранок або обід разом із жирною їжею.",
                    detailEn = "Move Vitamin D3 to morning or midday with a meal containing fat."
                )
            }
        }

        for (sw in zinc) {
            val trig = sw.schedules.firstOrNull { it.enabled && it.deletedAt == null }?.triggerType
            if (trig == SupplementTrigger.BEDTIME || trig == SupplementTrigger.BEFORE_WORKOUT) {
                result += StackInteraction(
                    id = "caution_zinc_fasted_${sw.item.id}",
                    kind = InteractionKind.CAUTION,
                    involvedItemIds = setOf(sw.item.id),
                    titleUk = "Цинк краще приймати з їжею",
                    titleEn = "Take Zinc with food",
                    detailUk = "Прийом натщесерце перед тренуванням чи сном часто викликає нудоту.",
                    detailEn = "Taking Zinc on an empty stomach pre-workout or at bedtime often causes nausea."
                )
            }
        }

        return result.distinctBy { it.id }
    }

    private fun checkSeparationPair(
        id: String,
        a: List<SupplementWithSchedules>,
        b: List<SupplementWithSchedules>,
        tagA: IngredientTag,
        tagB: IngredientTag,
        latestLogByTag: Map<IngredientTag, Instant>,
        now: Instant,
        bedtimeMinutesOfDay: Int,
        titleUk: String,
        titleEn: String,
        adviceUk: String,
        adviceEn: String,
        result: MutableList<StackInteraction>
    ) {
        if (a.isEmpty() || b.isEmpty()) return
        val ids = (a + b).map { it.item.id }.toSet()

        val logA = latestLogByTag[tagA]
        val logB = latestLogByTag[tagB]
        val minSinceA = logA?.let { Duration.between(it, now).toMinutes() }
        val minSinceB = logB?.let { Duration.between(it, now).toMinutes() }

        val liveWaitMinutes = when {
            minSinceA != null && minSinceA in 0..119 && logB == null -> 120 - minSinceA
            minSinceB != null && minSinceB in 0..119 && logA == null -> 120 - minSinceB
            else -> null
        }

        val closeInSchedule = hasCloseSchedule(a, b, bedtimeMinutesOfDay, maxGapMin = 120)
        if (liveWaitMinutes != null) {
            result += StackInteraction(
                id = "${id}_live",
                kind = InteractionKind.CONFLICT,
                involvedItemIds = ids,
                titleUk = "$titleUk • зачекай ще $liveWaitMinutes хв",
                titleEn = "$titleEn • wait $liveWaitMinutes min",
                detailUk = adviceUk,
                detailEn = adviceEn
            )
        } else if (closeInSchedule) {
            result += StackInteraction(
                id = "${id}_sched",
                kind = InteractionKind.CONFLICT,
                involvedItemIds = ids,
                titleUk = titleUk,
                titleEn = titleEn,
                detailUk = adviceUk,
                detailEn = adviceEn
            )
        } else {
            result += StackInteraction(
                id = "${id}_ok",
                kind = InteractionKind.SYNERGY,
                involvedItemIds = ids,
                titleUk = "Сумісність ОК: Цинк і Магній рознесені",
                titleEn = "Spacing OK: Zinc & Magnesium separated",
                detailUk = adviceUk,
                detailEn = adviceEn
            ).takeIf { tagA == IngredientTag.ZINC && tagB == IngredientTag.MAGNESIUM } ?: return
        }
    }

    private fun hasCloseSchedule(
        a: List<SupplementWithSchedules>,
        b: List<SupplementWithSchedules>,
        bedtimeMinutesOfDay: Int,
        maxGapMin: Int
    ): Boolean {
        for (sa in a) {
            for (sb in b) {
                val trigA = sa.schedules.firstOrNull { it.enabled && it.deletedAt == null }?.triggerType
                val trigB = sb.schedules.firstOrNull { it.enabled && it.deletedAt == null }?.triggerType
                if (trigA != null && trigA == trigB &&
                    trigA in setOf(SupplementTrigger.BEFORE_WORKOUT, SupplementTrigger.AFTER_WORKOUT, SupplementTrigger.BEDTIME)
                ) {
                    return true
                }
                val minA = effectiveScheduleMinutes(sa.schedules, bedtimeMinutesOfDay)
                val minB = effectiveScheduleMinutes(sb.schedules, bedtimeMinutesOfDay)
                if (minA != null && minB != null) {
                    val diff = kotlin.math.abs(minA - minB)
                    val wrapDiff = minOf(diff, 1440 - diff)
                    if (wrapDiff < maxGapMin) return true
                }
            }
        }
        return false
    }

    private fun effectiveScheduleMinutes(
        schedules: List<SupplementScheduleEntity>,
        bedtimeMinutesOfDay: Int
    ): Int? {
        val s = schedules.firstOrNull { it.enabled && it.deletedAt == null } ?: return null
        s.timeOfDay?.let { return it.hour * 60 + it.minute }
        return when (s.triggerType) {
            SupplementTrigger.BEDTIME -> ((bedtimeMinutesOfDay - 45) + 1440) % 1440
            SupplementTrigger.WITH_MEAL -> 9 * 60
            else -> null
        }
    }
}
