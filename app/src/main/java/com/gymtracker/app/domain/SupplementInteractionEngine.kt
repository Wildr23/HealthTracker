package com.gymtracker.app.domain

import com.gymtracker.app.data.db.SupplementCategory
import com.gymtracker.app.data.db.SupplementItemEntity
import com.gymtracker.app.data.db.SupplementLogEntity
import com.gymtracker.app.data.db.SupplementScheduleEntity
import com.gymtracker.app.data.db.SupplementTrigger
import com.gymtracker.app.data.db.SupplementUnit
import com.gymtracker.app.data.db.SupplementWithSchedules
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs

enum class IngredientTag {
    // Performance / Sport
    CREATINE,
    PROTEIN,
    PRE_WORKOUT_STIM_FREE,
    PRE_WORKOUT_CAFFEINE,
    CAFFEINE,
    CITRULLINE,
    BETA_ALANINE,
    BCAA_EAA,
    GLUTAMINE,
    ELECTROLYTES,
    CARNITINE,
    TAURINE,

    // Vitamins
    VITAMIN_D3,
    VITAMIN_K2,
    VITAMIN_A,
    VITAMIN_E,
    VITAMIN_C,
    B_COMPLEX,
    MULTIVITAMIN,

    // Minerals
    ZINC,
    MAGNESIUM,
    CALCIUM,
    IRON,
    COPPER,
    SELENIUM,
    IODINE,
    POTASSIUM,
    BORON,

    // Fats, Joints, Gut, Sleep, Adaptogens
    OMEGA_3,
    COLLAGEN,
    GLUCOSAMINE_MSM,
    CURCUMIN,
    ASHWAGANDHA,
    L_THEANINE,
    MELATONIN,
    GLYCINE,
    PROBIOTICS,
    NAC,
    COQ10,
    BERBERINE,
    RHODIOLA,
    FIBER_PSYLLIUM,
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
    val withFood: Boolean,
    val synergiesUk: String = "",
    val synergiesEn: String = "",
    val avoidUk: String = "",
    val avoidEn: String = ""
)

