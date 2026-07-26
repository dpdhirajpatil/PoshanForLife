package com.poshanforlife.android.feature.practitioner.upload

import com.poshanforlife.android.core.network.InBodyDataDto

data class FieldSpec(val key: String, val label: String, val unit: String, val isInt: Boolean = false)

data class FieldGroupSpec(val title: String, val fields: List<FieldSpec>)

/**
 * Backend's InBodyData is a flat 20-field scaffold with no server-side
 * grouping (see InBodyData.java) — these groups follow the section names
 * from the original Expo/web spec ("Body Composition, Muscle-Fat, Obesity,
 * Score, Segmental, Impedance"), best-fit-mapped onto the 20 fields that
 * actually exist. "Segmental" (per-limb lean mass) is omitted entirely —
 * no backend field models it; add a group here if a future backend prompt
 * extends InBodyData with real segmental data. "Impedance" holds
 * intracellular/extracellular water as the closest available proxy, since
 * there's no raw per-segment impedance (ohm) reading in the schema either.
 */
val REPORT_FIELD_GROUPS: List<FieldGroupSpec> = listOf(
    FieldGroupSpec(
        title = "Body Composition",
        fields = listOf(
            FieldSpec("weightKg", "Weight", "kg"),
            FieldSpec("bodyWaterL", "Total body water", "L"),
            FieldSpec("proteinKg", "Protein", "kg"),
            FieldSpec("mineralKg", "Mineral", "kg"),
            FieldSpec("bodyFatMassKg", "Body fat mass", "kg"),
            FieldSpec("fatFreeMassKg", "Fat-free mass", "kg"),
        ),
    ),
    FieldGroupSpec(
        title = "Muscle-Fat",
        fields = listOf(
            FieldSpec("skeletalMuscleMassKg", "Skeletal muscle mass", "kg"),
            FieldSpec("bodyFatPercent", "Body fat %", "%"),
        ),
    ),
    FieldGroupSpec(
        title = "Obesity",
        fields = listOf(
            FieldSpec("bmi", "BMI", ""),
            FieldSpec("waistHipRatio", "Waist-hip ratio", ""),
            FieldSpec("visceralFatLevel", "Visceral fat level", ""),
            FieldSpec("obesityDegreePercent", "Obesity degree", "%"),
            FieldSpec("targetWeightKg", "Target weight", "kg"),
            FieldSpec("weightControlKg", "Weight control", "kg"),
            FieldSpec("fatControlKg", "Fat control", "kg"),
            FieldSpec("muscleControlKg", "Muscle control", "kg"),
        ),
    ),
    FieldGroupSpec(
        title = "Score",
        fields = listOf(
            FieldSpec("inbodyScore", "InBody score", "", isInt = true),
            FieldSpec("basalMetabolicRate", "Basal metabolic rate", "kcal"),
        ),
    ),
    FieldGroupSpec(
        title = "Impedance",
        fields = listOf(
            FieldSpec("intracellularWaterL", "Intracellular water", "L"),
            FieldSpec("extracellularWaterL", "Extracellular water", "L"),
        ),
    ),
)

/** Total field count backing the confidence-tier thresholds in ReportUploadViewModel. */
val REPORT_FIELD_COUNT: Int = REPORT_FIELD_GROUPS.sumOf { it.fields.size }

fun InBodyDataDto.valueFor(key: String): Double? = when (key) {
    "weightKg" -> weightKg
    "bodyFatPercent" -> bodyFatPercent
    "skeletalMuscleMassKg" -> skeletalMuscleMassKg
    "bmi" -> bmi
    "visceralFatLevel" -> visceralFatLevel
    "bodyWaterL" -> bodyWaterL
    "proteinKg" -> proteinKg
    "mineralKg" -> mineralKg
    "basalMetabolicRate" -> basalMetabolicRate
    "bodyFatMassKg" -> bodyFatMassKg
    "fatFreeMassKg" -> fatFreeMassKg
    "waistHipRatio" -> waistHipRatio
    "targetWeightKg" -> targetWeightKg
    "weightControlKg" -> weightControlKg
    "fatControlKg" -> fatControlKg
    "muscleControlKg" -> muscleControlKg
    "obesityDegreePercent" -> obesityDegreePercent
    "intracellularWaterL" -> intracellularWaterL
    "extracellularWaterL" -> extracellularWaterL
    "inbodyScore" -> inbodyScore?.toDouble()
    else -> null
}

fun InBodyDataDto.withValue(key: String, value: Double?): InBodyDataDto = when (key) {
    "weightKg" -> copy(weightKg = value)
    "bodyFatPercent" -> copy(bodyFatPercent = value)
    "skeletalMuscleMassKg" -> copy(skeletalMuscleMassKg = value)
    "bmi" -> copy(bmi = value)
    "visceralFatLevel" -> copy(visceralFatLevel = value)
    "bodyWaterL" -> copy(bodyWaterL = value)
    "proteinKg" -> copy(proteinKg = value)
    "mineralKg" -> copy(mineralKg = value)
    "basalMetabolicRate" -> copy(basalMetabolicRate = value)
    "bodyFatMassKg" -> copy(bodyFatMassKg = value)
    "fatFreeMassKg" -> copy(fatFreeMassKg = value)
    "waistHipRatio" -> copy(waistHipRatio = value)
    "targetWeightKg" -> copy(targetWeightKg = value)
    "weightControlKg" -> copy(weightControlKg = value)
    "fatControlKg" -> copy(fatControlKg = value)
    "muscleControlKg" -> copy(muscleControlKg = value)
    "obesityDegreePercent" -> copy(obesityDegreePercent = value)
    "intracellularWaterL" -> copy(intracellularWaterL = value)
    "extracellularWaterL" -> copy(extracellularWaterL = value)
    "inbodyScore" -> copy(inbodyScore = value?.toInt())
    else -> this
}

fun InBodyDataDto.nonNullFieldCount(): Int =
    REPORT_FIELD_GROUPS.sumOf { group -> group.fields.count { valueFor(it.key) != null } }
