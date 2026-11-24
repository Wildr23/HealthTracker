package com.gymtracker.app.data.db

enum class ExerciseType {
    WEIGHTED,
    BODYWEIGHT,
    CARDIO
}

enum class Muscle {
    CHEST,
    BACK,
    LATS,
    TRAPS,
    LOWER_BACK,
    FRONT_DELTS,
    SIDE_DELTS,
    REAR_DELTS,
    BICEPS,
    TRICEPS,
    FOREARMS,
    ABS,
    HIP_FLEXORS,
    QUADS,
    HAMSTRINGS,
    GLUTES,
    CALVES,
    CARDIO
}

enum class CalorieTier {
    TIER_1_LOWER_COMPOUND,
    TIER_2_UPPER_COMPOUND,
    TIER_3_ISOLATION,
    CARDIO
}

enum class DataSource {
    APP,
    JEFIT_IMPORT,
    HEALTH_CONNECT
}

enum class HcStatus {
    NOT_ATTEMPTED,
    PENDING,
    MATCHED,
    HR_ONLY,
    NO_DATA,
    ERROR
}

enum class SetType {
    WORK,
    WARMUP,
    DROP,
    FAILURE
}

enum class CalorieKind {
    TOTAL,
    ACTIVE
}

enum class SyncState {
    PENDING_UPLOAD,
    SYNCED,
    FAILED
}

enum class OneRmFormula {
    EPLEY,
    BRZYCKI,
    LOMBARDI
}

enum class PrType {
    BEST_E1RM,
    MAX_WEIGHT,
    MAX_REPS_AT_WEIGHT,
    MAX_SET_VOLUME,
    MAX_SESSION_VOLUME
}

// Phase 2-4 forward-compatible enums
enum class SupplementCategory {
    PROTEIN,
    CREATINE,
    PRE_WORKOUT,
    OMEGA_3,
    VITAMIN,
    MINERAL,
    MEDICATION,
    OTHER
}

enum class SupplementUnit {
    G,
    MG,
    MCG,
    IU,
    ML,
    CAPSULE,
    SCOOP
}

enum class SupplementTrigger {
    FIXED_TIME,
    BEFORE_WORKOUT,
    AFTER_WORKOUT,
    WITH_MEAL,
    BEDTIME
}

enum class HealthEventType {
    ILLNESS,
    INJURY,
    MEDICATION_COURSE
}

enum class BodyZone {
    LEFT_SHOULDER,
    RIGHT_SHOULDER,
    LOWER_BACK,
    UPPER_BACK,
    LEFT_KNEE,
    RIGHT_KNEE,
    LEFT_ELBOW,
    RIGHT_ELBOW,
    LEFT_WRIST,
    RIGHT_WRIST,
    NECK,
    LEFT_HIP,
    RIGHT_HIP,
    CHEST,
    ABS
}

enum class ReportPeriodType {
    WEEKLY,
    MONTHLY
}