data class SupplementReferenceEntry(
    val tag: IngredientTag,
    val nameUk: String,
    val nameEn: String,
    val category: SupplementCategory,
    val defaultDose: Double,
    val unit: SupplementUnit,
    val caffeineMg: Int,
    val defaultTrigger: SupplementTrigger,
    val defaultTime: String,
    val recommendation: SupplementRecommendation
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
            "psyllium" in text || "псиліум" in text || "псилиум" in text || "fiber" in text || "клітковин" in text || "клетчат" in text ->
                IngredientTag.FIBER_PSYLLIUM
            "citrulline" in text || "цитрулін" in text || "цитрулл" in text || "arginine" in text || "аргінін" in text || "аргинин" in text || "aakg" in text ->
                IngredientTag.CITRULLINE
            "beta-alanine" in text || "beta alanine" in text || "бета-аланін" in text || "бета аланін" in text || "бета-аланин" in text ->
                IngredientTag.BETA_ALANINE
            "bcaa" in text || "eaa" in text || "аміно" in text || "амино" in text ->
                IngredientTag.BCAA_EAA
            "glutamine" in text || "глютамін" in text || "глутамін" in text || "глютамин" in text ->
                IngredientTag.GLUTAMINE
            "electrolyte" in text || "електроліт" in text || "электролит" in text || "isotonic" in text || "ізотонік" in text ->
                IngredientTag.ELECTROLYTES
            "carnitine" in text || "карнітин" in text || "карнитин" in text ->
                IngredientTag.CARNITINE
            "taurine" in text || "таурин" in text ->
                IngredientTag.TAURINE
            "collagen" in text || "колаген" in text || "коллаген" in text ->
                IngredientTag.COLLAGEN
            "glucosamine" in text || "глюкозамін" in text || "глюкозамин" in text || "chondroitin" in text || "хондроїтин" in text || "msm" in text || "мсм" in text ->
                IngredientTag.GLUCOSAMINE_MSM
            "curcumin" in text || "куркум" in text || "turmeric" in text ->
                IngredientTag.CURCUMIN
            "ashwagand" in text || "ашваганд" in text ->
                IngredientTag.ASHWAGANDHA
            "theanine" in text || "теанін" in text || "теанин" in text ->
                IngredientTag.L_THEANINE
            "melatonin" in text || "мелатонін" in text || "мелатонин" in text ->
                IngredientTag.MELATONIN
            "glycine" in text || "гліцин" in text || "глицин" in text ->
                IngredientTag.GLYCINE
            "probiotic" in text || "пробіотик" in text || "пробиотик" in text ->
                IngredientTag.PROBIOTICS
            "nac" in text || "n-acetyl" in text || "ацетилцистеїн" in text ->
                IngredientTag.NAC
            "coq10" in text || "co-q10" in text || "q10" in text || "коензим" in text || "коэнзим" in text || "ubiquinol" in text || "убіхінол" in text ->
                IngredientTag.COQ10
            "berberine" in text || "берберин" in text ->
                IngredientTag.BERBERINE
            "rhodiola" in text || "родіол" in text || "родиол" in text || "ginseng" in text || "женьшен" in text ->
                IngredientTag.RHODIOLA
            "multivit" in text || "мультивіт" in text || "мультивит" in text || "opti-men" in text || "daily multi" in text ->
                IngredientTag.MULTIVITAMIN
            "k2" in text || "к2" in text || "mk-7" in text || "mk7" in text || "menaquinone" in text ->
                IngredientTag.VITAMIN_K2
            "d3" in text || "d-3" in text || "д3" in text || "д-3" in text || "vitamin d" in text || "вітамін d" in text || "вітамін д" in text || "cholecalciferol" in text ->
                IngredientTag.VITAMIN_D3
            "vitamin a" in text || "вітамін a" in text || "вітамін а" in text || "retinol" in text || "ретинол" in text || "beta-carotene" in text ->
                IngredientTag.VITAMIN_A
            "vitamin e" in text || "вітамін e" in text || "вітамін е" in text || "tocopherol" in text || "токоферол" in text ->
                IngredientTag.VITAMIN_E
            "vitamin c" in text || "вітамін c" in text || "вітамін с" in text || "ascorb" in text || "аскорбін" in text ->
                IngredientTag.VITAMIN_C
            "b-complex" in text || "b complex" in text || "б-комплекс" in text || "vitamin b" in text || "вітамін b" in text || "вітаміни групи b" in text || "b12" in text || "в12" in text || "b6" in text || "в6" in text || "folate" in text || "біотин" in text || "biotin" in text ->
                IngredientTag.B_COMPLEX
            "copper" in text || "мідь" in text || "медь" in text || "bisglycinate copper" in text ->
                IngredientTag.COPPER
            "selenium" in text || "селен" in text ->
                IngredientTag.SELENIUM
            "iodine" in text || "йод" in text || "kelp" in text || "келп" in text ->
                IngredientTag.IODINE
            "potassium" in text || "калій" in text || "калий" in text ->
                IngredientTag.POTASSIUM
            "boron" in text || "бор" in text ->
                IngredientTag.BORON
            "zinc" in text || "цинк" in text || "zma" in text ->
                IngredientTag.ZINC
            "magnesium" in text || "магній" in text || "магний" in text ->
                IngredientTag.MAGNESIUM
            "iron" in text || "залізо" in text || "железо" in text || "ferr" in text ->
                IngredientTag.IRON
            "calcium" in text || "кальцій" in text || "кальций" in text ->
                IngredientTag.CALCIUM
            item.category == SupplementCategory.OMEGA_3 || "omega" in text || "омега" in text || "fish oil" in text || "риб'ячий" in text || "epa" in text || "dha" in text ->
                IngredientTag.OMEGA_3
            item.category == SupplementCategory.CREATINE || "creatine" in text || "креатин" in text ->
                IngredientTag.CREATINE
            item.category == SupplementCategory.PROTEIN || "protein" in text || "whey" in text || "протеїн" in text || "протеин" in text || "casein" in text || "казеїн" in text ->
                IngredientTag.PROTEIN
            item.category == SupplementCategory.PRE_WORKOUT || "pre-workout" in text || "передтрен" in text || "предтрен" in text ->
                if ((item.caffeineMgPerServing ?: 0) > 0) IngredientTag.PRE_WORKOUT_CAFFEINE else IngredientTag.PRE_WORKOUT_STIM_FREE
            "caffeine" in text || "кофеїн" in text || "кофеин" in text || "coffee" in text || "кава" in text || "guarana" in text || (item.caffeineMgPerServing ?: 0) > 0 ->
                IngredientTag.CAFFEINE
            else -> IngredientTag.OTHER
        }
    }

    fun recommendationFor(item: SupplementItemEntity): SupplementRecommendation =
        recommendationFor(classify(item))

    fun recommendationFor(tag: IngredientTag): SupplementRecommendation =
        when (tag) {
            IngredientTag.CREATINE -> SupplementRecommendation(
                timingUk = "Щодня після залу або з їжею",
                timingEn = "Daily post-workout or with food",
                tipUk = "Працює за рахунок накопичення (3–5 г щодня без пауз). Пий достатньо води.",
                tipEn = "Saturation-based (3–5 g daily every day). Maintain steady hydration.",
                withFood = false,
                synergiesUk = "Протеїн, Бета-аланін, Електроліти, вуглеводи",
                synergiesEn = "Protein, Beta-Alanine, Electrolytes, carbs",
                avoidUk = "Критичних конфліктів немає; не тримай наведений розчин годинами",
                avoidEn = "No major conflicts; drink promptly after mixing"
            )
            IngredientTag.PROTEIN -> SupplementRecommendation(
                timingUk = "Після тренування або між прийомами їжі",
                timingEn = "Post-workout or between meals",
                tipUk = "Зручно добирати норму 1.6–2.2 г/кг білка на добу.",
                tipEn = "Conveniently closes daily 1.6–2.2 g/kg protein target.",
                withFood = false,
                synergiesUk = "Креатин, вуглеводи",
                synergiesEn = "Creatine, carbs",
                avoidUk = "Кальцій із сироватки може заважати Залізу — рознось із препаратами заліза",
                avoidEn = "Dairy calcium can blunt standalone Iron pills if taken together"
            )
            IngredientTag.PRE_WORKOUT_STIM_FREE -> SupplementRecommendation(
                timingUk = "За 20–40 хв до залу",
                timingEn = "20–40 min pre-workout",
                tipUk = "0 мг кофеїну — безпечний для вечірніх тренувань, дає памп без шкоди для сну.",
                tipEn = "0 mg caffeine: ideal for evening workouts with pump and zero sleep impact.",
                withFood = false,
                synergiesUk = "Електроліти, Креатин, L-Цитрулін",
                synergiesEn = "Electrolytes, Creatine, L-Citrulline",
                avoidUk = "Може містити багато цитруліну — не дублюй великі дози окремого цитруліну",
                avoidEn = "Avoid stacking multiple extra high-dose Citrulline scoops unnecessarily"
            )
            IngredientTag.PRE_WORKOUT_CAFFEINE -> SupplementRecommendation(
                timingUk = "За 25–45 хв до залу",
                timingEn = "25–45 min pre-workout",
                tipUk = "Стеж за індикатором сну (T½ кофеїну ~5 год).",
                tipEn = "Watch the Sleep Guard curve (T½ ~5h).",
                withFood = false,
                synergiesUk = "L-Теанін (знімає тремтіння), Цитрулін, Бета-аланін",
                synergiesEn = "L-Theanine (smoother focus), Citrulline, Beta-Alanine",
                avoidUk = "Залізо/Кальцій (≥2 год), пізній вечір, Мелатонін/Ашваганда в один час",
                avoidEn = "Iron/Calcium (≥2h), late evening, taking directly with sedatives"
            )
            IngredientTag.CAFFEINE -> SupplementRecommendation(
                timingUk = "Ранок або до 14:00",
                timingEn = "Morning or before 2 PM",
                tipUk = "Залишок перед сном бажано тримати ≤ 50 мг.",
                tipEn = "Keep remaining caffeine ≤ 50 mg by bedtime.",
                withFood = false,
                synergiesUk = "L-Теанін (співвідношення ~1:2)",
                synergiesEn = "L-Theanine (~1:2 ratio)",
                avoidUk = "Залізо, Кальцій, B-вітаміни (робить вимивання); рознось на 1–2 год",
                avoidEn = "Iron, Calcium; separate by 1–2 hours"
            )
            IngredientTag.CITRULLINE -> SupplementRecommendation(
                timingUk = "За 30–45 хв до тренування",
                timingEn = "30–45 min pre-workout",
                tipUk = "Розширює судини (NO-донор), покращує памп та витривалість (6–8 г малат або 4–6 г чистий).",
                tipEn = "Boosts nitric oxide, blood flow, and endurance (6–8 g malate / 4–6 g pure).",
                withFood = false,
                synergiesUk = "Буряковий сік/нітрати, Бета-аланін, Електроліти",
                synergiesEn = "Nitrates, Beta-Alanine, Electrolytes",
                avoidUk = "Ліки для зниження тиску (можливе посилення гіпотензії)",
                avoidEn = "Blood pressure medications (additive BP drop)"
            )
            IngredientTag.BETA_ALANINE -> SupplementRecommendation(
                timingUk = "Щодня 3.2–6.4 г (перед залом або частинами)",
                timingEn = "Daily 3.2–6.4 g (split or pre-workout)",
                tipUk = "Підвищує карнозин у м'язах; легке поколювання (парестезія) безпечне.",
                tipEn = "Buffers muscle acidity; harmless tingling fades if split into smaller doses.",
                withFood = false,
                synergiesUk = "Креатин, Цитрулін",
                synergiesEn = "Creatine, Citrulline",
                avoidUk = "Дуже великі разові дози з Таурином можуть конкурувати за транспорт",
                avoidEn = "Very high acute doses with Taurine share transport"
            )
            IngredientTag.BCAA_EAA -> SupplementRecommendation(
                timingUk = "До / під час тренування (особливо натщесерце)",
                timingEn = "Pre / intra-workout (especially fasted)",
                tipUk = "Якщо в раціоні достатньо сироваткового протеїну, EAA корисніші за самі BCAA.",
                tipEn = "EAA covers all essential aminos; optional if daily protein intake is high.",
                withFood = false,
                synergiesUk = "Електроліти, Цитрулін під час залу",
                synergiesEn = "Electrolytes, Citrulline intra-workout",
                avoidUk = "Специфічних заборон немає",
                avoidEn = "No major supplement conflicts"
            )
            IngredientTag.GLUTAMINE -> SupplementRecommendation(
                timingUk = "Після тренування або вранці для ШКТ",
                timingEn = "Post-workout or morning gut support",
                tipUk = "5–10 г на день підтримують слизову кишечника та відновлення після великих навантажень.",
                tipEn = "5–10 g/day supports gut barrier & heavy training recovery.",
                withFood = false,
                synergiesUk = "Пробіотики, Колаген, Цинк (окремо від клітковини)",
                synergiesEn = "Probiotics, Collagen",
                avoidUk = "Псиліум/клітковина в один момент",
                avoidEn = "Fiber/Psyllium simultaneously"
            )
            IngredientTag.ELECTROLYTES -> SupplementRecommendation(
                timingUk = "Під час або після інтенсивного поту",
                timingEn = "During or after heavy sweat sessions",
                tipUk = "Відновлює натрій, калій і магній без стрибків цукру.",
                tipEn = "Restores sodium, potassium, and magnesium lost in sweat.",
                withFood = false,
                synergiesUk = "Креатин, Цитрулін, таурин",
                synergiesEn = "Creatine, Citrulline, Taurine",
                avoidUk = "Не перевищуй сумарний Калій/Магній із кількох добавок",
                avoidEn = "Watch cumulative Potassium & Magnesium from other pills"
            )
            IngredientTag.CARNITINE -> SupplementRecommendation(
                timingUk = "Ранок або до кардіо/залу (з вуглеводами для кращого входу)",
                timingEn = "Morning or pre-cardio (better uptake with carbs/insulin)",
                tipUk = "ALCAR дає фокус; L-тартрит підтримує відновлення м'язів.",
                tipEn = "ALCAR supports focus; L-tartrate supports muscle recovery.",
                withFood = true,
                synergiesUk = "CoQ10, Омега-3, Альфа-ліпоєва кислота",
                synergiesEn = "CoQ10, Omega-3",
                avoidUk = "Пізній вечір (ALCAR може бадьорити й погіршити засинання)",
                avoidEn = "Late evening (ALCAR can be mildly stimulating)"
            )
            IngredientTag.TAURINE -> SupplementRecommendation(
                timingUk = "До тренування (проти судом) або ввечері",
                timingEn = "Pre-workout or evening recovery",
                tipUk = "1–3 г допомагають електролітному балансу, роботі серця та зменшують судоми.",
                tipEn = "1–3 g aids hydration, cardiovascular support, and cramp prevention.",
                withFood = false,
                synergiesUk = "Магній, Електроліти, Кофеїн (пом'якшує пульс)",
                synergiesEn = "Magnesium, Electrolytes, Caffeine",
                avoidUk = "Гігадози Бета-аланіну в одному коктейлі (конкуренція транспорту)",
                avoidEn = "High simultaneous Beta-Alanine doses"
            )
            IngredientTag.VITAMIN_D3 -> SupplementRecommendation(
                timingUk = "Вранці під час їжі з жирами",
                timingEn = "Morning with a fat-containing meal",
                tipUk = "Жиророзчинний гормоноподібний вітамін; увечері може тимчасово пригнічувати мелатонін.",
                tipEn = "Fat-soluble vitamin; avoid late evening to protect natural melatonin.",
                withFood = true,
                synergiesUk = "Омега-3, Вітамін K2, Магній",
                synergiesEn = "Omega-3, Vitamin K2, Magnesium",
                avoidUk = "Псиліум/клітковина (блокує жиророзчинні), пізній вечір, дублікат із Мультивітамінами",
                avoidEn = "Fiber/Psyllium (≥2h), late evening, unmonitored Multi overlap"
            )
            IngredientTag.VITAMIN_K2 -> SupplementRecommendation(
                timingUk = "Вранці з D3 та жирною їжею",
                timingEn = "Morning alongside Vitamin D3 & dietary fat",
                tipUk = "Форма MK-7 (100 мкг) направляє кальцій у кістки, а не в судини.",
                tipEn = "MK-7 form directs calcium into bone matrix instead of soft tissue.",
                withFood = true,
                synergiesUk = "Вітамін D3, Кальцій, Омега-3",
                synergiesEn = "Vitamin D3, Calcium, Omega-3",
                avoidUk = "Псиліум/клітковина (≥2 год); обережно з антикоагулянтами (варфарин)",
                avoidEn = "Fiber/Psyllium (≥2h); consult doctor if on blood thinners (warfarin)"
            )
            IngredientTag.VITAMIN_A -> SupplementRecommendation(
                timingUk = "Із жирною їжею в першій половині дня",
                timingEn = "With a fat-containing meal",
                tipUk = "Не перевищуй верхні дози ретинолу; жиророзчинний.",
                tipEn = "Fat-soluble; watch total retinol dose if also taking a Multivitamin.",
                withFood = true,
                synergiesUk = "Вітамін D3, Цинк, Омега-3",
                synergiesEn = "Vitamin D3, Zinc, Omega-3",
                avoidUk = "Дублювання з Мультивітамінами та Псиліумом (≥2 год)",
                avoidEn = "Excess Multivitamin overlap, Fiber/Psyllium (≥2h)"
            )
            IngredientTag.VITAMIN_E -> SupplementRecommendation(
                timingUk = "Під час їжі з жирами (окремо від Заліза)",
                timingEn = "With a meal containing fat (away from Iron)",
                tipUk = "Суміш токоферолів працює найкраще разом із Селеном і Вітаміном C.",
                tipEn = "Mixed tocopherols work best alongside Selenium & Vitamin C.",
                withFood = true,
                synergiesUk = "Селен, Вітамін C, Омега-3",
                synergiesEn = "Selenium, Vitamin C, Omega-3",
                avoidUk = "Залізо в один прийом (≥2 год); у високих дозах з Омега-3+Куркуміном розріджує кров",
                avoidEn = "Iron simultaneously (≥2h); additive blood thinning with high Omega-3+Curcumin"
            )
            IngredientTag.VITAMIN_C -> SupplementRecommendation(
                timingUk = "Вранці або вдень (можна частинами)",
                timingEn = "Morning or daytime",
                tipUk = "Підвищує засвоєння заліза та синтез колагену.",
                tipEn = "Boosts non-heme Iron uptake and collagen synthesis.",
                withFood = false,
                synergiesUk = "Залізо, Колаген, Вітамін E",
                synergiesEn = "Iron, Collagen, Vitamin E",
                avoidUk = "Високі дози (≥500 мг) разом із вітаміном B12 / B-комплексом — рознось на 2 год",
                avoidEn = "High doses with Vitamin B12/B-Complex (separate by ≥2h)"
            )
            IngredientTag.B_COMPLEX -> SupplementRecommendation(
                timingUk = "Вранці після сніданку",
                timingEn = "Morning after breakfast",
                tipUk = "Підтримує енергетичний обмін і нервову систему; ввечері може давати надмірну бадьорість.",
                tipEn = "Supports energy metabolism; morning intake avoids sleep disruption.",
                withFood = true,
                synergiesUk = "Магній, Омега-3",
                synergiesEn = "Magnesium, Omega-3",
                avoidUk = "Пізній вечір, одночасний прийом з великою дозою Вітаміну C (руйнує B12) та кавою",
                avoidEn = "Late evening, simultaneous high-dose Vitamin C or coffee"
            )
            IngredientTag.MULTIVITAMIN -> SupplementRecommendation(
                timingUk = "Зі сніданком або обідом",
                timingEn = "With breakfast or lunch",
                tipUk = "Прийом із їжею прибирає нудоту й допомагає засвоїти жиророзчинні вітаміни.",
                tipEn = "Take with a main meal to avoid nausea and absorb fat-soluble vitamins.",
                withFood = true,
                synergiesUk = "Омега-3",
                synergiesEn = "Omega-3",
                avoidUk = "Слідкуй за сумарними дозами D3, Цинку, Заліза й вітаміну A; рознось із Псиліумом",
                avoidEn = "Watch cumulative D3/Zinc/Iron totals; separate from Fiber (≥2h)"
            )
            IngredientTag.ZINC -> SupplementRecommendation(
                timingUk = "Вдень під час їжі (окремо від Магнію, Міді й Заліза)",
                timingEn = "Midday with food (away from Mg, Copper, Iron)",
                tipUk = "Натщесерце часто нудить. При курсах ≥25 мг/день важливо контролювати баланс Міді.",
                tipEn = "Take with food to avoid nausea; chronic high doses deplete Copper.",
                withFood = true,
                synergiesUk = "Вітамін A, білок у їжі",
                synergiesEn = "Vitamin A, dietary protein",
                avoidUk = "Магній, Мідь, Залізо, Кальцій, Псиліум (мінімум 2 години інтервалу!)",
                avoidEn = "Magnesium, Copper, Iron, Calcium, Fiber (≥2h gap required)"
            )
            IngredientTag.MAGNESIUM -> SupplementRecommendation(
                timingUk = "Увечері перед сном (окремо від Цинку і Заліза)",
                timingEn = "Evening / Bedtime (away from Zinc & Iron)",
                tipUk = "Гліцинат/цитрат підтримує сон, знижує судоми та активує Вітамін D3.",
                tipEn = "Glycinate/citrate supports sleep, muscle relaxation, and D3 conversion.",
                withFood = false,
                synergiesUk = "Вітамін D3, L-Теанін, Гліцин, Ашваганда",
                synergiesEn = "Vitamin D3, L-Theanine, Glycine, Ashwagandha",
                avoidUk = "Цинк, Кальцій, Залізо, Псиліум в один момент (≥2 год інтервалу)",
                avoidEn = "Zinc, Calcium, Iron, Fiber simultaneously (≥2h gap)"
            )
            IngredientTag.CALCIUM -> SupplementRecommendation(
                timingUk = "Із їжею в окремий час від інших мінералів",
                timingEn = "With food, separate from Zinc/Iron/Magnesium",
                tipUk = "Порції понад 500 мг гірше засвоюються за один раз.",
                tipEn = "Absorption drops above 500 mg per serving.",
                withFood = true,
                synergiesUk = "Вітамін D3 + K2",
                synergiesEn = "Vitamin D3 + K2",
                avoidUk = "Залізо, Цинк, Магній, Кофеїн, Псиліум (≥2 год інтервалу)",
                avoidEn = "Iron, Zinc, Magnesium, Caffeine, Fiber (≥2h gap)"
            )
            IngredientTag.IRON -> SupplementRecommendation(
                timingUk = "Вранці з Вітаміном C (подалі від кави, кальцію, цинку й магнію)",
                timingEn = "Morning with Vitamin C (far from coffee/milk/Zinc/Mg)",
                tipUk = "Найсильніші інгібітори заліза — кава/чай (поліфеноли), молочне та інші мінерали.",
                tipEn = "Coffee, tea, dairy/calcium, Zinc, and Magnesium cut iron absorption steeply.",
                withFood = false,
                synergiesUk = "Вітамін C",
                synergiesEn = "Vitamin C",
                avoidUk = "Кава/Кофеїн, Кальцій, Цинк, Магній, Вітамін E, Псиліум (мінімум 2 год!)",
                avoidEn = "Coffee/Caffeine, Calcium, Zinc, Magnesium, Vitamin E, Fiber (≥2h)"
            )
            IngredientTag.COPPER -> SupplementRecommendation(
                timingUk = "Вранці з їжею (окремо від Цинку мінімум на 2 год)",
                timingEn = "Morning with meal (≥2h away from Zinc)",
                tipUk = "Потрібна при тривалому прийомі Цинку, але Цинк і Мідь конкурують напряму.",
                tipEn = "Essential balance when taking Zinc long-term, but separate by ≥2h.",
                withFood = true,
                synergiesUk = "Вітамін C (помірні дози), поєднання в добовому балансі з Цинком",
                synergiesEn = "Balanced daily ratio with Zinc (separated in time)",
                avoidUk = "Цинк, Залізо, високі дози Вітаміну C одночасно",
                avoidEn = "Zinc, Iron simultaneously (≥2h gap)"
            )
            IngredientTag.SELENIUM -> SupplementRecommendation(
                timingUk = "Під час їжі вдень",
                timingEn = "With a daytime meal",
                tipUk = "100–200 мкг селенометіоніну підтримують щитоподібну залозу й антиоксиданти.",
                tipEn = "100–200 mcg supports thyroid conversion and glutathione.",
                withFood = true,
                synergiesUk = "Вітамін E, Йод",
                synergiesEn = "Vitamin E, Iodine",
                avoidUk = "Великі дози Вітаміну C на порожній шлунок з неорганічним селенітом",
                avoidEn = "High-dose Vitamin C with inorganic sodium selenite"
            )
            IngredientTag.IODINE -> SupplementRecommendation(
                timingUk = "Вранці з їжею",
                timingEn = "Morning with food",
                tipUk = "Для щитоподібної залози найкраще працює в парі з достатнім рівнем Селену.",
                tipEn = "Works safely with adequate Selenium status for thyroid support.",
                withFood = true,
                synergiesUk = "Селен, Залізо (рознесене в часі)",
                synergiesEn = "Selenium",
                avoidUk = "Самостійні високі дози без контролю ТТГ при проблемах зі щитовидкою",
                avoidEn = "Excess dosing without thyroid monitoring"
            )
            IngredientTag.POTASSIUM -> SupplementRecommendation(
                timingUk = "Із їжею або в складі електролітів",
                timingEn = "With food or split in electrolyte drinks",
                tipUk = "Не перевищуй безпечну порцію в добавках; найкраще поєднується з Магнієм.",
                tipEn = "Keep supplemental doses moderate; pairs smoothly with Magnesium.",
                withFood = true,
                synergiesUk = "Магній, Електроліти",
                synergiesEn = "Magnesium, Electrolytes",
                avoidUk = "Кальцій і калійзберігаючі препарати без лікаря",
                avoidEn = "Potassium-sparing meds without medical supervision"
            )
            IngredientTag.BORON -> SupplementRecommendation(
                timingUk = "Із сніданком або обідом (3–6 мг)",
                timingEn = "With breakfast or lunch (3–6 mg)",
                tipUk = "Допомагає метаболізму Вітаміну D3 і Магнію (часто курсами 2 тижні / 1 тиждень).",
                tipEn = "Aids Vitamin D3 & Magnesium utilization (often cycled 2w ON / 1w OFF).",
                withFood = true,
                synergiesUk = "Вітамін D3, Магній",
                synergiesEn = "Vitamin D3, Magnesium",
                avoidUk = "Критичних конфліктів немає",
                avoidEn = "No major conflicts"
            )
            IngredientTag.OMEGA_3 -> SupplementRecommendation(
                timingUk = "Під час основного прийому їжі",
                timingEn = "With a main meal",
                tipUk = "Жири їжі посилюють засвоєння EPA/DHA та D3 і прибирають відрижку.",
                tipEn = "Dietary fats maximize EPA/DHA & D3 uptake and prevent reflux.",
                withFood = true,
                synergiesUk = "Вітамін D3, K2, CoQ10, Куркумін, Вітамін E",
                synergiesEn = "Vitamin D3, K2, CoQ10, Curcumin, Vitamin E",
                avoidUk = "Псиліум/клітковина (≥2 год); у високих дозах разом із Куркуміном+E посилює розрідження крові",
                avoidEn = "Fiber/Psyllium (≥2h); additive blood-thinning with Curcumin+E"
            )
            IngredientTag.COLLAGEN -> SupplementRecommendation(
                timingUk = "За 30–60 хв до тренування або вранці з Вітаміном C",
                timingEn = "30–60 min pre-workout or morning with Vitamin C",
                tipUk = "Вітамін C (50–250 мг) обов'язковий для зшивання колагенових волокон зв'язок і сухожиль.",
                tipEn = "Pair with Vitamin C to drive tendon/ligament collagen synthesis.",
                withFood = false,
                synergiesUk = "Вітамін C, Глюкозамін+MSM, Гіалуронова кислота",
                synergiesEn = "Vitamin C, Glucosamine+MSM",
                avoidUk = "Критичних конфліктів немає (не замінює повноцінний білок для м'язів)",
                avoidEn = "No major conflicts (does not replace complete protein)"
            )
            IngredientTag.GLUCOSAMINE_MSM -> SupplementRecommendation(
                timingUk = "Під час їжі (курсом 8–12 тижнів)",
                timingEn = "With a meal (8–12 week course)",
                tipUk = "Накопичувальна підтримка суглобів і зв'язок.",
                tipEn = "Cumulative joint and cartilage support.",
                withFood = true,
                synergiesUk = "Колаген, Омега-3, Куркумін",
                synergiesEn = "Collagen, Omega-3, Curcumin",
                avoidUk = "Обережно при алергії на молюсків (глюкозамін)",
                avoidEn = "Check shellfish source if allergic"
            )
            IngredientTag.CURCUMIN -> SupplementRecommendation(
                timingUk = "З жирною їжею (або з піперином/фосфоліпідами)",
                timingEn = "With a fatty meal (or piperine)",
                tipUk = "Потужний протизапальний ефект для суглобів; майже не засвоюється натще без жиру.",
                tipEn = "Needs fat/piperine for bioavailability; supports joint recovery.",
                withFood = true,
                synergiesUk = "Омега-3, Глюкозамін+MSM",
                synergiesEn = "Omega-3, Glucosamine+MSM",
                avoidUk = "Залізо (може трохи зв'язувати залізо), антикоагулянти + високі Омега-3/Віт.E",
                avoidEn = "Iron simultaneously; blood thinners / excess Omega-3+E stack"
            )
            IngredientTag.ASHWAGANDHA -> SupplementRecommendation(
                timingUk = "Увечері або перед сном (курсами 6–8 тиж ON / 2–4 тиж OFF)",
                timingEn = "Evening or bedtime (cycle 6–8w ON / 2–4w OFF)",
                tipUk = "Знижує кортизол і покращує сон; вранці може давати зайве розслаблення.",
                tipEn = "Lowers evening cortisol & aids sleep; cycling prevents blunted motivation.",
                withFood = true,
                synergiesUk = "Магній, L-Теанін, Гліцин",
                synergiesEn = "Magnesium, L-Theanine, Glycine",
                avoidUk = "Сильні стимулятори в один момент; при гіпертиреозі/аутоімуні потрібна консультація",
                avoidEn = "Combining directly with stimulants; check thyroid status"
            )
            IngredientTag.L_THEANINE -> SupplementRecommendation(
                timingUk = "Вдень із кавою для фокусу АБО перед сном для спокою",
                timingEn = "Daytime with coffee for smooth focus OR bedtime for calm",
                tipUk = "100–200 мг прибирають тремтіння від кофеїну, а ввечері з Магнієм заспокоюють думки.",
                tipEn = "Smooths caffeine jitters by day; deepens relaxation with Magnesium at night.",
                withFood = false,
                synergiesUk = "Кофеїн, Магній, Ашваганда, Мелатонін",
                synergiesEn = "Caffeine, Magnesium, Ashwagandha, Melatonin",
                avoidUk = "Критичних конфліктів немає",
                avoidEn = "No major conflicts"
            )
            IngredientTag.MELATONIN -> SupplementRecommendation(
                timingUk = "За 30–60 хв до сну при приглушеному світлі",
                timingEn = "30–60 min before bed in dim light",
                tipUk = "0.5–3 мг зазвичай ефективніше за високі дози без ранкової сонливості.",
                tipEn = "Low doses (0.5–3 mg) often work cleanly without morning grogginess.",
                withFood = false,
                synergiesUk = "Магній, L-Теанін, Гліцин",
                synergiesEn = "Magnesium, L-Theanine, Glycine",
                avoidUk = "Ранок / Перед тренуванням, Кофеїн або Вітамін D3 на ніч",
                avoidEn = "Morning / pre-workout, late Caffeine, late Vitamin D3"
            )
            IngredientTag.GLYCINE -> SupplementRecommendation(
                timingUk = "За 30–60 хв до сну (3 г)",
                timingEn = "30–60 min before bed (3 g)",
                tipUk = "М'яко знижує температуру тіла для глибшого сну; безпечний щодня.",
                tipEn = "Gently lowers core body temp to deepen sleep quality.",
                withFood = false,
                synergiesUk = "Магній, L-Теанін, Мелатонін, Креатин",
                synergiesEn = "Magnesium, L-Theanine, Melatonin",
                avoidUk = "Критичних конфліктів немає",
                avoidEn = "No major conflicts"
            )
            IngredientTag.PROBIOTICS -> SupplementRecommendation(
                timingUk = "Вранці за 20 хв до їжі або під час сніданку",
                timingEn = "Morning 20 min before breakfast or with breakfast",
                tipUk = "Запивай прохолодною водою (не гарячим чаєм/кавою!).",
                tipEn = "Take with cool water — hot drinks reduce live bacterial survival.",
                withFood = false,
                synergiesUk = "Клітковина/Псиліум (як пребіотик, якщо окремо від вітамінів), Глютамін",
                synergiesEn = "Prebiotics/Fiber, Glutamine",
                avoidUk = "Гарячі напої, антибіотики (розносити на ≥2–3 год)",
                avoidEn = "Hot beverages, antibiotics (separate by ≥2–3h)"
            )
            IngredientTag.NAC -> SupplementRecommendation(
                timingUk = "Вранці або вдень (600 мг, можна курсом)",
                timingEn = "Morning or daytime (600 mg)",
                tipUk = "Попередник глутатіону; підтримує печінку, дихальні шляхи та відновлення.",
                tipEn = "Glutathione precursor supporting liver, respiratory, and oxidative recovery.",
                withFood = false,
                synergiesUk = "Гліцин (GlyNAC), Вітамін C, Селен",
                synergiesEn = "Glycine (GlyNAC), Vitamin C, Selenium",
                avoidUk = "Нітрогліцерин, активоване вугілля",
                avoidEn = "Activated charcoal, nitroglycerin"
            )
            IngredientTag.COQ10 -> SupplementRecommendation(
                timingUk = "Із сніданком або обідом, що містять жири",
                timingEn = "Morning or lunch with dietary fat",
                tipUk = "Підтримує мітохондрії та серце; на ніч може трохи тонізувати.",
                tipEn = "Supports cellular ATP and heart health; take earlier to avoid alertness at night.",
                withFood = true,
                synergiesUk = "Омега-3, L-Карнітин",
                synergiesEn = "Omega-3, L-Carnitine",
                avoidUk = "Пізній вечір, Псиліум/клітковина (≥2 год)",
                avoidEn = "Late evening, Fiber/Psyllium (≥2h)"
            )
            IngredientTag.BERBERINE -> SupplementRecommendation(
                timingUk = "За 10–20 хв до або разом із вуглеводною їжею",
                timingEn = "Before or with carb-heavy meals",
                tipUk = "Згладжує глікемічні піки; краще курсами 8 тиж ON / 2 тиж OFF.",
                tipEn = "Supports glucose disposal; cycle 8w ON / 2w OFF and monitor GI comfort.",
                withFood = true,
                synergiesUk = "Омега-3, Магній",
                synergiesEn = "Omega-3, Magnesium",
                avoidUk = "Прийом натщесерце перед залом (ризик низького цукру/дискомфорту ШКТ), цукрознижувальні без лікаря",
                avoidEn = "Fasted pre-workout, combining unmonitored with diabetes meds"
            )
            IngredientTag.RHODIOLA -> SupplementRecommendation(
                timingUk = "Вранці натщесерце або за 30 хв до ранкового залу",
                timingEn = "Morning or pre-morning workout",
                tipUk = "Адаптоген проти втоми й туману в голові; ввечері часто викликає безсоння.",
                tipEn = "Energizing adaptogen against fatigue; avoid afternoon/evening dosing.",
                withFood = false,
                synergiesUk = "B-комплекс, L-Теанін",
                synergiesEn = "B-Complex, L-Theanine",
                avoidUk = "Вечір, одночасне поєднання з Мелатоніном/Ашвагандою для сну та великими дозами кофеїну (тремор)",
                avoidEn = "Evening/bedtime, sleep aids in same slot, high-caffeine jitter risk"
            )
            IngredientTag.FIBER_PSYLLIUM -> SupplementRecommendation(
                timingUk = "Окремо від усіх добавок і ліків (інтервал ≥ 2 год!) з великою склянкою води",
                timingEn = "Strictly ≥ 2h away from ALL vitamins, minerals & meds with full glass of water",
                tipUk = "Адсорбує та виводить активні речовини, якщо прийняти разом із вітамінами чи мінералами.",
                tipEn = "Gel-forming fiber traps vitamins, minerals, and oils if taken at the same time.",
                withFood = false,
                synergiesUk = "Пробіотики (в окремому від вітамінів вікні)",
                synergiesEn = "Probiotics, extra water",
                avoidUk = "УСІ вітаміни, мінерали, Омега-3 та ліки протягом 2 годин",
                avoidEn = "ALL vitamins, minerals, Omega-3, and medications within 2 hours"
            )
            IngredientTag.OTHER -> SupplementRecommendation(
                timingUk = "За індивідуальним графіком",
                timingEn = "Follow custom schedule",
                tipUk = "Регулярність важливіша за точний час; перевір етикетку щодо прийому з їжею.",
                tipEn = "Daily consistency matters most; follow label instructions.",
                withFood = false,
                synergiesUk = "—",
                synergiesEn = "—",
                avoidUk = "Не поєднуй із Псиліумом/клітковиною (≥2 год)",
                avoidEn = "Keep ≥2h away from Fiber/Psyllium"
            )
        }

    fun referenceCatalog(): List<SupplementReferenceEntry> = listOf(
        entry(IngredientTag.CREATINE, "Креатин моногідрат", "Creatine Monohydrate", SupplementCategory.CREATINE, 5.0, SupplementUnit.G, 0, SupplementTrigger.AFTER_WORKOUT, ""),
        entry(IngredientTag.PROTEIN, "Протеїн", "Whey Protein", SupplementCategory.PROTEIN, 1.0, SupplementUnit.SCOOP, 0, SupplementTrigger.AFTER_WORKOUT, ""),
        entry(IngredientTag.PRE_WORKOUT_STIM_FREE, "Передтрен (без кофеїну)", "Pre-Workout (Stim-Free)", SupplementCategory.PRE_WORKOUT, 1.0, SupplementUnit.SCOOP, 0, SupplementTrigger.BEFORE_WORKOUT, ""),
        entry(IngredientTag.PRE_WORKOUT_CAFFEINE, "Передтрен (з кофеїном)", "Pre-Workout (Stim)", SupplementCategory.PRE_WORKOUT, 1.0, SupplementUnit.SCOOP, 200, SupplementTrigger.BEFORE_WORKOUT, ""),
        entry(IngredientTag.CITRULLINE, "L-Цитрулін / Малат", "L-Citrulline", SupplementCategory.PRE_WORKOUT, 6.0, SupplementUnit.G, 0, SupplementTrigger.BEFORE_WORKOUT, ""),
        entry(IngredientTag.BETA_ALANINE, "Бета-аланін", "Beta-Alanine", SupplementCategory.PRE_WORKOUT, 3.2, SupplementUnit.G, 0, SupplementTrigger.BEFORE_WORKOUT, ""),
        entry(IngredientTag.BCAA_EAA, "EAA / BCAA амінокислоти", "EAA / BCAA", SupplementCategory.OTHER, 1.0, SupplementUnit.SCOOP, 0, SupplementTrigger.BEFORE_WORKOUT, ""),
        entry(IngredientTag.ELECTROLYTES, "Електроліти / Ізотонік", "Electrolytes", SupplementCategory.MINERAL, 1.0, SupplementUnit.SCOOP, 0, SupplementTrigger.BEFORE_WORKOUT, ""),
        entry(IngredientTag.CARNITINE, "L-Карнітин / ALCAR", "L-Carnitine / ALCAR", SupplementCategory.OTHER, 1500.0, SupplementUnit.MG, 0, SupplementTrigger.FIXED_TIME, "09:00"),
        entry(IngredientTag.TAURINE, "Таурин", "Taurine", SupplementCategory.OTHER, 2.0, SupplementUnit.G, 0, SupplementTrigger.BEFORE_WORKOUT, ""),
        entry(IngredientTag.VITAMIN_D3, "Вітамін D3", "Vitamin D3", SupplementCategory.VITAMIN, 4000.0, SupplementUnit.IU, 0, SupplementTrigger.WITH_MEAL, "09:00"),
        entry(IngredientTag.VITAMIN_K2, "Вітамін K2 (MK-7)", "Vitamin K2 (MK-7)", SupplementCategory.VITAMIN, 100.0, SupplementUnit.MCG, 0, SupplementTrigger.WITH_MEAL, "09:00"),
        entry(IngredientTag.OMEGA_3, "Омега-3 (EPA/DHA)", "Omega-3 Fish Oil", SupplementCategory.OMEGA_3, 2.0, SupplementUnit.CAPSULE, 0, SupplementTrigger.WITH_MEAL, "09:00"),
        entry(IngredientTag.VITAMIN_C, "Вітамін C", "Vitamin C", SupplementCategory.VITAMIN, 500.0, SupplementUnit.MG, 0, SupplementTrigger.WITH_MEAL, "09:00"),
        entry(IngredientTag.B_COMPLEX, "B-комплекс / B12", "B-Complex / B12", SupplementCategory.VITAMIN, 1.0, SupplementUnit.CAPSULE, 0, SupplementTrigger.WITH_MEAL, "09:00"),
        entry(IngredientTag.VITAMIN_E, "Вітамін E", "Vitamin E", SupplementCategory.VITAMIN, 200.0, SupplementUnit.IU, 0, SupplementTrigger.WITH_MEAL, "13:00"),
        entry(IngredientTag.MULTIVITAMIN, "Мультивітаміни", "Multivitamin", SupplementCategory.VITAMIN, 1.0, SupplementUnit.CAPSULE, 0, SupplementTrigger.WITH_MEAL, "09:00"),
        entry(IngredientTag.ZINC, "Цинк", "Zinc", SupplementCategory.MINERAL, 25.0, SupplementUnit.MG, 0, SupplementTrigger.FIXED_TIME, "14:00"),
        entry(IngredientTag.MAGNESIUM, "Магній (Гліцинат/Цитрат)", "Magnesium", SupplementCategory.MINERAL, 400.0, SupplementUnit.MG, 0, SupplementTrigger.BEDTIME, "23:15"),
        entry(IngredientTag.CALCIUM, "Кальцій", "Calcium", SupplementCategory.MINERAL, 500.0, SupplementUnit.MG, 0, SupplementTrigger.FIXED_TIME, "18:00"),
        entry(IngredientTag.IRON, "Залізо (Бісгліцинат)", "Iron Bisglycinate", SupplementCategory.MINERAL, 25.0, SupplementUnit.MG, 0, SupplementTrigger.FIXED_TIME, "08:00"),
        entry(IngredientTag.COPPER, "Мідь", "Copper", SupplementCategory.MINERAL, 2.0, SupplementUnit.MG, 0, SupplementTrigger.WITH_MEAL, "09:00"),
        entry(IngredientTag.SELENIUM, "Селен", "Selenium", SupplementCategory.MINERAL, 200.0, SupplementUnit.MCG, 0, SupplementTrigger.WITH_MEAL, "13:00"),
        entry(IngredientTag.IODINE, "Йод / Келп", "Iodine / Kelp", SupplementCategory.MINERAL, 150.0, SupplementUnit.MCG, 0, SupplementTrigger.WITH_MEAL, "09:00"),
        entry(IngredientTag.COLLAGEN, "Колаген (Гідролізований)", "Hydrolyzed Collagen", SupplementCategory.PROTEIN, 10.0, SupplementUnit.G, 0, SupplementTrigger.FIXED_TIME, "08:30"),
        entry(IngredientTag.GLUCOSAMINE_MSM, "Глюкозамін + Хондроїтин + MSM", "Glucosamine + MSM", SupplementCategory.OTHER, 2.0, SupplementUnit.CAPSULE, 0, SupplementTrigger.WITH_MEAL, "13:00"),
        entry(IngredientTag.CURCUMIN, "Куркумін", "Curcumin", SupplementCategory.OTHER, 500.0, SupplementUnit.MG, 0, SupplementTrigger.WITH_MEAL, "13:00"),
        entry(IngredientTag.ASHWAGANDHA, "Ашваганда (KSM-66)", "Ashwagandha (KSM-66)", SupplementCategory.OTHER, 600.0, SupplementUnit.MG, 0, SupplementTrigger.BEDTIME, "23:15"),
        entry(IngredientTag.L_THEANINE, "L-Теанін", "L-Theanine", SupplementCategory.OTHER, 200.0, SupplementUnit.MG, 0, SupplementTrigger.BEDTIME, "23:15"),
        entry(IngredientTag.MELATONIN, "Мелатонін", "Melatonin", SupplementCategory.MEDICATION, 1.0, SupplementUnit.MG, 0, SupplementTrigger.BEDTIME, "23:30"),
        entry(IngredientTag.GLYCINE, "Гліцин", "Glycine", SupplementCategory.OTHER, 3.0, SupplementUnit.G, 0, SupplementTrigger.BEDTIME, "23:15"),
        entry(IngredientTag.COQ10, "Коензим CoQ10", "CoQ10 Ubiquinol", SupplementCategory.OTHER, 100.0, SupplementUnit.MG, 0, SupplementTrigger.WITH_MEAL, "09:00"),
        entry(IngredientTag.RHODIOLA, "Родіола рожева", "Rhodiola Rosea", SupplementCategory.OTHER, 300.0, SupplementUnit.MG, 0, SupplementTrigger.FIXED_TIME, "09:00"),
        entry(IngredientTag.PROBIOTICS, "Пробіотики", "Probiotics", SupplementCategory.OTHER, 1.0, SupplementUnit.CAPSULE, 0, SupplementTrigger.FIXED_TIME, "08:00"),
        entry(IngredientTag.NAC, "NAC (N-Ацетилцистеїн)", "NAC", SupplementCategory.OTHER, 600.0, SupplementUnit.MG, 0, SupplementTrigger.FIXED_TIME, "10:00"),
        entry(IngredientTag.BERBERINE, "Берберин", "Berberine", SupplementCategory.OTHER, 500.0, SupplementUnit.MG, 0, SupplementTrigger.WITH_MEAL, "13:00"),
        entry(IngredientTag.FIBER_PSYLLIUM, "Псиліум / Клітковина", "Psyllium Fiber", SupplementCategory.OTHER, 5.0, SupplementUnit.G, 0, SupplementTrigger.FIXED_TIME, "16:30")
    )

    private fun entry(
        tag: IngredientTag,
        nameUk: String,
        nameEn: String,
        cat: SupplementCategory,
        dose: Double,
        unit: SupplementUnit,
        caffeineMg: Int,
        trig: SupplementTrigger,
        time: String
    ) = SupplementReferenceEntry(
        tag = tag,
        nameUk = nameUk,
        nameEn = nameEn,
        category = cat,
        defaultDose = dose,
        unit = unit,
        caffeineMg = caffeineMg,
        defaultTrigger = trig,
        defaultTime = time,
        recommendation = recommendationFor(tag)
    )

    fun previewForCandidate(
        candidate: SupplementItemEntity,
        schedule: SupplementScheduleEntity?,
        existingStack: List<SupplementWithSchedules>,
        bedtimeMinutesOfDay: Int = 0,
        now: Instant = Instant.now(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<StackInteraction> {
        val tempId = if (candidate.id != 0L) candidate.id else -1L
        val synItem = candidate.copy(id = tempId)
        val synScheds = listOfNotNull(schedule?.copy(supplementId = tempId, enabled = true, deletedAt = null))
        val merged = existingStack.filter { it.item.id != tempId } + SupplementWithSchedules(synItem, synScheds)
        return analyzeStack(merged, emptyList(), now, bedtimeMinutesOfDay, zoneId)
            .filter { tempId in it.involvedItemIds }
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

        val d3 = byTag[IngredientTag.VITAMIN_D3].orEmpty()
        val k2 = byTag[IngredientTag.VITAMIN_K2].orEmpty()
        val omega = byTag[IngredientTag.OMEGA_3].orEmpty()
        val mag = byTag[IngredientTag.MAGNESIUM].orEmpty()
        val zinc = byTag[IngredientTag.ZINC].orEmpty()
        val copper = byTag[IngredientTag.COPPER].orEmpty()
        val calcium = byTag[IngredientTag.CALCIUM].orEmpty()
        val iron = byTag[IngredientTag.IRON].orEmpty()
        val vitC = byTag[IngredientTag.VITAMIN_C].orEmpty()
        val vitE = byTag[IngredientTag.VITAMIN_E].orEmpty()
        val vitA = byTag[IngredientTag.VITAMIN_A].orEmpty()
        val bComp = byTag[IngredientTag.B_COMPLEX].orEmpty()
        val multi = byTag[IngredientTag.MULTIVITAMIN].orEmpty()
        val selenium = byTag[IngredientTag.SELENIUM].orEmpty()
        val iodine = byTag[IngredientTag.IODINE].orEmpty()
        val creatine = byTag[IngredientTag.CREATINE].orEmpty()
        val protein = byTag[IngredientTag.PROTEIN].orEmpty()
        val collagen = byTag[IngredientTag.COLLAGEN].orEmpty()
        val curcumin = byTag[IngredientTag.CURCUMIN].orEmpty()
        val coq10 = byTag[IngredientTag.COQ10].orEmpty()
        val theanine = byTag[IngredientTag.L_THEANINE].orEmpty()
        val ashwagandha = byTag[IngredientTag.ASHWAGANDHA].orEmpty()
        val melatonin = byTag[IngredientTag.MELATONIN].orEmpty()
        val glycine = byTag[IngredientTag.GLYCINE].orEmpty()
        val rhodiola = byTag[IngredientTag.RHODIOLA].orEmpty()
        val carnitine = byTag[IngredientTag.CARNITINE].orEmpty()
        val fiber = byTag[IngredientTag.FIBER_PSYLLIUM].orEmpty()
        val stimulants = byTag[IngredientTag.CAFFEINE].orEmpty() + byTag[IngredientTag.PRE_WORKOUT_CAFFEINE].orEmpty()
        val sleepAids = melatonin + ashwagandha + glycine

        // 1. Synergies
        fun addSynergy(id: String, a: List<SupplementWithSchedules>, b: List<SupplementWithSchedules>, titleUk: String, titleEn: String, detailUk: String, detailEn: String) {
            if (a.isEmpty() || b.isEmpty()) return
            result += StackInteraction(
                id = id,
                kind = InteractionKind.SYNERGY,
                involvedItemIds = (a + b).map { it.item.id }.toSet(),
                titleUk = titleUk,
                titleEn = titleEn,
                detailUk = detailUk,
                detailEn = detailEn
            )
        }

        addSynergy(
            "synergy_d3_k2", d3, k2,
            "Синергія: Вітамін D3 + K2", "Synergy: Vitamin D3 + K2",
            "K2 (MK-7) направляє кальцій, що засвоївся завдяки D3, саме в кістки, а не в судини.",
            "K2 (MK-7) directs D3-absorbed calcium into bones rather than arteries."
        )
        addSynergy(
            "synergy_d3_omega", d3, omega,
            "Синергія: Вітамін D3 + Омега-3", "Synergy: Vitamin D3 + Omega-3",
            if (hasCloseSchedule(d3, omega, bedtimeMinutesOfDay, 90))
                "Приймаються разом — жири Омега-3 підвищують засвоєння D3."
            else
                "Добре поєднувати D3 та Омега-3 в один прийом з їжею для кращого засвоєння.",
            if (hasCloseSchedule(d3, omega, bedtimeMinutesOfDay, 90))
                "Scheduled together — fats in Omega-3 boost Vitamin D3 absorption."
            else
                "Pair Vitamin D3 and Omega-3 with the same meal for optimal fat-soluble uptake."
        )
        addSynergy(
            "synergy_d3_mag", d3, mag,
            "Синергія: Магній допомагає роботі D3", "Synergy: Magnesium activates Vitamin D3",
            "Магній є кофактором ферментів, що перетворюють D3 в активну форму.",
            "Magnesium is a required cofactor converting Vitamin D3 into active form."
        )
        addSynergy(
            "synergy_creatine_protein", creatine, protein,
            "Синергія: Креатин + Протеїн після тренування", "Synergy: Creatine + Protein post-workout",
            "Зручна комбінація після залу для відновлення запасів АТФ і синтезу м'язового білка.",
            "Ideal post-workout pairing supporting ATP replenishment and muscle protein synthesis."
        )
        addSynergy(
            "synergy_iron_vitc", iron, vitC,
            "Синергія: Залізо + Вітамін C", "Synergy: Iron + Vitamin C",
            "Вітамін C переводить залізо в форму, яка засвоюється в 2–3 рази краще.",
            "Vitamin C boosts non-heme Iron absorption 2–3× when taken together."
        )
        addSynergy(
            "synergy_collagen_vitc", collagen, vitC,
            "Синергія: Колаген + Вітамін C", "Synergy: Collagen + Vitamin C",
            "Вітамін C є обов'язковим кофактором синтезу колагенових волокон зв'язок і сухожиль.",
            "Vitamin C is required for cross-linking collagen fibers in tendons and ligaments."
        )
        addSynergy(
            "synergy_caffeine_theanine", stimulants, theanine,
            "Синергія: Кофеїн + L-Теанін", "Synergy: Caffeine + L-Theanine",
            "Дає чистий фокус і енергію без тремору, тахікардії та різкого спаду.",
            "Delivers calm mental focus while smoothing out caffeine jitters."
        )
        addSynergy(
            "synergy_mag_sleep", mag, (theanine + glycine + ashwagandha + melatonin),
            "Синергія: Вечірній стек сну й релаксу", "Synergy: Evening Sleep & Recovery Stack",
            "Магній підсилює дію L-Теаніну / Гліцину / Ашваганди для глибшого відновлення.",
            "Magnesium pairs smoothly with L-Theanine / Glycine / Ashwagandha for deeper sleep."
        )
        addSynergy(
            "synergy_omega_fat_soluble", omega, (coq10 + curcumin + vitE),
            "Синергія: Жиророзчинне засвоєння з Омега-3", "Synergy: Enhanced Uptake with Omega-3",
            "Омега-3 підсилює біодоступність CoQ10, Куркуміну та Вітаміну E під час їжі.",
            "Omega-3 dietary lipids improve CoQ10, Curcumin, and Vitamin E absorption."
        )
        addSynergy(
            "synergy_selenium_iodine", selenium, iodine,
            "Синергія: Селен + Йод для щитовидки", "Synergy: Selenium + Iodine",
            "Селен необхідний для безпечної конверсії гормонів щитоподібної залози при прийомі йоду.",
            "Selenium supports safe thyroid hormone conversion alongside Iodine."
        )

        // 2. Fiber adsorption conflicts against all other items
        if (fiber.isNotEmpty()) {
            val nonFiber = items.filter { classify(it.item) != IngredientTag.FIBER_PSYLLIUM }
            for (other in nonFiber) {
                val otherTag = classify(other.item)
                val closeSched = hasCloseSchedule(fiber, listOf(other), bedtimeMinutesOfDay, 120)
                val fiberLog = latestLogByTag[IngredientTag.FIBER_PSYLLIUM]
                val minSinceFiber = fiberLog?.let { Duration.between(it, now).toMinutes() }
                val liveWait = if (minSinceFiber != null && minSinceFiber in 0..119 && latestLogByTag[otherTag] == null) {
                    120 - minSinceFiber
                } else null

                if (liveWait != null || closeSched) {
                    result += StackInteraction(
                        id = "conflict_fiber_${other.item.id}",
                        kind = InteractionKind.CONFLICT,
                        involvedItemIds = (fiber.map { it.item.id } + other.item.id).toSet(),
                        titleUk = if (liveWait != null)
                            "Псиліум заблокує ${other.item.nameUk} • зачекай $liveWait хв"
                        else
                            "Рознести Псиліум і ${other.item.nameUk} (≥ 2 год)",
                        titleEn = if (liveWait != null)
                            "Fiber blocks ${other.item.nameEn} • wait $liveWait min"
                        else
                            "Separate Fiber & ${other.item.nameEn} (≥ 2h)",
                        detailUk = "Клітковина зв'язує вітаміни, мінерали та Омега-3 в кишечнику — інтервал мінімум 2 години.",
                        detailEn = "Gel-forming fiber binds vitamins, minerals, and oils — keep ≥2 hours apart."
                    )
                }
            }
        }

        // 3. Mineral, Caffeine & Vitamin Separation Conflicts (Schedule + Live Logs)
        checkSeparationPair("sep_zinc_mag", zinc, mag, IngredientTag.ZINC, IngredientTag.MAGNESIUM, latestLogByTag, now, bedtimeMinutesOfDay, 120,
            "Рознести Цинк та Магній (≥ 2 год)", "Separate Zinc & Magnesium (≥ 2h)",
            "Конкурують при всмоктуванні у високих дозах. Оптимально: Цинк вдень із їжею, Магній перед сном.",
            "High doses compete for transport. Best: Zinc midday with food, Magnesium at bedtime.", result, emitOk = true)
        checkSeparationPair("sep_zinc_copper", zinc, copper, IngredientTag.ZINC, IngredientTag.COPPER, latestLogByTag, now, bedtimeMinutesOfDay, 120,
            "Рознести Цинк і Мідь (≥ 2 год)", "Separate Zinc & Copper (≥ 2h)",
            "Цинк і Мідь — прямі антагоністи при всмоктуванні; приймай в різні прийоми їжі.",
            "Zinc strongly blocks Copper when taken simultaneously; separate by ≥2 hours.", result)
        checkSeparationPair("sep_zinc_iron", zinc, iron, IngredientTag.ZINC, IngredientTag.IRON, latestLogByTag, now, bedtimeMinutesOfDay, 120,
            "Рознести Цинк і Залізо (≥ 2 год)", "Separate Zinc & Iron (≥ 2h)",
            "Цинк і Залізо конкурують за ті самі рецептори в кишечнику.",
            "Zinc and Iron compete directly for divalent metal transporters.", result)
        checkSeparationPair("sep_zinc_calcium", zinc, calcium, IngredientTag.ZINC, IngredientTag.CALCIUM, latestLogByTag, now, bedtimeMinutesOfDay, 120,
            "Рознести Цинк і Кальцій (≥ 2 год)", "Separate Zinc & Calcium (≥ 2h)",
            "Кальцій знижує біодоступність Цинку при одночасному прийомі.",
            "Calcium reduces Zinc uptake when taken at the same time.", result)
        checkSeparationPair("sep_iron_calcium", iron, calcium, IngredientTag.IRON, IngredientTag.CALCIUM, latestLogByTag, now, bedtimeMinutesOfDay, 120,
            "НЕ поєднувати Залізо і Кальцій (≥ 2 год)", "Avoid Iron + Calcium together (≥ 2h)",
            "Кальцій різко блокує засвоєння Заліза. Приймай Залізо вранці, Кальцій пізніше.",
            "Calcium blocks both heme and non-heme Iron absorption. Separate by ≥2 hours.", result)
        checkSeparationPair("sep_iron_mag", iron, mag, IngredientTag.IRON, IngredientTag.MAGNESIUM, latestLogByTag, now, bedtimeMinutesOfDay, 120,
            "Рознести Залізо і Магній (≥ 2 год)", "Separate Iron & Magnesium (≥ 2h)",
            "Двовалентні метали заважають повному засвоєнню Заліза.",
            "Magnesium reduces Iron absorption when taken together.", result)
        checkSeparationPair("sep_iron_vite", iron, vitE, IngredientTag.IRON, IngredientTag.VITAMIN_E, latestLogByTag, now, bedtimeMinutesOfDay, 120,
            "Рознести Залізо і Вітамін E (≥ 2 год)", "Separate Iron & Vitamin E (≥ 2h)",
            "Залізо може окиснювати Вітамін E у шлунку та знижувати ефект обох.",
            "Iron and Vitamin E can interfere with each other when taken simultaneously.", result)
        checkSeparationPair("sep_iron_caffeine", iron, stimulants, IngredientTag.IRON, IngredientTag.CAFFEINE, latestLogByTag, now, bedtimeMinutesOfDay, 120,
            "Не запивати Залізо кавою/кофеїном (≥ 2 год)", "Keep Iron ≥ 2h away from Caffeine/Coffee",
            "Кофеїн і поліфеноли кави знижують всмоктування Заліза на 40–60%.",
            "Coffee and caffeine cut Iron absorption by 40–60% if taken within 2 hours.", result)
        checkSeparationPair("sep_mag_calcium", mag, calcium, IngredientTag.MAGNESIUM, IngredientTag.CALCIUM, latestLogByTag, now, bedtimeMinutesOfDay, 120,
            "Рознести Магній і Кальцій (≥ 2 год)", "Separate Magnesium & Calcium (≥ 2h)",
            "Великі дози Кальцію й Магнію краще розділяти по часу доби.",
            "Large Magnesium and Calcium doses absorb better when separated by ≥2 hours.", result)
        checkSeparationPair("sep_vitc_b12", vitC, bComp, IngredientTag.VITAMIN_C, IngredientTag.B_COMPLEX, latestLogByTag, now, bedtimeMinutesOfDay, 120,
            "Рознести високі дози Вітаміну C та B-комплексу (≥ 2 год)", "Separate high Vitamin C & B-Complex (≥ 2h)",
            "Високі дози аскорбінової кислоти в одному прийомі можуть руйнувати B12.",
            "High Vitamin C doses can degrade Vitamin B12 if swallowed together.", result)

        if (stimulants.isNotEmpty() && sleepAids.isNotEmpty() && hasCloseSchedule(stimulants, sleepAids, bedtimeMinutesOfDay, 180)) {
            result += StackInteraction(
                id = "conflict_stim_sleep",
                kind = InteractionKind.CONFLICT,
                involvedItemIds = (stimulants + sleepAids).map { it.item.id }.toSet(),
                titleUk = "Стимулятори конфліктують із вечірніми засобами для сну",
                titleEn = "Stimulants conflict with Sleep Aids",
                detailUk = "Кофеїн і Мелатонін/Ашваганда в близькому вікні (< 3 год) знецінюють ефект одне одного.",
                detailEn = "Caffeine/Stimulants close to Melatonin/Ashwagandha counteract sleep onset."
            )
        }
        if (rhodiola.isNotEmpty() && sleepAids.isNotEmpty() && hasCloseSchedule(rhodiola, sleepAids, bedtimeMinutesOfDay, 180)) {
            result += StackInteraction(
                id = "conflict_rhodiola_sleep",
                kind = InteractionKind.CAUTION,
                involvedItemIds = (rhodiola + sleepAids).map { it.item.id }.toSet(),
                titleUk = "Родіола ввечері може перебивати добавки для сну",
                titleEn = "Rhodiola near Sleep Aids",
                detailUk = "Перенеси Родіолу на ранок або до обіду, а сонний стек залиш на вечір.",
                detailEn = "Take energizing Rhodiola in the morning; keep sleep supplements at night."
            )
        }

        // 4. Duplication, Blood-Thinning & Individual Timing Cautions
        if (multi.isNotEmpty() && (d3.isNotEmpty() || zinc.isNotEmpty() || iron.isNotEmpty() || vitA.isNotEmpty())) {
            val overlapped = multi + d3 + zinc + iron + vitA
            result += StackInteraction(
                id = "caution_multi_overlap",
                kind = InteractionKind.CAUTION,
                involvedItemIds = overlapped.map { it.item.id }.toSet(),
                titleUk = "Перевір сумарну дозу з Мультивітамінами",
                titleEn = "Check cumulative doses with Multivitamin",
                detailUk = "Мультивітаміни вже містять D3, Цинк, Вітамін A або Залізо — не перевищуй добові ліміти.",
                detailEn = "Multivitamins already supply D3, Zinc, Iron, or Vitamin A — verify combined daily totals."
            )
        }

        if (omega.isNotEmpty() && curcumin.isNotEmpty() && vitE.isNotEmpty()) {
            result += StackInteraction(
                id = "caution_blood_thinning",
                kind = InteractionKind.CAUTION,
                involvedItemIds = (omega + curcumin + vitE).map { it.item.id }.toSet(),
                titleUk = "Омега-3 + Куркумін + Вітамін E разом",
                titleEn = "Omega-3 + Curcumin + Vitamin E",
                detailUk = "Усі три помірно розріджують кров — будь обережний перед операціями чи з антикоагулянтами.",
                detailEn = "Combining high-dose Omega-3, Curcumin, and Vitamin E has additive blood-thinning effects."
            )
        }

        if (zinc.isNotEmpty() && copper.isEmpty()) {
            val highZinc = zinc.any { it.item.defaultDose >= 25.0 }
            if (highZinc) {
                result += StackInteraction(
                    id = "caution_zinc_no_copper",
                    kind = InteractionKind.CAUTION,
                    involvedItemIds = zinc.map { it.item.id }.toSet(),
                    titleUk = "Тривалий Цинк ≥25 мг виснажує Мідь",
                    titleEn = "Chronic Zinc ≥25 mg can deplete Copper",
                    detailUk = "При курсі довше 4–6 тижнів корисно додати 1–2 мг Міді (окремо від Цинку ≥2 год).",
                    detailEn = "For courses >4–6 weeks, consider 1–2 mg Copper taken ≥2h apart from Zinc."
                )
            }
        }

        // Individual timing checks
        for (sw in items) {
            val tag = classify(sw.item)
            val trig = sw.schedules.firstOrNull { it.enabled && it.deletedAt == null }?.triggerType
            val mins = effectiveScheduleMinutes(sw.schedules, bedtimeMinutesOfDay)

            if (tag in setOf(IngredientTag.VITAMIN_D3, IngredientTag.B_COMPLEX, IngredientTag.RHODIOLA, IngredientTag.COQ10, IngredientTag.CARNITINE) &&
                ((mins != null && mins >= 19 * 60) || trig == SupplementTrigger.BEDTIME)
            ) {
                result += StackInteraction(
                    id = "caution_late_${sw.item.id}",
                    kind = InteractionKind.CAUTION,
                    involvedItemIds = setOf(sw.item.id),
                    titleUk = "${sw.item.nameUk} ввечері може погіршити сон",
                    titleEn = "${sw.item.nameEn} late evening may disturb sleep",
                    detailUk = "Перенеси прийом на ранок або обід.",
                    detailEn = "Move intake to morning or midday."
                )
            }

            if (tag in setOf(IngredientTag.ZINC, IngredientTag.IRON, IngredientTag.MULTIVITAMIN, IngredientTag.VITAMIN_D3, IngredientTag.VITAMIN_K2, IngredientTag.OMEGA_3, IngredientTag.CURCUMIN, IngredientTag.COQ10) &&
                (trig == SupplementTrigger.BEFORE_WORKOUT || trig == SupplementTrigger.BEDTIME)
            ) {
                result += StackInteraction(
                    id = "caution_food_${sw.item.id}",
                    kind = InteractionKind.CAUTION,
                    involvedItemIds = setOf(sw.item.id),
                    titleUk = "${sw.item.nameUk} краще приймати з їжею",
                    titleEn = "Take ${sw.item.nameEn} with food",
                    detailUk = "Натщесерце перед залом чи сном гірше засвоюється або може викликати дискомфорт ШКТ.",
                    detailEn = "Fasted pre-workout or bedtime dosing lowers fat/meal uptake or risks stomach upset."
                )
            }

            if (tag == IngredientTag.MELATONIN &&
                (trig == SupplementTrigger.BEFORE_WORKOUT || (mins != null && mins < 17 * 60))
            ) {
                result += StackInteraction(
                    id = "caution_melatonin_day_${sw.item.id}",
                    kind = InteractionKind.CAUTION,
                    involvedItemIds = setOf(sw.item.id),
                    titleUk = "Мелатонін вдень викликає сонливість",
                    titleEn = "Daytime Melatonin causes drowsiness",
                    detailUk = "Використовуй лише за 30–60 хв перед сном.",
                    detailEn = "Only take 30–60 min before bedtime."
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
        gapMin: Int,
        titleUk: String,
        titleEn: String,
        adviceUk: String,
        adviceEn: String,
        result: MutableList<StackInteraction>,
        emitOk: Boolean = false
    ) {
        if (a.isEmpty() || b.isEmpty()) return
        val ids = (a + b).map { it.item.id }.toSet()

        val logA = latestLogByTag[tagA]
        val logB = latestLogByTag[tagB]
        val minSinceA = logA?.let { Duration.between(it, now).toMinutes() }
        val minSinceB = logB?.let { Duration.between(it, now).toMinutes() }

        val liveWaitMinutes = when {
            minSinceA != null && minSinceA in 0 until gapMin && logB == null -> gapMin - minSinceA
            minSinceB != null && minSinceB in 0 until gapMin && logA == null -> gapMin - minSinceB
            else -> null
        }

        val closeInSchedule = hasCloseSchedule(a, b, bedtimeMinutesOfDay, maxGapMin = gapMin)
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
        } else if (emitOk) {
            result += StackInteraction(
                id = "${id}_ok",
                kind = InteractionKind.SYNERGY,
                involvedItemIds = ids,
                titleUk = "Сумісність ОК: Цинк і Магній рознесені",
                titleEn = "Spacing OK: Zinc & Magnesium separated",
                detailUk = adviceUk,
                detailEn = adviceEn
            )
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
                    val diff = abs(minA - minB)
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
